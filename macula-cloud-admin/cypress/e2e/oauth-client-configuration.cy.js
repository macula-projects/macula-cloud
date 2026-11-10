import {protocolDefaults} from "../../src/utils/applicationProtocolRegistry"
import {installSessionApi,seedAuthenticatedSession} from '../support/auth-session'
beforeEach(() => installSessionApi())
describe("IAM 应用配置", () => {
  const ok = (data) => ({
    statusCode: 200,
    body: { success: true, code: "00000", data },
  });
  const record = (overrides = {}) => {
    const row = {
    id: 42,
    clientId: "existing-client",
    clientName: "业务应用",
    applicationType: "SPA",
    enabledProtocols:["OIDC","OAUTH2_1"],defaultProtocol:"OIDC",configurationStatus:"ENABLED",
    browserProviderBindings: {},
    miniappPlatform: null,
    hostedLoginMethods: ["PASSWORD"],
    tenantId: 1,
    userType: "employee",
    clientAuthenticationMethods: "none",
    authorizationGrantTypes: "authorization_code",
    redirectUris: "https://app.example/callback",
    scopes: "openid",
    allowedOrigins: ["https://app.example"],
    externalCodeTypes: [],
    externalProviderBindings: {},
    browserLoginProviderTypes: [],
    directLoginProviderType: "",
    requireProofKey: true,
    requireAuthorizationConsent: false,
    reuseRefreshTokens: true,
    secretConfigured: false,
    accessTokenTimeToLive: 3600,
    refreshTokenTimeToLive: 2592000,
    authorizationCodeTimeToLive: 300,
    tokenFormat: "self-contained",
    idTokenSignatureAlgorithm: "RS256",
    ...overrides,
    configurationVersion:"fixture-version",
  };
  row.purpose=overrides.purpose || 'APPLICATION';
  row.protocol=overrides.protocol || overrides.defaultProtocol || 'OIDC';
  row.settings=overrides.settings || {...protocolDefaults(row,row.protocol),clientAuthenticationMethods:row.clientAuthenticationMethods,
    authorizationGrantTypes:row.authorizationGrantTypes,redirectUris:row.redirectUris,scopes:row.scopes,
    allowedOrigins:row.allowedOrigins,requireProofKey:row.requireProofKey,requireAuthorizationConsent:row.requireAuthorizationConsent,
    jwkSetUrl:row.jwkSetUrl || null,signingAlgorithm:row.signingAlgorithm || null,
    accessTokenTimeToLive:row.accessTokenTimeToLive,tokenFormat:row.tokenFormat};
  return row;
  };
  function open(
    overrides = {},
    { metadataFails = false, catalogFails = false, clipboardFails = false, businessScopes = [{value:"api.read",label:"读取API",oidcOnly:false}] } = {}
  ) {
    let row = record(overrides);
    cy.intercept(
      "GET",
      /\/system\/api\/v1\/oauth2-clients\?/,
      ok({ records: [row], total: 1 })
    ).as("list");
    cy.intercept("GET", "**/system/api/v1/oauth2-clients/42*", (req) =>
      req.reply(ok(row))
    ).as("detail");
    cy.intercept(
      "GET",
      "**/system/api/v1/oauth2-clients/configuration-catalog*",
      catalogFails
        ? { statusCode: 503, body: { success: false, msg: "目录暂不可用" } }
        : ok({
            protocols: [{value:"OIDC",label:"OIDC"},{value:"OAUTH2_1",label:"OAuth2.1"}],
            scopes: [{value:"openid",label:"OpenID登录",oidcOnly:true},{value:"profile",label:"基础资料",oidcOnly:false},
              ...businessScopes],
            sources: [
              {id:8,name:"微信小程序源",providerType:"WECHAT",sourceType:"WECHAT_MINIAPP",label:"微信小程序",scene:"MINIAPP",
                available:true,browser:false,capabilities:[{codeType:"WECHAT_MINIAPP_LOGIN_CODE",label:"身份登录",scene:"MINIAPP"}]},
              {id:9,name:"企微源一",providerType:"WECOM",sourceType:"WECOM_WEB",label:"企业微信网页",scene:"BROWSER",
                available:true,browser:true,capabilities:[{browser:true,loginCapability:"WECOM_BROWSER",label:"浏览器网页登录",applicationTypes:["WEB_SERVER","SPA"]},{browser:true,loginCapability:"WECOM_WEBVIEW",label:"企微端内授权",applicationTypes:["IN_APP"]}]},
              {id:10,name:"企微源二",providerType:"WECOM",sourceType:"WECOM_WEB",label:"企业微信网页",scene:"BROWSER",
                available:true,browser:true,capabilities:[{browser:true,loginCapability:"WECOM_BROWSER",label:"浏览器网页登录",applicationTypes:["WEB_SERVER","SPA"]},{browser:true,loginCapability:"WECOM_WEBVIEW",label:"企微端内授权",applicationTypes:["IN_APP"]}]},
              {id:11,name:"微信原生SDK源",providerType:"WECHAT",sourceType:"WECHAT_MOBILE_APP",label:"微信移动应用 SDK",scene:"APP",
                available:true,browser:false,capabilities:[{codeType:"WECHAT_OPEN_APP_CODE",label:"原生授权",scene:"APP",applicationTypes:["NATIVE"]}]},
              {id:12,name:"钉钉端内免登源",providerType:"DINGTALK",sourceType:"DINGTALK_WEBVIEW",label:"钉钉端内网页",scene:"WEBVIEW",
                available:true,browser:false,capabilities:[{codeType:"DINGTALK_WEBVIEW_CODE",label:"端内免登",scene:"WEBVIEW",applicationTypes:["IN_APP"]}]},
              {id:14,name:"飞书AppLink源",providerType:"FEISHU",sourceType:"FEISHU_WEB",available:true,browser:false,
                capabilities:[{codeType:"FEISHU_APP_CODE",loginCapability:"FEISHU_APPLINK",label:"移动AppLink授权",scene:"APP",flow:"FEISHU_APPLINK_V3",providerPkce:true,applicationTypes:["NATIVE"]}]},
              {id:13,name:"飞书端内Code源",providerType:"FEISHU",sourceType:"FEISHU_WEBVIEW",label:"飞书端内网页",scene:"WEBVIEW",flow:"FEISHU_WEBVIEW_V3",
                available:true,browser:false,capabilities:[{codeType:"FEISHU_WEBVIEW_CODE",label:"端内免登",scene:"WEBVIEW",applicationTypes:["IN_APP"]}]}
            ],
          })
    ).as("catalog");
    cy.intercept(
      "GET",
      "**/system/api/v1/oauth2-clients/metadata*",
      metadataFails
        ? ok({
            available: false,
            message: "平台接入地址暂不可用",
            endpoints: {},
          })
        : ok({
            available: true,
            endpoints: {
              issuer: "https://iam.example",
              discovery: "https://iam.example/.well-known/openid-configuration",
              authorization_endpoint: "https://iam.example/auth",
              token_endpoint: "https://iam.example/token",
              jwks_uri: "https://iam.example/keys",
            },
          })
    ).as("metadata");
    cy.intercept(
      "GET",
      "**/system/api/v1/tenants/options*",
      ok([{ value: 1, label: "测试租户" }])
    ).as("tenants");
    cy.intercept("PUT", "**/system/api/v1/oauth2-clients/42*", (req) => {
      row = { ...row, ...req.body };
      req.reply(ok(row));
    }).as("update");
    cy.intercept("POST", "**/system/api/v1/oauth2-clients/drafts", (req) => {
      row = record({...req.body,id:42,clientId:"11111111-2222-4333-8444-555555555555",configurationStatus:"DRAFT",defaultProtocol:null,enabledProtocols:[],protocolConfigurations:[],authorizationGrantTypes:"",scopes:"",tenantId:null,userType:null});
      row.protocol=null;row.settings=null;
      delete row.clientSecret;
      req.reply(ok(row));
    }).as("create");
    cy.visit("/#/system/oauth2-client", {
      onBeforeLoad(win) {
        seedAuthenticatedSession(win)
        const storage = {
          USER_INFO: { username: "root", roles: ["ROOT"] },
          MENU: [
            {
              path: "/system",
              component: "Layout",
              meta: { title: "系统管理" },
              children: [
                {
                  path: "/system/oauth2-client",
                  component: "system/oauth2Client/index",
                  meta: { title: "IAM应用" },
                },
              ],
            },
          ],
        };
        Object.entries(storage).forEach(([key, content]) =>
          win.sessionStorage.setItem(
            key,
            JSON.stringify({ content, datetime: 0 })
          )
        );
        Object.defineProperty(win.navigator, "clipboard", {
          configurable: true,
          value: {
            writeText: clipboardFails
              ? cy.stub().rejects(new Error("复制失败"))
              : cy.stub().resolves(),
          },
        });
      },
    });
    cy.wait(["@list", "@catalog", "@tenants"]);
  }
  function detail() {
    cy.contains("tr", "业务应用").contains("button", "配置").click();
    cy.wait(["@detail", "@metadata"]);
  }
  function tab(label) {
    cy.contains('[role="tab"]', label).click();
  }
  function field(label) {
    return cy.get(".el-form-item__label:visible").contains(label).closest(".el-form-item");
  }

  it("creates a minimal draft in a type-card dialog without an authentication address", () => {
    open();
    cy.contains("button", "创建应用").click();
    cy.get('.el-dialog').should('be.visible');
    field('应用名称').find('input').type('新应用');
    cy.get('[data-application-type="MINIAPP"]').click();
    cy.get('.client-create').then(elements => {
      const rows = [...elements[0].querySelectorAll('.el-form-item')].filter(row => ['接入用途','小程序平台'].includes(row.querySelector('.el-form-item__label')?.textContent.trim()));
      const starts = rows.map(row => {
        const label = row.querySelector('.el-form-item__label').getBoundingClientRect();
        const radio = row.querySelector('.el-radio').getBoundingClientRect();
        expect(Math.abs((label.top + label.height / 2) - (radio.top + radio.height / 2))).to.be.at.most(1);
        return radio.left;
      });
      expect(Math.abs(starts[0] - starts[1])).to.be.at.most(1);
    });
    field('接入用途').scrollIntoView().screenshot('create-purpose-alignment-desktop');
    field('小程序平台').scrollIntoView().screenshot('create-platform-alignment-desktop');
    cy.contains('.el-dialog .el-radio','企业微信').click();
    cy.get('[data-application-type="SPA"]').click();
    cy.contains('.el-dialog .el-form-item__label','接入协议').should('not.exist');
    field('应用名称').find('input').should('have.value','新应用');
    cy.contains('.el-form-item__label','认证地址').should('not.exist');
    cy.contains('.el-form-item__label','Client ID').should('not.exist');
    cy.contains('.el-form-item__label','登录回调 URI').should('not.exist');
    cy.get('.el-dialog').contains('button','创建').click();
    cy.wait('@create').then(({request}) => {
      expect(request.body).to.deep.equal({clientName:'新应用',applicationType:'SPA',miniappPlatform:null,purpose:'APPLICATION'});
    });
    cy.contains('暂不可用于认证').should('be.visible');
    cy.contains('尚未选择接入协议').should('be.visible');
    tab('协议配置');
    cy.get('.client-credentials').should('not.exist');
    cy.contains('.el-radio','OIDC').click();
    field('Client ID').find('input').should('have.attr','readonly');
    field('Client ID').find('input').should('have.value','11111111-2222-4333-8444-555555555555');
    tab('应用配置');
    cy.contains('保存后只读').should('be.visible');
    tab('协议配置');
    field('Scope').should('contain.text','OpenID登录');
  });

  it("preserves other sections on save and independently controls hosted methods", () => {
    open();
    detail();
    field("应用名称").find("input").clear().type("更新名称");
    tab("协议配置");
    field("Scope").find(".el-select").click();
    cy.get(".el-select-dropdown:visible").contains("基础资料").click();
    tab("登录控制");
    cy.contains("label", "账号密码").click();
    cy.contains("button", "保存并启用").click();
    cy.wait("@update").then(({ request }) => {
      expect(request.body.clientName).to.eq("更新名称");
      expect(request.body.settings.scopes).to.eq("openid,profile");
      expect(request.body.hostedLoginMethods).to.deep.eq([]);
      expect(request.body.settings.allowedOrigins).to.deep.eq(["https://app.example"]);
      expect(request.body.settings.authorizationGrantTypes).to.eq(
        "authorization_code"
      );
    });
  });

  it("requires an explicit application type without guessing missing configuration", () => {
    open({ applicationType: null, hostedLoginMethods: null });
    detail();
    field("应用类型").contains(".el-radio","单页 Web").click();
    cy.contains("button", "保存并启用").click();
    cy.wait("@update").then(({ request }) => {
      expect(request.body.applicationType).to.eq("SPA");
      expect(request.body.hostedLoginMethods).to.eq(null);
      expect(request.body.settings.authorizationGrantTypes).to.eq(
        "authorization_code"
      );
    });
    cy.contains("保存后只读").should("be.visible");
  });

  it("keeps authentication and matching credentials together outside application basics", () => {
    open({ applicationType: "WEB_SERVER", clientAuthenticationMethods: "none" });
    detail();
    cy.contains('.el-form-item__label', 'Client ID').should('not.exist');
    cy.contains('.el-form-item__label', 'Client Secret').should('not.exist');
    tab('协议配置');
    field('Client ID').find('input').should('have.value', 'existing-client');
    cy.get('[data-testid="client-auth"] .el-radio').then(radios => {
      expect([...radios].map(radio => radio.textContent.trim())).to.deep.equal(['none','client_secret_basic','client_secret_post','private_key_jwt']);
    });
    cy.contains('.el-form-item__label', 'Client Secret').should('not.exist');
    cy.get('[data-testid="client-auth"]').contains('.el-radio', 'client_secret_basic').click();
    field('Client Secret').find('input').should('have.attr', 'type', 'password');
    field('Client Secret').contains('button', '安全生成').click();
    field('Client Secret').find('input').invoke('val').should('have.length.at.least', 16);
    cy.viewport(1440, 900);
    cy.document().should(doc => expect(doc.documentElement.scrollWidth).to.be.at.most(1440));
    cy.get('[data-testid="client-auth"] .el-radio').then(radios => {
      const top = radios[0].getBoundingClientRect().top;
      [...radios].forEach(radio => expect(radio.getBoundingClientRect().top).to.be.closeTo(top, 1));
    });
    cy.get('.client-page, .client-detail-main, .configuration-form, .client-credentials, .credentials-grid, .authentication-methods').each(element => {
      const node = element[0];
      expect(node.scrollWidth, `${node.className}: width=${node.clientWidth}`).to.be.at.most(node.clientWidth + 1);
    });
    cy.window().then(win => {
      const page = win.document.querySelector('.client-page').getBoundingClientRect();
      expect(page.right, `page ${page.width}, viewport ${win.innerWidth}`).to.be.at.most(win.innerWidth + 1);
    });
    cy.get('.client-credentials').scrollIntoView();
    cy.screenshot('client-auth-credentials-desktop', { capture: 'viewport', scale: true });
    cy.viewport(390, 844);
    cy.document().should(doc => expect(doc.documentElement.scrollWidth).to.be.at.most(390));
    cy.get('.client-credentials').scrollIntoView();
    cy.get('[data-testid="client-auth"]').then(group => {
      expect(group[0].scrollWidth).to.be.at.most(group[0].clientWidth);
    });
    cy.screenshot('client-auth-credentials-mobile', { capture: 'viewport', scale: true });
    cy.get('[data-testid="client-auth"]').contains('.el-radio', 'client_secret_post').click();
    field('Client Secret').should('be.visible');
    cy.get('[data-testid="client-auth"]').contains('.el-radio', 'private_key_jwt').click();
    cy.contains('.el-form-item__label', 'Client Secret').should('not.exist');
    field('客户端 JWKS URL').should('be.visible');
    cy.get('[data-testid="client-auth"]').contains('.el-radio', 'none').click();
    cy.contains('.el-form-item__label', 'Client Secret').should('not.exist');
    cy.contains('.el-form-item__label', '客户端 JWKS URL').should('not.exist');
  });

  it("shows actual platform endpoints and separates the client JWKS URL", () => {
    open({
      applicationType: "WEB_SERVER",
      clientAuthenticationMethods: "private_key_jwt",
      jwkSetUrl: "https://client.example/jwks",
      signingAlgorithm: "RS256",
    });
    detail();
    cy.contains('协议端点信息').should('not.exist');
    tab("协议配置");
    cy.get('.protocol-endpoints:visible .el-collapse-item__header').contains('协议端点信息').click();
    cy.contains('.endpoint-row','平台 JWKS').find("input").should('not.exist');
    cy.contains(".endpoint-row", "平台 JWKS").find("span").should("contain", "https://iam.example/keys");
    field("客户端 JWKS URL")
      .find("input")
      .should("have.value", "https://client.example/jwks");
    tab("快速开始");
    cy.contains("pre", "https://iam.example/token").should("be.visible");
    cy.contains("在客户端生成 private_key_jwt 签名").click();
    cy.contains("pre", "PRIVATE_KEY_FILE").should("be.visible");
  });

  it("handles metadata and clipboard failure with a retry action", () => {
    open({}, { metadataFails: true, clipboardFails: true });
    detail();
    tab('协议配置');
    field("Client ID").contains("button", "复制").click();
    cy.contains("复制失败").should("be.visible");
    tab('协议配置');
    cy.get('.protocol-endpoints:visible .el-collapse-item__header').contains('协议端点信息').click();
    cy.contains("平台接入地址暂不可用").should("be.visible");
    cy.contains("button", "重新获取").should("be.visible");
    tab("快速开始");
    cy.contains("获取成功后生成请求示例").should("be.visible");
    cy.get("pre").should("not.exist");
  });

  for (const type of ['SPA','NATIVE','MINIAPP','IN_APP']) {
    it(`filters browser, native SDK and container codes for ${type}`,()=>{
      open({applicationType:type,miniappPlatform:type==='MINIAPP'?'WECHAT':null});
      detail();tab('登录控制');cy.contains('button','添加身份源').click();
      if(type==='IN_APP') {
        cy.contains('.source-option','钉钉端内免登源').should('be.visible');
        cy.contains('.source-option','飞书端内Code源').should('be.visible');
        cy.contains('.source-option','微信原生SDK源').should('not.exist');
        cy.contains('.source-option','微信小程序源').should('not.exist');
      } else if(type==='SPA') {
        cy.contains('.source-option','企微源一').should('be.visible');
        cy.contains('.source-option','钉钉端内免登源').should('not.exist');
        cy.contains('.source-option','飞书端内Code源').should('not.exist');
      } else if(type==='NATIVE') {
        cy.contains('.source-option','微信原生SDK源').should('be.visible');
        cy.contains('.source-option','钉钉端内免登源').should('not.exist');
        cy.contains('.source-option','微信小程序源').should('not.exist');
      } else {
        cy.contains('.source-option','微信小程序源').should('be.visible');
        cy.contains('.source-option','微信原生SDK源').should('not.exist');
        cy.contains('.source-option','钉钉端内免登源').should('not.exist');
        cy.contains('.source-option','企微源一').should('not.exist');
      }
      cy.screenshot(`identity-capabilities-${type}`,{capture:'viewport'});
    });
  }

  it('binds Feishu in-client Code only for an in-app application',()=>{
    open({applicationType:'IN_APP',hostedLoginMethods:[]});detail();tab('登录控制');cy.contains('button','添加身份源').click();
    cy.contains('.source-option','飞书端内Code源').contains('.el-radio','端内免登').click();
    cy.get('.el-dialog').contains('button','添加所选').click();
    cy.contains('.selected-source','飞书端内Code源').should('be.visible');
    cy.contains('button','保存并启用').click();
    cy.wait('@update').then(({request})=>{
      expect(request.body.externalCodeTypes).to.deep.equal(['FEISHU_WEBVIEW_CODE']);
      expect(request.body.externalProviderBindings).to.deep.equal({FEISHU_WEBVIEW_CODE:13});
      expect(request.body.browserLoginProviderTypes).to.deep.equal([]);
      expect(request.body.settings.authorizationGrantTypes).to.include('urn:macula:params:oauth:grant-type:external_code');
    });
  });

  it('lets miniapp use only hosted IAM login without a platform Code',()=>{
    open({applicationType:'MINIAPP',miniappPlatform:'WECHAT',hostedLoginMethods:null});detail();tab('登录控制');
    field('统一登录入口').find('.el-switch').should('have.class','is-checked');
    cy.contains('.el-radio','使用平台默认').should('be.visible');
    cy.contains('button','保存并启用').click();
    cy.wait('@update').then(({request})=>{
      expect(request.body.settings.authorizationGrantTypes).to.equal('authorization_code');
      expect(request.body.externalCodeTypes).to.deep.equal([]);
      expect(request.body.hostedLoginMethods).to.equal(null);
    });
  });
  it('adds and removes hosted entry without deleting miniapp Code or explicit local policy',()=>{
    const external='urn:macula:params:oauth:grant-type:external_code';
    open({applicationType:'MINIAPP',miniappPlatform:'WECHAT',authorizationGrantTypes:external,
      externalCodeTypes:['WECHAT_MINIAPP_LOGIN_CODE'],externalProviderBindings:{WECHAT_MINIAPP_LOGIN_CODE:8},hostedLoginMethods:[]});
    detail();tab('登录控制');
    field('统一登录入口').find('.el-switch').should('not.have.class','is-checked').click();
    cy.contains('.selected-source','微信小程序源').should('be.visible');
    cy.contains('button','保存并启用').click();
    cy.wait('@update').then(({request})=>{
      expect(request.body.settings.authorizationGrantTypes).to.include('authorization_code').and.include(external);
      expect(request.body.externalCodeTypes).to.deep.equal(['WECHAT_MINIAPP_LOGIN_CODE']);
      expect(request.body.hostedLoginMethods).to.deep.equal([]);
    });
    field('统一登录入口').find('.el-switch').click();
    cy.contains('button','保存并启用').click();
    cy.wait('@update').then(({request})=>{
      expect(request.body.settings.authorizationGrantTypes).to.equal(external);
      expect(request.body.externalProviderBindings).to.deep.equal({WECHAT_MINIAPP_LOGIN_CODE:8});
    });
  });

  it("selects an explicit browser source when multiple sources of the same vendor coexist", () => {
    open();
    detail();
    tab("登录控制");
    cy.contains('企微源一').should('not.exist');
    cy.contains('企微源二').should('not.exist');
    cy.contains('button','添加身份源').click();
    cy.contains('.source-option','企微源二').contains('.el-checkbox','网页登录').click();
    cy.get('.el-dialog').contains('button','添加所选').click();
    cy.contains('.selected-source','企微源二').should('be.visible');
    cy.contains('.selected-source','企微源一').should('not.exist');
    cy.contains("button", "保存并启用").click();
    cy.wait("@update").then(({request}) => {
      expect(request.body.browserLoginProviderTypes).to.deep.equal(["WECOM"]);
      expect(request.body.browserProviderBindings).to.deep.equal({WECOM:10});
      expect(request.body.externalCodeTypes).to.deep.equal([]);
    });
  });

  it('selects one protocol and clears settings only after confirmation', () => {
    open(); detail(); tab('协议配置');
    field('登录回调 URI').find('textarea').should('have.value','https://app.example/callback');
    field('协议').contains('.el-radio','OAuth 2.1').click();
    cy.get('.el-message-box').contains('button','取消').click();
    field('登录回调 URI').find('textarea').should('have.value','https://app.example/callback');
    field('协议').contains('.el-radio','OAuth 2.1').click();
    cy.get('.el-message-box').contains('button','确定').click();
    field('登录回调 URI').find('textarea').should('have.value','');
    cy.contains('.el-form-item__label','默认协议').should('not.exist');
    field('访问令牌有效期').should('be.visible');
    cy.contains('button','保存草稿').click();
    cy.wait('@update').then(({request})=>{
      expect(request.body.protocol).to.equal('OAUTH2_1');
      expect(request.body.settings.redirectUris).to.equal('');
      expect(request.body.settings.scopes).to.equal('profile');
      expect(request.body).not.to.have.property('protocolConfigurations');
      expect(request.body).not.to.have.property('defaultProtocol');
    });
    tab('访问授权');field('用户类型').should('be.visible');
  });

  it('does not add on cancel and clears direct login when its source is removed', () => {
    open(); detail(); tab('登录控制');
    cy.contains('button','添加身份源').click();
    cy.contains('.source-option','企微源二').contains('.el-checkbox','网页登录').click();
    cy.get('.el-dialog').contains('button','取消').click();
    cy.get('.selected-source').should('not.exist');
    cy.contains('button','添加身份源').click();
    cy.contains('.source-option','企微源二').contains('.el-checkbox','网页登录').click();
    cy.get('.el-dialog').contains('button','添加所选').click();
    field('默认直登').contains('.el-radio','企微源二').click();
    cy.contains('.selected-source','企微源二').contains('button','移除').click();
    cy.contains('.el-form-item__label','默认直登').should('not.exist');
    cy.contains('button','保存并启用').click();
    cy.wait('@update').then(({request}) => {
      expect(request.body.browserProviderBindings).to.deep.equal({});
      expect(request.body.directLoginProviderType).to.equal('');
    });
  });

  it("warns before discarding changes and clears temporary guide secrets", () => {
    open({
      applicationType: "WEB_SERVER",
      clientAuthenticationMethods: "client_secret_basic",
      secretConfigured: true,
    });
    detail();
    tab("快速开始");
    field("临时示例密钥").find("input").type("temporary-example-secret");
    cy.get("pre").should("not.contain", "temporary-example-secret");
    tab("应用配置");
    tab("快速开始");
    field("临时示例密钥").find("input").should("have.value", "");
    tab("应用配置");
    field("应用名称").find("input").clear().type("未保存");
    cy.contains("button", "返回列表").click();
    cy.get(".el-message-box")
      .should("contain", "未保存")
      .contains("button", "取消")
      .click();
    cy.contains("button", "保存并启用").should("be.visible");
  });

  it("fills the management area on desktop and keeps narrow layouts within the viewport", () => {
    cy.viewport(1920, 1080);
    open();
    cy.get(".client-page").then((page) => {
      expect(page[0].getBoundingClientRect().width).to.be.closeTo(
        page[0].parentElement.clientWidth,
        1
      );
    });
    cy.get(".client-list-main").then((main) =>
      expect(main[0].clientHeight).to.be.greaterThan(500)
    );
    cy.get(".el-loading-mask").should("not.exist");
    cy.get(".client-page").then((page) => {
      const header = page[0]
        .querySelector(".el-header")
        .getBoundingClientRect();
      const main = page[0].querySelector(".el-main").getBoundingClientRect();
      expect(main.top).to.be.at.least(header.bottom - 1);
    });
    cy.screenshot("iam-application-list-full", { capture: "viewport", scale: true });
    detail();
    cy.get(".client-page").then((page) => {
      expect(page[0].getBoundingClientRect().width).to.be.closeTo(
        page[0].parentElement.clientWidth,
        1
      );
    });
    cy.get(".el-loading-mask").should("not.exist");
    cy.document().then((doc) =>
      expect(doc.documentElement.scrollWidth).to.be.at.most(1920)
    );
    cy.get(
      ".adminui-main, .client-page, .configuration-form, .aminui-body"
    ).each((element) => {
      expect(element[0].scrollWidth, element[0].className).to.be.at.most(
        element[0].clientWidth + 1
      );
    });
    cy.get(".client-page").then((page) => {
      const header = page[0]
        .querySelector(".el-header")
        .getBoundingClientRect();
      const main = page[0].querySelector(".el-main").getBoundingClientRect();
      expect(main.top).to.be.at.least(header.bottom - 1);
    });
    cy.screenshot("iam-application-desktop", { capture: "viewport", scale: true });
    cy.viewport(390, 844);
    tab("协议配置");
    cy.get(".el-loading-mask").should("not.exist");
    cy.get(".adminui-main, .client-page, .configuration-form").each(
      (element) => {
        expect(element[0].scrollWidth, element[0].className).to.be.at.most(
          element[0].clientWidth + 1
        );
      }
    );
    cy.screenshot("iam-application-mobile", { capture: "viewport", scale: true });
    cy.document().then((doc) =>
      expect(doc.documentElement.scrollWidth).to.be.at.most(390)
    );
  });
  it("keeps actions visible while each configuration section scrolls on desktop and mobile", () => {
    open(); detail();
    for (const [width,height,device] of [[1440,900,'desktop'],[390,844,'mobile']]) {
      cy.viewport(width,height);
      for (const label of ['应用配置','协议配置','登录控制','访问授权','快速开始']) {
        tab(label);
        cy.get('.save-bar').should('be.visible').then(elements => {
          const bounds=elements[0].getBoundingClientRect();
          expect(bounds.bottom).to.be.at.most(height);
          expect(bounds.top).to.be.greaterThan(0);
          expect(bounds.right).to.be.at.most(width + 1);
        });
        cy.contains('.save-bar button','保存并启用').should('be.visible');
        cy.get('.detail-tabs').should('be.visible');
        cy.get('.client-detail-main').scrollTo('bottom',{ensureScrollable:false});
        cy.get('.detail-tabs').should('be.visible');
        cy.get('.save-bar').then(elements => {
          const bounds=elements[0].getBoundingClientRect();
          expect(bounds.bottom).to.be.at.most(height);
        });
        cy.get('.client-detail-main').scrollTo('top',{ensureScrollable:false});
        cy.document().then(doc=>expect(doc.documentElement.scrollWidth).to.be.at.most(width));
        cy.window().then(win=>{
          const rect=element=>{const value=element.getBoundingClientRect();return {left:value.left,right:value.right,top:value.top,bottom:value.bottom,width:value.width,height:value.height}};
          const selectors=['.client-page','.client-detail-main','.save-bar','[data-testid="client-auth"]'];
          const geometry={width:win.innerWidth,height:win.innerHeight,documentWidth:win.document.documentElement.scrollWidth,elements:{}};
          for(const selector of selectors){const element=win.document.querySelector(selector);if(element)geometry.elements[selector]={...rect(element),display:win.getComputedStyle(element).display,wrap:win.getComputedStyle(element).flexWrap};}
          geometry.buttons=[...win.document.querySelectorAll('.save-bar button')].map(element=>({text:element.innerText,...rect(element)}));
          for(const button of geometry.buttons)expect(button.right,button.text).to.be.at.most(width);
        });
        cy.screenshot('iam-layout-'+device+'-'+label,{capture:'viewport',scale:true});
      }
    }
  });

  it("offers actual OAuth profile even without extra business scopes", () => {
    open({protocol:'OAUTH2_1',scopes:'profile'},{businessScopes:[]});detail();tab('协议配置');
    field('Scope').find('.el-select').click();
    cy.get('.el-select-dropdown:visible').contains('基础资料').should('be.visible');
    cy.get('.el-select-dropdown:visible').contains('OpenID登录').should('not.exist');
    cy.contains('profile 授权读取用户名和昵称').should('be.visible');
  });

  it("uses the OAuth profile when selecting miniapp SDK sources", () => {
    open({applicationType:'MINIAPP',miniappPlatform:'WECHAT',enabledProtocols:['OAUTH2_1'],defaultProtocol:'OAUTH2_1',
      authorizationGrantTypes:'urn:macula:params:oauth:grant-type:external_code',scopes:'api.read'});
    detail(); tab('登录控制'); cy.contains('button','添加身份源').click();
    cy.contains('.source-option','微信小程序源').contains('.el-checkbox','身份登录').should('not.have.class','is-disabled').click();
    cy.get('.el-dialog').contains('button','添加所选').click();
    cy.contains('.selected-source','微信小程序源').should('be.visible');
    cy.contains('button','保存并启用').click();
    cy.wait('@update').then(({request})=>{
      expect(request.body.externalCodeTypes).to.deep.equal(['WECHAT_MINIAPP_LOGIN_CODE']);
      expect(request.body.externalProviderBindings).to.deep.equal({WECHAT_MINIAPP_LOGIN_CODE:8});
    });
  });
  for (const width of [1280,390]) {
    it(`configures OIDC SDK-only and removes its final source at ${width}px`,()=>{
      cy.viewport(width,844);
      open({applicationType:'MINIAPP',miniappPlatform:'WECHAT',protocol:'OIDC',scopes:'openid,profile',
        authorizationGrantTypes:'urn:macula:params:oauth:grant-type:external_code',redirectUris:''});
      detail();tab('协议配置');cy.contains('h4','第三方Code登录').should('be.visible');
      cy.contains('button','管理身份源').click();cy.contains('button','添加身份源').click();
      cy.contains('.source-option','微信小程序源').contains('.el-checkbox','身份登录').click();
      cy.get('.el-dialog').contains('button','添加所选').click();cy.contains('button','保存并启用').click();
      cy.wait('@update').then(({request})=>{
        expect(request.body.protocol).to.equal('OIDC');expect(request.body.settings.authorizationGrantTypes).to.equal('urn:macula:params:oauth:grant-type:external_code');
        expect(request.body.settings.redirectUris).to.equal('');expect(request.body.externalProviderBindings).to.deep.equal({WECHAT_MINIAPP_LOGIN_CODE:8});
      });
      cy.contains('.selected-source','微信小程序源').contains('button','移除').click();
      cy.contains('button','保存草稿').click();
      cy.wait('@update').then(({request})=>{expect(request.body.externalCodeTypes).to.deep.equal([]);expect(request.body.settings.authorizationGrantTypes).to.equal('');});
    });
  }
  for (const width of [1440, 390]) {
    it(`keeps application fields in one column and spaces the credential section at ${width}px`, () => {
      cy.viewport(width, 900); open(); detail();
      cy.get(".client-page .el-loading-mask").should("not.exist");
      cy.get('.application-fields .el-form-item').then(items => {
        const rects = [...items].map(item => item.getBoundingClientRect());
        for (let index = 1; index < rects.length; index++) {
          expect(Math.abs(rects[index].left - rects[0].left)).to.be.lessThan(1);
          expect(rects[index].top).to.be.at.least(rects[index - 1].bottom);
        }
      });
      cy.get('.application-fields').should('not.contain.text','Client Secret');
      cy.screenshot(`application-single-column-${width}px`,{capture:'viewport'});
      tab('协议配置');
      cy.get('.protocol-details').then(section => {
        const heading = section[0].querySelector('.client-credentials h3').getBoundingClientRect();
        expect(heading.top - section[0].getBoundingClientRect().top).to.be.at.least(24);
      });
      cy.screenshot(`protocol-credential-spacing-${width}px`,{capture:'viewport'});
    });
  }

  it("keeps creation dialog actions visible on narrow screens", () => {
    cy.viewport(390,844); open(); cy.contains('button','创建应用').click();
    cy.get('.application-settings-dialog:visible .el-dialog__footer').then(elements=>{
      const rect=elements[0].getBoundingClientRect(); expect(rect.bottom).to.be.at.most(844);
    });
    cy.get('[data-application-type="MINIAPP"]').click();
    cy.get('.client-create .el-form-item').each(row => {
      const label = row[0].querySelector('.el-form-item__label');
      const radio = row[0].querySelector('.el-radio');
      if (label && radio) expect(radio.getBoundingClientRect().top - label.getBoundingClientRect().bottom).to.be.at.least(-1);
    });
    cy.screenshot('create-radio-alignment-390px',{capture:'viewport'});
    cy.get('.application-settings-dialog:visible .el-dialog__body').scrollTo('bottom',{ensureScrollable:false});
    cy.get('.application-settings-dialog:visible .el-dialog__footer').contains('button','创建').should('be.visible');
  });

  it('registers an AppLink callback without enabling IAM authorization_code', () => {
    open({applicationType:'NATIVE',protocol:'OAUTH2_1',defaultProtocol:'OAUTH2_1',enabledProtocols:['OAUTH2_1'],
      authorizationGrantTypes:'urn:macula:params:oauth:grant-type:external_code',scopes:'api.read',redirectUris:'',
      externalCodeTypes:['FEISHU_APP_CODE'],externalProviderBindings:{FEISHU_APP_CODE:14}});
    detail(); tab('协议配置');
    cy.contains('.el-form-item','授权回调 URI 白名单').find('textarea').type('https://app.example/applink');
    cy.get('[data-grant="authorization_code"] input').should('not.be.checked');
    cy.contains('button','保存并启用').click();
    cy.wait('@update').then(({request})=>{
      expect(request.body.settings.redirectUris).to.equal('https://app.example/applink');
      expect(request.body.settings.authorizationGrantTypes).not.to.contain('authorization_code');
    });
  });

  for (const width of [1440,390]) {
    it(`selects only one mobile SDK and confirms replacing the saved source at ${width}px`,()=>{
      cy.viewport(width,900); open({applicationType:'NATIVE',externalCodeTypes:['WECHAT_OPEN_APP_CODE'],externalProviderBindings:{WECHAT_OPEN_APP_CODE:11}});
      detail();tab('登录控制');cy.contains('button','添加身份源').click();
      cy.contains('.source-option','微信原生SDK源').contains('.el-radio','原生授权').click();
      cy.contains('.source-option','飞书AppLink源').contains('.el-radio','移动AppLink授权').click();
      cy.get('.source-options .el-radio.is-checked').should('have.length',1);
      cy.get('.el-dialog').contains('button','添加所选').click();
      cy.contains('.el-message-box','替换登录能力').should('be.visible');
      cy.get('.el-message-box').contains('button','确定').click();
      cy.contains('.selected-source','飞书AppLink源').should('be.visible');
      cy.contains('.selected-source','微信原生SDK源').should('not.exist');
      cy.contains('.el-form-item','统一登录入口').should('be.visible');
    });
    it(`simplifies service configuration without login and callbacks at ${width}px`,()=>{
      cy.viewport(width,900);open({applicationType:'SERVICE',protocol:'OAUTH2_1',userType:null,secretConfigured:true,clientAuthenticationMethods:'client_secret_basic',authorizationGrantTypes:'client_credentials',scopes:'api.read',hostedLoginMethods:[]});
      detail();cy.get('.detail-tabs').contains('登录控制').should('not.exist');cy.get('.detail-tabs').contains('访问授权').should('not.exist');tab('协议配置');
      cy.contains('.el-form-item','登录回调 URI').should('not.exist');cy.contains('.el-form-item','浏览器 Origin').should('not.exist');
      cy.contains('.el-form-item','S256 PKCE').should('not.exist');cy.contains('h4','第三方Code登录').should('not.exist');
      cy.get('[data-grant="client_credentials"]').should('be.visible');cy.get('[data-grant="refresh_token"]').should('not.exist');
      cy.contains('button','保存并启用').click();cy.wait('@update').then(({request})=>{expect(request.body.userType).to.equal(null);expect(request.body.settings.authorizationGrantTypes).to.equal('client_credentials');});
    });
  }

  for(const width of [1440,390]) {
    it(`previews an explicit web-to-container conversion without changing Client ID at ${width}px`,()=>{
      cy.viewport(width,900);open({applicationType:'WEB_SERVER',secretConfigured:true,clientAuthenticationMethods:'client_secret_basic'});
      detail();cy.contains('button','转换为端内应用').click();
      cy.contains('.source-option','企微源一').contains('.el-radio','企微端内授权').click();
      cy.get('.el-dialog').contains('button','添加所选').click();
      cy.contains('.el-message-box','确认转换为端内应用').should('be.visible');
      cy.get('.el-message-box').should('contain','保留：Client ID').should('contain','移除：其他网页与Code来源');
      cy.get('.el-message-box').contains('button','准备转换').click();
      cy.contains('已准备转换为端内应用').should('be.visible');tab('登录控制');
      cy.contains('.el-form-item','统一登录入口').should('not.exist');cy.contains('.selected-source','企微源一').should('be.visible');
      cy.contains('button','保存并启用').click();cy.wait('@update').then(({request})=>{
        expect(request.body.clientId).to.equal('existing-client');expect(request.body.applicationType).to.equal('IN_APP');
        expect(request.body.convertToInApp).to.equal(true);expect(request.body.hostedLoginMethods).to.deep.equal([]);
        expect(request.body.browserCapabilityBindings).to.deep.equal({WECOM_WEBVIEW:9});
        expect(request.body).not.to.have.property('clientSecret');
      });
      cy.screenshot(`application-conversion-${width}`,{capture:'viewport'});
    });
  }

});
