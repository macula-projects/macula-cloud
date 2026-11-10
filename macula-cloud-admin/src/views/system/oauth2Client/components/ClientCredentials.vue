<template>
  <section class="configuration-panel client-credentials">
    <div class="section-heading"><h3>客户端认证与凭据</h3></div>
    <el-form-item label="认证方式">
      <el-radio-group class="authentication-methods" data-testid="client-auth" v-model="settings.clientAuthenticationMethods" @change="$emit('authentication-change')">
        <el-radio v-for="method in methods" :key="method.value" :value="method.value">{{ method.value }}</el-radio>
      </el-radio-group>
      <p class="credential-hint">{{ methods.find(method => method.value === settings.clientAuthenticationMethods)?.description }}</p>
    </el-form-item>
    <div class="credentials-grid">
      <el-form-item label="Client ID">
        <div class="credential-controls">
          <el-input :model-value="client.clientId" readonly>
            <template #append><el-button @click="$emit('copy', client.clientId)">复制</el-button></template>
          </el-input>
        </div>
      </el-form-item>
      <el-form-item v-if="usesSecret(client)" label="Client Secret">
        <div v-if="!client.secretConfigured" class="credential-controls">
          <el-input v-model="client.clientSecret" type="password" show-password autocomplete="new-password" placeholder="生成或输入至少16位密钥" />
          <div class="credential-actions">
            <el-button link type="primary" @click="generate">安全生成</el-button>
            <el-button link type="primary" :disabled="!client.clientSecret" @click="$emit('copy', client.clientSecret)">复制</el-button>
          </div>
        </div>
        <div v-else class="credential-controls">
          <el-input model-value="已配置" readonly>
            <template #append><el-button @click="resetOpen = true">重置密钥</el-button></template>
          </el-input>
        </div>
        <p class="credential-hint">{{ client.secretConfigured
          ? "无法读取已保存密钥；重置后请同步接入应用。"
          : "密钥仅在新建或初始化时显示，保存成功后请立即妥善保管。" }}</p>
      </el-form-item>
    </div>
    <div v-if="settings.clientAuthenticationMethods === 'private_key_jwt'" class="credentials-grid">
      <el-form-item label="客户端 JWKS URL" required>
        <el-input v-model="settings.jwkSetUrl" placeholder="https://client.example.com/.well-known/jwks.json" />
        <p class="credential-hint">私钥由接入应用保管，IAM 从此地址读取公钥。</p>
      </el-form-item>
      <el-form-item label="客户端签名算法">
        <el-radio-group v-model="settings.signingAlgorithm"><el-radio v-for="alg in ['RS256', 'RS384', 'RS512']" :key="alg" :value="alg">{{ alg }}</el-radio></el-radio-group>
      </el-form-item>
    </div>
    <el-dialog class="application-settings-dialog" top="5vh"
      v-model="resetOpen"
      destroy-on-close
      title="重置客户端密钥"
      width="min(560px, 94vw)"
      @closed="clear"
    >
      <p>重置成功后所有使用该应用密钥的协议都需同步更新。离开本窗口后无法再次查看新密钥。</p>
      <el-input
        v-model="resetValue"
        type="password"
        show-password
        :disabled="resetDone"
        autocomplete="new-password"
      />
      <el-button :disabled="resetDone" @click="generateReset"
        >安全生成</el-button
      >
      <el-button :disabled="!resetValue" @click="$emit('copy', resetValue)"
        >复制</el-button
      >
      <template #footer
        ><el-button @click="resetOpen = false">关闭</el-button
        ><el-button
          type="primary"
          :loading="resetSaving"
          :disabled="resetDone || !resetValue"
          @click="reset"
          >确认重置</el-button
        ></template
      >
      <el-alert
        v-if="resetDone"
        title="密钥已重置，请在关闭前复制保存"
        type="success"
        :closable="false"
      />
    </el-dialog>
  </section>
</template>
<script setup>
const client = defineModel("client", { type: Object, required: true });
import { computed, ref, onBeforeUnmount, onDeactivated } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import api, { oauthClientErrorMessage } from "@/api/model/system/oauth2Client";
import {
  AUTHENTICATION_METHODS,
  usesSecret,
  generateClientSecret,
} from "@/utils/oauthClientConfiguration";
const settings = defineModel("settings", { type: Object, required: true });
defineEmits(["copy", "authentication-change"]);
const methods = computed(() => AUTHENTICATION_METHODS.filter(method => {
  if (client.value.purpose === 'RESOURCE_SERVER' || client.value.applicationType === 'SERVICE') return method.value !== 'none';
  if (['SPA', 'NATIVE', 'MINIAPP'].includes(client.value.applicationType)) return method.value === 'none';
  return true;
}));
const resetOpen = ref(false),
  resetValue = ref(""),
  resetSaving = ref(false),
  resetDone = ref(false);
function generate() {
  try {
    client.value.clientSecret = generateClientSecret();
  } catch (error) {
    ElMessage.error(error.message);
  }
}
function generateReset() {
  try {
    resetValue.value = generateClientSecret();
  } catch (error) {
    ElMessage.error(error.message);
  }
}
function clear() {
  resetValue.value = "";
  resetDone.value = false;
}
async function reset() {
  if (resetSaving.value) return;
  try {
    await ElMessageBox.confirm("旧密钥将立即失效，确认重置？", "重置密钥");
    resetSaving.value = true;
    const response = await api.resetSecret(client.value.id, resetValue.value, client.value.configurationVersion);
    if (!response?.success) throw new Error(oauthClientErrorMessage(response));
    resetDone.value = true;
    ElMessage.success("密钥已重置，请重新加载应用配置后再保存其他修改");
  } catch (error) {
    if (error !== "cancel" && error !== "close")
      ElMessage.error(error.message || "重置失败");
  } finally {
    resetSaving.value = false;
  }
}
onBeforeUnmount(clear);
onDeactivated(() => {
  clear();
  resetOpen.value = false;
});
</script>

<style scoped>
.client-credentials .authentication-methods { display: flex; flex-wrap: wrap; gap: 8px 24px; width: auto; max-width: 100%; }
.authentication-methods :deep(.el-radio) { display: inline-flex; flex: 0 0 auto; align-items: center; width: auto; min-width: 0; max-width: 100%; height: 32px; min-height: 32px; margin: 0; padding: 0; border: 0; background: transparent; }
.authentication-methods :deep(.el-radio__label) { white-space: nowrap; font-family: var(--el-font-family); }

.credentials-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 32px; }
.credential-controls { display: flex; flex-wrap: wrap; align-items: center; gap: 0; width: 100%; min-width: 0; }
.credential-controls .el-input { flex: 1 1 100%; min-width: 0; }
.credential-actions { display: flex; align-items: center; gap: 16px; margin-top: 8px; }
.credential-actions :deep(.el-button) { min-height: 24px; height: 24px; padding: 0; }
.credential-controls .el-button + .el-button { margin-left: 0; }
.credential-hint { flex-basis: 100%; margin: 8px 0 0; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; }
@media (max-width: 760px) { .credentials-grid { grid-template-columns: minmax(0, 1fr); } }
@media (max-width: 600px) { .credential-controls { flex-wrap: wrap; } .credential-controls .el-input { flex-basis: 100%; } }
</style>
