<template>
  <el-main>

    <div v-if="loadError" class="load-error">
      <el-alert :title="loadError" type="error" :closable="false" show-icon />
      <el-button type="primary" plain @click="load">{{
        $t("securityPolicyPage.retry")
      }}</el-button>
    </div>

    <el-card class="policy-card" shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane :label="$t('securityPolicyPage.basic')" name="basic">
          <el-form
            ref="basicFormRef"
            class="policy-form"
            :model="basic"
            :rules="basicRules"
            label-width="240px"
          >
            <el-form-item
              :label="$t('securityPolicyPage.sessionValidTime')"
              prop="sessionValidTime"
            >
              <div class="field-body">
                <div class="control-line">
                  <el-input-number
                    v-model="basic.sessionValidTime"
                    :min="1"
                    :max="2592000"
                  />
                  <span class="unit">{{
                    $t("securityPolicyPage.seconds")
                  }}</span>
                </div>
                <p class="field-help">
                  {{ $t("securityPolicyPage.sessionValidTimeHelp") }}
                </p>
              </div>
            </el-form-item>
            <el-form-item
              :label="$t('securityPolicyPage.verifyCodeValidTime')"
              prop="verifyCodeValidTime"
            >
              <div class="field-body">
                <div class="control-line">
                  <el-input-number
                    v-model="basic.verifyCodeValidTime"
                    :min="1"
                    :max="86400"
                  />
                  <span class="unit">{{
                    $t("securityPolicyPage.seconds")
                  }}</span>
                </div>
                <p class="field-help">
                  {{ $t("securityPolicyPage.verifyCodeValidTimeHelp") }}
                </p>
              </div>
            </el-form-item>
            <el-form-item
              :label="$t('securityPolicyPage.rememberMeValidTime')"
              prop="rememberMeValidTime"
            >
              <div class="field-body">
                <div class="control-line">
                  <el-input-number
                    v-model="basic.rememberMeValidTime"
                    :min="0"
                    :max="31536000"
                  />
                  <span class="unit">{{
                    $t("securityPolicyPage.seconds")
                  }}</span>
                </div>
                <p class="field-help">
                  {{ $t("securityPolicyPage.rememberMeValidTimeHelp") }}
                </p>
              </div>
            </el-form-item>
            <el-form-item
              :label="$t('securityPolicyPage.sessionMaximum')"
              prop="sessionMaximum"
            >
              <div class="field-body">
                <div class="control-line">
                  <el-input-number
                    v-model="basic.sessionMaximum"
                    :min="1"
                    :max="1000"
                  />
                  <span class="unit">{{
                    $t("securityPolicyPage.sessions")
                  }}</span>
                </div>
                <p class="field-help">
                  {{ $t("securityPolicyPage.sessionMaximumHelp") }}
                </p>
              </div>
            </el-form-item>
            <el-form-item class="form-actions">
              <el-button
                type="primary"
                :loading="saving.basic"
                :disabled="!loaded"
                @click="saveBasic"
                >{{ $t("securityPolicyPage.saveBasic") }}</el-button
              >
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane :label="$t('securityPolicyPage.defense')" name="defense">
          <el-form
            ref="defenseFormRef"
            class="policy-form"
            :model="defense"
            :rules="defenseRules"
            label-width="240px"
          >
            <el-form-item
              :label="$t('securityPolicyPage.loginFailureDuration')"
              prop="loginFailureDuration"
            >
              <div class="field-body">
                <div class="control-line">
                  <el-input-number
                    v-model="defense.loginFailureDuration"
                    :min="1"
                    :max="1440"
                  />
                  <span class="unit">{{
                    $t("securityPolicyPage.minutes")
                  }}</span>
                </div>
                <p class="field-help">
                  {{ $t("securityPolicyPage.loginFailureDurationHelp") }}
                </p>
              </div>
            </el-form-item>
            <el-form-item
              :label="$t('securityPolicyPage.loginFailureCount')"
              prop="loginFailureCount"
            >
              <div class="field-body">
                <div class="control-line">
                  <el-input-number
                    v-model="defense.loginFailureCount"
                    :min="1"
                    :max="100"
                  />
                  <span class="unit">{{ $t("securityPolicyPage.times") }}</span>
                </div>
                <p class="field-help">
                  {{ $t("securityPolicyPage.loginFailureCountHelp") }}
                </p>
              </div>
            </el-form-item>
            <el-form-item
              :label="$t('securityPolicyPage.autoUnlockTime')"
              prop="autoUnlockTime"
            >
              <div class="field-body">
                <div class="control-line">
                  <el-input-number
                    v-model="defense.autoUnlockTime"
                    :min="1"
                    :max="10080"
                  />
                  <span class="unit">{{
                    $t("securityPolicyPage.minutes")
                  }}</span>
                </div>
                <p class="field-help">
                  {{ $t("securityPolicyPage.autoUnlockTimeHelp") }}
                </p>
              </div>
            </el-form-item>
            <el-form-item
              class="wide-field"
              :label="$t('securityPolicyPage.contentSecurityPolicy')"
              prop="contentSecurityPolicy"
            >
              <div class="field-body">
                <el-input
                  v-model="defense.contentSecurityPolicy"
                  type="textarea"
                  :rows="5"
                  maxlength="16000"
                  show-word-limit
                  :placeholder="$t('securityPolicyPage.cspPlaceholder')"
                />
                <p class="field-help">
                  {{ $t("securityPolicyPage.contentSecurityPolicyHelp") }}
                </p>
              </div>
            </el-form-item>
            <el-form-item class="form-actions">
              <el-button
                type="primary"
                :loading="saving.defense"
                :disabled="!loaded"
                @click="saveDefense"
                >{{ $t("securityPolicyPage.saveDefense") }}</el-button
              >
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane :label="$t('securityPolicyPage.password')" name="password">
          <el-form
            ref="passwordFormRef"
            class="policy-form"
            :model="password"
            :rules="passwordRules"
            label-width="240px"
          >
            <el-form-item
              :label="$t('securityPolicyPage.passwordLeastLength')"
              prop="passwordLeastLength"
            >
              <div class="field-body">
                <el-input-number
                  v-model="password.passwordLeastLength"
                  :min="6"
                  :max="128"
                />
                <p class="field-help">
                  {{ $t("securityPolicyPage.passwordLeastLengthHelp") }}
                </p>
              </div>
            </el-form-item>
            <el-form-item
              :label="$t('securityPolicyPage.passwordBiggestLength')"
              prop="passwordBiggestLength"
            >
              <div class="field-body">
                <el-input-number
                  v-model="password.passwordBiggestLength"
                  :min="6"
                  :max="128"
                />
                <p class="field-help">
                  {{ $t("securityPolicyPage.passwordBiggestLengthHelp") }}
                </p>
              </div>
            </el-form-item>
            <el-form-item
              :label="$t('securityPolicyPage.passwordComplexity')"
              prop="passwordComplexity"
            >
              <div class="field-body">
                <el-radio-group
                  v-model="password.passwordComplexity"
                  class="complexity-options"
                >
                  <el-radio
                    v-for="option in complexityOptions"
                    :key="option.value"
                    :label="option.value"
                    >{{ option.label }}</el-radio
                  >
                </el-radio-group>
                <p class="field-help">
                  {{ $t("securityPolicyPage.passwordComplexityHelp") }}
                </p>
              </div>
            </el-form-item>
            <el-form-item :label="$t('securityPolicyPage.weakPasswordCheck')">
              <div class="field-body">
                <el-switch v-model="password.weakPasswordCheck" />
                <p class="field-help">
                  {{ $t("securityPolicyPage.weakPasswordCheckHelp") }}
                </p>
              </div>
            </el-form-item>
            <el-form-item
              class="wide-field"
              :label="$t('securityPolicyPage.customWeakPassword')"
            >
              <div class="field-body">
                <el-input
                  v-model="password.customWeakPassword"
                  type="textarea"
                  :rows="5"
                  maxlength="60000"
                  :placeholder="
                    $t('securityPolicyPage.weakPasswordPlaceholder')
                  "
                />
                <p class="field-help">
                  {{ $t("securityPolicyPage.customWeakPasswordHelp") }}
                </p>
              </div>
            </el-form-item>
            <el-form-item :label="$t('securityPolicyPage.includeAccountCheck')">
              <div class="field-body">
                <el-switch v-model="password.includeAccountCheck" />
                <p class="field-help">
                  {{ $t("securityPolicyPage.includeAccountCheckHelp") }}
                </p>
              </div>
            </el-form-item>
            <el-form-item
              :label="$t('securityPolicyPage.notSameChars')"
              prop="notSameChars"
            >
              <div class="field-body">
                <div class="control-line">
                  <el-input-number
                    v-model="password.notSameChars"
                    :min="0"
                    :max="20"
                  />
                  <span class="unit">{{
                    $t("securityPolicyPage.characters")
                  }}</span>
                </div>
                <p class="field-help">
                  {{ $t("securityPolicyPage.notSameCharsHelp") }}
                </p>
              </div>
            </el-form-item>
            <el-form-item
              :label="$t('securityPolicyPage.historyPasswordCheck')"
            >
              <div class="field-body">
                <el-switch
                  v-model="password.historyPasswordCheck"
                  @change="onHistoryCheckChange"
                />
                <p class="field-help">
                  {{ $t("securityPolicyPage.historyPasswordCheckHelp") }}
                </p>
              </div>
            </el-form-item>
            <el-form-item
              :label="$t('securityPolicyPage.historyPasswordCheckCount')"
              prop="historyPasswordCheckCount"
            >
              <div class="field-body">
                <div class="control-line">
                  <el-input-number
                    v-model="password.historyPasswordCheckCount"
                    :disabled="!password.historyPasswordCheck"
                    :min="password.historyPasswordCheck ? 1 : 0"
                    :max="50"
                  />
                  <span class="unit">{{ $t("securityPolicyPage.times") }}</span>
                </div>
                <p class="field-help">
                  {{ $t("securityPolicyPage.historyPasswordCheckCountHelp") }}
                </p>
              </div>
            </el-form-item>
            <el-form-item
              :label="$t('securityPolicyPage.illegalSequenceCheck')"
            >
              <div class="field-body">
                <el-switch v-model="password.illegalSequenceCheck" />
                <p class="field-help">
                  {{ $t("securityPolicyPage.illegalSequenceCheckHelp") }}
                </p>
              </div>
            </el-form-item>
            <el-form-item
              :label="$t('securityPolicyPage.passwordValidDays')"
              prop="passwordValidDays"
            >
              <div class="field-body">
                <div class="control-line">
                  <el-input-number
                    v-model="password.passwordValidDays"
                    :min="0"
                    :max="3650"
                    @change="onValidDaysChange"
                  />
                  <span class="unit">{{ $t("securityPolicyPage.days") }}</span>
                </div>
                <p class="field-help">
                  {{ $t("securityPolicyPage.passwordValidDaysHelp") }}
                </p>
              </div>
            </el-form-item>
            <el-form-item
              :label="$t('securityPolicyPage.passwordValidWarnBeforeDays')"
              prop="passwordValidWarnBeforeDays"
            >
              <div class="field-body">
                <div class="control-line">
                  <el-input-number
                    v-model="password.passwordValidWarnBeforeDays"
                    :disabled="password.passwordValidDays === 0"
                    :min="0"
                    :max="365"
                  />
                  <span class="unit">{{ $t("securityPolicyPage.days") }}</span>
                </div>
                <p class="field-help">
                  {{ $t("securityPolicyPage.passwordValidWarnBeforeDaysHelp") }}
                </p>
              </div>
            </el-form-item>
            <el-form-item class="form-actions">
              <el-button
                type="primary"
                :loading="saving.password"
                :disabled="!loaded"
                @click="savePassword"
                >{{ $t("securityPolicyPage.savePassword") }}</el-button
              >
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </el-main>
</template>

