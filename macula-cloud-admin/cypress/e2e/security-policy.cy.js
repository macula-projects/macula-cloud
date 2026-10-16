describe("租户安全策略", () => {
  const response = {
    success: true,
    code: "00000",
    data: {
      basic: {
        sessionValidTime: 1800,
        verifyCodeValidTime: 300,
        rememberMeValidTime: 0,
        sessionMaximum: 1,
      },
      defense: {
        loginFailureDuration: 15,
        loginFailureCount: 5,
        autoUnlockTime: 30,
        contentSecurityPolicy: "",
      },
      password: {
        passwordBiggestLength: 128,
        passwordLeastLength: 8,
        passwordComplexity: 2,
        customWeakPassword: "",
        weakPasswordCheck: true,
        includeAccountCheck: true,
        notSameChars: 3,
        historyPasswordCheck: false,
        historyPasswordCheckCount: 0,
        illegalSequenceCheck: true,
        passwordValidDays: 0,
        passwordValidWarnBeforeDays: 0,
      },
    },
  };

  beforeEach(() => {
    cy.intercept("GET", "**/system/api/v1/security-policy*", response).as("loadPolicy");
    cy.intercept("PUT", "**/system/api/v1/security-policy/basic", {
      ...response,
      data: response.data.basic,
    }).as("saveBasic");
    cy.setCookie("TOKEN", "root-test-token");
    cy.visit("/#/system/security-policy", {
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
                children: [
                  {
                    path: "/system/security-policy",
                    component: "system/securityPolicy/index",
                    meta: { title: "安全策略" },
                  },
                ],
              },
            ],
            datetime: 0,
          })
        );
      },
    });
    cy.wait("@loadPolicy");
  });

  it("loads three tabs and saves only the active section", () => {
    cy.contains("暂未生效").should("be.visible");
    cy.contains(".el-tabs__item", "通用安全").should("be.visible");
    cy.contains(".el-tabs__item", "防御策略").should("be.visible");
    cy.contains(".el-tabs__item", "密码策略").should("be.visible");
    cy.contains("button", "保存通用安全").click();
    cy.wait("@saveBasic").then(({ request }) => {
      expect(request.body).to.deep.equal(response.data.basic);
      expect(request.body).not.to.have.property("tenantId");
    });
  });

  it("blocks saves after a load failure and supports retry", () => {
    cy.intercept("GET", "**/system/api/v1/security-policy*", {
      statusCode: 503,
      body: { success: false, msg: "加载失败" },
    }).as("loadFailure");
    cy.reload();
    cy.wait("@loadFailure");
    cy.contains("重新加载").should("be.visible");
    cy.contains("button", "保存通用安全").should("be.disabled");

    cy.intercept("GET", "**/system/api/v1/security-policy*", response).as("retryPolicy");
    cy.contains("button", "重新加载").click();
    cy.wait("@retryPolicy");
    cy.contains("button", "保存通用安全").should("not.be.disabled");
  });
});
