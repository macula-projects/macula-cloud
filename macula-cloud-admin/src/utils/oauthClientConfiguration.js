import {externalCodeAllowed,singleSourceApplication,isInApp,isWebApplication,webLoginMode} from '@/utils/identitySourceCompatibility';
import {projectProtocol,anyProtocolUses,protocolDefaults,RETIRED_GRANTS} from './applicationProtocolRegistry';
import {
  AUTHORIZATION_CODE_GRANT,
  EXTERNAL_CODE_GRANT,
} from "@/api/model/system/oauth2Client";

export const AUTHENTICATION_METHODS = Object.freeze([
  {
    value: "none",
    label: "none",
    description:
      "浏览器、App 等无法保管密钥的客户端；授权码登录必须使用 S256 PKCE。",
  },
  {
    value: "client_secret_basic",
    label: "client_secret_basic",
    description: "应用后端保管密钥，通过 Authorization: Basic 请求头认证。",
  },
  {
    value: "client_secret_post",
    label: "client_secret_post",
    description: "应用后端保管密钥，通过 POST 表单传递；优先使用请求头认证。",
  },
  {
    value: "private_key_jwt",
    label: "private_key_jwt",
    description: "客户端持有私钥，IAM 从客户端 JWKS URL 获取公钥验签。",
  },
]);
export const AUTHORIZATION_GRANTS = Object.freeze([
  {
    value: AUTHORIZATION_CODE_GRANT,
    label: "授权码",
    description:
      "跳转 IAM 登录页后换取 Token；登录页可使用已启用的密码或短信认证。",
  },
  {
    value: "refresh_token",
    label: "刷新令牌",
    description: "使用已签发的刷新令牌更新访问令牌。",
  },
  {
    value: "client_credentials",
    label: "服务凭据",
    description: "服务自身身份，不代表用户，也不签发刷新令牌。",
  },
  {
    value: EXTERNAL_CODE_GRANT,
    label: "第三方Code登录",
    description: "使用已配置的 SDK/小程序凭证及身份源换 Token。",
  },
]);
export const csvValues = (value) => [
  ...new Set(
    (value || "")
      .split(",")
      .map((v) => v.trim())
      .filter(Boolean)
  ),
];
const equalSet = (a, b) =>
  a.length === b.length && a.every((v) => b.includes(v));
export const usesSecret = (client) =>
  client.settings ? anyProtocolUses(client, settings => csvValues(settings.clientAuthenticationMethods).some(v=>v.startsWith("client_secret_"))) : csvValues(client.clientAuthenticationMethods).some((v) =>
    v.startsWith("client_secret_")
  );
export const usesPrivateKey = (client) =>
  client.settings ? anyProtocolUses(client, settings => csvValues(settings.clientAuthenticationMethods).includes("private_key_jwt")) : csvValues(client.clientAuthenticationMethods).includes("private_key_jwt");

export function grantIssue(client, grant) {
  if (RETIRED_GRANTS.includes(grant)) return "该授权方式已退役，请显式移除并整改接入";
  const auth = csvValues(client.clientAuthenticationMethods);
  const type = client.applicationType;
  if (type === 'SERVICE' && grant !== 'client_credentials') return '后端服务仅允许服务凭据';
  if (grant === 'client_credentials' && type && type !== 'SERVICE') return '服务凭据仅用于后端服务';
  if (grant === EXTERNAL_CODE_GRANT && ['WEB_SERVER','SPA'].includes(type) && !isInApp(client)) return '网页应用仅支持第三方网页授权';
  if (isInApp(client) && ![AUTHORIZATION_CODE_GRANT,EXTERNAL_CODE_GRANT].includes(grant)) return '端内应用只允许固定平台登录';
  if (grant === 'client_credentials' && !client.enabledProtocols?.includes('OAUTH2_1'))
    return '此直接Token授权需要启用OAuth2.1';
  if (auth.includes("none") && grant === "client_credentials")
    return "公共客户端不能使用服务凭据授权";
  if (auth.includes("none") && grant === "refresh_token")
    return "当前公共客户端流程不签发刷新令牌，请通过SSO重新授权";
  if (grant === EXTERNAL_CODE_GRANT) {
    if (!auth.includes("none") && !(client.applicationType === "WEB_SERVER" && isInApp(client))) return "外部凭证授权只允许公共客户端（none）";
    if (!client.externalCodeTypes?.length)
      return "请先在登录能力中选择外部凭证登录方式及身份源";
    if (client.externalCodeTypes.some(code => !externalCodeAllowed(client,code)))
      return "第三方Code与当前应用类型或小程序平台不兼容，请在登录控制中修正";
  }
  return "";
}