<script>
export default { name: "SecurityPolicyPage" };
</script>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import api from "@/api/model/system/securityPolicy";
import i18n from "@/locales";

const t = (...args) => i18n.global.t(...args);
const activeTab = ref("basic");
const loading = ref(false);
const loaded = ref(false);
const loadError = ref("");
const basicFormRef = ref();
const defenseFormRef = ref();
const passwordFormRef = ref();
const saving = reactive({ basic: false, defense: false, password: false });

const basic = reactive({
  sessionValidTime: 1800,
  verifyCodeValidTime: 300,
  rememberMeValidTime: 0,
  sessionMaximum: 1,
});
const defense = reactive({
  loginFailureDuration: 15,
  loginFailureCount: 5,
  autoUnlockTime: 30,
  contentSecurityPolicy: "",
});
const password = reactive({
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
});

const requiredNumber = [
  {
    required: true,
    message: t("securityPolicyPage.requiredNumber"),
    trigger: "change",
  },
];
const basicRules = Object.fromEntries(
  Object.keys(basic).map((key) => [key, requiredNumber])
);
const defenseRules = {
  loginFailureDuration: requiredNumber,
  loginFailureCount: requiredNumber,
  autoUnlockTime: requiredNumber,
};
const passwordRules = {
  passwordBiggestLength: requiredNumber,
  passwordLeastLength: requiredNumber,
  passwordComplexity: requiredNumber,
  notSameChars: requiredNumber,
  historyPasswordCheckCount: requiredNumber,
  passwordValidDays: requiredNumber,
  passwordValidWarnBeforeDays: requiredNumber,
};
const complexityOptions = computed(() =>
  [0, 1, 2, 3, 4, 5].map((value) => ({
    value,
    label: t(`securityPolicyPage.complexity${value}`),
  }))
);

