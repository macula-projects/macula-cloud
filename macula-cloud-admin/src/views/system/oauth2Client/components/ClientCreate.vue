<template>
  <section class="client-create">
    <el-form-item label="接入用途"><el-radio-group :model-value="client.purpose || 'APPLICATION'" @change="purposeChanged"><el-radio value="APPLICATION">业务应用</el-radio><el-radio value="RESOURCE_SERVER">资源服务器（令牌内省）</el-radio></el-radio-group></el-form-item>
    <template v-if="client.purpose==='RESOURCE_SERVER'"><el-form-item label="名称" required><el-input :model-value="client.clientName" @update:model-value="value=>emit('change',{...client,clientName:value})" maxlength="200" /></el-form-item><p class="hint">创建后配置客户端认证与内省许可，不需要用户登录参数。</p></template>
    <template v-else><div class="create-columns"><div><h3>选择应用类型</h3>
    <div class="application-types">
      <button
        v-for="type in APPLICATION_TYPES"
        :key="type.value"
        type="button"
        :class="{ selected: client.applicationType === type.value }"
        :data-application-type="type.value"
        @click="change(type.value)"
      >
        <strong>{{ type.label }}</strong
        ><span>{{ type.description }}</span>
      </button>
    </div>
    <el-form-item
      v-if="client.applicationType === 'MINIAPP'"
      label="小程序平台"
    >
      <el-radio-group
        :model-value="client.miniappPlatform"
        @change="(platform) => change('MINIAPP', platform)"
      >
        <el-radio value="WECHAT">微信</el-radio
        ><el-radio value="WECOM">企业微信</el-radio>
      </el-radio-group>
    </el-form-item>
    </div><div class="create-basics"><el-form-item label="应用名称" required>
      <el-input :model-value="client.clientName" @update:model-value="value => emit('change',{...client,clientName:value})" placeholder="输入应用名称" maxlength="200" />
    </el-form-item><p class="hint">创建后继续配置协议、回调和访问授权，完成配置前不可用于认证。</p></div></div>
    <p class="hint">
      切换类型会清空原类型的登录方式、身份源和回调，应用名称与通用参数保留。保存后类型不可修改。
    </p></template>
  </section>
</template>
<script setup>
import { APPLICATION_TYPES } from "@/api/model/system/oauth2Client";
import { applicationTemplate } from "@/utils/oauthClientConfiguration";
const props = defineProps({ client: { type: Object, required: true } });
const emit = defineEmits(["change"]);
function purposeChanged(purpose) {
 if(purpose==='RESOURCE_SERVER')emit('change',{...props.client,purpose,applicationType:'SERVICE',miniappPlatform:null,protocol:null,settings:{kind:'oauth2',clientAuthenticationMethods:'client_secret_basic',authorizationGrantTypes:'',scopes:'',signingAlgorithm:'RS256'}})
 else emit('change',applicationTemplate(props.client,'WEB_SERVER'))
}
function change(type, platform) {
  if (
    type === props.client.applicationType &&
    (!platform || platform === props.client.miniappPlatform)
  )
    return;
  emit("change", applicationTemplate(props.client, type, platform));
}
</script>
<style scoped>
.client-create :deep(.el-form-item) {align-items:center;}
.client-create :deep(.el-form-item__label) {
  flex:0 0 100px;
  width:100px;
  height:auto;
  line-height:32px;
  padding:0 12px 0 0;
  justify-content:flex-start;
  box-sizing:border-box;
}
.client-create :deep(.el-form-item__content) {min-width:0;min-height:32px;}
.client-create :deep(.el-radio-group) {display:flex;flex-wrap:wrap;gap:8px 20px;}
.client-create :deep(.el-radio) {margin:0;height:32px;max-width:100%;}
.client-create :deep(.el-radio__label) {white-space:normal;line-height:22px;}
@media(max-width:600px) {
  .client-create :deep(.el-form-item) {display:block;}
  .client-create :deep(.el-form-item__label) {width:auto;line-height:24px;padding:0 0 4px;}
  .client-create :deep(.el-form-item__content) {margin-left:0 !important;}
}
.create-columns {display:grid;grid-template-columns:1fr 1fr;gap:32px;}
.create-basics {padding-top:20px;border-left:1px solid var(--el-border-color-lighter);padding-left:32px;}
@media(max-width:640px) {.create-columns {grid-template-columns:1fr;gap:12px;}.create-basics {padding:0;border:0;}}

.application-types {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
  margin: 20px 0;
}
button {
  text-align: left;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  padding: 16px;
  cursor: pointer;
}
button.selected {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.application-types strong,
.application-types span {
  display: block;
}
.application-types span {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
.hint {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