export function grantDependency(client, grant) {
  if (grant === AUTHORIZATION_CODE_GRANT && client.managedKey === 'platform-admin') return '平台Admin需保留授权码登录入口';
  if (
    grant === AUTHORIZATION_CODE_GRANT &&
    client.browserLoginProviderTypes?.length
  )
    return "请先取消依赖授权码的网页登录方式";
  if (grant === EXTERNAL_CODE_GRANT && client.externalCodeTypes?.length)
    return "请先在登录能力中取消外部凭证登录方式";
  return "";
}

export function configurationIssues(client) {
  if (client.purpose==='RESOURCE_SERVER') {
    const auth=csvValues(client.settings?.clientAuthenticationMethods)
    const issues=[]
    if(!auth.length || auth.includes('none'))issues.push('资源服务器必须使用机密客户端认证')
    if(auth.includes('private_key_jwt') && !client.settings?.jwkSetUrl?.startsWith('https://'))issues.push('请配置 HTTPS JWKS URL')
    return issues
  }
  if (client.settings) {
    if(!client.protocol)return ['请选择一个接入协议']
    const issues=configurationIssues(projectProtocol(client))
    if(csvValues(client.settings.authorizationGrantTypes).includes('authorization_code') && !client.settings.redirectUris?.trim())issues.push('请填写登录回调 URI')
    return [...new Set(issues)]
  }

  if (client.externalCodeTypes?.includes('FEISHU_APP_CODE') && !csvValues(client.redirectUris).some(value => {
    try { const uri = new URL(value); return uri.protocol === 'https:' && !!uri.hostname && !uri.username && !uri.password && !uri.hash && !value.includes('#'); }
    catch { return false; }
  })) return ['飞书AppLink客户端必须登记至少一个HTTPS授权回调地址'];
  const grants = csvValues(client.authorizationGrantTypes);
  const auth = csvValues(client.clientAuthenticationMethods);
  const issues = [];
  if (!['WEB_SERVER','SPA','NATIVE','MINIAPP','SERVICE'].includes(client.applicationType)) issues.push('应用类型无效');
  if (isWebApplication(client) ? !['BROWSER','IN_APP'].includes(webLoginMode(client)) : client.webLoginMode != null) issues.push('网页登录模式无效');
  if (client.managedKey === 'platform-admin' && webLoginMode(client) !== 'BROWSER') issues.push('平台Admin必须使用浏览器模式');
  const bindings = [...Object.values(client.browserCapabilityBindings || {}),...Object.values(client.externalProviderBindings || {})];
  if (singleSourceApplication(client) && new Set(bindings.map(String)).size > 1) issues.push('当前应用只能关联一个身份源');
  if (client.applicationType === 'NATIVE' && (client.externalCodeTypes || []).length > 1) issues.push('移动客户端只能选择一个SDK登录能力');
  if (isInApp(client) && (bindings.length > 1 || client.configurationStatus === 'ENABLED' && bindings.length !== 1)) issues.push('端内应用必须且只能关联一个端内登录能力');
  if (['NATIVE','MINIAPP','SERVICE'].includes(client.applicationType) && (client.browserLoginProviderTypes?.length || Object.keys(client.browserCapabilityBindings || {}).length)) issues.push('当前应用不支持第三方网页登录，请移除旧关联');
  if (isInApp(client) && (client.hostedLoginMethods?.length || grants.includes(AUTHORIZATION_CODE_GRANT) && grants.includes(EXTERNAL_CODE_GRANT))) issues.push('端内应用不支持IAM内置登录或混合登录路径');
  if (
    ["SPA", "NATIVE", "MINIAPP"].includes(client.applicationType) &&
    !equalSet(auth, ["none"])
  )
    issues.push("公共应用必须使用none认证");
  if (
    client.applicationType === "SERVICE" &&
    (auth.includes("none") ||
      !equalSet(grants, ["client_credentials"]) ||
      client.browserLoginProviderTypes?.length ||
      client.externalCodeTypes?.length ||
      client.hostedLoginMethods?.length)
  )
    issues.push("后端服务仅允许客户端凭据，不提供用户登录或刷新令牌");
  if (
    client.applicationType === "MINIAPP" &&
    ((!grants.includes(AUTHORIZATION_CODE_GRANT) && !client.externalCodeTypes?.length) ||
      client.externalCodeTypes?.some(
        (code) =>
          !(
            client.miniappPlatform === "WECHAT"
              ? ["WECHAT_MINIAPP_LOGIN_CODE", "WECHAT_MINIAPP_PHONE_CODE"]
              : ["WECOM_MINIAPP_CODE"]
          ).includes(code)
      ))
  )
    issues.push("请配置IAM统一登录或对应小程序Code及身份源");
  if (
    !auth.length ||
    auth.some((v) => !AUTHENTICATION_METHODS.some((m) => m.value === v)) ||
    (auth.includes("none") && auth.length !== 1)
  )
    issues.push("请选择有效的认证方式；none 不能与其他方式组合");
  const protocols = client.enabledProtocols || [];
  if (!protocols.length || protocols.some(value => !['OIDC','OAUTH2_1'].includes(value))) issues.push('至少启用一个支持的协议');

  if (protocols.includes('OIDC') && (!csvValues(client.scopes).includes('openid') || !grants.includes('authorization_code') && !grants.includes(EXTERNAL_CODE_GRANT)))
    issues.push('OIDC登录需要openid和授权码或第三方Code登录');
  if (!protocols.includes('OIDC') && csvValues(client.scopes).some(scope => scope==='openid'))
    issues.push('OAuth2.1不能申请openid');
  if (grants.includes('authorization_code') && !client.requireProofKey) issues.push("授权码流程必须启用S256 PKCE");
  if (!grants.length) issues.push("至少选择一种授权方式");
  for (const value of grants) {
    const grant = AUTHORIZATION_GRANTS.find((g) => g.value === value);
    const reason = grant
      ? grantIssue(client, value)
      : RETIRED_GRANTS.includes(value) ? "授权方式已退役，请显式移除后保存" : "未知授权方式，请显式移除后保存";
    if (reason) issues.push(`${grant?.label || value}：${reason}`);
  }
  if (client.externalCodeTypes?.length && !grants.includes(EXTERNAL_CODE_GRANT))
    issues.push("已选择外部凭证登录方式，必须保留外部凭证授权");
  return issues;
}

