import {installSessionApi,seedAuthenticatedSession} from '../support/auth-session'
beforeEach(() => installSessionApi())
describe("平台身份、消息与接入应用管理", () => {
  const ok = (data) => ({
    statusCode: 200,
    body: { success: true, code: "00000", data: data?.records ? {...data, records:data.records.map(row => row.providerType ?
      {...row,sourceType:sourceType(row.providerType),version:0,autoBindEnabled:false,bindingRule:null,identityFlow:flowType(row.providerType),directoryPolicy:"IDENTITY_ONLY"} : row)} : data },
  });
  const providerCatalog = {
    categories: [
      { code: "social", label: "社交身份提供商" },
      { code: "enterprise", label: "企业身份提供商" },
    ],
    providers: [
      {
        code: "GITHUB",
        label: "GitHub",
        category: "social",
        fields: [
          {
            key: "clientId",
            label: "Client ID",
            required: true,
            secret: false,
          },
          {
            key: "clientSecret",
            label: "Client Secret",
            required: true,
            secret: true,
          },
        ],
      },
      {
        code: "ALIPAY",
        label: "支付宝",
        category: "social",
        fields: [
          { key: "appId", label: "应用 ID", required: true, secret: false },
          {
            key: "appPrivateKey",
            label: "应用私钥",
            required: true,
            secret: true,
            multiline: true,
          },
          {
            key: "alipayPublicKey",
            label: "支付宝公钥",
            required: true,
            secret: false,
            multiline: true,
          },
        ],
      },
      {
        code: "WECHAT",
        label: "微信",
        category: "social",
        fields: [
          { key: "appId", label: "App ID", required: true, secret: false },
          {
            key: "appSecret",
            label: "App Secret",
            required: true,
            secret: true,
          },
        ],
      },
      {
        code: "WECOM",
        label: "企业微信",
        category: "enterprise",
        fields: [
          { key: "corpId", label: "企业 ID", required: true, secret: false },
          { key: "agentId", label: "AgentId", required: true, secret: false },
          { key: "appSecret", label: "Secret", required: true, secret: true },
        ],
      },
      {
        code: "FEISHU",
        label: "飞书",
        category: "enterprise",
        fields: [
          { key: "appId", label: "App ID", required: true, secret: false },
          {
            key: "appSecret",
            label: "App Secret",
            required: true,
            secret: true,
          },
        ],
      },
      {
        code: "DINGTALK",
        label: "钉钉",
        category: "enterprise",
        fields: [
          { key: "appKey", label: "AppKey", required: true, secret: false },
          {
            key: "appSecret",
            label: "AppSecret",
            required: true,
            secret: true,
          },
          { key: "corpId", label: "CorpID", required: false, secret: false },
        ],
      },
    ],
  };
  const sourceType = vendor => ({GITHUB:"GITHUB_OAUTH",ALIPAY:"ALIPAY_APPLICATION",WECHAT:"WECHAT_WEBSITE",
    WECOM:"WECOM_WEB",FEISHU:"FEISHU_WEB",DINGTALK:"DINGTALK_WEB"}[vendor]);
  const flowType = vendor => ({GITHUB:'GITHUB_OAUTH_V1',WECHAT:'WECHAT_WEBSITE_V1',WECOM:'WECOM_WEB_V1',FEISHU:'FEISHU_OAUTH_V3',DINGTALK:'DINGTALK_OAUTH_V1'}[vendor]);
  providerCatalog.sources = providerCatalog.providers.map(provider => ({
    code:sourceType(provider.code),label:provider.label + '具体来源',providerType:provider.code,
    scene:provider.code === 'ALIPAY' ? 'CONFIGURATION' : 'BROWSER',fields:provider.fields,
    identityFields:provider.fields.filter(field => !field.secret && field.key !== 'alipayPublicKey').map(field => field.key),
    bindingRules:provider.category === 'enterprise' ? [{value:provider.code === 'FEISHU' ? 'EMPLOYEE_NO_TO_USERNAME' : 'MEMBER_ID_TO_USERNAME',
      label:provider.code === 'FEISHU' ? '员工编号 → 本地用户名' : '成员 UserID → 本地用户名',prerequisite:'需平台身份属性权限'}] : [],
    flows: flowType(provider.code) ? [{value:flowType(provider.code),label:flowType(provider.code),available:true}] : [],
    directoryPolicies:['IDENTITY_ONLY'],
    autoBindUnavailableReason:provider.category === 'enterprise' ? null : '首次登录需要手动绑定'
  }));
  function selectSourceType() {
    cy.contains('.el-drawer .el-form-item','接入类型').find('.el-radio').first().click();
    cy.get('.el-drawer').then(drawer => {
      if (drawer.find('.el-form-item').text().includes('平台接口版本')) {
        cy.contains('.el-drawer .el-form-item', '平台接口版本').find('.el-radio').first().click();
      }
    });
  }
  const createUrls = {
    GITHUB: "https://github.com/settings/developers",
    ALIPAY: "https://open.alipay.com/module/webApp",
    WECHAT: "https://open.weixin.qq.com/",
    WECOM: "https://work.weixin.qq.com/wework_admin/frame#apps",
    FEISHU: "https://open.feishu.cn/app?lang=zh-CN",
    DINGTALK: "https://open.dingtalk.com/",
  };
  providerCatalog.providers.forEach((provider) => {
    provider.callbackUrl = `/login/${provider.code.toLowerCase()}`;
    provider.createUrl = createUrls[provider.code];
    provider.createHint = `${provider.label}平台 → 创建应用`;
    provider.fields.forEach((field) => {
      field.help = `${provider.label}应用详情 → ${field.label}`;
    });
  });
  const children = [
    [
      "/system/identity-provider",
      "system/identityProvider/index",
      "身份提供商",
    ],
    ["/system/security-policy", "system/securityPolicy/index", "安全策略"],
    ["/system/message-settings", "system/messageSettings/index", "消息设置"],
    ["/system/oauth2-client", "system/oauth2Client/index", "IAM接入应用"],
  ].map(([path, component, title]) => ({ path, component, meta: { title } }));

  function open(
    path,
    externalCodeCatalog = { protocols: [], scopes: [], sources: [] },
    externalCodeCatalogDelay = 0,
    externalCodeCatalogAlias = "externalCodeCatalog",
    externalCodeCatalogTimes
  ) {
    cy.intercept(
      "GET",
      "**/system/api/v1/identity-providers/catalog*",
      ok(providerCatalog)
    ).as("providerCatalog");
    cy.intercept(
      {
        method: "GET",
        url: "**/system/api/v1/oauth2-clients/configuration-catalog*",
        ...(externalCodeCatalogTimes
          ? { times: externalCodeCatalogTimes }
          : {}),
      },
      { ...ok(externalCodeCatalog), delay: externalCodeCatalogDelay }
    ).as(externalCodeCatalogAlias);
    cy.visit(`/#${path}`, {
      onBeforeLoad(win) {
        seedAuthenticatedSession(win)
        win.sessionStorage.setItem(
          "USER_INFO",
          JSON.stringify({
            content: { username: "root", roles: ["ROOT"] },
            datetime: 0,
          })
        );
        win.sessionStorage.setItem(
          "MENU",
          JSON.stringify({
            content: [
              {
                path: "/system",
                component: "Layout",
                meta: { title: "系统管理" },
                children,
              },
            ],
            datetime: 0,
          })
        );
      },
    });
  }

  it("separates social and enterprise providers with provider-specific JSON fields", () => {
    cy.intercept("GET", "**/system/api/v1/identity-providers*", (req) =>
      req.reply(
        ok({
          records:
            new URL(req.url).searchParams.get("category") === "enterprise"
              ? [
                  {
                    id: 2,
                    name: "企业微信",
                    providerType: "WECOM",
                    category: "enterprise",
                    config: { corpId: "corp-1", agentId: "agent-1" },
                    configuredSecrets: { appSecret: true },
                    enabled: true,
                    visible: true,
                  },
                ]
              : [
                  {
                    id: 1,
                    name: "GitHub 登录",
                    providerType: "GITHUB",
                    category: "social",
                    config: { clientId: "client-id" },
                    configuredSecrets: { clientSecret: true },
                    enabled: true,
                    visible: true,
                  },
                ],
          total: 1,
        })
      )
    ).as("providers");
    cy.intercept("POST", "**/system/api/v1/identity-providers", {
      ...ok({ id: 3 }),
      delay: 400,
    }).as("createProvider");
    open("/system/identity-provider");
    cy.wait("@providers").then(({ request }) => {
      expect(new URL(request.url).searchParams.get("category")).to.equal(
        "social"
      );
    });
    cy.get(".identity-page .el-header").then(($header) => {
      cy.get(".identity-page .el-table").then(($table) => {
        const gap =
          $table[0].getBoundingClientRect().top -
          $header[0].getBoundingClientRect().bottom;
        expect(gap).to.be.lessThan(75);
      });
    });
    cy.get(".identity-page .scTable").should("be.visible");
    cy.get(".identity-page .identity-main").then(($main) => {
      cy.get(".identity-page .scTable").then(($table) => {
        const bottomGap =
          $main[0].getBoundingClientRect().bottom -
          $table[0].getBoundingClientRect().bottom;
        expect(Math.abs(bottomGap)).to.be.lessThan(4);
      });
    });
    cy.get(".identity-page .scTable-page").should("be.visible");
    cy.contains("GitHub 登录").should("be.visible");
    cy.contains("已配置").should("be.visible");
    cy.contains("暂未生效").should("not.exist");
    cy.contains(".el-tabs__item", "企业身份提供商").click();
    cy.wait("@providers").then(({ request }) => {
      expect(new URL(request.url).searchParams.get("category")).to.equal(
        "enterprise"
      );
    });
    cy.contains("企业微信").should("be.visible");
    cy.contains("GitHub 登录").should("not.exist");
    cy.contains("tr", "企业微信").contains("button", "修改").click();
    cy.contains(".el-drawer .el-form-item", "企业 ID")
      .find("input")
      .should("have.value", "corp-1");
    cy.contains(".el-drawer .el-form-item__label", /^Secret$/).closest(".el-form-item")
      .find("input")
      .should("have.value", "");
    cy.contains(".el-drawer", "取消").contains("button", "取消").click();
    cy.contains("添加认证源").click();
    selectSourceType();
    cy.contains(".el-drawer .el-form-item", "企业 ID").should("be.visible");
    cy.contains(".el-drawer .el-form-item__label", /^AgentId$/).closest(".el-form-item").should("be.visible");
    cy.contains(".el-drawer .el-form-item__label", /^Secret$/).closest(".el-form-item").should("be.visible");
    cy.contains(".el-drawer .el-form-item", "Client ID").should("not.exist");
    cy.contains(".el-drawer .el-form-item", "回调地址")
      .should("contain", "/login/wecom")
      .find("input")
      .should("not.exist");
    cy.contains(".el-drawer .el-form-item", "认证源名称")
      .find("input")
      .type("新企业微信");
    cy.contains(".el-drawer .el-form-item", "企业 ID")
      .find("input")
      .type("corp-new");
    cy.contains(".el-drawer .el-form-item__label", /^AgentId$/).closest(".el-form-item")
      .find("input")
      .type("agent-new");
    cy.contains(".el-drawer .el-form-item__label", /^Secret$/).closest(".el-form-item")
      .find("input")
      .type("test-secret");
    cy.contains(".el-drawer", "确定").contains("button", "确定").dblclick();
    cy.wait("@createProvider").then(({ request }) => {
      expect(request.body.category).to.equal("enterprise");
      expect(request.body).not.to.have.property("callbackUrl");
      expect(request.body.providerType).to.equal("WECOM");
      expect(request.body.sourceType).to.equal("WECOM_WEB");
      expect(request.body.autoBindEnabled).to.equal(false);
      expect(request.body).not.to.have.property("bindingRule");
      expect(request.body.bindingMappings).to.deep.equal([]);
      expect(request.body.config).to.deep.equal({
        corpId: "corp-new",
        agentId: "agent-new",
        appSecret: "test-secret",
      });
      expect(request.body.configJson).to.be.undefined;
    });
    cy.get("@createProvider.all").should("have.length", 1);
  });

  it("requires an explicit automatic binding rule and clears it when disabled", () => {
    cy.intercept("GET", "**/system/api/v1/identity-providers*", ok({records:[],total:0}));
    cy.intercept("POST", "**/system/api/v1/identity-providers", ok({id:9})).as("saveBindingPolicy");
    open("/system/identity-provider");
    cy.contains(".el-tabs__item", "企业身份提供商").click();
    cy.contains("添加认证源").click();
    selectSourceType();
    cy.contains(".el-drawer .el-form-item", "认证源名称").find("input").type("显式绑定策略");
    cy.contains(".el-drawer .el-form-item", "企业 ID").find("input").type("corp-policy");
    cy.contains(".el-drawer .el-form-item__label", /^AgentId$/).closest(".el-form-item").find("input").type("agent-policy");
    cy.contains(".el-drawer .el-form-item__label", /^Secret$/).closest(".el-form-item").find("input").type("test-secret");
    cy.contains(".el-drawer .el-form-item", "自动绑定").find(".el-switch").click();
    cy.contains(".el-drawer .el-form-item", "身份源字段").find(".el-select").click();
    cy.get(".el-select-dropdown:visible .el-select-dropdown__item").contains("成员 ID").click();
    cy.contains(".el-drawer .el-form-item", "IAM 匹配字段").find(".el-select").click();
    cy.get(".el-select-dropdown:visible .el-select-dropdown__item").contains("用户名").click();
    cy.contains(".el-drawer .el-form-item", "自动绑定").find(".el-switch").click();
    cy.contains(".el-drawer .el-form-item", "身份源字段").should("not.exist");
    cy.contains(".el-drawer .el-form-item", "自动绑定").find(".el-switch").click();
    cy.contains(".el-drawer .el-form-item", "身份源字段").find(".el-select").click();
    cy.get(".el-select-dropdown:visible .el-select-dropdown__item").contains("成员 ID").click();
    cy.contains(".el-drawer .el-form-item", "IAM 匹配字段").find(".el-select").click();
    cy.get(".el-select-dropdown:visible .el-select-dropdown__item").contains("用户名").click();
    cy.contains(".el-drawer button", "确定").click();
    cy.wait("@saveBindingPolicy").then(({request}) => {
      expect(request.body.autoBindEnabled).to.equal(true);
      expect(request.body.bindingMappings).to.deep.equal([{sourceField:"member_id",targetField:"username"}]);
      expect(request.body).not.to.have.property("bindingRule");
    });
  });

  [
    {
      category: "social",
      label: "GitHub",
      providerType: "GITHUB",
      fields: ["Client ID", "Client Secret"],
    },
    {
      category: "social",
      label: "支付宝",
      providerType: "ALIPAY",
      fields: ["应用 ID", "应用私钥", "支付宝公钥"],
    },
    {
      category: "social",
      label: "微信",
      providerType: "WECHAT",
      fields: ["App ID", "App Secret"],
    },
    {
      category: "enterprise",
      label: "企业微信",
      providerType: "WECOM",
      fields: ["企业 ID", "AgentId", "Secret"],
    },
    {
      category: "enterprise",
      label: "飞书",
      providerType: "FEISHU",
      fields: ["App ID", "App Secret"],
    },
    {
      category: "enterprise",
      label: "钉钉",
      providerType: "DINGTALK",
      fields: ["AppKey", "AppSecret", "CorpID"],
    },
  ].forEach(({ category, label, providerType, fields }) => {
    it(`shows only ${label} configuration fields`, () => {
      cy.intercept("GET", "**/system/api/v1/identity-providers*", (req) => {
        const matches =
          new URL(req.url).searchParams.get("category") === category;
        req.reply(
          ok({
            records: matches
              ? [
                  {
                    id: 1,
                    name: `${label}示例`,
                    providerType,
                    enabled: true,
                    visible: true,
                  },
                ]
              : [],
            total: matches ? 1 : 0,
          })
        );
      }).as("providers");
      open("/system/identity-provider");
      cy.wait("@providers");
      if (category === "enterprise") {
        cy.contains(".el-tabs__item", "企业身份提供商").click();
        cy.wait("@providers");
      }
      cy.contains(".el-table__row", `${label}示例`)
        .find(".provider-icon svg")
        .should("be.visible");
      cy.contains("添加认证源").click();
    selectSourceType();
      cy.contains(".el-drawer .el-form-item", "认证源类型").contains('.el-radio',label).click();
      selectSourceType();
      cy.get(".el-drawer .el-form-item__label").then(($labels) => {
        const common = [
          "认证源类型",
          "接入类型",
          "平台接口版本 / Flow",
          "通讯录策略",
          "固定组织标识",
          "已登记上游回调",
          "自动绑定",
          "绑定依据",
          "身份源字段",
          "IAM 匹配字段",
          "创建应用",
          "回调地址",
          "认证源名称",
          "是否显示",
          "启用",
          "备注",
        ];
        const dynamic = [...$labels]
          .map((node) => node.textContent.trim())
          .filter((name) => !common.includes(name));
        expect(dynamic).to.deep.equal(fields);
      });
      cy.get(".el-drawer a.provider-create-link")
        .should("have.attr", "href", createUrls[providerType])
        .should("have.attr", "target", "_blank")
        .should("have.attr", "rel", "noopener noreferrer");
      fields.forEach((field) => {
        cy.contains(".el-drawer .el-form-item", field)
          .find(".field-help")
          .should("contain", field);
      });
      cy.contains(".el-drawer", "Gitee").should("not.exist");
      cy.contains(".el-drawer", "QQ").should("not.exist");
    });
  });

  it("keeps server pagination and resets to page one when searching", () => {
    cy.intercept("GET", "**/system/api/v1/identity-providers*", (req) => {
      const query = new URL(req.url).searchParams;
      req.reply(
        ok({
          records: [
            {
              id: Number(query.get("pageNum")),
              name:
                query.get("pageNum") === "2" ? "第二页认证源" : "第一页认证源",
              providerType: "GITHUB",
              enabled: true,
              visible: true,
            },
          ],
          total: 21,
        })
      );
    }).as("providers");
    open("/system/identity-provider");
    cy.wait("@providers");
    cy.get(".identity-page .scTable-page .el-pager .number")
      .contains("2")
      .click();
    cy.wait("@providers").then(({ request }) => {
      expect(new URL(request.url).searchParams.get("pageNum")).to.equal("2");
    });
    cy.contains("第二页认证源").should("be.visible");
    cy.get(".identity-page .right-panel-search input").type("测试认证源");
    cy.contains(".identity-page .right-panel-search button", "查询").click();
    cy.wait("@providers").then(({ request }) => {
      const query = new URL(request.url).searchParams;
      expect(query.get("pageNum")).to.equal("1");
      expect(query.get("name")).to.equal("测试认证源");
      expect(query.get("category")).to.equal("social");
    });
    cy.contains("第一页认证源").should("be.visible");
  });

  it("keeps the full-height table when the category is empty or fails", () => {
    let failed = false;
    cy.intercept("GET", "**/system/api/v1/identity-providers*", (req) => {
      req.reply(
        failed
          ? {
              statusCode: 200,
              body: {
                success: false,
                code: "ERROR",
                msg: "认证源列表失败",
                data: { records: [], total: 0 },
              },
            }
          : ok({ records: [], total: 0 })
      );
    }).as("providers");
    open("/system/identity-provider");
    cy.wait("@providers");
    cy.contains("暂无数据").should("be.visible");
    cy.get(".identity-page .identity-main").then(($main) => {
      cy.get(".identity-page .scTable").then(($table) => {
        expect(
          Math.abs(
            $main[0].getBoundingClientRect().bottom -
              $table[0].getBoundingClientRect().bottom
          )
        ).to.be.lessThan(4);
      });
    });
    cy.then(() => {
      failed = true;
    });
    cy.get(".identity-page .scTable-do button").first().click();
    cy.wait("@providers");
    cy.contains("认证源列表失败").should("be.visible");
  });

  it("manages the fixed mail and SMS provider/template scope", () => {
    cy.intercept(
      "GET",
      "**/message-settings/templates/MAIL*",
      ok([
        {
          templateKey: "MAIL_LOGIN",
          channel: "MAIL",
          senderName: "Macula Cloud",
          title: "登录验证",
          content: "验证码 ${code}",
          enabled: true,
          overridden: false,
          variables: ["code"],
        },
      ])
    ).as("mailTemplates");
    cy.intercept(
      "POST",
      "**/message-settings/templates/MAIL/MAIL_LOGIN/preview",
      ok({
        content: "<p>验证码 123456</p>",
      })
    ).as("preview");
    cy.intercept(
      "GET",
      "**/message-settings/providers/MAIL*",
      ok({
        channel: "MAIL",
        providerType: "CUSTOM",
        endpoint: "smtp.example.test",
        port: 465,
        securityMode: "SSL",
        account: "mail-user",
        sender: "sender@example.test",
        enabled: true,
        credentialConfigured: true,
      })
    ).as("mailProvider");
    cy.intercept(
      "GET",
      "**/message-settings/providers/SMS*",
      ok({
        channel: "SMS",
        providerType: "TENCENT",
        account: "secret-id",
        sender: "Macula",
        regionCode: "ap-guangzhou",
        smsSdkAppId: "1400000000",
        enabled: true,
        credentialConfigured: true,
      })
    ).as("smsProvider");
    cy.intercept(
      "GET",
      "**/message-settings/templates/SMS*",
      ok([
        {
          templateKey: "SMS_LOGIN",
          channel: "SMS",
          providerType: "TENCENT",
          languageCode: "zh-CN",
          providerTemplateCode: "123456",
          enabled: true,
          overridden: true,
          variables: ["code"],
        },
      ])
    ).as("smsTemplates");
    cy.intercept(
      "POST",
      "**/message-settings/test",
      ok({ status: "submitted" })
    ).as("sendTest");

    open("/system/message-settings");
    cy.wait("@mailTemplates");
    cy.contains("登录验证").should("be.visible");
    cy.contains("button", "配置").click();
    cy.contains(".el-drawer:visible", "发件人显示名").should("be.visible");
    cy.get(".el-drawer:visible").contains("button", "取消").click();
    cy.get(".el-drawer:visible").should("not.exist");
    cy.contains("button", "浏览").click();
    cy.wait("@preview");
    cy.contains(".el-dialog:visible .el-dialog__title", "登录验证").should(
      "be.visible"
    );
    cy.get("iframe[sandbox]")
      .should("be.visible")
      .and("have.attr", "srcdoc")
      .and("contain", 'class="mail-preview-card"')
      .and("contain", 'class="mail-preview-subject"')
      .and("contain", "登录验证")
      .and("contain", "验证码 123456");
    cy.get(".el-dialog:visible .el-dialog__headerbtn").click();
    cy.get(".el-dialog:visible").should("not.exist");

    cy.contains(".el-tabs__item", "邮件服务").click();
    cy.wait("@mailProvider");
    cy.contains(".message-page .el-form-item", "SMTP 服务器").should(
      "be.visible"
    );
    cy.contains("Exchange").should("not.exist");

    cy.contains(".el-tabs__item", "短信服务").click();
    cy.wait("@smsProvider");
    cy.wait("@smsTemplates");
    cy.contains("登录验证").scrollIntoView().should("be.visible");
    cy.contains("七牛").should("not.exist");
    cy.contains("button", "测试").click();
    cy.get(".el-dialog:visible input").type("13800000000");
    cy.get(".el-dialog:visible").contains("button", "提交").click();
    cy.wait("@sendTest").then(({ request }) => {
      expect(request.body.scene).to.equal("SMS_LOGIN");
      expect(request.body.target).to.equal("13800000000");
      expect(request.body.variables.code).to.equal("123456");
    });
  });

  it("loads SMS templates on first enable and follows the template language", () => {
    cy.intercept("GET", "**/message-settings/templates/MAIL*", ok([])).as(
      "emptyMailTemplates"
    );
    cy.intercept(
      "GET",
      "**/message-settings/providers/SMS*",
      ok({
        channel: "SMS",
        providerType: null,
        enabled: false,
        credentialConfigured: false,
      })
    ).as("emptySmsProvider");
    cy.intercept("GET", "**/message-settings/templates/SMS*", (request) => {
      const languageCode = new URL(request.url).searchParams.get(
        "languageCode"
      );
      request.reply(
        ok([
          {
            templateKey: "SMS_LOGIN",
            channel: "SMS",
            providerType: "ALIYUN",
            languageCode,
            providerTemplateCode: "",
            enabled: false,
            overridden: false,
            variables: ["code"],
          },
        ])
      );
    }).as("smsTemplatesByLanguage");

    open("/system/message-settings");
    cy.wait("@emptyMailTemplates");
    cy.contains(".el-tabs__item", "短信服务").click();
    cy.wait("@emptySmsProvider");
    cy.contains(".el-form-item", "启用服务").find(".el-switch").click();
    cy.contains(".el-form-item", "服务商").find(".el-select").click();
    cy.contains(".el-select-dropdown__item", "阿里云").click();
    cy.wait("@smsTemplatesByLanguage").then(({ request }) => {
      expect(new URL(request.url).searchParams.get("languageCode")).to.equal(
        "zh-CN"
      );
    });
    cy.contains("登录验证").should("be.visible");

    cy.contains(".el-radio-button", "English").click();
    cy.wait("@smsTemplatesByLanguage").then(({ request }) => {
      expect(new URL(request.url).searchParams.get("languageCode")).to.equal(
        "en-US"
      );
    });
    cy.contains("Sign-in verification").should("be.visible");
    cy.contains("登录验证").should("not.exist");
  });

  it("keeps SMS templates bound to the latest provider and language request", () => {
    cy.intercept("GET", "**/message-settings/templates/MAIL*", ok([])).as(
      "emptyMailTemplates"
    );
    cy.intercept(
      "GET",
      "**/message-settings/providers/SMS*",
      ok({
        channel: "SMS",
        providerType: "ALIYUN",
        account: "access-key",
        sender: "Macula",
        enabled: true,
        credentialConfigured: true,
      })
    ).as("aliyunProvider");
    cy.intercept(
      "GET",
      "**/message-settings/templates/SMS*providerType=ALIYUN*",
      ok([
        {
          templateKey: "SMS_LOGIN",
          channel: "SMS",
          providerType: "ALIYUN",
          languageCode: "zh-CN",
          providerTemplateCode: "ALIYUN_CURRENT",
          enabled: true,
          overridden: true,
          variables: ["code"],
        },
      ])
    ).as("aliyunTemplates");
    let resolveTencent;
    let tencentCalls = 0;
    cy.intercept(
      "GET",
      "**/message-settings/templates/SMS*providerType=TENCENT*",
      (request) => {
        tencentCalls += 1;
        if (tencentCalls > 1) {
          request.reply({
            statusCode: 500,
            body: { success: false, msg: "腾讯模板加载失败" },
          });
          return;
        }
        return new Promise((resolve) => {
          resolveTencent = () => {
            request.reply(
              ok([
                {
                  templateKey: "SMS_LOGIN",
                  channel: "SMS",
                  providerType: "TENCENT",
                  languageCode: "zh-CN",
                  providerTemplateCode: "TENCENT_STALE",
                  enabled: true,
                  overridden: true,
                  variables: ["code"],
                },
              ])
            );
            resolve();
          };
        });
      }
    ).as("tencentTemplates");

    open("/system/message-settings");
    cy.wait("@emptyMailTemplates");
    cy.contains(".el-tabs__item", "短信服务").click();
    cy.wait("@aliyunProvider");
    cy.wait("@aliyunTemplates");
    cy.contains(".el-form-item", "服务商").find(".el-select").click();
    cy.contains(".el-select-dropdown__item", "腾讯云").click();
    cy.wrap(null).should(() => expect(resolveTencent).to.be.a("function"));
    cy.contains(".el-form-item", "服务商").find(".el-select").click();
    cy.contains(".el-select-dropdown__item", "阿里云").click();
    cy.wait("@aliyunTemplates");
    cy.get(".el-table input").should("have.value", "ALIYUN_CURRENT");
    cy.then(() => resolveTencent());
    cy.get(".el-table input").should("have.value", "ALIYUN_CURRENT");

    cy.contains(".el-form-item", "服务商").find(".el-select").click();
    cy.contains(".el-select-dropdown__item", "腾讯云").click();
    cy.wait("@tencentTemplates");
    cy.contains("腾讯模板加载失败").should("exist");
    cy.get(".el-table input").should("not.exist");
  });

  it("opens an independent browser view for every mail template", () => {
    const templates = [
      ["MAIL_BIND", "绑定邮箱"],
      ["MAIL_CHANGE_BIND", "修改绑定邮箱"],
      ["MAIL_FORGOT_PASSWORD", "忘记密码"],
      ["MAIL_CHANGE_PASSWORD", "修改密码"],
      ["MAIL_RESET_SUCCESS", "管理员重置密码成功"],
      ["MAIL_LOGIN", "登录验证"],
      ["MAIL_PASSWORD_EXPIRY", "密码到期提醒"],
      ["MAIL_WELCOME", "欢迎邮件"],
    ].map(([templateKey, title]) => ({
      templateKey,
      channel: "MAIL",
      senderName: "Macula Cloud",
      title,
      content: `<p>${templateKey}</p>`,
      enabled: true,
      overridden: false,
      variables: [],
    }));
    cy.intercept(
      "GET",
      "**/message-settings/templates/MAIL*",
      ok(templates)
    ).as("allMailTemplates");
    cy.intercept(
      "POST",
      "**/message-settings/templates/MAIL/*/preview",
      (request) => {
        const scene = request.url.match(/\/MAIL\/([^/]+)\/preview/)?.[1];
        request.reply(ok({ content: `<p>preview-${scene}</p>` }));
      }
    ).as("previewMailTemplate");

    open("/system/message-settings");
    cy.wait("@allMailTemplates");
    cy.get(".mail-template-item").should("have.length", 8);
    templates.forEach(({ templateKey, title }) => {
      cy.contains(".mail-template-item", title)
        .contains("button", "浏览")
        .click();
      cy.wait("@previewMailTemplate").then(({ request }) => {
        expect(request.url).to.contain(`/MAIL/${templateKey}/preview`);
      });
      cy.get(".el-dialog:visible .el-dialog__title").should(
        "contain.text",
        title
      );
      cy.get(".el-dialog:visible iframe[sandbox]")
        .should("have.attr", "srcdoc")
        .and("contain", `preview-${templateKey}`);
      cy.get(".el-dialog:visible").contains("button", "确定").click();
      cy.get(".el-dialog:visible").should("not.exist");
    });
  });
  [1440,390].forEach(width => {
    it(`stores one WeCom app with several login capabilities at ${width}px`, () => {
      cy.viewport(width,900);
      const fields=[{key:'corpId',label:'企业ID',required:true},{key:'agentId',label:'Agent ID',required:true},
        {key:'appSecret',label:'应用Secret',required:true,secret:true}];
      providerCatalog.credentials=[{code:'WECOM_APPLICATION',label:'企业微信应用',providerType:'WECOM',sourceType:'WECOM_WEB',fields,
        capabilities:[{code:'WECOM_BROWSER',label:'PC网页扫码',available:true,browser:true},
          {code:'WECOM_WEBVIEW',label:'企微端内网页',available:true,browser:true},
          {code:'WECOM_MINIAPP',label:'关联小程序登录',available:true,browser:false}]}];
      cy.intercept('GET','**/system/api/v1/identity-providers*',ok({records:[],total:0}));
      cy.intercept('POST','**/system/api/v1/identity-providers',ok({id:20})).as('saveCapabilities');
      open('/system/identity-provider');
      cy.contains('.el-tabs__item','企业身份提供商').click();
      cy.contains('添加认证源').click();
      cy.contains('.el-drawer .el-form-item','认证源类型').contains('.el-radio','企业微信').click();
      cy.get('.el-drawer .el-form-item__label').should('not.contain','应用类型');
      cy.contains('.el-drawer .el-checkbox','PC网页扫码').find('input').should('be.checked');
      ['PC网页扫码','企微端内网页','关联小程序登录'].forEach(label => {
        cy.contains('.el-drawer .el-checkbox',label).find('input').check({force:true});
      });
      [['认证源名称','统一企微应用'],['企业ID','corp-fixture'],['Agent ID','1001'],['应用Secret','fixture-secret']].forEach(([label,value]) => {
        cy.contains('.el-drawer .el-form-item',label).find('input').first().type(value);
      });
      cy.get('.el-drawer .el-checkbox input:checked').should('have.length',3);
      cy.get('.el-drawer__body').then($body => expect($body[0].scrollWidth).to.be.at.most($body[0].clientWidth+1));
      cy.get('.el-drawer__body').scrollTo('top');
      cy.screenshot(`identity-capabilities-${width}`);
      cy.contains('.el-drawer button','确定').click();
      cy.wait('@saveCapabilities').then(({request}) => {
        expect(request.body.credentialType).to.eq('WECOM_APPLICATION');
        expect(request.body.loginCapabilities).to.have.members(['WECOM_BROWSER','WECOM_WEBVIEW','WECOM_MINIAPP']);
        expect(request.body.config).to.deep.eq({corpId:'corp-fixture',agentId:'1001',appSecret:'fixture-secret'});
        expect(request.body).not.to.have.property('identityFlow');
      });
      cy.then(()=>{delete providerCatalog.credentials;});
    });
  });

  it('enables mobile SDK with web and in-app login on the same DingTalk app', () => {
    const fields=[{key:'appKey',label:'Client ID / AppKey',required:true},
      {key:'appSecret',label:'AppSecret',required:true,secret:true},{key:'corpId',label:'企业ID',required:true}];
    providerCatalog.credentials=[{code:'DINGTALK_APPLICATION',label:'钉钉应用',providerType:'DINGTALK',sourceType:'DINGTALK_WEB',fields,
      capabilities:[{code:'DINGTALK_WEB',label:'网页授权',available:true,browser:true},
        {code:'DINGTALK_SDK',label:'移动SDK授权登录',available:true,browser:false},
        {code:'DINGTALK_WEBVIEW',label:'端内免登',available:true,browser:false}]}];
    cy.intercept('GET','**/system/api/v1/identity-providers*',ok({records:[],total:0}));
    cy.intercept('POST','**/system/api/v1/identity-providers',ok({id:21})).as('saveDingMobile');
    open('/system/identity-provider');
    cy.contains('.el-tabs__item','企业身份提供商').click();
    cy.contains('添加认证源').click();
    cy.contains('.el-drawer .el-form-item','认证源类型').contains('.el-radio','钉钉').click();
    cy.get('.el-drawer .el-form-item__label').should('not.contain','应用类型');
    ['网页授权','移动SDK授权登录','端内免登'].forEach(label=>{
      cy.contains('.el-drawer .el-checkbox',label).find('input').should('not.be.disabled').check({force:true});
    });
    [['认证源名称','统一钉钉应用'],['Client ID / AppKey','ding-fixture'],['AppSecret','fixture-secret'],['企业ID','corp-fixture']].forEach(([label,value])=>{
      cy.contains('.el-drawer .el-form-item',label).find('input').first().type(value);
    });
    cy.get('.el-drawer__body').scrollTo('top');cy.screenshot('dingtalk-unified-mobile');
    cy.contains('.el-drawer button','确定').click();
    cy.wait('@saveDingMobile').then(({request})=>{
      expect(request.body.credentialType).to.eq('DINGTALK_APPLICATION');
      expect(request.body.loginCapabilities).to.have.members(['DINGTALK_WEB','DINGTALK_SDK','DINGTALK_WEBVIEW']);
      expect(request.body.config).to.deep.eq({appKey:'ding-fixture',appSecret:'fixture-secret',corpId:'corp-fixture'});
    });
    cy.then(()=>{delete providerCatalog.credentials;});
  });

});
