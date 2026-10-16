<template>
  <div class="security-policy-page" v-loading="loading">
    <h2>{{ $t("adminSettings.securityPolicy") }}</h2>
    <el-alert
      :title="$t('adminSettings.securityPolicyPending')"
      type="warning"
      :closable="false"
    />
    <div v-if="loadError" class="load-error">
      <el-alert :title="loadError" type="error" :closable="false" show-icon />
      <el-button type="primary" plain @click="load">{{ $t("securityPolicyPage.retry") }}</el-button>
    </div>

    <el-card class="policy-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane :label="$t('securityPolicyPage.basic')" name="basic">
          <el-form
            ref="basicFormRef"
            :model="basic"
            :rules="basicRules"
            label-width="220px"
          >
            <el-form-item :label="$t('securityPolicyPage.sessionValidTime')" prop="sessionValidTime">
              <el-input-number
                v-model="basic.sessionValidTime"
                :min="1"
                :max="2592000"
              />
            </el-form-item>
            <el-form-item :label="$t('securityPolicyPage.verifyCodeValidTime')" prop="verifyCodeValidTime">
              <el-input-number
                v-model="basic.verifyCodeValidTime"
                :min="1"
                :max="86400"
              />
            </el-form-item>
            <el-form-item :label="$t('securityPolicyPage.rememberMeValidTime')" prop="rememberMeValidTime">
              <el-input-number
                v-model="basic.rememberMeValidTime"
                :min="0"
                :max="31536000"
              />
            </el-form-item>
            <el-form-item :label="$t('securityPolicyPage.sessionMaximum')" prop="sessionMaximum">
              <el-input-number
                v-model="basic.sessionMaximum"
                :min="1"
                :max="1000"
              />
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                :loading="saving.basic"
                :disabled="!loaded"
                @click="saveBasic"
              >{{ $t("securityPolicyPage.saveBasic") }}</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane :label="$t('securityPolicyPage.defense')" name="defense">
          <el-form
            ref="defenseFormRef"
            :model="defense"
            :rules="defenseRules"
            label-width="220px"
          >
            <el-form-item :label="$t('securityPolicyPage.loginFailureDuration')" prop="loginFailureDuration">
              <el-input-number
                v-model="defense.loginFailureDuration"
                :min="1"
                :max="1440"
              />
            </el-form-item>
            <el-form-item :label="$t('securityPolicyPage.loginFailureCount')" prop="loginFailureCount">
              <el-input-number
                v-model="defense.loginFailureCount"
                :min="1"
                :max="100"
              />
            </el-form-item>
            <el-form-item :label="$t('securityPolicyPage.autoUnlockTime')" prop="autoUnlockTime">
              <el-input-number
                v-model="defense.autoUnlockTime"
                :min="1"
                :max="10080"
              />
            </el-form-item>
            <el-form-item :label="$t('securityPolicyPage.contentSecurityPolicy')" prop="contentSecurityPolicy">
              <el-input
                v-model="defense.contentSecurityPolicy"
                type="textarea"
                :rows="5"
                maxlength="16000"
                show-word-limit
                :placeholder="$t('securityPolicyPage.cspPlaceholder')"
              />
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                :loading="saving.defense"
                :disabled="!loaded"
                @click="saveDefense"
              >{{ $t("securityPolicyPage.saveDefense") }}</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane :label="$t('securityPolicyPage.password')" name="password">
          <el-form
            ref="passwordFormRef"
            :model="password"
            :rules="passwordRules"
            label-width="220px"
          >
            <el-form-item :label="$t('securityPolicyPage.passwordLeastLength')" prop="passwordLeastLength">
              <el-input-number
                v-model="password.passwordLeastLength"
                :min="6"
                :max="128"
              />
            </el-form-item>
            <el-form-item :label="$t('securityPolicyPage.passwordBiggestLength')" prop="passwordBiggestLength">
              <el-input-number
                v-model="password.passwordBiggestLength"
                :min="6"
                :max="128"
              />
            </el-form-item>
            <el-form-item :label="$t('securityPolicyPage.passwordComplexity')" prop="passwordComplexity">
              <el-select v-model="password.passwordComplexity">
                <el-option
                  v-for="option in complexityOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('securityPolicyPage.weakPasswordCheck')">
              <el-switch v-model="password.weakPasswordCheck" />
            </el-form-item>
            <el-form-item :label="$t('securityPolicyPage.customWeakPassword')">
              <el-input
                v-model="password.customWeakPassword"
                type="textarea"
                :rows="5"
                maxlength="60000"
                :placeholder="$t('securityPolicyPage.weakPasswordPlaceholder')"
              />
            </el-form-item>
            <el-form-item :label="$t('securityPolicyPage.includeAccountCheck')">
              <el-switch v-model="password.includeAccountCheck" />
            </el-form-item>
            <el-form-item :label="$t('securityPolicyPage.notSameChars')" prop="notSameChars">
              <el-input-number v-model="password.notSameChars" :min="0" :max="20" />
            </el-form-item>
            <el-form-item :label="$t('securityPolicyPage.historyPasswordCheck')">
              <el-switch
                v-model="password.historyPasswordCheck"
                @change="onHistoryCheckChange"
              />
            </el-form-item>
            <el-form-item :label="$t('securityPolicyPage.historyPasswordCheckCount')" prop="historyPasswordCheckCount">
              <el-input-number
                v-model="password.historyPasswordCheckCount"
                :disabled="!password.historyPasswordCheck"
                :min="password.historyPasswordCheck ? 1 : 0"
                :max="50"
              />
            </el-form-item>
            <el-form-item :label="$t('securityPolicyPage.illegalSequenceCheck')">
              <el-switch v-model="password.illegalSequenceCheck" />
            </el-form-item>
            <el-form-item :label="$t('securityPolicyPage.passwordValidDays')" prop="passwordValidDays">
              <el-input-number
                v-model="password.passwordValidDays"
                :min="0"
                :max="3650"
                @change="onValidDaysChange"
              />
            </el-form-item>
            <el-form-item :label="$t('securityPolicyPage.passwordValidWarnBeforeDays')" prop="passwordValidWarnBeforeDays">
              <el-input-number
                v-model="password.passwordValidWarnBeforeDays"
                :disabled="password.passwordValidDays === 0"
                :min="0"
                :max="365"
              />
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                :loading="saving.password"
                :disabled="!loaded"
                @click="savePassword"
              >{{ $t("securityPolicyPage.savePassword") }}</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
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

