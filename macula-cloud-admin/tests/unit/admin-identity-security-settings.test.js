import { describe, expect, it, vi } from "vitest";

vi.mock("@/utils/request", () => ({
  default: {
    get: vi.fn((url, params) => Promise.resolve({ url, params })),
    post: vi.fn((url, data) => Promise.resolve({ url, data })),
    put: vi.fn((url, data) => Promise.resolve({ url, data })),
    delete: vi.fn((url) => Promise.resolve({ url })),
  },
}));

import identity from "@/api/model/system/identityProvider";
import message from "@/api/model/system/messageSettings";
import oauth, {
  AUTHORIZATION_CODE_GRANT,
  CLIENT_TERMINALS,
  COMMON_EXTERNAL_CODE_TYPES,
  EXTERNAL_CODE_GRANT,
  TERMINAL_LOGIN_METHODS,
  invalidSelectedBrowserProviders,
  loginCapabilitySummary,
  reconcileExternalProviderBindings,
  terminalForClient,
  unsupportedExternalCodeTypes,
  updateLoginMethodConfiguration,
} from "@/api/model/system/oauth2Client";
import identityPage from "@/views/system/identityProvider/index.vue?raw";
import { Wecom } from "@/assets/icons";
import messagePage from "@/views/system/messageSettings/index.vue?raw";
import oauthPage from "@/views/system/oauth2Client/index.vue?raw";

