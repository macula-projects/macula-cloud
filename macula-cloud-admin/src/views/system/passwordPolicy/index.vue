<template>
  <div class="settings-page" v-loading="loading">
    <h2>{{ $t("adminSettings.password") }}</h2>
    <el-alert
      :title="$t('adminSettings.passwordPending')"
      type="warning"
      :closable="false"
    />
    <el-card class="form-card" header="密码规则">
      <el-form ref="formRef" :model="form" label-width="220px">
        <el-form-item label="最小长度"
          ><el-input-number v-model="form.minLength" :min="6" :max="128"
        /></el-form-item>
        <el-form-item label="最大长度"
          ><el-input-number v-model="form.maxLength" :min="6" :max="128"
        /></el-form-item>
        <el-form-item label="复杂度档位"
          ><el-select v-model="form.complexityLevel"
            ><el-option
              v-for="n in 5"
              :key="n - 1"
              :label="`档位 ${n - 1}`"
              :value="n - 1" /></el-select
        ></el-form-item>
        <el-form-item label="过期天数（0 为不过期）"
          ><el-input-number v-model="form.expiryDays" :min="0" :max="3650"
        /></el-form-item>
        <el-form-item label="提前提醒天数"
          ><el-input-number v-model="form.reminderDays" :min="0" :max="365"
        /></el-form-item>
        <el-form-item label="连续相同字符上限"
          ><el-input-number
            v-model="form.maxConsecutiveCharacters"
            :min="0"
            :max="20"
        /></el-form-item>
        <el-form-item label="检查用户信息"
          ><el-switch v-model="form.checkUserInformation"
        /></el-form-item>
        <el-form-item label="历史密码数量"
          ><el-input-number v-model="form.historyCount" :min="0" :max="50"
        /></el-form-item>
        <el-form-item label="非法字符序列"
          ><el-input
            v-model="illegalText"
            type="textarea"
            placeholder="每行一个序列"
        /></el-form-item>
        <el-form-item label="启用弱密码检查"
          ><el-switch v-model="form.weakPasswordCheck"
        /></el-form-item>
        <el-form-item label="自定义弱密码"
          ><el-input
            v-model="weakText"
            type="textarea"
            placeholder="每行一个；仅配置，不在本轮执行检查"
        /></el-form-item>
        <el-form-item
          ><el-button type="primary" :loading="saving" @click="save"
            >保存策略</el-button
          ></el-form-item
        >
      </el-form>
    </el-card>
  </div>
</template>

<script>
export default { name: "PasswordPolicyPage" };
</script>
<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import api from "@/api/model/system/passwordPolicy";

const form = reactive({
  minLength: 8,
  maxLength: 128,
  complexityLevel: 2,
  expiryDays: 0,
  reminderDays: 0,
  maxConsecutiveCharacters: 3,
  checkUserInformation: true,
  historyCount: 0,
  illegalSequences: [],
  weakPasswordCheck: true,
  customWeakPasswords: [],
});
const illegalText = ref(""),
  weakText = ref(""),
  loading = ref(false),
  saving = ref(false);
const unwrap = (res) => {
  if (!res?.success) throw new Error(res?.msg || res?.cause || "操作失败");
  return res.data;
};
const lines = (value) =>
  value
    .split("\n")
    .map((s) => s.trim())
    .filter(Boolean);
async function load() {
  loading.value = true;
  try {
    const data = unwrap(await api.get());
    Object.keys(form).forEach((key) => {
      form[key] = data[key];
    });
    illegalText.value = (form.illegalSequences || []).join("\n");
    weakText.value = (form.customWeakPasswords || []).join("\n");
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    loading.value = false;
  }
}
async function save() {
  if (saving.value) return;
  if (form.minLength > form.maxLength)
    return ElMessage.error("最小长度不得大于最大长度");
  saving.value = true;
  try {
    const payload = {
      ...form,
      illegalSequences: lines(illegalText.value),
      customWeakPasswords: lines(weakText.value),
    };
    unwrap(await api.save(payload));
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
