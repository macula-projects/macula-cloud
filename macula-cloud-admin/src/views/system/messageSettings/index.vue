<template>
  <div class="message-page" v-loading="loading">
    <h2>{{ $t("adminSettings.message") }}</h2>
    <p>{{ $t("adminSettings.messageDescription") }}</p>
    <el-alert v-if="error" :title="error" type="error" show-icon><el-button link @click="load">{{ $t("messageSettingsPage.retry") }}</el-button></el-alert>
    <el-tabs v-model="active" @tab-change="load">
      <el-tab-pane :label="$t('adminSettings.mailTemplate')" name="MAIL_TEMPLATE" />
      <el-tab-pane :label="$t('adminSettings.mailService')" name="MAIL_PROVIDER" />
      <el-tab-pane :label="$t('adminSettings.smsService')" name="SMS_PROVIDER" />
    </el-tabs>

    <el-card v-if="active === 'MAIL_TEMPLATE'" class="mail-template-list" shadow="never">
      <el-empty v-if="!templates.length" :description="$t('messageSettingsPage.emptyTemplates')" />
      <div v-for="row in templates" :key="row.templateKey" class="mail-template-item">
        <div class="mail-template-copy">
          <div class="mail-template-title">
            {{ sceneLabel(row.templateKey) }}
            <el-tag v-if="row.overridden && row.enabled" size="small">{{ $t("messageSettingsPage.custom") }}</el-tag>
          </div>
          <div class="mail-template-description">{{ sceneDescription(row.templateKey) }}</div>
        </div>
        <div class="mail-template-actions">
          <el-button link type="primary" @click="editTemplate(row)">{{ $t("messageSettingsPage.configure") }}</el-button>
          <el-button link type="primary" @click="preview(row)">{{ $t("messageSettingsPage.preview") }}</el-button>
        </div>
      </div>
    </el-card>

    <template v-else>
      <el-card shadow="never">
        <el-form label-width="150px">
          <el-form-item :label="$t('messageSettingsPage.enableService')"><el-switch v-model="provider.enabled" @change="providerEnabledChanged" /></el-form-item>
          <template v-if="provider.enabled">
          <el-form-item :label="$t('messageSettingsPage.provider')"><el-select v-model="provider.providerType" @change="providerChanged"><el-option v-for="item in providerOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <template v-if="channel === 'MAIL'">
            <el-form-item v-if="provider.providerType === 'CUSTOM'" :label="$t('messageSettingsPage.smtpHost')"><el-input v-model="provider.endpoint" /></el-form-item>
            <el-form-item v-if="provider.providerType === 'CUSTOM'" :label="$t('messageSettingsPage.port')"><el-input-number v-model="provider.port" :min="1" :max="65535" /></el-form-item>
            <el-form-item v-if="provider.providerType === 'CUSTOM'" :label="$t('messageSettingsPage.securityMode')"><el-radio-group v-model="provider.securityMode"><el-radio label="NONE">None</el-radio><el-radio label="SSL">SSL</el-radio></el-radio-group></el-form-item>
            <el-form-item :label="$t('messageSettingsPage.mailAccount')"><el-input v-model="provider.account" /></el-form-item>
            <el-form-item :label="$t('messageSettingsPage.senderAddress')"><el-input v-model="provider.sender" /></el-form-item>
          </template>
          <template v-else>
            <el-form-item :label="provider.providerType === 'ALIYUN' ? 'AccessKey ID' : 'SecretId'"><el-input v-model="provider.account" /></el-form-item>
            <el-form-item :label="$t('messageSettingsPage.smsSignature')"><el-input v-model="provider.sender" /></el-form-item>
            <el-form-item v-if="provider.providerType === 'TENCENT'" :label="$t('messageSettingsPage.region')"><el-input v-model="provider.regionCode" placeholder="ap-guangzhou" /></el-form-item>
            <el-form-item v-if="provider.providerType === 'TENCENT'" :label="$t('messageSettingsPage.smsAppId')"><el-input v-model="provider.smsSdkAppId" /></el-form-item>
          </template>
          <el-form-item :label="channel === 'MAIL' ? $t('messageSettingsPage.password') : provider.providerType === 'ALIYUN' ? 'AccessKey Secret' : 'SecretKey'"><el-input v-model="provider.credential" type="password" show-password :placeholder="provider.credentialConfigured ? $t('messageSettingsPage.keepSecret') : ''" /></el-form-item>
          </template>
          <el-form-item><el-button type="primary" :loading="saving" :disabled="loading" @click="saveProvider">{{ $t("messageSettingsPage.save") }}</el-button></el-form-item>
        </el-form>
      </el-card>
      <template v-if="channel === 'SMS' && provider.enabled">
        <div class="sms-toolbar"><el-radio-group v-model="language" @change="loadTemplates"><el-radio-button label="zh-CN">中文</el-radio-button><el-radio-button label="en-US">English</el-radio-button></el-radio-group></div>
        <el-table v-loading="templatesLoading" :data="templates" :empty-text="$t('messageSettingsPage.emptyTemplates')" stripe>
          <el-table-column :label="$t('messageSettingsPage.scene')"><template #default="{ row }">{{ sceneLabel(row.templateKey) }}</template></el-table-column>
          <el-table-column :label="$t('messageSettingsPage.templateCode')"><template #default="{ row }"><el-input v-model="row.providerTemplateCode" /></template></el-table-column>
          <el-table-column :label="$t('messageSettingsPage.actions')" width="180"><template #default="{ row }"><el-button link type="primary" :loading="row.saving" :disabled="templatesLoading" @click="saveSmsTemplate(row)">{{ $t("messageSettingsPage.save") }}</el-button><el-button link type="primary" :disabled="!provider.enabled || templatesLoading" @click="openTest(row)">{{ $t("messageSettingsPage.test") }}</el-button></template></el-table-column>
        </el-table>
      </template>
    </template>

    <el-drawer v-model="templateDrawer" :title="templateForm.templateKey ? sceneLabel(templateForm.templateKey) : $t('messageSettingsPage.configureMail')" size="min(680px, 100vw)"><el-form label-width="110px"><el-form-item :label="$t('messageSettingsPage.customMail')"><el-switch v-model="templateForm.enabled" /></el-form-item><template v-if="templateForm.enabled"><el-form-item :label="$t('messageSettingsPage.variables')">{{ (templateForm.variables || []).join(', ') || $t('messageSettingsPage.none') }}</el-form-item><el-form-item :label="$t('messageSettingsPage.senderName')"><el-input v-model="templateForm.senderName" /></el-form-item><el-form-item :label="$t('messageSettingsPage.subject')"><el-input v-model="templateForm.title" /></el-form-item><el-form-item :label="$t('messageSettingsPage.htmlContent')"><el-input v-model="templateForm.content" type="textarea" :rows="10" /></el-form-item></template></el-form><template #footer><el-button @click="templateDrawer=false">{{ $t("messageSettingsPage.cancel") }}</el-button><el-button type="primary" :loading="saving" @click="saveMailTemplate">{{ $t("messageSettingsPage.save") }}</el-button></template></el-drawer>
    <el-dialog v-model="previewDialog" :title="previewTitle" width="min(800px, calc(100vw - 32px))" destroy-on-close><iframe :title="previewTitle" sandbox="" :srcdoc="previewHtml" class="preview-frame" /><template #footer><el-button @click="previewDialog=false">{{ $t("messageSettingsPage.cancel") }}</el-button><el-button type="primary" @click="previewDialog=false">{{ $t("messageSettingsPage.confirm") }}</el-button></template></el-dialog>
    <el-dialog v-model="testDialog" :title="$t('messageSettingsPage.testSend')" width="460px"><el-input v-model="testTarget" :placeholder="$t('messageSettingsPage.phone')" /><template #footer><el-button @click="testDialog=false">{{ $t("messageSettingsPage.cancel") }}</el-button><el-button type="primary" :loading="saving" @click="sendTest">{{ $t("messageSettingsPage.submit") }}</el-button></template></el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import i18n from "@/locales";
