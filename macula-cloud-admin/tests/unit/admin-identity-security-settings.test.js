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
  invalidSelectedBrowserProviders,
  oauthClientErrorMessage,
  reconcileExternalProviderBindings,
} from "@/api/model/system/oauth2Client";
import {configurationIssues, grantIssue} from "@/utils/oauthClientConfiguration";
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
      await oauth.configurationCatalog(),
    ])
      expect(result.url).toContain("/system/api/v1/");
  });

  it("does not send a client secret in a read request", async () => {
    const result = await oauth.get(42);
    expect(result.url).toContain("/oauth2-clients/42");
    expect(result.data).toBeUndefined();
  });

  it("rejects confidential verification grants and exposes only safe business errors", () => {
    expect(
      Boolean(grantIssue({enabledProtocols:["OAUTH2_1"],
        clientAuthenticationMethods: "client_secret_basic,client_secret_post",
        authorizationGrantTypes:
          "authorization_code,client_credentials,refresh_token,password,sms",
      }, "sms"))
    ).toBe(true);
    expect(
      Boolean(grantIssue({enabledProtocols:["OAUTH2_1"],
        clientAuthenticationMethods: "none",
        authorizationGrantTypes: "authorization_code,sms",
      }, "sms"))
    ).toBe(true);
    expect(
      configurationIssues({clientAuthenticationMethods:"", authorizationGrantTypes:"sms"}).join(" ")
    ).toContain("认证方式");
    expect(
      oauthClientErrorMessage({
        cause: '400 BAD_REQUEST "短信或邮箱验证码 grant 必须由公共客户端使用"',
        msg: "系统异常",
      })
    ).toBe("短信或邮箱验证码 grant 必须由公共客户端使用");
    expect(
      oauthClientErrorMessage({
        cause: "java.lang.IllegalStateException: internal detail",
        msg: "系统异常",
      })
    ).toBe("系统异常");
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
    expect(messagePage).toContain("EXCHANGE");
    expect(messagePage).toContain("CUSTOM_HTTP");
    expect(messagePage).not.toContain('value:"CUSTOM",label:"自定义短信"');
    expect(oauthPage).toContain("ClientApplication");
    expect(oauthPage).not.toContain("<el-drawer");
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
      sources: [
        { id:20, providerType:"WECOM", available:true, capabilities:[{codeType:"WECOM_APP_CODE"}] },
        { id:30, providerType:"FEISHU", available:true, capabilities:[{codeType:"FEISHU_APP_CODE"}] },
        { id:31, providerType:"FEISHU", available:true, capabilities:[{codeType:"FEISHU_APP_CODE"}] },
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

  it("requires an explicit compatible source for each selected browser provider", () => {
    const client = {
      browserLoginProviderTypes: ["GITHUB", "WECOM", "FEISHU"],
      browserProviderBindings: {GITHUB:1},
    };
    const catalog = {
      sources: [
        { id:1, providerType:"GITHUB", available:true, browser:true },
        { id:2, providerType:"FEISHU", available:true, browser:true },
        { id:3, providerType:"FEISHU", available:true, browser:true },
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