/** Web Crypto 不可用时明确失败，禁止回退到非安全随机数。 */
export function generateClientSecret(cryptoApi = globalThis.crypto) {
  if (!cryptoApi?.getRandomValues)
    throw new Error("当前环境无法安全生成密钥，请使用 HTTPS 或 localhost");
  const bytes = cryptoApi.getRandomValues(new Uint8Array(32));
  return btoa(String.fromCharCode(...bytes))
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replace(/=+$/, "");
}

export const shellQuote = (value) =>
  "'" + String(value).replaceAll("'", "'\"'\"'") + "'";
const formEncode = (value) =>
  new URLSearchParams({ x: value }).toString().slice(2);
/** OAuth Basic 先分别表单编码，避免冒号、空格和 Unicode 改变用户名/密码的边界。 */
export const basicAuthorization = (clientId, secret) =>
  `Basic ${btoa(`${formEncode(clientId)}:${formEncode(secret)}`)}`;

export function pkceExample() {
  return `// 在客户端执行；保存 verifier 和 state，回调校验 state 后用同一 verifier 换 Token。\nconst bytes = crypto.getRandomValues(new Uint8Array(32));\nconst base64url = b => btoa(String.fromCharCode(...b)).replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/, '');\nconst verifier = base64url(bytes);\nconst challenge = base64url(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))));\nconst state = base64url(crypto.getRandomValues(new Uint8Array(32)));`;
}

