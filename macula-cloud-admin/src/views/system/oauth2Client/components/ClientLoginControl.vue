<template>
  <section class="configuration-panel">
    <div v-if="client.applicationType !== 'SERVICE' && !isInApp(client)" class="section-heading"><h3>IAM 统一登录</h3><p>使用内置账号密码、短信或邮箱验证码登录。标准Web与SPA还可展示已关联的第三方网页授权图标。</p></div>
    <el-form-item v-if="client.applicationType !== 'SERVICE' && !isInApp(client)" label="统一登录入口">
      <el-switch :model-value="hostedEnabled" @change="setHostedEnabled" />
      <p class="hint">启用后需在应用配置填写登录回调；使用授权码与S256 PKCE。第三方平台Code仍走独立登录路径。</p>
    </el-form-item>
    <template v-if="client.applicationType !== 'SERVICE' && !isInApp(client) && hostedEnabled">
      <el-form-item label="本地登录策略">
        <el-radio-group :model-value="client.hostedLoginMethods == null ? 'default' : 'custom'"
          @change="value => client.hostedLoginMethods = value === 'default' ? null : ['PASSWORD', 'SMS', 'EMAIL']">
          <el-radio value="default">使用平台默认</el-radio><el-radio value="custom">为此应用单独设置</el-radio>
        </el-radio-group>
        <p class="hint">平台默认只跟随 IAM 的密码、短信、邮箱登录全局开关；不包含身份源或自动绑定策略。</p>
      </el-form-item>
      <el-form-item v-if="client.hostedLoginMethods != null" label="本地认证">
        <el-checkbox-group v-model="client.hostedLoginMethods">
          <el-checkbox value="PASSWORD">账号密码</el-checkbox><el-checkbox value="SMS">手机验证码</el-checkbox><el-checkbox value="EMAIL">邮箱验证码</el-checkbox>
        </el-checkbox-group>
        <p class="hint">全部取消关闭此应用的本地登录。平台全局关闭或服务未配置时，此处不能开启。</p>
      </el-form-item>
    </template>
    <el-alert v-if="client.applicationType === 'SERVICE'" title="后端服务不提供用户登录" type="info" :closable="false" />
    <p v-if="client.applicationType === 'MINIAPP'" class="hint">小程序只关联一个同平台小程序身份源；微信登录Code与手机号Code可以同源并存。另开IAM页面时仅提供内置登录。</p>
    <p v-if="client.applicationType === 'NATIVE'" class="hint">{{ $t('applicationBoundary.native') }} IAM页面不提供第三方网页登录。</p>
    <p v-if="!isInApp(client) && ['WEB_SERVER','SPA'].includes(client.applicationType)" class="hint">第三方网页授权图标展示在IAM登录页下方；可关联多个通用网页身份源。</p>
    <el-alert v-if="isInApp(client)" title="只能选择一个端内登录能力；网页授权直接跳转平台，JSAPI由业务前端取Code。无IAM标准登录页。" type="info" :closable="false" />
    <div v-if="client.applicationType !== 'SERVICE'" class="source-toolbar">
      <div class="section-heading source-heading"><h3>关联登录能力</h3><p>网页登录用于IAM统一登录；平台Code用于应用直接登录。可用能力按应用类型筛选，绑定策略在身份源管理中设置。</p></div>
      <div class="source-actions"><el-button link @click="$emit('reload')">刷新配置目录</el-button><el-button v-if="client.applicationType !== 'SERVICE'" type="primary" :disabled="!catalogLoaded" @click="pickerOpen = true">添加身份源</el-button></div>
    </div>
    <el-alert v-if="catalogError" :title="catalogError" type="error" :closable="false" />
    <el-alert v-if="boundaryIssue" :title="boundaryIssue" type="error" :closable="false" />
    <div v-loading="catalogLoading" class="selected-sources">
      <el-empty v-if="!relations.length" description="尚未添加身份源" :image-size="70" />
      <article v-for="row in relations" :key="row.id" class="selected-source">
        <div><strong>{{ row.source?.name || '来源 #' + row.id }}</strong>
          <p class="hint">{{ row.source?.label || '来源已删除或无法读取' }} · {{ row.capabilities.join('、') }}</p>
          <el-tag v-if="!row.source?.available" type="danger">来源已失效，请移除或更换</el-tag>
          <el-tag v-else-if="row.incompatible" type="danger">认证能力与当前应用类型不兼容，请移除或更换</el-tag></div>
        <el-button link type="danger" @click="removeSource(row.id)">移除</el-button>
      </article>
    </div>
    <el-form-item v-if="!isInApp(client) && ['WEB_SERVER','SPA'].includes(client.applicationType) && client.browserLoginProviderTypes?.length" label="默认直登">
      <el-radio-group v-model="client.directLoginProviderType"><el-radio value="">先展示登录页</el-radio>
        <el-radio v-for="provider in client.browserLoginProviderTypes" :key="provider" :value="provider">{{ browserName(provider) }}</el-radio>
      </el-radio-group>
    </el-form-item>
    <IdentitySourcePicker v-model:open="pickerOpen" :client="client" :catalog="catalog" :loaded="catalogLoaded"
      :loading="catalogLoading" :error="catalogError" @reload="$emit('reload')" @add="addSources" />
  </section>
