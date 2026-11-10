<template>
 <section class="configuration-panel">
  <div class="section-heading"><h3>资源服务器</h3><p>用于调用令牌内省接口验证访问令牌，不申请用户或服务令牌。</p></div>
  <el-alert v-if="client.managedKey" title="平台内置身份：不可删除、停用或变更用途。" type="info" :closable="false" />
  <div class="form-grid">
   <el-form-item label="名称" prop="clientName" required><el-input v-model="client.clientName" maxlength="200" /></el-form-item>
  </div>
  <ClientCredentials v-model:client="client" :settings="client.settings" @copy="value => $emit('copy', value)" />
  <p class="hint">资源服务器与目标令牌所属应用可以不同。接口返回令牌状态及权限信息；不需要配置 client_credentials、回调或用户目录。</p>
  <h4>内省调用示例</h4><pre>POST /oauth2/introspect
Authorization: Basic &lt;客户端凭据&gt;
Content-Type: application/x-www-form-urlencoded

token=&lt;需要验证的Access Token&gt;</pre>
  <p class="hint">Basic 内容由 client_id 与密钥分别进行表单编码后，以冒号连接再 Base64 编码。使用 private_key_jwt 时发送新生成的客户端签名断言。</p>
 </section>
</template>
<script setup>
import ClientCredentials from './ClientCredentials.vue'
const client=defineModel('client',{type:Object,required:true})
defineEmits(['copy'])
</script>
<style scoped>
:deep(.el-radio-group),:deep(.el-checkbox-group){display:flex;flex-wrap:wrap;gap:12px 24px;}
:deep(.el-radio),:deep(.el-checkbox){height:auto;min-height:36px;margin:0;}
:deep(.el-radio__label){white-space:normal;}
.hint{color:var(--el-text-color-secondary);line-height:1.7;}
pre{padding:16px;background:var(--el-fill-color-light);border-radius:8px;overflow:auto;line-height:1.8;}
</style>
