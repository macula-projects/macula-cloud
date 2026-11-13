<template>
  <section class="configuration-panel">
    <div class="section-heading"><h3>应用基本信息</h3><p>设置应用名称与认证租户。认证方式及凭据统一在协议配置中管理。</p></div>
    <div class="application-fields">
    <el-form-item v-if="!creating" label="应用类型">
      <el-radio-group
        v-if="!originalType"
        v-model="client.applicationType"
        placeholder="请选择应用类型"
      >
        <el-radio
          v-for="type in APPLICATION_TYPES"
          :key="type.value"
          :value="type.value"
        >{{type.label}}</el-radio>
      </el-radio-group>
      <span v-else
        >{{
          APPLICATION_TYPES.find((t) => t.value === client.applicationType)
            ?.label || client.applicationType
        }}（保存后只读）</span
      >
    </el-form-item>
    <el-form-item v-if="['WEB_SERVER','SPA'].includes(client.applicationType)" :label="$t('applicationBoundary.webLoginMode')">
      <span class="mode-value">{{ $t(client.webLoginMode === 'IN_APP' ? 'applicationBoundary.inAppMode' : 'applicationBoundary.browserMode') }}（创建后只读）</span>
      <p class="hint">需要使用另一登录模式时，请新建应用。</p>
    </el-form-item>
    <el-form-item
      v-if="!creating && client.applicationType === 'MINIAPP'"
      label="小程序平台"
    >
      <el-select v-model="client.miniappPlatform" :disabled="!!originalType"
        ><el-option label="微信" value="WECHAT" /><el-option
          label="企业微信"
          value="WECOM"
      /></el-select>
    </el-form-item>
    <el-form-item label="应用名称" prop="clientName" required
      ><el-input v-model="client.clientName" maxlength="200"
    /></el-form-item>
    <el-form-item label="认证租户" prop="tenantId" required
      ><el-select v-model="client.tenantId" filterable
        ><el-option
          v-for="tenant in tenants"
          :key="tenant.value"
          :label="tenant.label"
          :value="tenant.value" /></el-select
    ></el-form-item>

    </div>
  </section>
</template>
<script setup>
const client = defineModel("client", { type: Object, required: true });
import { APPLICATION_TYPES } from "@/api/model/system/oauth2Client";
defineProps({
  originalType: { type: String, default: null },
  creating: Boolean,
  tenants: { type: Array, default: () => [] },
});
</script>


<style scoped>
.application-fields {display:flex;flex-direction:column;max-width:640px;}
.application-fields :deep(.el-select) {width:100%;}
</style>