</template>
<script setup>
import {isInApp} from '@/utils/identitySourceCompatibility';
import {ensureProtocol,projectProtocol} from '@/utils/applicationProtocolRegistry';

import {computed,ref} from 'vue';
import {externalCodeAllowed,browserSourceAllowed,browserCapabilities,singleSourceApplication,sourceSelectionIssue} from '@/utils/identitySourceCompatibility';
import {ElMessageBox,ElMessage} from 'element-plus';
import {EXTERNAL_CODE_GRANT} from '@/api/model/system/oauth2Client';
import {csvValues} from '@/utils/oauthClientConfiguration';
import IdentitySourcePicker from './IdentitySourcePicker.vue';
const client = defineModel('client',{type:Object,required:true});
const props = defineProps({catalog:{type:Object,required:true},catalogLoaded:Boolean,catalogLoading:Boolean,catalogError:{type:String,default:''}});
defineEmits(['reload']);
const pickerOpen = ref(false);
const hostedEnabled = computed(() => csvValues(projectProtocol(client.value).authorizationGrantTypes).includes('authorization_code'));
function setHostedEnabled(enabled) {
  if (!client.value.protocol) return ElMessage.warning('请先选择接入协议');
  if (!enabled && client.value.browserLoginProviderTypes?.length) return ElMessage.warning('请先移除依赖IAM统一登录的网页身份源');
  const settings = ensureProtocol(client.value).settings;
  const grants = new Set(csvValues(settings.authorizationGrantTypes));
  if (enabled) {
    grants.add('authorization_code');
    // 默认但尚未关联来源的Code占位可以移除；已有Code关联及本地登录策略不被开关重置。
    if (!client.value.externalCodeTypes?.length) grants.delete(EXTERNAL_CODE_GRANT);
    settings.requireProofKey = true;
  } else grants.delete('authorization_code');
  settings.authorizationGrantTypes = [...grants].join(',');
}