const unwrap = (response) => {
  if (!response?.success) {
    throw new Error(
      response?.msg ||
        response?.cause ||
        t("securityPolicyPage.operationFailed")
    );
  }
  return response.data;
};
const assignKnown = (target, source) => {
  Object.keys(target).forEach((key) => {
    if (Object.prototype.hasOwnProperty.call(source || {}, key))
      target[key] = source[key];
  });
};

async function load() {
  loading.value = true;
  loaded.value = false;
  loadError.value = "";
  try {
    const data = unwrap(await api.get());
    assignKnown(basic, data.basic);
    assignKnown(defense, data.defense);
    assignKnown(password, data.password);
    loaded.value = true;
  } catch (error) {
    loadError.value = error.message || t("securityPolicyPage.loadFailed");
    ElMessage.error(loadError.value);
  } finally {
    loading.value = false;
  }
}

async function saveSection(section, formRef, request, target) {
  if (saving[section]) return;
  saving[section] = true;
  try {
    if (!(await formRef.value.validate().catch(() => false))) return;
    const data = unwrap(await request({ ...target }));
    assignKnown(target, data);
    ElMessage.success(t("securityPolicyPage.saveSuccess"));
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    saving[section] = false;
  }
}

const saveBasic = () =>
  saveSection("basic", basicFormRef, api.saveBasic, basic);
