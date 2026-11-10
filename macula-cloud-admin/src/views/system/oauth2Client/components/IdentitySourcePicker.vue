<template>
  <el-dialog class="application-settings-dialog" top="5vh" v-model="open" title="添加身份源" width="min(760px, 94vw)" destroy-on-close @open="reset">
    <el-input v-model="keyword" placeholder="搜索来源名称、厂商或接入类型" clearable />
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-button link @click="$emit('reload')">刷新配置目录</el-button>
    <p class="hint">{{ client.applicationType === 'IN_APP' ? '选择唯一端内能力；网页授权直接进入固定平台，JSAPI由业务前端取Code，无IAM标准登录页。' : '按应用类型选择登录能力；移动端只选一个SDK，小程序只选一个本平台注册。' }}</p>
    <div v-loading="loading" class="source-options">
      <el-empty v-if="loaded && !sources.length" description="没有兼容的已配置身份源">
        <router-link to="/system/identity-provider">前往身份源管理</router-link>
      </el-empty>
      <article v-for="source in sources" :key="source.id" class="source-option">
        <strong>{{ source.name }}</strong><p class="hint">{{ source.label }} · {{ source.capabilities?.some(item => item.loginCapability) ? '按登录能力选择' : sceneLabels[source.scene] || source.scene }}</p>
        <el-radio-group v-if="['NATIVE','IN_APP'].includes(client.applicationType)" :model-value="singleSelection" @update:model-value="value=>selectOne(source,value)">
          <el-radio v-for="item in choices(source)" :key="item.value" :value="source.id + ':' + item.value">{{ item.label }}</el-radio>
        </el-radio-group>
        <el-checkbox-group v-else :model-value="selected[source.id]" @update:model-value="value=>selectMany(source,value)">
          <el-checkbox v-for="item in browserCapabilities(client,source)" :key="item.loginCapability" :value="item.loginCapability">IAM网页登录 · {{ item.label }}<span v-if="browserEnvironmentLabel({...source,browserEnvironments:item.environments || source.browserEnvironments})">（{{ browserEnvironmentLabel({...source,browserEnvironments:item.environments || source.browserEnvironments}) }}）</span></el-checkbox>
          <el-checkbox v-for="capability in capabilities(source)" :key="capability.codeType" :value="capability.codeType"
            :disabled="!sdkAllowed">{{ capability.label }}</el-checkbox>
        </el-checkbox-group>
      </article>
      <p v-if="!sdkAllowed && client.applicationType !== 'SERVICE'" class="hint">直接Code登录需当前协议使用公共客户端认证（none）；网页授权继续使用授权码。</p>
    </div>
    <template #footer><el-button @click="open = false">取消</el-button>
      <el-button type="primary" :disabled="!loaded || loading" @click="confirm">添加所选</el-button></template>
  </el-dialog>
</template>
<script setup>
import {computed,ref} from 'vue';
import {externalCodeAllowed,browserEnvironmentLabel,browserCapabilities,sourceSelectionIssue} from '@/utils/identitySourceCompatibility';
import {enabledProtocolCodes,projectProtocol} from '@/utils/applicationProtocolRegistry';
import {ElMessage} from 'element-plus';
const open = defineModel('open',{type:Boolean});
const props = defineProps({client:{type:Object,required:true},catalog:{type:Object,required:true},loaded:Boolean,loading:Boolean,error:{type:String,default:''}});
const emit = defineEmits(['add','reload']);
const keyword = ref(''), selected = ref({}), singleSelection = ref('');
const sceneLabels = {BROWSER:'网页',WEBVIEW:'端内网页',APP:'SDK / 应用',MINIAPP:'小程序',CONFIGURATION:'仅凭据管理'};
const oauth = computed(() => projectProtocol(props.client));
const sdkAllowed = computed(() => (oauth.value.clientAuthenticationMethods === 'none' || props.client.applicationType === 'IN_APP') && enabledProtocolCodes(props.client).some(code=>['OIDC','OAUTH2_1'].includes(code)));
function capabilities(source) {
  return (source.capabilities || []).filter(capability => sdkAllowed.value && externalCodeAllowed(props.client,capability.codeType,source));
}
const sources = computed(() => (props.catalog.sources || []).filter(source => source.available &&
  (browserCapabilities(props.client,source).length || capabilities(source).length) &&
  [source.name,source.label,source.providerType].join(' ').toLowerCase().includes(keyword.value.toLowerCase())));
function reset() { singleSelection.value=''; keyword.value = ''; selected.value = Object.fromEntries((props.catalog.sources || []).map(source => [source.id,[]])); }
function choices(source) {
  return [...browserCapabilities(props.client,source).map(item=>({value:item.loginCapability,label:item.label})),
    ...capabilities(source).map(item=>({value:item.codeType,label:item.label}))];
}
function selectOne(source,value) {
  selected.value = Object.fromEntries((props.catalog.sources || []).map(item=>[item.id,[]]));
  singleSelection.value=value;
  selected.value[source.id]=[value.slice(String(source.id).length+1)];
}
function selectMany(source,values) {
  if (props.client.applicationType==='MINIAPP' && values.length) {
    for (const id of Object.keys(selected.value)) if (String(id)!==String(source.id)) selected.value[id]=[];
  }
  selected.value[source.id]=values;
}
function confirm() {
  const additions = sources.value.flatMap(source => (selected.value[source.id] || []).filter(capability => browserCapabilities(props.client,source).some(item => item.loginCapability === capability) || capabilities(source).some(item => item.codeType === capability))
    .map(capability => ({source,capability})));
  if (!additions.length) return ElMessage.warning('请选择来源及具体认证能力');
  const issue = sourceSelectionIssue(props.client,additions);
  if (issue) return ElMessage.warning(issue);
  emit('add',additions); open.value = false;
}
</script>
<style scoped>
.source-options {margin-top:16px;}
.source-option {border:1px solid var(--el-border-color-lighter);border-radius:6px;padding:16px;margin-bottom:12px;}
.hint {color:var(--el-text-color-secondary);font-size:13px;line-height:1.7;}
:deep(.el-checkbox__label) {white-space:normal;}
</style>
