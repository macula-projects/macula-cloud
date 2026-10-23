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
    externalCodeCatalog = { codeTypes: [], providerRegistrations: [] },
    externalCodeCatalogDelay = 0,
    externalCodeCatalogAlias = "externalCodeCatalog"
  ) {
    cy.intercept(
      "GET",
      "**/system/api/v1/identity-providers/catalog*",
      ok(providerCatalog)
    ).as("providerCatalog");
    cy.intercept(
      "GET",
      "**/system/api/v1/oauth2-clients/external-code-catalog*",
      { ...ok(externalCodeCatalog), delay: externalCodeCatalogDelay }
    ).as(externalCodeCatalogAlias);
    cy.setCookie("TOKEN", "root-test-token");
    cy.visit(`/#${path}`, {
      onBeforeLoad(win) {
        win.localStorage.setItem("MACULA_CLOUD_ADMIN_STORAGE_VERSION", "2");
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
      .find("input")
      .should("not.exist");
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
    cy.contains(".el-drawer", "终端类型").should("be.visible");
    cy.contains(".el-drawer", "高级协议配置").click();
    cy.contains(".el-drawer", "强制 PKCE").should("be.visible");
  });

  it("configures login methods by terminal without exposing code types", () => {
    const catalog = {
      codeTypes: [
        {
          value: "WECOM_APP_CODE",
          providerType: "WECOM",
          scene: "APP",
          capability: "DEFAULT",
        },
        {
          value: "WECHAT_MINIAPP_PHONE_CODE",
          providerType: "WECHAT",
          scene: "MINIAPP",
          capability: "WECHAT_MINIAPP",
        },
        {
          value: "FEISHU_BROWSER_CODE",
          providerType: "FEISHU",
          scene: "BROWSER",
          capability: "FEISHU_EXTERNAL_REDIRECT",
        },
      ],
      providerRegistrations: [
        {
          id: 1,
          name: "GitHub",
          providerType: "GITHUB",
          capabilities: ["DEFAULT"],
        },
        {
          id: 2,
          name: "企业微信",
          providerType: "WECOM",
          capabilities: ["DEFAULT"],
        },
        {
          id: 3,
          name: "微信小程序",
          providerType: "WECHAT",
          capabilities: ["WECHAT_MINIAPP"],
        },
      ],
    };
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

    open("/system/oauth2-client", catalog);
    cy.wait("@clients");
    cy.wait("@tenants");
    cy.contains("新增接入应用").click();
    cy.get('[data-login-method="BROWSER_GITHUB"]').click();
    cy.contains(".el-segmented__item", "App").click();
    cy.get('[data-login-method="APP_WECOM"]').click();
    cy.contains(".el-drawer .el-form-item__label", /^企业微信 SDK身份源$/)
      .parents(".el-form-item")
      .should("contain", "自动绑定");
    cy.contains(".el-segmented__item", "微信小程序").click();
    cy.get('[data-login-method="WECHAT_MINIAPP_MOBILE"]').click();
    cy.contains("微信手机号登录身份源").should("contain", "自动绑定");
    cy.contains(".el-drawer .el-form-item", "应用名称")
      .find("input")
      .type("多端应用");
    cy.contains(".el-drawer .el-form-item", "Client ID")
      .find("input")
      .type("multi-terminal-app");
    cy.contains(".el-drawer .el-form-item", "所属租户")
      .find(".el-select")
      .click();
    cy.contains(".el-select-dropdown__item", "平台租户").click();
    cy.contains(".el-drawer .el-form-item", "重定向 URI")
      .find("textarea")
      .type("https://example.test/callback");
    cy.contains(".el-drawer", "保存").contains("button", "保存").click();
    cy.wait("@createClient").then(({ request }) => {
      expect(request.body.browserLoginProviderTypes).to.deep.equal(["GITHUB"]);
      expect(request.body.externalCodeTypes).to.include.members([
        "WECOM_APP_CODE",
        "WECHAT_MINIAPP_PHONE_CODE",
      ]);
      expect(request.body.externalProviderBindings).to.deep.include({
        WECOM_APP_CODE: 2,
        WECHAT_MINIAPP_PHONE_CODE: 3,
      });
      expect(request.body.authorizationGrantTypes).to.contain(
        "authorization_code"
      );
      expect(request.body.authorizationGrantTypes).to.contain("external_code");
    });
    cy.contains("允许的 code type").should("not.exist");
  });

  it("reconciles edited bindings and keeps shared and compatible login settings", () => {
    const catalog = {
      codeTypes: [
        {
          value: "WECOM_APP_CODE",
          providerType: "WECOM",
          scene: "APP",
          capability: "DEFAULT",
        },
        {
          value: "FEISHU_APP_CODE",
          providerType: "FEISHU",
          scene: "APP",
          capability: "DEFAULT",
        },
        {
          value: "DINGTALK_APP_CODE",
          providerType: "DINGTALK",
          scene: "APP",
          capability: "DEFAULT",
        },
        {
          value: "FEISHU_BROWSER_CODE",
          providerType: "FEISHU",
          scene: "BROWSER",
          capability: "FEISHU_EXTERNAL_REDIRECT",
        },
      ],
      providerRegistrations: [
        {
          id: 20,
          name: "新企业微信",
          providerType: "WECOM",
          capabilities: ["DEFAULT"],
        },
        {
          id: 30,
          name: "飞书主应用",
          providerType: "FEISHU",
          capabilities: ["DEFAULT", "FEISHU_EXTERNAL_REDIRECT"],
        },
        {
          id: 31,
          name: "飞书备用应用",
          providerType: "FEISHU",
          capabilities: ["DEFAULT"],
        },
      ],
    };
    let client = {
      id: 7,
      clientId: "existing-terminal-app",
      clientName: "旧多端应用",
      tenantId: 1,
      userType: "employee",
      clientAuthenticationMethods: "none",
      authorizationGrantTypes:
        "authorization_code,refresh_token,urn:macula:params:oauth:grant-type:external_code",
      redirectUris: "https://example.test/callback",
      scopes: "openid,profile",
      externalCodeTypes: [
        "WECOM_APP_CODE",
        "FEISHU_APP_CODE",
        "DINGTALK_APP_CODE",
        "FEISHU_BROWSER_CODE",
      ],
      externalProviderBindings: {
        WECOM_APP_CODE: 10,
        FEISHU_APP_CODE: 30,
        DINGTALK_APP_CODE: 50,
        FEISHU_BROWSER_CODE: 30,
      },
      allowedOrigins: [],
      browserLoginProviderTypes: ["WECOM"],
      directLoginProviderType: "WECOM",
      requireProofKey: true,
      requireAuthorizationConsent: false,
      authorizationCodeTimeToLive: 300,
      accessTokenTimeToLive: 3600,
      refreshTokenTimeToLive: 2592000,
      tokenFormat: "self-contained",
      reuseRefreshTokens: true,
      idTokenSignatureAlgorithm: "RS256",
    };
    cy.intercept("GET", "**/system/api/v1/oauth2-clients*", (req) => {
      req.reply(ok({ records: [client], total: 1 }));
    }).as("clients");
    cy.intercept(
      "GET",
      "**/system/api/v1/tenants/options*",
      ok([{ value: 1, label: "平台租户" }])
    ).as("tenants");
    cy.intercept("PUT", "**/system/api/v1/oauth2-clients/7", (req) => {
      client = { ...client, ...req.body };
      req.reply(ok(client));
    }).as("updateClient");

    open("/system/oauth2-client", catalog);
    cy.wait("@clients");
    cy.wait("@tenants");
    cy.contains("tr", "旧多端应用")
      .should("contain", "网页（浏览器/端内）：企业微信")
      .and("contain", "高级兼容配置")
      .contains("button", "编辑")
      .click();
    cy.contains(".el-drawer .el-form-item__label", /^企业微信 SDK身份源$/)
      .parents(".el-form-item")
      .should("contain", "新企业微信")
      .and("contain", "自动绑定");
    cy.contains(".el-drawer .el-form-item__label", /^飞书 AppLink身份源$/)
      .parents(".el-form-item")
      .find(".el-select__selected-item")
      .should("contain", "飞书主应用");
    cy.contains("钉钉 SDK缺少可用身份源").should("be.visible");
    cy.get('[data-login-method="APP_DINGTALK"]').click();

    cy.contains(".el-segmented__item", "端内网页").click();
    cy.get('[data-login-method="WEBVIEW_WECOM"]').should(
      "have.class",
      "is-checked"
    );
    cy.contains(".el-segmented__item", "浏览器").click();
    cy.get('[data-login-method="BROWSER_WECOM"]')
      .should("have.class", "is-checked")
      .click();
    cy.contains(".el-segmented__item", "端内网页").click();
    cy.get('[data-login-method="WEBVIEW_WECOM"]')
      .should("not.have.class", "is-checked")
      .click();
    cy.contains(".el-segmented__item", "浏览器").click();
    cy.get('[data-login-method="BROWSER_WECOM"]').should(
      "have.class",
      "is-checked"
    );
    cy.contains(".el-drawer .el-form-item", "进入登录页后")
      .find(".el-select")
      .click();
    cy.contains(".el-select-dropdown__item", "直接进入企业微信认证").click();
    cy.contains(".el-drawer", "保存").contains("button", "保存").click();
    cy.wait("@updateClient").then(({ request }) => {
      expect(request.body.externalCodeTypes).not.to.include(
        "DINGTALK_APP_CODE"
      );
      expect(request.body.externalCodeTypes).to.include("FEISHU_BROWSER_CODE");
      expect(request.body.externalProviderBindings).to.deep.include({
        WECOM_APP_CODE: 20,
        FEISHU_APP_CODE: 30,
        FEISHU_BROWSER_CODE: 30,
      });
      expect(request.body.externalProviderBindings).not.to.have.property(
        "DINGTALK_APP_CODE"
      );
      expect(request.body.browserLoginProviderTypes).to.deep.equal(["WECOM"]);
      expect(request.body.directLoginProviderType).to.equal("WECOM");
    });
    cy.wait("@clients");
    cy.contains("tr", "旧多端应用")
      .should("contain", "网页（浏览器/端内）：企业微信")
      .and("contain", "高级兼容配置")
      .contains("button", "编辑")
      .click();
    cy.contains(".el-drawer .el-form-item__label", /^企业微信 SDK身份源$/)
      .parents(".el-form-item")
      .should("contain", "新企业微信");
    cy.contains(".el-drawer", "高级协议配置").click();
    cy.contains(".el-checkbox", "FEISHU_BROWSER_CODE").should(
      "have.class",
      "is-checked"
    );
  });

  it("re-infers the edited terminal after a delayed external catalog", () => {
    const catalog = {
      codeTypes: [
        {
          value: "WECOM_APP_CODE",
          providerType: "WECOM",
          scene: "APP",
          capability: "DEFAULT",
        },
      ],
      providerRegistrations: [
        {
          id: 20,
          name: "企业微信",
          providerType: "WECOM",
          capabilities: ["DEFAULT"],
        },
      ],
    };
    cy.intercept(
      "GET",
      "**/system/api/v1/oauth2-clients*",
      ok({
        records: [
          {
            id: 10,
            clientId: "delayed-catalog-app",
            clientName: "延迟目录应用",
            tenantId: 1,
            userType: "employee",
            clientAuthenticationMethods: "none",
            authorizationGrantTypes:
              "urn:macula:params:oauth:grant-type:external_code",
            redirectUris: "",
            scopes: "openid",
            externalCodeTypes: ["WECOM_APP_CODE"],
            externalProviderBindings: { WECOM_APP_CODE: 20 },
            allowedOrigins: [],
            browserLoginProviderTypes: [],
            directLoginProviderType: "",
            requireProofKey: true,
          },
        ],
        total: 1,
      })
    ).as("clients");
    cy.intercept(
      "GET",
      "**/system/api/v1/tenants/options*",
      ok([{ value: 1, label: "平台租户" }])
    ).as("tenants");

    open("/system/oauth2-client", catalog, 1000, "manualExternalCodeCatalog");
    cy.wait("@clients");
    cy.contains("tr", "延迟目录应用").contains("button", "编辑").click();
    cy.wait("@manualExternalCodeCatalog");
    cy.get('[data-login-method="APP_WECOM"]')
      .should("be.visible")
      .and("have.class", "is-checked");
    cy.contains(".el-drawer .el-form-item__label", /^企业微信 SDK身份源$/)
      .parents(".el-form-item")
      .should("contain", "企业微信");

    cy.get(".el-drawer__close-btn:visible").click();
    cy.visit("/#/");
    open("/system/oauth2-client", catalog, 1000);
    cy.contains("tr", "延迟目录应用").contains("button", "编辑").click();
    cy.contains(".el-segmented__item", "微信小程序").click();
    cy.wait("@externalCodeCatalog");
    cy.get('[data-login-method="WECHAT_MINIAPP_IDENTITY"]').should(
      "be.visible"
    );
    cy.get('[data-login-method="APP_WECOM"]').should("not.exist");
  });

  it("blocks selected browser providers without one active registration", () => {
    const clients = [
      {
        id: 11,
        clientId: "missing-browser-provider",
        clientName: "缺少网页身份源",
        browserLoginProviderTypes: ["WECOM"],
        directLoginProviderType: "WECOM",
      },
      {
        id: 12,
        clientId: "duplicate-browser-provider",
        clientName: "重复网页身份源",
        browserLoginProviderTypes: ["FEISHU"],
        directLoginProviderType: "FEISHU",
      },
    ].map((client) => ({
      tenantId: 1,
      userType: "employee",
      clientAuthenticationMethods: "none",
      authorizationGrantTypes: "authorization_code",
      redirectUris: "https://example.test/callback",
      scopes: "openid",
      externalCodeTypes: [],
      externalProviderBindings: {},
      allowedOrigins: [],
      directLoginProviderType: "",
      requireProofKey: true,
      ...client,
    }));
    const catalog = {
      codeTypes: [],
      providerRegistrations: [
        { id: 30, name: "飞书一", providerType: "FEISHU" },
        { id: 31, name: "飞书二", providerType: "FEISHU" },
      ],
    };
    let writes = 0;
    cy.intercept(
      "GET",
      "**/system/api/v1/oauth2-clients*",
      ok({ records: clients, total: 2 })
    ).as("clients");
    cy.intercept(
      "GET",
      "**/system/api/v1/tenants/options*",
      ok([{ value: 1, label: "平台租户" }])
    ).as("tenants");
    cy.intercept("PUT", "**/system/api/v1/oauth2-clients/*", (req) => {
      writes += 1;
      req.reply(ok({}));
    }).as("updateClient");

    open("/system/oauth2-client", catalog);
    cy.wait("@clients");
    cy.wait("@externalCodeCatalog");
    cy.contains("tr", "缺少网页身份源").contains("button", "编辑").click();
    cy.contains(".el-drawer", "保存").contains("button", "保存").click();
    cy.contains("网页登录方式必须且只能有一个启用身份源：企业微信").should(
      "be.visible"
    );
    cy.then(() => expect(writes).to.equal(0));
    cy.get('[data-login-method="BROWSER_WECOM"]').click();
    cy.contains(".el-drawer", "保存").contains("button", "保存").click();
    cy.wait("@updateClient").then(({ request }) => {
      expect(request.body.browserLoginProviderTypes).to.deep.equal([]);
      expect(request.body.directLoginProviderType).to.equal("");
    });
    cy.wait("@clients");
    cy.contains("tr", "重复网页身份源").contains("button", "编辑").click();
    cy.contains(".el-drawer", "保存").contains("button", "保存").click();
    cy.contains("网页登录方式必须且只能有一个启用身份源：飞书").should(
      "be.visible"
    );
    cy.then(() => expect(writes).to.equal(1));
    cy.get('[data-login-method="BROWSER_FEISHU"]').click();
    cy.contains(".el-drawer", "保存").contains("button", "保存").click();
    cy.wait("@updateClient").then(({ request }) => {
      expect(request.body.browserLoginProviderTypes).to.deep.equal([]);
      expect(request.body.directLoginProviderType).to.equal("");
    });
    cy.then(() => expect(writes).to.equal(2));
  });

  it("rejects missing redirects and unsupported historical code types", () => {
    const unsupportedClient = {
      id: 8,
      clientId: "unsupported-client",
      clientName: "待迁移应用",
      tenantId: 1,
      userType: "employee",
      clientAuthenticationMethods: "none",
      authorizationGrantTypes: "authorization_code",
      redirectUris: "https://example.test/callback",
      scopes: "openid",
      externalCodeTypes: ["REMOVED_CODE"],
      externalProviderBindings: { REMOVED_CODE: 99 },
      allowedOrigins: [],
      browserLoginProviderTypes: [],
      directLoginProviderType: "",
      requireProofKey: true,
    };
    let writes = 0;
    cy.intercept(
      "GET",
      "**/system/api/v1/oauth2-clients*",
      ok({ records: [unsupportedClient], total: 1 })
    ).as("clients");
    cy.intercept(
      "GET",
      "**/system/api/v1/tenants/options*",
      ok([{ value: 1, label: "平台租户" }])
    ).as("tenants");
    cy.intercept("POST", "**/system/api/v1/oauth2-clients", (req) => {
      writes += 1;
      req.reply(ok({ id: 9 }));
    });
    cy.intercept("PUT", "**/system/api/v1/oauth2-clients/8", (req) => {
      writes += 1;
      req.reply(ok({ id: 8 }));
    });

    open("/system/oauth2-client");
    cy.wait("@clients");
    cy.wait("@tenants");
    cy.contains("新增接入应用").click();
    cy.contains(".el-drawer .el-form-item", "应用名称")
      .find("input")
      .type("无回调应用");
    cy.contains(".el-drawer .el-form-item", "Client ID")
      .find("input")
      .type("missing-redirect");
    cy.contains(".el-drawer .el-form-item", "所属租户")
      .find(".el-select")
      .click();
    cy.contains(".el-select-dropdown__item", "平台租户").click();
    cy.contains(".el-drawer", "保存").contains("button", "保存").click();
    cy.contains("使用 IAM 授权登录时必须配置重定向 URI").should("be.visible");
    cy.then(() => expect(writes).to.equal(0));
    cy.contains(".el-drawer", "取消").contains("button", "取消").click();

    cy.contains("tr", "待迁移应用").contains("button", "编辑").click();
    cy.contains(".el-drawer", "保存").contains("button", "保存").click();
    cy.contains("存在不受支持的历史凭证类型：REMOVED_CODE").should(
      "be.visible"
    );
    cy.then(() => expect(writes).to.equal(0));
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