import zhCn from "@/locales/lang/zh-cn";
import en from "@/locales/lang/en";
import api from "@/api/model/system/messageSettings";
const t = (...args) => i18n.global.t(...args);
const active = ref("MAIL_TEMPLATE"), loading = ref(false), saving = ref(false), error = ref("");
const language = ref("zh-CN"), templates = ref([]), provider = reactive({}), templateDrawer = ref(false), templateForm = reactive({});
const loadedProviderType = ref("");
const templatesLoading = ref(false);
const previewDialog = ref(false), previewTitle = ref(""), previewHtml = ref(""), testDialog = ref(false), testTarget = ref(""), testScene = ref(null);
let templateRequestId = 0;
const channel = computed(() => active.value.startsWith("MAIL") ? "MAIL" : "SMS");
const providerOptions = computed(() => channel.value === "MAIL" ? [{ value:"CUSTOM",label:t("messageSettingsPage.customSmtp")},{value:"ALIYUN",label:t("messageSettingsPage.aliyunMail")},{value:"TENCENT",label:t("messageSettingsPage.tencentMail")},{value:"NETEASE",label:t("messageSettingsPage.neteaseMail")}] : [{value:"ALIYUN",label:t("messageSettingsPage.aliyunSms")},{value:"TENCENT",label:t("messageSettingsPage.tencentSms")}]);
const templateMessages = { "zh-CN": zhCn, "en-US": en };
const sceneLabel = (key) => channel.value === "SMS"
  ? templateMessages[language.value]?.messageSettingsPage.scenes[key] || key
  : t(`messageSettingsPage.scenes.${key}`);
