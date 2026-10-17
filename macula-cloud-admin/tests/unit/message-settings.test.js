import { describe, expect, it, vi } from "vitest";
vi.mock("@/utils/request", () => ({ default: { get: vi.fn((url, params) => ({ url, params })), put: vi.fn((url, data) => ({ url, data })), post: vi.fn((url, data) => ({ url, data })) } }));
import api from "@/api/model/system/messageSettings";
import page from "@/views/system/messageSettings/index.vue?raw";
import zhCn from "@/locales/lang/zh-cn";
import en from "@/locales/lang/en";

describe("message settings", () => {
  it("uses the global channel API", async () => {
    expect((await api.provider("MAIL")).url).toContain("/providers/MAIL");
    expect((await api.templates("SMS", "ALIYUN", "en-US")).params.languageCode).toBe("en-US");
  });
  it("renders the accepted provider scope and safe preview", () => {
    expect((page.match(/<el-tab-pane/g) || []).length).toBe(3);
    for (const value of ["ALIYUN", "TENCENT", "NETEASE"]) expect(page).toContain(value);
    expect(page).not.toContain("EXCHANGE");
    expect(page).not.toContain("QINIU");
    expect(page).toContain('sandbox=""');
    expect(page).toContain('i18n.global.t');
    expect(page).toContain('@change="providerEnabledChanged"');
    expect(page).toMatch(/providerChanged\(\).*loadTemplates/);
    expect(page).toContain('templateMessages[language.value]');
    expect(page).not.toContain('useI18n');
    expect(page).toContain("messageSettingsPage.keepSecret");
    expect(zhCn.messageSettingsPage.keepSecret).toBe("留空保持原值");
    expect(en.messageSettingsPage.scenes.SMS_LOGIN).toBe("Sign-in verification");
    expect(zhCn.messageSettingsPage.scenes.SMS_LOGIN).toBe("登录验证");
  });
});
