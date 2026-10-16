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
import security from "@/api/model/system/securitySettings";
import password from "@/api/model/system/passwordPolicy";
import message from "@/api/model/system/messageSettings";
import oauth from "@/api/model/system/oauth2Client";
import identityPage from "@/views/system/identityProvider/index.vue?raw";
import {Wecom} from "@/assets/icons";
import securityPage from "@/views/system/securitySettings/index.vue?raw";
import passwordPage from "@/views/system/passwordPolicy/index.vue?raw";
import messagePage from "@/views/system/messageSettings/index.vue?raw";
import oauthPage from "@/views/system/oauth2Client/index.vue?raw";

describe("平台身份与安全设置管理", () => {
  it("routes all five management APIs through System", async () => {
    for (const result of [
      await identity.catalog(),
      await identity.list({ pageNum: 1 }),
      await security.get(),
      await password.get(),
      await message.providers("MAIL"),
      await oauth.list({ pageNum: 1 }),
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
    expect(identityPage).toMatch(/config:\s*\{\s*\.\.\.\(row\.config\s*\|\|\s*\{\}\)\s*\}/);
    expect(identityPage).toContain("留空保留已配置凭据");
    expect(identityPage).not.toContain("configJson");
    expect(identityPage).toContain("selectedProvider.value.callbackUrl");
    expect(identityPage).toContain("回调地址");
    expect(identityPage).toContain("只读地址");
    expect(identityPage).not.toMatch(/callbackUrl:\s*form\./);
    expect(securityPage).toContain("暂未生效");
    expect(passwordPage).toContain("暂未生效");
    expect(messagePage).toContain("临时收件邮箱");
    expect(messagePage).toContain('v-model="providerDrawer"');
    expect(messagePage).toContain('v-model="templateDrawer"');
    expect(messagePage).toContain('v-model="testDialog"');
    expect(messagePage).toMatch(/credential:\s*["']{2}/);
    expect(oauthPage).toContain("<el-drawer");
    expect(oauthPage).not.toContain("<el-dialog");
    expect(oauthPage).toMatch(/clientSecret:\s*["']{2}/);
    expect(oauthPage).toContain("所属租户");
  });

  it("uses the six provider marks in a compact role-style list", () => {
    expect(identityPage).toContain('class="left-panel"');
    expect(identityPage).toContain('class="right-panel-search"');
    expect(identityPage).toContain('class="nopadding identity-main"');
    expect(identityPage).toContain('class="provider-icon" :size="32"');
    expect(identityPage).toContain('`sc-icon-${row.providerType.toLowerCase()}`');
    expect(identityPage).not.toContain('providerIcons');
    expect(Wecom).toBeDefined();
    expect(identityPage).toContain("<scTable");
    expect(identityPage).toContain('class="identity-table"');
    expect(identityPage).not.toContain("<el-pagination");
    expect(identityPage).not.toContain("provider-mark");
  });
});
