/* Macula IAM integration lab. Author: Rain. Credentials never enter localStorage or logs. */
(() => {
  'use strict';
  const $ = (id) => document.getElementById(id);
  const root = '/api/v1/iam-playground';
  const bindingKey = 'iam-playground-binding';
  const flowKey = 'iam-playground-flow';
  const lastKey = 'iam-playground-last';
  // Remove the callback code/state before the first asynchronous operation.
  const callbackParameters = document.body.dataset.page === 'callback' ? new URLSearchParams(location.search) : null;
  if (callbackParameters) history.replaceState(null, '', location.pathname);
  let configuration;
  let current;
  let scene = 'h5';
  let timer;
  let revealTimer;
  let generation = 0;
  let polling = false;
  let pollFailures = 0;
  let submitting = false;
  const scenes = {
    h5: {
      title: 'H5 与 IAM 的第一次握手', description: '从浏览器登录到获取用户信息，看清每一次请求由谁发出。',
      actor: '浏览器 / H5', actorNote: 'Web Crypto 生成一次性 PKCE、state、nonce',
      grant: 'Authorization Code + S256 PKCE', oidc: true,
      preparation: '准备公共客户端与测试身份。演示由后端中转换码以验证会话归属；纯 SPA 接入时，应由你的客户端直接调用许可的 token endpoint。',
      steps: ['浏览器生成随机 verifier，计算 S256 challenge；另生成 state 与 nonce。', '跳转 IAM 授权端点，由用户登录并确认权限。浏览器绝不携带 client secret。', '回调核对 state，携带原 verifier 与精确 redirect_uri 换码。授权码只用一次。', '验证 ID Token 签名、issuer、audience、有效期和 nonce；用 Access Token 调用 UserInfo。'],
      explanation: '公共授权码客户端不获得 refresh token，这是当前框架的策略。需要演示刷新时请选择“应用后端 / 机密授权码”。'
    },
    mobile: {
      title: '移动应用，用系统浏览器登录', description: '网页演示协议本身；应用回调、安全存储与真机集成由你的移动端实现。',
      actor: 'iOS / Android', actorNote: '系统浏览器 + 应用回调 + 安全存储',
      grant: 'Authorization Code + S256 PKCE', oidc: true,
      preparation: '原生应用应注册精确的 Universal Link / App Link 或受控回调，不在 WebView 收集 IAM 密码。本页使用浏览器回调演示，不是原生 SDK。',
      steps: ['应用生成随机 verifier/state/nonce，保留到本次回调完成，使用系统浏览器打开授权地址。', 'IAM 完成正常登录与用户授权，不把 client secret 编进安装包。', '应用收到回调后核对 state 与回调来源，用 verifier 换码。', '验签及校验 ID Token，将令牌存入系统安全存储；取消、重复或超时回调必须丢弃。'],
      explanation: '本页仍是公共 PKCE 流程，不签发 refresh token；不要据此宣称已经验证真机深链或移动端刷新。'
    },
    device: {
      title: '没有键盘，也能安全连接', description: '设备发码，用户在另一块屏幕确认，设备按协议轮询结果。',
      actor: 'Device / CLI', actorNote: '显示 user_code，不显示 device_code',
      grant: 'OAuth Device Authorization', oidc: false,
      preparation: '使用专用公共 Device 客户端。请在独立浏览器、无痕窗口或手机打开确认地址。设备无需接收浏览器回调。',
      steps: ['设备向 device_authorization 请求代码，保存私有 device_code，显示 user_code 和确认地址。', '用户在另一浏览器登录 IAM，核对代码后明确批准或拒绝。', '按 interval 轮询 token；authorization_pending 继续等待，slow_down 至少增加 5 秒。', '成功后停止轮询并使用 Access Token；拒绝、过期、取消或离页停止。取消轮询不是撤销设备码。'],
      explanation: '此场景仅演示 OAuth Device，scope 为 playground.read，不申请 openid、不发 ID Token 或 refresh token。公共 Device 认证需要本部署的专用适配。'
    },
    server: {
      title: '把机密留在应用后端', description: '区分用户委托登录和服务间访问；浏览器不接触 client secret。',
      actor: '应用后端', actorNote: '服务端保管 secret 并进行客户端认证',
      grant: 'Confidential Code / Client Credentials', oidc: true,
      preparation: '需由运维注入 IAM_PLAYGROUND_CLIENT_SECRET。该值只在服务器使用，不展示、不复制到页面；未配置时不会伪造成功。',
      steps: ['用户登录选择“机密授权码”；无人值守的服务访问选择“客户端凭据”。', '机密授权码仍使用 PKCE，并在服务端换码时附加客户端认证。', '授权码流程拿到 refresh token 后可刷新，不扩大 scope；客户端凭据不代表任何用户。', '通过 introspection 查询自己的令牌，通过 revoke 撤销，再查询确认 inactive。'],
      explanation: 'client_credentials 的主体是客户端，不能当用户登录；它不返回 ID Token。机密授权码可演示 OIDC 与刷新。'
    },
    legacy: {
      title: '兼容旧接入，不改变原业务', description: '保留 password / sms 的原参数、认证流程和错误语义。',
      actor: '已有可信客户端', actorNote: '仅为存量接入保留',
      grant: 'Legacy password / sms', oidc: false,
      preparation: '仅使用测试凭据。password/sms 不是 OAuth 2.1 推荐的新接入方式，也不是 OIDC 登录。新应用优先使用授权码与 PKCE。',
      steps: ['选择已有的 password 或 sms 扩展。机密客户端认证由演示后端完成。', 'password 发送 username/password；sms 发送 phone/captcha，不添加 PKCE 要求。', '原 IAM Provider 验证身份并返回原协议结果；页面只瞬时转发输入，不保存密码或验证码。', '验证码校验默认 true 是保留的占位行为；手机号身份源未接入时展示真实失败，不模拟短信成功。'],
      explanation: '兼容模式不签发 ID Token。sms 授权存储仍保留原 password 标记；本页不修改 Provider，不发送短信，也不替代生产验证码校验。'
    }
  };

  async function request(path, data) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 15000);
    try {
      const response = await fetch(root + path, {
        method: data === undefined ? 'GET' : 'POST', credentials: 'same-origin', cache: 'no-store',
        headers: data === undefined ? {} : { 'Content-Type': 'application/json', [configuration.csrfHeader]: configuration.csrfToken },
        body: data === undefined ? undefined : JSON.stringify(data), signal: controller.signal
      });
      const raw = await response.json();
      // Macula Boot can wrap controller output; plain MVC test responses remain supported.
      const result = raw && Object.hasOwn(raw, 'success') && Object.hasOwn(raw, 'data') ? raw.data : raw;
      if (!response.ok || raw.success === false || result?.error) {
        throw new Error(response.status === 403 ? '会话校验失败，请刷新页面后重试。' : '请求未完成：流程失效、参数不匹配或场景未配置。');
      }
      return result;
    } catch (error) {
      if (error.name === 'AbortError') throw new Error('请求超时，请检查 IAM 可达性。');
      throw error;
    } finally { clearTimeout(timeout); }
  }
  function fail(message) {
    $('error').textContent = message;
    $('error').hidden = false;
    $('error').focus();
  }
  function clearError() { $('error').hidden = true; $('error').textContent = ''; }
  function busy(value) {
    document.querySelectorAll('#start-form button, #token-actions button, nav button, #variant').forEach((button) => { button.disabled = value; });
    $('start-form')?.setAttribute('aria-busy', String(value));
  }
  function random() { return base64(crypto.getRandomValues(new Uint8Array(32))); }
  function base64(bytes) { return btoa(String.fromCharCode(...bytes)).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', ''); }
  function scenario() {
    return scene === 'device' ? 'DEVICE' : scene === 'server' || scene === 'legacy' ? $('variant').value : 'PUBLIC_CODE';
  }
  function mask() { clearTimeout(revealTimer); $('tokens').textContent = ''; $('revealed').hidden = true; }
  function stopLocal() { polling = false; clearTimeout(timer); $('stop') && ($('stop').hidden = true); }
  function example(kind) {
    const auth = `GET /oauth2/authorize?response_type=code
  &client_id=<REGISTERED_CLIENT_ID>
  &redirect_uri=<EXACT_REGISTERED_CALLBACK>
  &scope=openid%20profile%20playground.read
  &state=<RANDOM_STATE>&nonce=<RANDOM_NONCE>
  &code_challenge=<S256_CHALLENGE>
  &code_challenge_method=S256

POST /oauth2/token
Content-Type: application/x-www-form-urlencoded
${kind === 'CONFIDENTIAL_CODE' ? 'Authorization: Basic <SERVER_SIDE_CLIENT_CREDENTIALS>\n' : ''}
grant_type=authorization_code
&client_id=<REGISTERED_CLIENT_ID>
&code=<ONE_TIME_CODE>
&redirect_uri=<EXACT_REGISTERED_CALLBACK>
&code_verifier=<ORIGINAL_VERIFIER>`;
    if (kind.endsWith('_CODE')) return auth;
    if (kind === 'DEVICE') return `POST /oauth2/device_authorization
client_id=<REGISTERED_DEVICE_CLIENT>&scope=playground.read

# Display user_code + verification_uri; keep device_code private.
# Wait interval seconds. On slow_down add at least 5 seconds.
POST /oauth2/token
grant_type=urn:ietf:params:oauth:grant-type:device_code
&client_id=<REGISTERED_DEVICE_CLIENT>
&device_code=<PRIVATE_DEVICE_CODE>`;
    return `POST /oauth2/token
Authorization: Basic <SERVER_SIDE_CLIENT_CREDENTIALS>
Content-Type: application/x-www-form-urlencoded

grant_type=${kind.toLowerCase()}&scope=playground.read${kind === 'PASSWORD' ? '\n&username=<USERNAME>&password=<PASSWORD>' : kind === 'SMS' ? '\n&phone=<PHONE>&captcha=<CAPTCHA>' : ''}

# Confidential client only:
POST /oauth2/introspect
token=<ACCESS_TOKEN>&token_type_hint=access_token

POST /oauth2/revoke
token=<ACCESS_TOKEN>&token_type_hint=access_token`;
  }

  function selectScene(name, restore = false) {
    stopLocal(); generation += 1; mask(); clearError();
    if (!restore) {
      if (current?.data.polling) request('/flows/' + current.flowId + '/actions/stop', {}).catch(() => {});
      current = null; sessionStorage.removeItem(flowKey); sessionStorage.removeItem(lastKey);
    }
    scene = name;
    const info = scenes[name];
    document.querySelectorAll('[data-scene]').forEach((button) => button.setAttribute('aria-pressed', String(button.dataset.scene === name)));
    $('scene-title').textContent = info.title; $('scene-description').textContent = info.description;
    $('actor-client').textContent = info.actor; $('actor-client-note').textContent = info.actorNote;
    $('grant-label').textContent = info.grant; $('oidc-label').hidden = !info.oidc;
    $('preparation').textContent = info.preparation; $('explanation').textContent = info.explanation;
    $('actor-resource').textContent = info.oidc ? 'UserInfo' : '演示令牌';
    $('actor-resource-note').textContent = info.oidc ? '使用 Access Token 获取已授权信息' : '只授予 playground.read，无业务权限';
    $('steps').replaceChildren(...info.steps.map((text) => { const item = document.createElement('li'); item.textContent = text; return item; }));
    $('variant-field').hidden = !['server', 'legacy'].includes(name);
    const variants = name === 'server' ? [['CONFIDENTIAL_CODE', '机密授权码 + PKCE / 刷新'], ['CLIENT_CREDENTIALS', '客户端凭据 · 服务间访问']]
      : [['PASSWORD', 'password · 账号密码'], ['SMS', 'sms · 手机验证码']];
    $('variant').replaceChildren(...variants.map(([value, label]) => new Option(label, value)));
    updateVariant();
    $('device-result').hidden = true; $('token-actions').hidden = true;
    $('status').textContent = '环境就绪。准备好后，开始一次真实协议请求。';
    $('transcript').textContent = '尚未发起请求'; $('http-status').textContent = 'WAITING';
    $('verification-state').textContent = 'ID Token：尚未验证';
  }
  function updateVariant() {
    const kind = scenario();
    $('password-fields').hidden = kind !== 'PASSWORD'; $('sms-fields').hidden = kind !== 'SMS';
    ['username', 'password'].forEach((id) => { $(id).required = kind === 'PASSWORD'; });
    ['phone', 'captcha'].forEach((id) => { $(id).required = kind === 'SMS'; });
    $('example').textContent = example(kind);
    $('start').textContent = kind.endsWith('_CODE') ? '生成 PKCE 并前往 IAM →' : kind === 'DEVICE' ? '获取设备代码 →' : '发送真实请求 →';
  }

  function render(response) {
    current = response;
    sessionStorage.setItem(flowKey, JSON.stringify({ id: response.flowId, scene, expires: response.expiresAt }));
    $('verification-state').textContent = response.verification === 'verified' ? 'ID Token：已验签并验证绑定 ✓' : 'ID Token：本次尚未验证 / 不适用';
    if (response.transcript) {
      $('transcript').textContent = JSON.stringify(response.transcript, null, 2);
      $('http-status').textContent = response.transcript.status ? 'HTTP ' + response.transcript.status : 'NETWORK ERROR';
      sessionStorage.setItem(lastKey, JSON.stringify(response.transcript)); // sanitized transcript only
    }
    const code = response.transcript?.body.error;
    $('status').textContent = response.data.polling ? '等待用户确认；下一次轮询约 ' + response.pollAfterSeconds + ' 秒。'
      : code ? 'IAM 返回：' + code : response.data.hasAccessToken ? '已获得演示令牌，敏感值默认遮罩。' : '本次请求已处理。';
    if (code && !['authorization_pending', 'slow_down'].includes(code)) {
      fail('IAM 返回 ' + code + '。请查看右侧说明；页面不会伪造成功。');
    }
    $('token-actions').hidden = !response.data.hasAccessToken;
    document.querySelectorAll('[data-action]').forEach((button) => {
      const action = button.dataset.action;
      const confidential = !['PUBLIC_CODE', 'DEVICE'].includes(response.scenario);
      button.hidden = action === 'refresh' || action === 'revoke-refresh' ? !response.data.hasRefreshToken
        : ['introspect', 'revoke-access'].includes(action) ? !confidential
        : ['userinfo', 'logout'].includes(action) ? response.verification !== 'verified' : false;
    });
    if (response.data.userCode) {
      $('user-code').textContent = response.data.userCode;
      $('verify-device').href = response.data.verificationUriComplete;
      $('device-result').hidden = false;
    }
    if (response.data.userInfo) $('transcript').textContent += '\n\n已验证 sub 的 UserInfo：\n' + JSON.stringify(response.data.userInfo, null, 2);
    if (response.data.tokens) {
      $('tokens').textContent = JSON.stringify(response.data.tokens, null, 2); $('revealed').hidden = false;
      revealTimer = setTimeout(mask, 30000);
      // Do not keep the revealed values in the JS state object.
      delete response.data.tokens;
    }
  }

  function schedule(seconds) {
    clearTimeout(timer);
    if (!polling) return;
    $('stop').hidden = false;
    timer = setTimeout(async () => {
      if (!current || Date.now() >= Date.parse(current.expiresAt)) { stopLocal(); fail('设备流程已过期，请重新开始。'); return; }
      const version = generation;
      try {
        const result = await request('/flows/' + current.flowId + '/actions/poll', {});
        if (version !== generation || !polling) return;
        pollFailures = 0; render(result);
        if (result.data.polling) schedule(result.pollAfterSeconds || 5); else stopLocal();
      } catch (error) {
        if (version !== generation || !polling) return;
        pollFailures += 1;
        if (pollFailures > 3) { stopLocal(); fail('连续请求失败，已停止轮询，请检查网络后重新开始。'); }
        else { $('status').textContent = '网络请求失败，延长间隔后重试…'; schedule(Math.min(60, 5 * 2 ** pollFailures)); }
      }
    }, Math.max(1, seconds) * 1000);
  }

  async function initialize() {
    configuration = await request('/configuration');
    if (callbackParameters) {
      const raw = sessionStorage.getItem(bindingKey);
      sessionStorage.removeItem(bindingKey);
      const binding = raw ? JSON.parse(raw) : null;
      if (!binding || binding.state !== callbackParameters.get('state') || Date.now() > binding.expires) {
        throw new Error('state 与当前浏览器流程不匹配或已经过期。请重新开始，不要刷新兑换旧授权码。');
      }
      if (callbackParameters.has('error')) throw new Error('用户拒绝授权或 IAM 未完成请求，请重新开始。');
      const code = callbackParameters.get('code');
      if (!code) throw new Error('回调缺少授权码。');
      const result = await request('/flows/' + binding.flowId + '/callback', { state: binding.state, code, verifier: binding.verifier });
      if (result.transcript?.status !== 200 || result.verification !== 'verified') throw new Error('换码或 ID Token 验证未通过。');
      sessionStorage.setItem(flowKey, JSON.stringify({ id: result.flowId, scene: binding.scene, expires: result.expiresAt }));
      sessionStorage.setItem(lastKey, JSON.stringify(result.transcript));
      location.replace('/playground');
      return;
    }
    $('issuer').textContent = configuration.issuer; $('callback').textContent = configuration.callback;
    $('confidential-status').textContent = configuration.confidentialConfigured ? '已配置（secret 不会下发）' : '未配置 · 仅公共 PKCE / Device 可用';
    const previous = sessionStorage.getItem(flowKey);
    const previousTranscript = sessionStorage.getItem(lastKey);
    selectScene('h5');
    if (previous) {
      const saved = JSON.parse(previous);
      if (scenes[saved.scene] && Date.now() < Date.parse(saved.expires)) {
        try {
          selectScene(saved.scene, true);
          render(await request('/flows/' + saved.id));
          if (previousTranscript) $('transcript').textContent = previousTranscript;
          if (current.data.polling) { polling = true; schedule(current.pollAfterSeconds || 5); }
        } catch (error) { fail('之前的流程已失效，请重新开始。'); }
      }
    }
    document.querySelectorAll('[data-scene]').forEach((button) => button.addEventListener('click', () => selectScene(button.dataset.scene)));
    $('variant').addEventListener('change', updateVariant);
    $('start-form').addEventListener('submit', async (event) => {
      event.preventDefault();
      if (submitting) return;
      submitting = true; clearError(); mask(); stopLocal(); busy(true);
      try {
        const kind = scenario();
        if (['CONFIDENTIAL_CODE', 'CLIENT_CREDENTIALS', 'PASSWORD', 'SMS'].includes(kind) && !configuration.confidentialConfigured) {
          throw new Error('机密客户端未配置，请由运维注入专用 secret；不要在浏览器输入 secret。');
        }
        const input = { scenario: kind };
        let verifier;
        if (kind.endsWith('_CODE')) {
          verifier = random(); input.state = random(); input.nonce = random();
          input.challenge = base64(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))));
        }
        if (kind === 'PASSWORD') { input.username = $('username').value; input.password = $('password').value; }
        if (kind === 'SMS') { input.phone = $('phone').value; input.captcha = $('captcha').value; }
        const result = await request('/flows', input);
        render(result);
        if (result.authorizeUrl) {
          sessionStorage.setItem(bindingKey, JSON.stringify({ flowId: result.flowId, scene, verifier, state: input.state,
            nonce: input.nonce, expires: Date.parse(result.expiresAt) }));
          location.assign(result.authorizeUrl);
        } else if (result.data.polling) { polling = true; pollFailures = 0; schedule(result.pollAfterSeconds || 5); }
      } catch (error) { fail(error.message); }
      finally { $('password').value = ''; $('captcha').value = ''; submitting = false; busy(false); }
    });
    document.querySelectorAll('[data-action]').forEach((button) => button.addEventListener('click', async () => {
      if (!current) return;
      clearError(); mask(); busy(true);
      try {
        const result = await request('/flows/' + current.flowId + '/actions/' + button.dataset.action, {});
        if (button.dataset.action === 'logout') {
          sessionStorage.removeItem(flowKey); sessionStorage.removeItem(lastKey); sessionStorage.removeItem(bindingKey);
          const form = document.createElement('form'); form.method = 'POST'; form.action = result.data.action;
          for (const [name, value] of Object.entries({ id_token_hint: result.data.idTokenHint, post_logout_redirect_uri: result.data.postLogoutRedirectUri })) {
            const field = document.createElement('input'); field.type = 'hidden'; field.name = name; field.value = value; form.append(field);
          }
          document.body.append(form); form.submit();
        } else render(result);
      } catch (error) { fail(error.message); } finally { busy(false); }
    }));
    $('reset').addEventListener('click', async () => {
      stopLocal(); generation += 1; mask(); busy(true);
      try { await request('/reset', {}); sessionStorage.removeItem(bindingKey); selectScene(scene); }
      catch (error) { fail(error.message); } finally { busy(false); }
    });
    $('stop').addEventListener('click', async () => {
      stopLocal(); generation += 1;
      try { if (current) render(await request('/flows/' + current.flowId + '/actions/stop', {})); }
      catch (error) { fail(error.message); }
    });
    $('hide-tokens').addEventListener('click', mask);
    $('copy-example').addEventListener('click', async () => {
      try { await navigator.clipboard.writeText($('example').textContent); $('status').textContent = '已复制占位符示例，不含本次令牌。'; }
      catch (error) { fail('浏览器不允许自动复制，请手动选择示例文本。'); }
    });
    addEventListener('pagehide', () => { stopLocal(); mask(); });
    busy(false);
  }
  initialize().catch((error) => fail(error.message || '无法加载演示配置，请检查环境与网络。'));
})();