export function privateKeyExample(client) {
  const algorithm = ["RS256", "RS384", "RS512"].includes(
    client.signingAlgorithm
  )
    ? client.signingAlgorithm
    : "RS256";
  return `// 保存为 client-assertion.mjs，使用 Node.js 执行。私钥仅由客户端持有。\n// CLIENT_ID、JWT_AUDIENCE、KEY_ID、PRIVATE_KEY_FILE 由接入应用环境配置。\nimport { readFileSync } from 'node:fs';\nimport { sign, randomUUID } from 'node:crypto';\nconst { CLIENT_ID, JWT_AUDIENCE, KEY_ID, PRIVATE_KEY_FILE } = process.env;\nif (![CLIENT_ID, JWT_AUDIENCE, KEY_ID, PRIVATE_KEY_FILE].every(Boolean)) throw new Error('请配置客户端、audience、kid 和私钥文件');\nconst encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');\nconst now = Math.floor(Date.now() / 1000);\nconst input = encode({ alg: '${algorithm}', kid: KEY_ID, typ: 'JWT' }) + '.' + encode({ iss: CLIENT_ID, sub: CLIENT_ID, aud: JWT_AUDIENCE, iat: now, exp: now + 60, jti: randomUUID() });\nconst signature = sign('RSA-SHA${algorithm.slice(
    2
  )}', Buffer.from(input), readFileSync(PRIVATE_KEY_FILE)).toString('base64url');\nconsole.log(input + '.' + signature);\n// JWKS URL 发布与 KEY_ID 对应的公钥。JWT_AUDIENCE 按 IAM 部署配置确认，每次请求重新签名。`;
}

