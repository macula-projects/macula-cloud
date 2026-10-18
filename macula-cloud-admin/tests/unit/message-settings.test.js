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
    expect(page).toContain(':title="previewTitle"');
    expect(page).toContain('class="mail-preview-card"');
    expect(page).toContain('class="mail-preview-subject"');
    expect(page).toContain('class="mail-template-list"');
    expect(page).toContain('class="mail-template-item"');
    expect(page).toContain('sceneDescription(row.templateKey)');
    expect(page).toContain('messageSettingsPage.confirm');
    expect(page).toContain("escapeHtml(row.senderName");
    expect(page).toContain('i18n.global.t');
    expect(page).toContain('@change="providerEnabledChanged"');
    expect(page).toMatch(/providerChanged\(\).*loadTemplates/);
    expect(page).toContain("requestId===templateRequestId");
    expect(page).toContain('channel.value==="SMS"');
    expect(page).toContain("provider.providerType===providerType");
    expect(page).toContain("language.value===languageCode");
    expect(page).toContain("templates.value=[];error.value=");
    expect(page).toContain("api.preview(row.templateKey)");
    expect(page).not.toContain("api.preview(row.templateKey,Object");
    expect(page).toContain('templateMessages[language.value]');
    expect(page).not.toContain('useI18n');
    expect(page).toContain("messageSettingsPage.keepSecret");
    expect(zhCn.messageSettingsPage.keepSecret).toBe("留空保持原值");
    expect(en.messageSettingsPage.scenes.SMS_LOGIN).toBe("Sign-in verification");
    expect(zhCn.messageSettingsPage.scenes.SMS_LOGIN).toBe("登录验证");
    expect(zhCn.messageSettingsPage.sceneDescriptions).toEqual({
      MAIL_BIND: "每当用户绑定邮箱时，都会发送此电子邮件。",
      MAIL_CHANGE_BIND: "每当用户需要修改绑定邮箱、发送验证码时，都会发送此电子邮件。",
      MAIL_FORGOT_PASSWORD: "每当用户忘记密码需要重置时，都会发送此电子邮件。",
      MAIL_CHANGE_PASSWORD: "每当用户要求更改密码时，都会发送此电子邮件。",
      MAIL_RESET_SUCCESS: "每当管理员重置用户密码成功时，都会发送此电子邮件。",
      MAIL_LOGIN: "每当用户选择邮箱验证码登录时，都会发送此电子邮件。",
      MAIL_PASSWORD_EXPIRY: "用户距离上次修改密码有效期即将过期时，都会发送此电子邮件。",
      MAIL_WELCOME: "一旦用户验证其电子邮件地址，将发送此电子邮件。如果验证电子邮件已关闭，则会在用户第一次注册或登录时发送。",
    });
    expect(en.messageSettingsPage.sceneDescriptions.MAIL_WELCOME).toContain("verifies");
  });
});
