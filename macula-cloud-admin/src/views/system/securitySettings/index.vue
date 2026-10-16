<template>
  <div class="settings-page" v-loading="loading">
    <h2>{{ $t("adminSettings.security") }}</h2>
    <el-alert
      :title="$t('adminSettings.securityPending')"
      type="warning"
      :closable="false"
    />
    <el-card class="form-card" header="基本设置与防御策略">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="220px">
        <el-form-item label="用户并发会话数" prop="maxConcurrentSessions"
          ><el-input-number
            v-model="form.maxConcurrentSessions"
            :min="1"
            :max="1000"
        /></el-form-item>
        <el-form-item label="会话时长（分钟）" prop="sessionTimeoutMinutes"
          ><el-input-number
            v-model="form.sessionTimeoutMinutes"
            :min="1"
            :max="43200"
        /></el-form-item>
        <el-form-item label="记住我时长（天）" prop="rememberMeDays"
          ><el-input-number v-model="form.rememberMeDays" :min="0" :max="365"
        /></el-form-item>
        <el-form-item label="验证码有效期（秒）" prop="captchaValiditySeconds"
          ><el-input-number
            v-model="form.captchaValiditySeconds"
            :min="30"
            :max="3600"
        /></el-form-item>
        <el-divider>登录失败防御</el-divider>
        <el-form-item label="统计窗口（分钟）" prop="failureWindowMinutes"
          ><el-input-number
            v-model="form.failureWindowMinutes"
            :min="1"
            :max="1440"
        /></el-form-item>
        <el-form-item label="最大失败次数" prop="maxFailures"
          ><el-input-number v-model="form.maxFailures" :min="1" :max="100"
        /></el-form-item>
        <el-form-item label="自动解锁时间（分钟）" prop="unlockMinutes"
          ><el-input-number v-model="form.unlockMinutes" :min="1" :max="10080"
        /></el-form-item>
        <el-form-item label="内容安全策略"
          ><el-input
            v-model="form.contentSecurityPolicy"
            type="textarea"
            :rows="4"
            placeholder="例如：default-src 'self'"
        /></el-form-item>
        <el-form-item
          ><el-button type="primary" :loading="saving" @click="save"
            >保存配置</el-button
          ></el-form-item
        >
      </el-form>
    </el-card>
  </div>
</template>

<script>
export default { name: "SecuritySettingsPage" };
</script>
<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import api from "@/api/model/system/securitySettings";

const form = reactive({
  maxConcurrentSessions: 1,
  sessionTimeoutMinutes: 30,
  rememberMeDays: 0,
  captchaValiditySeconds: 300,
  failureWindowMinutes: 15,
  maxFailures: 5,
  unlockMinutes: 30,
  contentSecurityPolicy: "",
});
const loading = ref(false),
  saving = ref(false),
  formRef = ref();
const rules = Object.fromEntries(
  Object.keys(form)
    .filter((key) => key !== "contentSecurityPolicy")
    .map((key) => [
      key,
      [{ required: true, message: "请输入有效数值", trigger: "change" }],
    ])
);
const unwrap = (res) => {
  if (!res?.success) throw new Error(res?.msg || res?.cause || "操作失败");
  return res.data;
};
async function load() {
  loading.value = true;
  try {
    const data = unwrap(await api.get());
    Object.keys(form).forEach((key) => {
      form[key] = data[key];
    });
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    loading.value = false;
  }
}
async function save() {
  if (saving.value) return;
  saving.value = true;
  try {
    if (!(await formRef.value.validate().catch(() => false))) return;
    unwrap(await api.save({ ...form }));
    ElMessage.success("保存成功，当前暂未生效");
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    saving.value = false;
  }
}
onMounted(load);
</script>

<style scoped>
.settings-page {
  padding: 24px;
}
.form-card {
  margin-top: 20px;
  max-width: 900px;
}
</style>