/** 只生成文本，不发送请求；所有动态 shell 参数都进行单引号转义。 */
export function requestExample(
  client,
  {
    baseUrl = "https://iam.example.com",
    secret = "",
    grant = "",
    authMethod = "",
    endpoints = null,
    codeType = '',
    source = null,
  } = {}
) {
  if (client.settings) client=projectProtocol(client)
  const selected = csvValues(client.authorizationGrantTypes);
  if (selected.some(value => RETIRED_GRANTS.includes(value))) return "存在已退役授权，请先显式整改配置";
  if (!AUTHORIZATION_GRANTS.some(value => value.value === grant)) return "授权方式不受支持";
  if (!selected.includes(grant)) return "请先选择授权方式";
  const auth = authMethod || csvValues(client.clientAuthenticationMethods)[0];
  const id = client.clientId || "<CLIENT_ID>";
  const base = baseUrl.replace(/\/+$/, "");
  if (!/^https?:\/\//.test(base) || /[?#\r\n]/.test(base))
    return "请输入不含查询参数或片段的 IAM HTTP(S) 基地址";
  if (
    endpoints &&
    (!endpoints.token_endpoint ||
      (grant === "authorization_code" && !endpoints.authorization_endpoint))
  )
    return "Discovery未发布此流程所需端点，请联系管理员检查平台配置";
  const exampleScopes = csvValues(client.scopes).filter(scope =>
    ['authorization_code',EXTERNAL_CODE_GRANT].includes(grant) && client.defaultProtocol === 'OIDC' || scope!=='openid');
  if (grant === 'authorization_code' && !exampleScopes.length) return '当前协议没有可请求的Scope，请在协议配置中选择对应Scope后生成授权码示例。';
  const params = [["grant_type", grant]];
  if (grant !== "refresh_token")
    params.push(["scope", exampleScopes.join(" ")]);
  if (grant === "authorization_code")
    params.push(
      ["code", "<AUTHORIZATION_CODE>"],
      ["redirect_uri", csvValues(client.redirectUris)[0] || "<REDIRECT_URI>"]
    );
  if (
    grant === "authorization_code" &&
    (auth === "none" || client.requireProofKey)
  )
    params.push(["code_verifier", "<SAVED_CODE_VERIFIER>"]);
  if (grant === "refresh_token")
    params.push(["refresh_token", "<REFRESH_TOKEN>"]);
  if (grant === EXTERNAL_CODE_GRANT) {
    if (!externalCodeAllowed(client,codeType,source) || !client.externalCodeTypes?.includes(codeType) || !source?.available || source.id == null
      || client.externalProviderBindings?.[codeType] == null || String(client.externalProviderBindings?.[codeType]) !== String(source.id)
      || !source.capabilities?.some(item => item.codeType === codeType)) {
      return '请选择已关联且可用的第三方认证能力';
    }
    const selectedCapability = source.capabilities.find(item => item.codeType === codeType);
    const contract = selectedCapability?.loginCapability ? {...source,...selectedCapability} : source;
    if (contract.providerPkce) {
      if (codeType !== 'FEISHU_APP_CODE' || contract.flow !== 'FEISHU_APPLINK_V3' ) {
        return '该平台Flow尚未提供完整接入合同';
      }
      const redirect = csvValues(client.redirectUris).find(value => {
        try { const uri = new URL(value); return uri.protocol === 'https:' && !!uri.hostname && !uri.username && !uri.password && !value.includes('#'); }
        catch { return false; }
      });
      if (!redirect) return '请先在应用协议配置登记AppLink HTTPS回调地址';
      params.push(['provider_code_verifier', '<PLATFORM_CODE_VERIFIER>'], ['provider_redirect_uri', redirect]);
    }
    if (client.defaultProtocol === 'OIDC') params.push(["nonce", "<OPTIONAL_RANDOM_NONCE>"]);
    params.push(
      ["code_type", codeType],
      ["code", "<EXTERNAL_CODE>"]
    );
  }
  const lines = [
    `curl --request POST ${shellQuote(
      endpoints?.token_endpoint || base + "/oauth2/token"
    )}`,
    `  --header 'Content-Type: application/x-www-form-urlencoded'`,
  ];
  if (auth === "client_secret_basic")
    lines.push(
      `  --header ${shellQuote(
        "Authorization: " +
          (secret
            ? basicAuthorization(id, secret)
            : "Basic <BASE64_OF_FORM_ENCODED_CLIENT_ID_AND_SECRET>")
      )}`
    );
  else {
    params.push(["client_id", id]);
    if (auth === "client_secret_post")
      params.push(["client_secret", secret || "<CLIENT_SECRET>"]);
    if (auth === "private_key_jwt")
      params.push(
        [
          "client_assertion_type",
          "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
        ],
        ["client_assertion", "<SIGNED_CLIENT_ASSERTION>"]
      );
  }
  lines.push(
    ...params.map(
      ([key, value]) => `  --data-urlencode ${shellQuote(key + "=" + value)}`
    )
  );
  let intro = "# 将 <...> 占位符替换为本次请求的实际值。";
  if (grant === EXTERNAL_CODE_GRANT && isInApp(client)) intro += client.applicationType === 'WEB_SERVER'
    ? ' JSAPI仅在网页取Code；以下请求由业务后端使用机密凭据发送，密钥不得进入网页。'
    : ' SPA取得平台Code后以none提交IAM；直接Code不建立浏览器SSO，不执行IAM授权码PKCE。';
  if (grant === "client_credentials")
    intro += " 服务身份，不包含用户身份，不返回刷新令牌。";
  if (grant === "authorization_code") {
    const query = new URLSearchParams({
      client_id: id,
      response_type: "code",
      redirect_uri: csvValues(client.redirectUris)[0] || "<REDIRECT_URI>",
      scope: exampleScopes.join(" "),
      state: "<RANDOM_STATE>",
    });
    if (client.defaultProtocol==='OIDC') query.set('nonce','<RANDOM_NONCE>');
    if (auth === "none" || client.requireProofKey) {
      query.set("code_challenge", "<S256_CHALLENGE>");
      query.set("code_challenge_method", "S256");
    }
    intro += `\n# 先在浏览器访问授权地址（不带客户端密钥），回调校验 state：\n${
      endpoints?.authorization_endpoint || base + "/oauth2/authorize"
    }?${query}\n# 再换取 Token：`;
  }
  return intro + "\n" + lines.join(" \\\n");
}

/** 生成公开客户端标识；UUID 不是密钥，不作为客户端认证凭据。 */
export function generateClientId(cryptoApi = globalThis.crypto) {
  if (cryptoApi?.randomUUID) return cryptoApi.randomUUID();
  if (!cryptoApi?.getRandomValues)
    throw new Error(
      "当前环境无法安全生成 Client ID，请使用 HTTPS 或 localhost"
    );
  const bytes = cryptoApi.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = [...bytes]
    .map((value) => value.toString(16).padStart(2, "0"))
    .join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(
    12,
    16
  )}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/** 创建类型模板；切换时保留ID和通用参数，清空旧类型的登录与绑定。 */
export function applicationTemplate(
  client,
  applicationType,
  miniappPlatform = null
) {
  const service = applicationType === "SERVICE";
  const confidential = service || applicationType === "WEB_SERVER";
  const mini = applicationType === "MINIAPP";
  return {
    ...client,
    purpose: 'APPLICATION',
    protocol:service || mini?'OAUTH2_1':'OIDC',
    settings:protocolDefaults({...client,applicationType},service || mini?'OAUTH2_1':'OIDC'),
    applicationType,
    webLoginMode: ['WEB_SERVER','SPA'].includes(applicationType) ? 'BROWSER' : null,
    miniappPlatform: mini ? miniappPlatform || "WECHAT" : null,
    clientAuthenticationMethods: confidential ? "client_secret_basic" : "none",
    clientSecret: "",
    secretConfigured: false,
    authorizationGrantTypes: service
      ? "client_credentials"
      : mini
      ? EXTERNAL_CODE_GRANT
      : confidential ? "authorization_code,refresh_token" : "authorization_code",
    enabledProtocols: [service || mini ? "OAUTH2_1" : "OIDC"],
    defaultProtocol: service || mini ? "OAUTH2_1" : "OIDC",
    configurationStatus: "DRAFT",
    scopes: service || mini ? "" : "openid,profile",
    redirectUris: "",
    browserLoginProviderTypes: [],
    browserProviderBindings: {},
    browserCapabilityBindings: null,
    externalCodeTypes: [],
    externalProviderBindings: {},
    directLoginProviderType: "",
    hostedLoginMethods: service ? [] : mini ? null : ["PASSWORD", "SMS", "EMAIL"],
    requireProofKey: true,
    reuseRefreshTokens: false,
    jwkSetUrl: "",
    signingAlgorithm: "",
  };
}

/** 仅发送API表单字段，示例密钥和只读状态不会进入更新请求。 */
export function clientPayload(client) {
  const fields = ['clientId','clientName','purpose','protocol','settings','configurationVersion','configurationStatus',
    'webLoginMode','browserProviderBindings','browserCapabilityBindings','applicationType','miniappPlatform','hostedLoginMethods','externalCodeTypes','externalProviderBindings',
    'browserLoginProviderTypes','directLoginProviderType','userType','tenantId'];
  const data = Object.fromEntries(
    fields
      .filter((key) => Object.hasOwn(client, key))
      .map((key) => [key, client[key]])
  );
  if (!isWebApplication(client)) delete data.webLoginMode;
  if (data.browserCapabilityBindings == null) delete data.browserCapabilityBindings;
  if (
    usesSecret(client) &&
    (!client.id || !client.secretConfigured) &&
    client.clientSecret
  )
    data.clientSecret = client.clientSecret;
  return data;
}