const requiredNumber = [{ required: true, message: t("securityPolicyPage.requiredNumber"), trigger: "change" }];
const basicRules = Object.fromEntries(Object.keys(basic).map((key) => [key, requiredNumber]));
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
const complexityOptions = computed(() => [0, 1, 2, 3, 4, 5].map((value) => ({
  value,
  label: t(`securityPolicyPage.complexity${value}`),
})));

const unwrap = (response) => {
  if (!response?.success) {
    throw new Error(response?.msg || response?.cause || t("securityPolicyPage.operationFailed"));
  }
  return response.data;
};
const assignKnown = (target, source) => {
  Object.keys(target).forEach((key) => {
    if (Object.prototype.hasOwnProperty.call(source || {}, key)) target[key] = source[key];
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

const saveBasic = () => saveSection("basic", basicFormRef, api.saveBasic, basic);
const saveDefense = () => saveSection("defense", defenseFormRef, api.saveDefense, defense);
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
  password.historyPasswordCheckCount = enabled ? Math.max(1, password.historyPasswordCheckCount) : 0;
}
function onValidDaysChange(days) {
  if (days === 0) password.passwordValidWarnBeforeDays = 0;
}

onMounted(load);
</script>

<style scoped>
.security-policy-page {
  padding: 24px;
}
.policy-card {
  margin-top: 20px;
  max-width: 980px;
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
</style>
