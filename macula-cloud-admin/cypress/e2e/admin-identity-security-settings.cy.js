describe("平台身份、消息与接入应用管理", () => {
  const ok = (data) => ({
    statusCode: 200,
    body: { success: true, code: "00000", data },
  });
  const providerCatalog = {
    categories: [
      { code: "social", label: "社交身份提供商" },
      { code: "enterprise", label: "企业身份提供商" },
    ],
    providers: [
      { code: "GITHUB", label: "GitHub", category: "social", fields: [
        { key: "clientId", label: "Client ID", required: true, secret: false },
        { key: "clientSecret", label: "Client Secret", required: true, secret: true },
      ] },
      { code: "ALIPAY", label: "支付宝", category: "social", fields: [
        { key: "appId", label: "应用 ID", required: true, secret: false },
        { key: "appPrivateKey", label: "应用私钥", required: true, secret: true, multiline: true },
        { key: "alipayPublicKey", label: "支付宝公钥", required: true, secret: false, multiline: true },
      ] },
      { code: "WECHAT", label: "微信", category: "social", fields: [
        { key: "appId", label: "App ID", required: true, secret: false },
        { key: "appSecret", label: "App Secret", required: true, secret: true },
      ] },
      { code: "WECOM", label: "企业微信", category: "enterprise", fields: [
        { key: "corpId", label: "企业 ID", required: true, secret: false },
        { key: "agentId", label: "AgentId", required: true, secret: false },
        { key: "appSecret", label: "Secret", required: true, secret: true },
      ] },
      { code: "FEISHU", label: "飞书", category: "enterprise", fields: [
        { key: "appId", label: "App ID", required: true, secret: false },
        { key: "appSecret", label: "App Secret", required: true, secret: true },
      ] },
      { code: "DINGTALK", label: "钉钉", category: "enterprise", fields: [
        { key: "appKey", label: "AppKey", required: true, secret: false },
        { key: "appSecret", label: "AppSecret", required: true, secret: true },
        { key: "corpId", label: "CorpID", required: false, secret: false },
      ] },
    ],
  };
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

  function open(path) {
    cy.intercept("GET", "**/system/api/v1/identity-providers/catalog*", ok(providerCatalog))
      .as("providerCatalog");
    cy.setCookie("TOKEN", "root-test-token");
    cy.visit(`/#${path}`, {
      onBeforeLoad(win) {
        win.localStorage.setItem(
          "USER_INFO",
          JSON.stringify({
            content: { username: "root", roles: ["ROOT"] },
            datetime: 0,
          })
        );
        win.localStorage.setItem(
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
    cy.intercept(
      "POST",
      "**/system/api/v1/identity-providers",
      { ...ok({ id: 3 }), delay: 400 }
    ).as("createProvider");
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
    cy.contains(".el-drawer .el-form-item", "Secret")
      .find("input")
      .should("have.value", "");
    cy.contains(".el-drawer", "取消").contains("button", "取消").click();
    cy.contains("添加认证源").click();
    cy.contains(".el-drawer .el-form-item", "企业 ID").should("be.visible");
    cy.contains(".el-drawer .el-form-item", "AgentId").should("be.visible");
    cy.contains(".el-drawer .el-form-item", "Secret").should("be.visible");
    cy.contains(".el-drawer .el-form-item", "Client ID").should("not.exist");
    cy.contains(".el-drawer .el-form-item", "回调地址")
      .should("contain", "/login/wecom")
      .find("input").should("not.exist");
    cy.contains(".el-drawer .el-form-item", "认证源名称")
      .find("input")
      .type("新企业微信");
    cy.contains(".el-drawer .el-form-item", "企业 ID")
      .find("input")
      .type("corp-new");
    cy.contains(".el-drawer .el-form-item", "AgentId")
      .find("input")
      .type("agent-new");
    cy.contains(".el-drawer .el-form-item", "Secret")
      .find("input")
      .type("test-secret");
    cy.contains(".el-drawer", "确定").contains("button", "确定").dblclick();
    cy.wait("@createProvider").then(({ request }) => {
      expect(request.body.category).to.equal("enterprise");
      expect(request.body).not.to.have.property("callbackUrl");
      expect(request.body.providerType).to.equal("WECOM");
      expect(request.body.config).to.deep.equal({
        corpId: "corp-new",
        agentId: "agent-new",
        appSecret: "test-secret",
      });
      expect(request.body.configJson).to.be.undefined;
    });
    cy.get("@createProvider.all").should("have.length", 1);
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
    { category: "social", label: "微信", providerType: "WECHAT", fields: ["App ID", "App Secret"] },
    {
      category: "enterprise",
      label: "企业微信",
      providerType: "WECOM",
      fields: ["企业 ID", "AgentId", "Secret"],
    },
    { category: "enterprise", label: "飞书", providerType: "FEISHU", fields: ["App ID", "App Secret"] },
    {
      category: "enterprise",
      label: "钉钉",
      providerType: "DINGTALK",
      fields: ["AppKey", "AppSecret", "CorpID"],
    },
  ].forEach(({ category, label, providerType, fields }) => {
    it(`shows only ${label} configuration fields`, () => {
      cy.intercept("GET", "**/system/api/v1/identity-providers*", (req) => {
        const matches = new URL(req.url).searchParams.get("category") === category;
        req.reply(ok({
          records: matches
            ? [{ id: 1, name: `${label}示例`, providerType, enabled: true, visible: true }]
            : [],
          total: matches ? 1 : 0,
        }));
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
      cy.contains(".el-drawer .el-form-item", "认证源类型")
        .find(".el-select")
        .click();
      cy.contains(".el-select-dropdown__item", label).click();
      cy.get(".el-drawer .el-form-item__label").then(($labels) => {
        const common = [
          "认证源类型",
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
      req.reply(ok({
        records: [{
          id: Number(query.get("pageNum")),
          name: query.get("pageNum") === "2" ? "第二页认证源" : "第一页认证源",
          providerType: "GITHUB",
          enabled: true,
          visible: true,
        }],
        total: 21,
      }));
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
      req.reply(failed
        ? { statusCode: 200, body: { success: false, code: "ERROR", msg: "认证源列表失败", data: { records: [], total: 0 } } }
        : ok({ records: [], total: 0 }));
    }).as("providers");
    open("/system/identity-provider");
    cy.wait("@providers");
    cy.contains("暂无数据").should("be.visible");
    cy.get(".identity-page .identity-main").then(($main) => {
      cy.get(".identity-page .scTable").then(($table) => {
        expect(Math.abs(
          $main[0].getBoundingClientRect().bottom -
          $table[0].getBoundingClientRect().bottom
        )).to.be.lessThan(4);
      });
    });
    cy.then(() => { failed = true; });
    cy.get(".identity-page .scTable-do button").first().click();
    cy.wait("@providers");
    cy.contains("认证源列表失败").should("be.visible");
  });

  it("requires a selected tenant for OAuth client creation", () => {
    cy.intercept(
      "GET",
      "**/system/api/v1/oauth2-clients*",
      ok({ records: [], total: 0 })
    ).as("clients");
    cy.intercept(
      "GET",
      "**/system/api/v1/tenants/options*",
      ok([{ value: 1, label: "平台租户" }])
    ).as("tenants");
    open("/system/oauth2-client");
    cy.wait("@clients");
    cy.wait("@tenants").then(({ request }) => {
      expect(new URL(request.url).searchParams.get("filterMe")).to.equal("0");
    });
    cy.contains("新增接入应用").click();
    cy.contains(".el-drawer", "所属租户").should("be.visible");
    cy.contains(".el-drawer", "强制 PKCE").should("be.visible");
  });

  it("manages the fixed mail and SMS provider/template scope", () => {
    cy.intercept("GET", "**/message-settings/templates/MAIL*", ok([
      { templateKey: "MAIL_LOGIN", channel: "MAIL", senderName: "Macula Cloud",
        title: "登录验证", content: "验证码 ${code}", enabled: true,
        overridden: false, variables: ["code"] },
    ])).as("mailTemplates");
    cy.intercept("POST", "**/message-settings/templates/MAIL/MAIL_LOGIN/preview", ok({
      content: "<p>验证码 123456</p>",
    })).as("preview");
    cy.intercept("GET", "**/message-settings/providers/MAIL*", ok({
      channel: "MAIL", providerType: "CUSTOM", endpoint: "smtp.example.test", port: 465,
      securityMode: "SSL", account: "mail-user", sender: "sender@example.test",
      enabled: true, credentialConfigured: true,
    })).as("mailProvider");
    cy.intercept("GET", "**/message-settings/providers/SMS*", ok({
      channel: "SMS", providerType: "TENCENT", account: "secret-id", sender: "Macula",
      regionCode: "ap-guangzhou", smsSdkAppId: "1400000000", enabled: true,
      credentialConfigured: true,
    })).as("smsProvider");
    cy.intercept("GET", "**/message-settings/templates/SMS*", ok([
      { templateKey: "SMS_LOGIN", channel: "SMS", providerType: "TENCENT",
        languageCode: "zh-CN", providerTemplateCode: "123456", enabled: true,
        overridden: true, variables: ["code"] },
    ])).as("smsTemplates");
    cy.intercept("POST", "**/message-settings/test", ok({ status: "submitted" })).as("sendTest");

    open("/system/message-settings");
    cy.wait("@mailTemplates");
    cy.contains("登录验证").should("be.visible");
    cy.contains("button", "配置").click();
    cy.contains(".el-drawer:visible", "发件人显示名").should("be.visible");
    cy.get(".el-drawer:visible").contains("button", "取消").click();
    cy.get(".el-drawer:visible").should("not.exist");
    cy.contains("button", "浏览").click();
    cy.wait("@preview");
    cy.get("iframe[sandbox]").should("be.visible");
    cy.get(".el-dialog:visible .el-dialog__headerbtn").click();
    cy.get(".el-dialog:visible").should("not.exist");

    cy.contains(".el-tabs__item", "邮件服务").click();
    cy.wait("@mailProvider");
    cy.contains(".message-page .el-form-item", "SMTP 服务器")
      .should("be.visible");
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

  it("does not save OAuth client before the tenant is selected", () => {
    cy.intercept(
      "GET",
      "**/system/api/v1/oauth2-clients*",
      ok({ records: [], total: 0 })
    ).as("clients");
    cy.intercept(
      "GET",
      "**/system/api/v1/tenants/options*",
      ok([{ value: 1, label: "平台租户" }])
    ).as("tenants");
    cy.intercept("POST", "**/system/api/v1/oauth2-clients", ok({ id: 3 })).as(
      "createClient"
    );
    open("/system/oauth2-client");
    cy.wait("@clients");
    cy.wait("@tenants");
    cy.contains("新增接入应用").click();
    cy.contains(".el-drawer .el-form-item", "应用名称")
      .find("input")
      .type("PKCE App");
    cy.contains(".el-drawer .el-form-item", "Client ID")
      .find("input")
      .type("pkce-app");
    cy.contains(".el-drawer .el-form-item", "重定向 URI")
      .find("textarea")
      .type("https://example.test/callback");
    cy.contains(".el-drawer", "保存").contains("button", "保存").click();
    cy.contains("请选择租户").should("exist");
    cy.get("@createClient.all").should("have.length", 0);
    cy.contains(".el-drawer .el-form-item", "所属租户")
      .find(".el-select")
      .click();
    cy.contains(".el-select-dropdown__item", "平台租户").click();
    cy.contains(".el-drawer", "保存").contains("button", "保存").click();
    cy.wait("@createClient").then(({ request }) => {
      expect(request.body.tenantId).to.equal(1);
      expect(request.body.requireProofKey).to.equal(true);
      expect(request.body.clientAuthenticationMethods).to.equal("none");
      expect(request.body.clientSecret).to.equal("");
    });
  });
});