const saveDefense = () =>
  saveSection("defense", defenseFormRef, api.saveDefense, defense);
async function savePassword() {
  if (password.passwordLeastLength > password.passwordBiggestLength) {
    ElMessage.error(t("securityPolicyPage.invalidLength"));
    return;
  }
  if (
    password.passwordValidDays > 0 &&
    password.passwordValidWarnBeforeDays >= password.passwordValidDays
  ) {
    ElMessage.error(t("securityPolicyPage.invalidWarnDays"));
    return;
  }
  await saveSection("password", passwordFormRef, api.savePassword, password);
}
function onHistoryCheckChange(enabled) {
  password.historyPasswordCheckCount = enabled
    ? Math.max(1, password.historyPasswordCheckCount)
    : 0;
}
function onValidDaysChange(days) {
  if (days === 0) password.passwordValidWarnBeforeDays = 0;
}

onMounted(load);
</script>

<style scoped>
.security-policy-page {
  box-sizing: border-box;
  width: 100%;
  min-height: 100%;
  padding: 24px;
}
.page-heading h2,
.section-heading h3 {
  margin: 0;
  color: var(--el-text-color-primary);
}
.page-heading p,
.section-heading p {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  line-height: 1.7;
}
.policy-card {
  margin-top: 0;
  width: 100%;
  box-sizing: border-box;
}
.section-heading {
  padding: 8px 4px 24px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  margin-bottom: 28px;
}
.section-heading h3 {
  font-size: 18px;
}
.policy-form {
  width: 100%;
}
.policy-form :deep(.el-form-item) {
  margin-bottom: 28px;
}
.policy-form :deep(.el-form-item__label) {
  align-items: flex-start;
  height: auto;
  min-height: 32px;
  padding-top: 1px;
  line-height: 30px;
}
.field-body {
  width: 100%;
  min-width: 0;
}
.control-line {
  display: flex;
  align-items: center;
  gap: 12px;
}
.control-line :deep(.el-input-number),
.field-body > :deep(.el-input-number) {
  width: 220px;
}
.unit {
  white-space: nowrap;
  color: var(--el-text-color-regular);
}
.field-help {
  margin: 7px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}
.complexity-options {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}
.complexity-options :deep(.el-radio) {
  height: auto;
  min-height: 28px;
  margin-right: 0;
  white-space: normal;
}
.form-actions {
  padding-top: 20px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.form-actions :deep(.el-form-item__content) {
  justify-content: flex-end;
}
.load-error {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}
.load-error .el-alert {
  flex: 1;
}

@media (max-width: 768px) {
  .security-policy-page {
    padding: 12px;
  }
  .policy-card {
    margin-top: 0;
  }
  .policy-card :deep(.el-card__body) {
    padding: 14px;
  }
  .policy-form :deep(.el-form-item) {
    display: block;
  }
  .policy-form :deep(.el-form-item__label) {
    width: 100% !important;
    justify-content: flex-start;
    padding: 0 0 6px;
  }
  .policy-form :deep(.el-form-item__content) {
    margin-left: 0 !important;
  }
  .control-line :deep(.el-input-number),
  .field-body > :deep(.el-input-number) {
    width: min(100%, 320px);
  }
  .wide-field :deep(.el-textarea) {
    width: 100%;
  }
  .form-actions :deep(.el-form-item__content) {
    justify-content: stretch;
  }
  .form-actions :deep(.el-button) {
    width: 100%;
  }
  .load-error {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