describe("平台身份、消息与接入应用管理", () => {
  it("routes management APIs through System", async () => {
    for (const result of [
      await identity.catalog(),
      await identity.list({ pageNum: 1 }),
      await message.provider("MAIL"),
      await oauth.list({ pageNum: 1 }),
      await oauth.externalCodeCatalog(),
    ])
      expect(result.url).toContain("/system/api/v1/");
  });

  it("does not send a client secret in a read request", async () => {
    const result = await oauth.get(42);
    expect(result.url).toContain("/oauth2-clients/42");
    expect(result.data).toBeUndefined();
  });

  it("marks pending runtime settings and avoids credential echo", () => {
    expect(identityPage).toContain("<el-drawer");
    expect(identityPage).toContain('size="min(720px, 100vw)"');
    expect(identityPage).not.toContain("<el-dialog");
    expect(identityPage).toContain('v-for="category in categories"');
    expect(identityPage).toContain(':name="category.code"');
    expect(identityPage).toContain("api.catalog()");
    expect(identityPage).toContain("selectedProvider.createUrl");
    expect(identityPage).toContain("field.help");
    expect(identityPage).toContain('rel="noopener noreferrer"');
    expect(identityPage).not.toContain('value: "GITHUB"');
    expect(identityPage).not.toContain('value: "WECOM"');
    expect(identityPage).toContain("category: activeCategory.value");
    expect(identityPage).toMatch(
      /config:\s*\{\s*\.\.\.\(row\.config\s*\|\|\s*\{\}\)\s*\}/
    );
    expect(identityPage).toContain("留空保留已配置凭据");
    expect(identityPage).toContain("configuredSecrets[field.key]");
    expect(identityPage).not.toContain("IAM 登录回调尚未接入");
    expect(identityPage).toContain("供第三方平台配置 IAM 登录回调");
    expect(identityPage).not.toContain("configJson");
    expect(identityPage).toContain("selectedProvider.value.callbackUrl");
    expect(identityPage).toContain("回调地址");
    expect(identityPage).toContain("只读地址");
    expect(identityPage).not.toMatch(/callbackUrl:\s*form\./);
    expect((messagePage.match(/<el-tab-pane/g) || []).length).toBe(3);
    expect(messagePage).toContain('v-model="templateDrawer"');
    expect(messagePage).toContain('v-model="testDialog"');
    expect(messagePage).toContain('sandbox=""');
    expect(messagePage).not.toContain("EXCHANGE");
    expect(messagePage).not.toContain('value:"CUSTOM",label:"自定义短信"');
    expect(oauthPage).toContain("<el-drawer");
    expect(oauthPage).not.toContain("<el-dialog");
    expect(oauthPage).toMatch(/clientSecret:\s*["']{2}/);
    expect(oauthPage).toContain("所属租户");
    expect(oauthPage).toContain("先选择终端，再选择该终端允许的登录方式");
    expect(oauthPage).toContain("终端类型");
    expect(oauthPage).toContain("允许的登录方式");
    expect(oauthPage).toContain("externalCodeTypes");
    expect(oauthPage).toContain("externalProviderBindings");
    expect(oauthPage).toContain("allowedOriginsText");
    expect(oauthPage).toContain("externalCodeCatalog");
    expect(oauthPage).toContain(
      "provider.capabilities?.includes(codeType.capability)"
    );
    expect(oauthPage).toContain("activeTerminal");
    expect(oauthPage).toContain("activeLoginMethods");
    expect(oauthPage).toContain("toggleLoginMethod");
    expect(oauthPage).toContain("高级协议配置");
    expect(oauthPage).toContain("browserLoginProviderTypes");
    expect(oauthPage).toContain("directLoginProviderType");
    expect(oauthPage).toContain("selectedBrowserLoginProviders");
    expect(oauthPage).toContain("updateLoginMethodConfiguration");
    expect(oauthPage).toContain("reconcileExternalProviderBindings");
  });

  it("maps terminal login choices to stable protocol values", () => {
    expect(CLIENT_TERMINALS.map((terminal) => terminal.value)).toEqual([
      "BROWSER",
      "APP",
      "WECHAT_MINIAPP",
      "WECOM_MINIAPP",
      "WEBVIEW",
    ]);
    expect(TERMINAL_LOGIN_METHODS.WECHAT_MINIAPP).toEqual([
      expect.objectContaining({
        label: "微信身份登录",
        externalCodeType: "WECHAT_MINIAPP_LOGIN_CODE",
      }),
      expect.objectContaining({
        label: "微信手机号登录",
        externalCodeType: "WECHAT_MINIAPP_PHONE_CODE",
      }),
    ]);
    expect(TERMINAL_LOGIN_METHODS.WECOM_MINIAPP[0]).toEqual(
      expect.objectContaining({ externalCodeType: "WECOM_MINIAPP_CODE" })
    );
    expect(TERMINAL_LOGIN_METHODS.WEBVIEW).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ externalCodeType: "FEISHU_WEBVIEW_CODE" }),
        expect.objectContaining({ externalCodeType: "DINGTALK_WEBVIEW_CODE" }),
        expect.objectContaining({ browserProviderType: "WECOM" }),
        expect.objectContaining({ browserProviderType: "WECHAT" }),
      ])
    );
    expect(COMMON_EXTERNAL_CODE_TYPES).not.toContain("FEISHU_BROWSER_CODE");
  });

  it("reconciles edited client bindings against the current provider catalog", () => {
    const catalog = {
      codeTypes: [
        {
          value: "WECOM_APP_CODE",
          providerType: "WECOM",
          capability: "DEFAULT",
        },
        {
          value: "FEISHU_APP_CODE",
          providerType: "FEISHU",
          capability: "DEFAULT",
        },
        {
          value: "DINGTALK_APP_CODE",
          providerType: "DINGTALK",
          capability: "DEFAULT",
        },
      ],
      providerRegistrations: [
        { id: 20, providerType: "WECOM", capabilities: ["DEFAULT"] },
        { id: 30, providerType: "FEISHU", capabilities: ["DEFAULT"] },
        { id: 31, providerType: "FEISHU", capabilities: ["DEFAULT"] },
      ],
    };
    const client = {
      externalCodeTypes: [
        "WECOM_APP_CODE",
        "FEISHU_APP_CODE",
        "DINGTALK_APP_CODE",
        "REMOVED_CODE",
      ],
      externalProviderBindings: {
        WECOM_APP_CODE: 10,
        FEISHU_APP_CODE: 30,
        DINGTALK_APP_CODE: 40,
        REMOVED_CODE: 99,
        DESELECTED_CODE: 88,
      },
    };

    expect(reconcileExternalProviderBindings(client, catalog)).toEqual({
      WECOM_APP_CODE: 20,
      FEISHU_APP_CODE: 30,
      REMOVED_CODE: 99,
    });
    expect(
      reconcileExternalProviderBindings(
        {
          ...client,
          externalProviderBindings: {
            ...client.externalProviderBindings,
            FEISHU_APP_CODE: 32,
          },
        },
        catalog
      )
    ).not.toHaveProperty("FEISHU_APP_CODE");
  });

  it("updates grants, shared web providers, direct login and bindings together", () => {
    const appMethod = TERMINAL_LOGIN_METHODS.APP.find(
      (method) => method.value === "APP_WECOM"
    );
    let client = updateLoginMethodConfiguration(
      {
        authorizationGrantTypes: AUTHORIZATION_CODE_GRANT,
        externalCodeTypes: [],
        externalProviderBindings: {},
        browserLoginProviderTypes: ["WECOM"],
        directLoginProviderType: "WECOM",
      },
      appMethod,
      true,
      [{ id: 20 }]
    );
    expect(client.externalCodeTypes).toEqual(["WECOM_APP_CODE"]);
    expect(client.externalProviderBindings).toEqual({ WECOM_APP_CODE: 20 });
    expect(client.authorizationGrantTypes).toContain(EXTERNAL_CODE_GRANT);

    const sharedWebMethod = TERMINAL_LOGIN_METHODS.WEBVIEW.find(
      (method) => method.value === "WEBVIEW_WECOM"
    );
    client = updateLoginMethodConfiguration(client, sharedWebMethod, false);
    expect(client.browserLoginProviderTypes).toEqual([]);
    expect(client.directLoginProviderType).toBe("");

    client = updateLoginMethodConfiguration(client, appMethod, false);
    expect(client.externalCodeTypes).toEqual([]);
    expect(client.externalProviderBindings).toEqual({});
    expect(client.authorizationGrantTypes).not.toContain(EXTERNAL_CODE_GRANT);
  });

  it("derives editing terminal, shared summaries and unsupported values", () => {
    const catalog = {
      codeTypes: [
        { value: "WECOM_APP_CODE", scene: "APP" },
        { value: "FEISHU_WEBVIEW_CODE", scene: "WEBVIEW" },
      ],
    };
    expect(
      terminalForClient({ externalCodeTypes: ["WECOM_APP_CODE"] }, catalog)
    ).toBe("APP");
    expect(
      loginCapabilitySummary({
        browserLoginProviderTypes: ["GITHUB", "WECOM", "WECHAT"],
        externalCodeTypes: ["WECOM_APP_CODE", "REMOVED_CODE"],
      })
    ).toBe(
      "浏览器：GitHub；网页（浏览器/端内）：企业微信、微信；企业微信 SDK；高级兼容配置"
    );
    expect(
      unsupportedExternalCodeTypes(
        { externalCodeTypes: ["WECOM_APP_CODE", "REMOVED_CODE"] },
        catalog,
        true
      )
    ).toEqual(["REMOVED_CODE"]);
    expect(
      unsupportedExternalCodeTypes(
        { externalCodeTypes: ["REMOVED_CODE"] },
        catalog,
        false
      )
    ).toEqual([]);
  });

  it("rejects selected browser providers without exactly one registration", () => {
    const client = {
      browserLoginProviderTypes: ["GITHUB", "WECOM", "FEISHU"],
    };
    const catalog = {
      providerRegistrations: [
        { id: 1, providerType: "GITHUB" },
        { id: 2, providerType: "FEISHU" },
        { id: 3, providerType: "FEISHU" },
      ],
    };

    expect(invalidSelectedBrowserProviders(client, catalog)).toEqual([
      "WECOM",
      "FEISHU",
    ]);
  });

  it("uses provider marks in a compact role-style list", () => {
    expect(identityPage).toContain('class="left-panel"');
    expect(identityPage).toContain('class="right-panel-search"');
    expect(identityPage).toContain('class="nopadding identity-main"');
    expect(identityPage).toContain('class="provider-icon" :size="32"');
    expect(identityPage).toContain(
      "`sc-icon-${row.providerType.toLowerCase()}`"
    );
    expect(identityPage).not.toContain("providerIcons");
    expect(Wecom).toBeDefined();
    expect(identityPage).toContain("<scTable");
    expect(identityPage).toContain('class="identity-table"');
    expect(identityPage).not.toContain("<el-pagination");
    expect(identityPage).not.toContain("provider-mark");
  });
});
