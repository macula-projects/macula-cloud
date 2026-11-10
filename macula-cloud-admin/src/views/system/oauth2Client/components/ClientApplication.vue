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
    <el-form-item v-if="!creating && ['WEB_SERVER','SPA'].includes(originalType) && !client.managedKey && !client.convertToInApp" label="端内登录">
      <el-button @click="conversionOpen = true">转换为端内应用</el-button>
      <p class="hint">保留Client ID、原凭据和用户绑定。须选择唯一端内能力并预览确认，保存后生效。</p>
    </el-form-item>
    <el-alert v-if="client.convertToInApp" title="已准备转换为端内应用，尚未保存；请核对协议和登录能力后保存。" type="warning" :closable="false" />
    <IdentitySourcePicker v-model:open="conversionOpen" :client="conversionClient" :catalog="catalog" :loaded="catalogLoaded" @add="convert" />
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
import {computed,ref,toRaw} from 'vue';
import {ElMessageBox,ElMessage} from 'element-plus';
import IdentitySourcePicker from './IdentitySourcePicker.vue';
import {inAppConversion} from '@/utils/oauthClientConfiguration';
const conversionOpen = ref(false);
const client = defineModel("client", { type: Object, required: true });
import { APPLICATION_TYPES } from "@/api/model/system/oauth2Client";
defineProps({
  catalog: {type:Object,default:()=>({sources:[]})},
  catalogLoaded:Boolean,
  originalType: { type: String, default: null },
  creating: Boolean,
  tenants: { type: Array, default: () => [] },
});
defineEmits(["copy"]);
const conversionClient = computed(()=>({...client.value,applicationType:'IN_APP',settings:{...client.value.settings,clientAuthenticationMethods:'none'}}));
async function convert(additions) {
  if (additions.length !== 1) return ElMessage.warning('请选择唯一端内登录能力');
  const {source,capability}=additions[0];
  try {
    const next=inAppConversion(toRaw(client.value),source,capability);
    const browser=!!Object.keys(next.browserCapabilityBindings).length;
    await ElMessageBox.confirm(`保留：Client ID、原凭据和用户绑定。\n唯一来源：${source.name} / ${capability}。\n移除：其他网页与Code来源、IAM内置入口和Origin。\n授权改为：${browser?'授权码 + S256（保留原客户端认证）':'第三方Code（none认证）'}。${browser?'原回调白名单保留，请核对。':'原回调仅作为已登记白名单保留。'}\n保存后旧授权版本失效，应用类型不能直接改回。`,'确认转换为端内应用',{type:'warning',confirmButtonText:'准备转换'});
    client.value=next;
  } catch(error) {if(error !== 'cancel' && error !== 'close') ElMessage.error(error.message);}
}
</script>


<style scoped>
.application-fields {display:flex;flex-direction:column;max-width:640px;}
.application-fields :deep(.el-select) {width:100%;}
</style>