const sceneDescription = (key) => t(`messageSettingsPage.sceneDescriptions.${key}`);
function unwrap(response){if(response?.success===false)throw new Error(response.cause||response.msg||t("messageSettingsPage.operationFailed"));return response&&Object.prototype.hasOwnProperty.call(response,"data")?response.data:response;}
function escapeHtml(value){return String(value??"").replace(/[&<>"']/g,character=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"})[character]);}
function mailPreviewDocument(row,content){const sender=escapeHtml(row.senderName||"Macula Cloud"),subject=escapeHtml(row.title||"");return `<!doctype html><html><head><meta charset="UTF-8"><meta name="color-scheme" content="light"><style>*{box-sizing:border-box}body{margin:0;padding:28px;background:#f3f5f8;color:#303133;font:16px/1.7 -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}.mail-preview-card{max-width:600px;margin:0 auto;overflow:hidden;border:1px solid #e4e7ed;border-radius:8px;background:#fff;box-shadow:0 8px 24px rgba(31,45,61,.12)}.mail-preview-sender{padding:22px 30px;background:#484f60;color:#fff;font-size:24px;font-weight:600}.mail-preview-subject{padding:18px 34px;border-bottom:1px solid #ebeef5;color:#303133;font-size:18px;font-weight:600}.mail-preview-body{min-height:300px;padding:34px}.mail-preview-content{word-break:break-word}.mail-preview-content>:first-child{margin-top:0}.mail-preview-content>:nth-last-child(-n+2){text-align:right}.mail-preview-content>:last-child{margin-bottom:0}.mail-preview-content strong{color:#4a8db8}</style></head><body><article class="mail-preview-card"><header class="mail-preview-sender">${sender}</header><div class="mail-preview-subject">${subject}</div><main class="mail-preview-body"><div class="mail-preview-content">${content||""}</div></main></article></body></html>`;}
async function providerChanged(){provider.credential="";if(channel.value==="MAIL"&&provider.providerType==="CUSTOM"){provider.port ||= 25;provider.securityMode ||= "NONE";}if(channel.value==="SMS"&&provider.providerType==="ALIYUN"){provider.regionCode="";provider.smsSdkAppId="";}if(channel.value==="SMS"&&provider.enabled)await loadTemplates();}
async function providerEnabledChanged(enabled){if(channel.value!=="SMS")return;if(enabled)await loadTemplates();else{templateRequestId++;templatesLoading.value=false;templates.value=[];}}
function invalid(message){ElMessage.error(message||t("messageSettingsPage.invalidForm"));return false;}
function validProvider(){if(!provider.enabled&&loadedProviderType.value)return true;if(!provider.providerType||!provider.account||!provider.sender)return invalid();if((!provider.credentialConfigured||loadedProviderType.value!==provider.providerType)&&!provider.credential)return invalid(t("messageSettingsPage.credentialRequired"));if(channel.value==="MAIL"&&provider.providerType==="CUSTOM"&&(!provider.endpoint||!provider.port||!provider.securityMode))return invalid();if(channel.value==="SMS"&&provider.providerType==="TENCENT"&&(!provider.regionCode||!provider.smsSdkAppId))return invalid();return true;}
async function load(){ loading.value=true;error.value="";try{ if(active.value==="MAIL_TEMPLATE") templates.value=unwrap(await api.templates("MAIL","","zh-CN")); else {Object.assign(provider,unwrap(await api.provider(channel.value)),{credential:""});loadedProviderType.value=provider.providerType||""; if(channel.value==="SMS") await loadTemplates();}}catch(e){error.value=e?.message||t("messageSettingsPage.loadFailed");}finally{loading.value=false;} }
async function loadTemplates(){const providerType=provider.providerType,languageCode=language.value,requestId=++templateRequestId;if(!providerType){templatesLoading.value=false;templates.value=[];return;}templatesLoading.value=true;error.value="";try{const result=unwrap(await api.templates("SMS",providerType,languageCode));if(requestId===templateRequestId&&channel.value==="SMS"&&provider.providerType===providerType&&language.value===languageCode)templates.value=result;}catch(e){if(requestId===templateRequestId&&channel.value==="SMS"){templates.value=[];error.value=e?.message||t("messageSettingsPage.loadFailed");}}finally{if(requestId===templateRequestId)templatesLoading.value=false;}}
async function saveProvider(){if(saving.value||!validProvider())return;saving.value=true;try{Object.assign(provider,unwrap(await api.saveProvider(channel.value,provider)),{credential:""});loadedProviderType.value=provider.providerType||"";ElMessage.success(t("messageSettingsPage.saveSuccess"));if(channel.value==="SMS")await loadTemplates();}finally{saving.value=false;}}
function editTemplate(row){Object.assign(templateForm,JSON.parse(JSON.stringify(row)));templateDrawer.value=true;}
async function saveMailTemplate(){if(saving.value)return;if(templateForm.enabled&&(!templateForm.senderName||!templateForm.title||!templateForm.content)){invalid();return;}saving.value=true;try{unwrap(await api.saveTemplate("MAIL",templateForm.templateKey,templateForm));templateDrawer.value=false;await load();ElMessage.success(t("messageSettingsPage.saveSuccess"));}finally{saving.value=false;}}
async function preview(row){const result=unwrap(await api.preview(row.templateKey));previewTitle.value=sceneLabel(row.templateKey);previewHtml.value=mailPreviewDocument(row,result.content);previewDialog.value=true;}
async function saveSmsTemplate(row){if(row.saving||templatesLoading.value)return;if(!row.providerTemplateCode){invalid(t("messageSettingsPage.templateCodeRequired"));return;}row.saving=true;try{unwrap(await api.saveTemplate("SMS",row.templateKey,{...row,providerType:provider.providerType,languageCode:language.value,enabled:true}));ElMessage.success(t("messageSettingsPage.saveSuccess"));}finally{row.saving=false;}}
function openTest(row){testScene.value=row;testTarget.value="";testDialog.value=true;}
async function sendTest(){if(saving.value)return;if(!/^\+?[0-9]{6,20}$/.test(testTarget.value)){invalid(t("messageSettingsPage.invalidPhone"));return;}saving.value=true;try{const variables=Object.fromEntries((testScene.value.variables||[]).map(k=>[k,k==="code"?"123456":k==="days"?"7":"Macula Cloud"]));unwrap(await api.test({scene:testScene.value.templateKey,target:testTarget.value,languageCode:language.value,variables}));testDialog.value=false;ElMessage.success(t("messageSettingsPage.submitted"));}finally{saving.value=false;}}
onMounted(load);
</script>

<style scoped>.message-page{padding:20px}.mail-template-list{margin-top:4px}.mail-template-list :deep(.el-card__body){padding:0 28px}.mail-template-item{display:flex;align-items:center;justify-content:space-between;gap:24px;padding:22px 4px;border-bottom:1px solid var(--el-border-color-lighter)}.mail-template-item:last-child{border-bottom:0}.mail-template-copy{min-width:0}.mail-template-title{display:flex;align-items:center;gap:10px;color:var(--el-text-color-primary);font-size:16px;font-weight:600}.mail-template-description{margin-top:8px;color:var(--el-text-color-secondary);line-height:1.6}.mail-template-actions{display:flex;flex:none}.sms-toolbar{margin:20px 0}.preview-frame{border:0;width:100%;height:480px;background:#fff}@media(max-width:640px){.mail-template-list :deep(.el-card__body){padding:0 16px}.mail-template-item{align-items:flex-start;flex-direction:column;gap:8px}.mail-template-actions{align-self:flex-end}}</style>
