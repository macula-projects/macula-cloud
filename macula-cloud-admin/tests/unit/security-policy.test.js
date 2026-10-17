import { describe, expect, it, vi } from "vitest";

vi.mock("@/utils/request", () => ({
  default: {
    get: vi.fn((url) => Promise.resolve({ url })),
    put: vi.fn((url, data) => Promise.resolve({ url, data })),
  },
}));

import api from "@/api/model/system/securityPolicy";
import page from "@/views/system/securityPolicy/index.vue?raw";

describe("租户安全策略", () => {
  it("uses one read endpoint and three section write endpoints", async () => {
    expect((await api.get()).url).toContain("/system/api/v1/security-policy");
    expect((await api.saveBasic({})).url).toMatch(/security-policy\/basic$/);
    expect((await api.saveDefense({})).url).toMatch(/security-policy\/defense$/);
    expect((await api.savePassword({})).url).toMatch(/security-policy\/password$/);
  });

  it("renders the three tabs and exact policy fields", () => {
    for (const tab of ["basic", "defense", "password"]) {
      expect(page).toContain(`securityPolicyPage.${tab}`);
    }
    for (const key of [
      "sessionValidTime",
      "verifyCodeValidTime",
      "rememberMeValidTime",
      "sessionMaximum",
      "loginFailureDuration",
      "loginFailureCount",
      "autoUnlockTime",
      "contentSecurityPolicy",
      "passwordBiggestLength",
      "passwordLeastLength",
      "passwordComplexity",
      "customWeakPassword",
      "weakPasswordCheck",
      "includeAccountCheck",
      "notSameChars",
      "historyPasswordCheck",
      "historyPasswordCheckCount",
      "illegalSequenceCheck",
      "passwordValidDays",
      "passwordValidWarnBeforeDays",
    ]) {
      expect(page).toContain(key);
    }
    expect(page).toContain("securityPolicyPage.saveSuccess");
    expect(page).toContain("adminSettings.securityPolicyPending");
    expect(page).toContain(':disabled="!loaded"');
    expect(page).toContain('v-if="loadError"');
    expect(page).toContain('@click="load"');
    expect(page).toContain('class="field-help"');
    expect(page).toContain("securityPolicyPage.contentSecurityPolicyHelp");
    expect(page).toContain("securityPolicyPage.customWeakPasswordHelp");
    expect(page).toContain("securityPolicyPage.passwordValidWarnBeforeDaysHelp");
    expect(page).toContain("<el-radio-group");
    expect(page).toContain("width: 100%;");
    expect(page).toContain("@media (max-width: 768px)");
    expect(page).not.toContain("max-width: 980px");
  });
});