const relations = computed(() => {
  const rows = new Map();
  function add(id,capability,codeType) {
    const key = String(id);
    if (!rows.has(key)) rows.set(key,{id:key,source:(props.catalog.sources || []).find(source => String(source.id) === key),capabilities:[],incompatible:false});
    rows.get(key).capabilities.push(capability);
    const row = rows.get(key);
    row.incompatible ||= capability === 'IAM网页登录' ? !browserSourceAllowed(client.value,row.source) : !externalCodeAllowed(client.value,codeType,row.source);
  }
  if (client.value.browserCapabilityBindings != null) {
    Object.entries(client.value.browserCapabilityBindings).forEach(([capability,id]) => {
      const label = (props.catalog.sources || []).flatMap(source => source.capabilities || []).find(item => item.loginCapability === capability)?.label || capability;
      add(id,'IAM网页登录'); rows.get(String(id)).capabilities.push(label);
      const source = rows.get(String(id)).source;
      rows.get(String(id)).incompatible ||= !(source?.capabilities || []).some(item => item.loginCapability === capability && item.available !== false);
    });
  } else Object.values(client.value.browserProviderBindings || {}).forEach(id => add(id,'IAM网页登录'));
  (client.value.externalCodeTypes || []).forEach(code => {
    const id = client.value.externalProviderBindings?.[code];
    if (id != null) {
      const capability = (props.catalog.sources || []).flatMap(source => source.capabilities || []).find(item => item.codeType === code);
      add(id,capability?.label || '认证能力已失效',code);
    }
  });
  return [...rows.values()];
});
const boundaryIssue = computed(()=>{
  if (!singleSourceApplication(client.value)) return '';
  if (relations.value.length > 1) return '当前应用存在多个第三方来源，请明确移除或替换为一个来源';
  if ((client.value.applicationType === 'NATIVE' || isInApp(client.value)) &&
    Object.keys(client.value.externalProviderBindings || {}).length + Object.keys(client.value.browserCapabilityBindings || client.value.browserProviderBindings || {}).length > 1)
    return '当前应用存在多个第三方登录能力，请明确保留一个';
  return '';
});
function browserName(provider) { return (props.catalog.sources || []).find(source => String(source.id) === String(client.value.browserProviderBindings?.[provider]))?.name || provider; }
async function addSources(additions) {
  const issue = sourceSelectionIssue(client.value,additions);
  if (issue) return ElMessage.warning(issue);
  const selectedIds = new Set(additions.map(item=>String(item.source.id)));
  const replaces = singleSourceApplication(client.value) && (relations.value.some(row=>!selectedIds.has(String(row.id))) ||
    (client.value.applicationType === 'NATIVE' || isInApp(client.value)) && relations.value.length > 0);
  if (replaces) {
    try { await ElMessageBox.confirm('将移除当前第三方登录关联，替换为本次所选来源与能力。IAM内置登录设置保留。','替换登录能力',{type:'warning'}); }
    catch { return; }
  }

  const browser = replaces ? {} : {...(client.value.browserProviderBindings || {})};
  const sdk = replaces ? {} : {...(client.value.externalProviderBindings || {})};
  const web = replaces ? {} : {...(client.value.browserCapabilityBindings || {})};
  // 首次使用新版能力时只等价保留旧关联，不能把旧按钮扩成全部平台能力。
  if (!replaces && client.value.browserCapabilityBindings == null) {
    for (const id of Object.values(browser)) {
      const source = (props.catalog.sources || []).find(item => String(item.id) === String(id));
      for (const item of browserCapabilities(client.value,source)) if (item.loginCapability !== 'BROWSER') web[item.loginCapability] = id;
    }
  }
  let usesCapabilities = replaces || client.value.browserCapabilityBindings != null;
  let conflict = false;
  for (const {source,capability} of additions) {
    const isWeb = browserCapabilities(client.value,source).some(item => item.loginCapability === capability);
    const map = isWeb && capability !== 'BROWSER' ? web : capability === 'BROWSER' ? browser : sdk;
    const key = capability === 'BROWSER' ? source.providerType : capability;
    if (isWeb && capability !== 'BROWSER') usesCapabilities = true;
    if (map[key] != null && String(map[key]) !== String(source.id)) conflict = true;
    map[key] = source.id;
  }
  if (conflict) {
    try { await ElMessageBox.confirm('所选能力已有其他来源，是否明确更换为本次选择？','更换身份源',{type:'warning'}); }
    catch { return; }
  }
  // 同次选择也遵守每厂商/每能力一个来源，不以列表顺序静默覆盖。
  const slots = additions.map(({source,capability}) => capability === 'BROWSER' ? 'browser:'+source.providerType : 'capability:'+capability);
  if (new Set(slots).size !== slots.length) return ElMessage.warning('同一网页厂商或认证能力只能选择一个来源，请重新选择');
  if (usesCapabilities) {
    client.value.browserCapabilityBindings = web;
    for (const key of Object.keys(browser)) delete browser[key];
    for (const id of Object.values(web)) {
      const source = (props.catalog.sources || []).find(item => String(item.id) === String(id));
      if (source) browser[source.providerType] ??= id;
    }
  }
  client.value.browserProviderBindings = browser;
  client.value.browserLoginProviderTypes = Object.keys(browser);
  if (!browser[client.value.directLoginProviderType]) client.value.directLoginProviderType = '';
  client.value.externalProviderBindings = sdk;
  client.value.externalCodeTypes = Object.keys(sdk);
  if(Object.keys(browser).length) {
    const code=client.value.protocol || 'OIDC';
    const settings=ensureProtocol(client.value,code).settings;
    const grants=new Set(csvValues(settings.authorizationGrantTypes));grants.add('authorization_code');
    if (isInApp(client.value)) grants.delete(EXTERNAL_CODE_GRANT);
    settings.authorizationGrantTypes=[...grants].join(',');settings.requireProofKey=true;
  }
  if(Object.keys(sdk).length) {
    const settings=ensureProtocol(client.value,client.value.protocol).settings;
    const grants=new Set(csvValues(settings.authorizationGrantTypes));grants.add(EXTERNAL_CODE_GRANT);
    if (isInApp(client.value)) {grants.delete('authorization_code');}
    settings.authorizationGrantTypes=[...grants].join(',');
    ElMessage.info('已按当前协议启用第三方Code登录');
  }
}
function removeSource(id) {
  if (client.value.browserCapabilityBindings != null) {
    client.value.browserCapabilityBindings = Object.fromEntries(Object.entries(client.value.browserCapabilityBindings).filter(([,value]) => String(value) !== id));
  }
  const browser = Object.fromEntries(Object.entries(client.value.browserProviderBindings || {}).filter(([,value]) => String(value) !== id));
  const sdk = Object.fromEntries(Object.entries(client.value.externalProviderBindings || {}).filter(([,value]) => String(value) !== id));
  if (client.value.browserCapabilityBindings != null) {
    for (const registration of Object.values(client.value.browserCapabilityBindings)) {
      const source = (props.catalog.sources || []).find(item => String(item.id) === String(registration));
      if (source) browser[source.providerType] ??= registration;
    }
  }
  client.value.browserProviderBindings = browser;
  client.value.browserLoginProviderTypes = Object.keys(browser);
  if (!browser[client.value.directLoginProviderType]) client.value.directLoginProviderType = '';
  client.value.externalProviderBindings = sdk;
  client.value.externalCodeTypes = Object.keys(sdk);
  if (!Object.keys(sdk).length) {const settings=client.value.settings;if(settings)settings.authorizationGrantTypes=csvValues(settings.authorizationGrantTypes).filter(grant=>grant!==EXTERNAL_CODE_GRANT).join(',');}
}
</script>
<style scoped>
.source-toolbar { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; margin-top: 28px; padding-top: 24px; border-top: 1px solid var(--el-border-color-lighter); }
.source-actions { display: flex; align-items: center; gap: 16px; flex: none; padding-top: 2px; }
@media (max-width: 760px) { .source-toolbar { flex-direction: column; gap: 0; } .source-actions { padding-bottom: 16px; } }

.hint {color:var(--el-text-color-secondary);font-size:13px;line-height:1.7;overflow-wrap:anywhere;}
.source-toolbar .source-heading {margin-top:0;}
.selected-sources {margin:20px 0;}
:deep(.el-checkbox-group) {display:flex;flex-wrap:wrap;gap:12px 24px;}
.selected-source {display:flex;justify-content:space-between;gap:16px;align-items:center;border-bottom:1px solid var(--el-border-color-lighter);padding:16px 0;}
:deep(.el-radio-group) {display:flex;flex-wrap:wrap;gap:12px 24px;}
:deep(.el-radio__label) {white-space:normal;}
</style>
