<template>
 <section class="configuration-panel">
  <div class="section-heading"><h3>接入协议</h3><p>选择应用使用的协议，再配置认证方式、授权与令牌。</p></div>
  <el-form-item v-if="client.applicationType !== 'SERVICE'" label="协议" required>
   <el-radio-group :model-value="client.protocol" :disabled="!!client.managedKey" @change="choose">
    <el-radio v-for="definition in APPLICATION_PROTOCOLS" :key="definition.code" :value="definition.code" :disabled="!protocolSupported(client,definition.code)">
     {{definition.label}}
    </el-radio>
   </el-radio-group>
   <p class="protocol-description">{{ APPLICATION_PROTOCOLS.find(definition => definition.code === client.protocol)?.description }}</p>
  </el-form-item>
  <div v-if="client.protocol" class="protocol-details">
   <OAuthProtocolSettings :application="client" :protocol="client.protocol" :catalog="catalog" :catalog-loaded="catalogLoaded" :catalog-loading="catalogLoading" :catalog-error="catalogError" @reload="$emit('reload')" @copy="value => $emit('copy', value)" @manage-sources="$emit('manage-sources')" />
   <ProtocolEndpoints :service="client.applicationType === 'SERVICE'" :protocol="client.protocol" :metadata="metadata?.[client.protocol]" :loading="metadataLoading?.[client.protocol]" @reload="$emit('reload-metadata',client.protocol)" @copy="value=>$emit('copy',value)" />
  </div>
 </section>
</template>
<script setup>
import {ElMessageBox} from 'element-plus'
import OAuthProtocolSettings from './OAuthProtocolSettings.vue'
import ProtocolEndpoints from './ProtocolEndpoints.vue'
import {APPLICATION_PROTOCOLS,selectProtocol,protocolSupported} from '@/utils/applicationProtocolRegistry'
const client=defineModel('client',{type:Object,required:true})
defineProps({original:Object,catalog:{type:Object,default:()=>({scopes:[]})},catalogLoaded:Boolean,catalogLoading:Boolean,catalogError:String,metadata:Object,metadataLoading:Object})
defineEmits(['reload','reload-metadata','copy','manage-sources'])
async function choose(code) {
 if(code===client.value.protocol)return
 try {
  if(client.value.protocol)await ElMessageBox.confirm('切换会清空协议参数、回调及身份源关联；保存后旧令牌失效，需要重新登录。','切换接入协议',{type:'warning'})
  selectProtocol(client.value,code)
 } catch(error) {if(error!=='cancel' && error!=='close')throw error}
}
</script>
<style scoped>
.protocol-details { margin-top: 24px; padding-top: 24px; border-top: 1px solid var(--el-border-color-lighter); }
:deep(.protocol-endpoints) { margin-top: 28px; }
:deep(.el-radio-group) { display: flex; flex-wrap: wrap; gap: 8px 24px; max-width: 100%; }
:deep(.el-radio) { height: 32px; min-height: 32px; margin: 0; padding: 0; max-width: 100%; }
:deep(.el-radio__label) { white-space: normal; line-height: 1.6; }
.protocol-description { flex-basis: 100%; margin: 4px 0 0; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; }
</style>
