<template>
  <el-container class="client-page" direction="vertical" v-loading="loading">
    <template v-if="!editing">
      <el-header>
        <div class="left-panel">
          <el-button type="primary" @click="navigate('new')"
            >创建应用</el-button
          >
        </div>
        <div class="right-panel">
          <div class="right-panel-search">
            <el-input
              v-model="filter.keyword"
              placeholder="Client ID / 名称"
              clearable
              @keyup.enter="search"
              @clear="search"
            />
            <el-button type="primary" @click="search">查询</el-button>
          </div>
        </div>
      </el-header>
      <el-main class="nopadding client-list-main">
        <el-table :data="rows" height="100%" stripe empty-text="暂无接入应用"
          ><el-table-column
            prop="clientName"
            label="应用名称"
            min-width="160"
          /><el-table-column label="应用类型" min-width="130"
            ><template #default="{ row }">{{
              loginCapabilitySummary(row)
            }}</template></el-table-column
          ><el-table-column
            prop="clientId"
            label="Client ID"
            min-width="200"
            show-overflow-tooltip
          /><el-table-column
            prop="tenantId"
            label="认证租户"
            width="110"
          /><el-table-column label="操作" width="160"
            ><template #default="{ row }"
              ><el-button link type="primary" @click="navigate(row.id)"
                >配置</el-button
              ><el-popconfirm v-if="!row.managedKey" title="确认删除应用？" @confirm="remove(row)"
                ><template #reference
                  ><el-button link type="danger">删除</el-button></template
                ></el-popconfirm
              ></template
            ></el-table-column
          ></el-table
        >
      </el-main>
      <el-footer class="client-pagination">
        <el-pagination
          v-model:current-page="filter.pageNum"
          :page-size="filter.pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="load"
        />
      </el-footer>
    </template>
    <template v-else>
      <el-header class="detail-header">
        <el-button @click="navigate()">返回列表</el-button>
        <div>
          <h2>{{ creating ? "创建应用" : form.clientName || "应用配置" }}</h2>
          <p class="page-description">
            {{
              creating
                ? "选择架构类型，系统自动准备适用配置。"
                : loginCapabilitySummary(form)
            }}
          </p>
        </div>
        <el-tag class="configuration-status" :type="form.configurationStatus === 'ENABLED' ? 'success' : 'info'" effect="plain">{{ form.configurationStatus === 'ENABLED' ? '已启用' : '草稿' }}</el-tag>
        <el-tag v-if="dirty" type="warning" effect="plain">未保存</el-tag>
      </el-header>
      <nav v-if="!detailError" class="detail-tabs" aria-label="应用设置分区">
            <el-tabs
              :model-value="activeTab"
              @update:model-value="
                (tab) => router.replace({ query: { ...route.query, tab } })
              "
            >
              <el-tab-pane v-if="form.purpose!=='RESOURCE_SERVER'" label="快速开始" name="quickstart" />
              <el-tab-pane label="应用配置" name="application" />
              <el-tab-pane v-if="form.purpose!=='RESOURCE_SERVER'" label="协议配置" name="protocol" />
              <el-tab-pane v-if="form.purpose!=='RESOURCE_SERVER' && form.applicationType!=='SERVICE'" label="登录控制" name="login" />
              <el-tab-pane v-if="form.purpose!=='RESOURCE_SERVER' && form.applicationType!=='SERVICE'" label="访问授权" name="access" />
            </el-tabs>
      </nav>
      <el-main class="client-detail-main">
        <el-alert
          v-if="detailError"
          :title="detailError"
          type="error"
          :closable="false"
        /><el-button v-if="detailError" @click="loadDetail">重新加载</el-button>
        <el-alert v-if="form.configurationStatus !== 'ENABLED' && !detailError" title="应用尚未完成配置，暂不可用于认证。" type="info" :closable="false" />
        <el-form
          v-if="!detailError"
          ref="formRef"
          :disabled="saving"
          :model="form"
          :rules="rules"
          label-position="top"
          class="configuration-form"
          @submit.prevent="save(form.configurationStatus)"
        >

            <template v-if="form.purpose==='RESOURCE_SERVER'"><ResourceServerSettings v-model:client="draft" @copy="copyText" /></template>
            <template v-else-if="activeTab === 'application'"
              ><ClientApplication
                v-model:client="draft"
                :tenants="tenants"
                :original-type="original?.applicationType"
            /></template>
            <template v-else-if="activeTab === 'protocol'"
              ><ClientProtocol
                v-model:client="draft"
                :original="original" :catalog="catalog" :catalog-loaded="catalogLoaded"
                :catalog-loading="catalogLoading" :catalog-error="catalogError" @reload="loadCatalog"
                :metadata="metadata" :metadata-loading="metadataLoading" @reload-metadata="loadMetadata" @copy="copyText" @manage-sources="router.replace({query:{...route.query,tab:'login'}})" /></template>
            <ClientLoginControl
              v-else-if="activeTab === 'login'"
              v-model:client="draft"
              :catalog="catalog"
              :catalog-loaded="catalogLoaded"
              :catalog-loading="catalogLoading"
              :catalog-error="catalogError"
              @reload="loadCatalog"
            />
            <ClientAccessAuthorization v-else-if="activeTab === 'access' && form.applicationType !== 'SERVICE'" v-model:client="draft" />
            <ClientGuide
              v-else
              :client="form"
              :catalog="catalog"
              :metadata="metadata"
              @copy="copyText"
            />

        </el-form>
      </el-main>
          <el-footer v-if="!detailError && !creating" class="save-bar" height="auto">
            <span class="hint">{{
              creating
                ? "保存后应用类型不可修改"
                : "修改将在保存后生效"
            }}</span
            ><el-button
              v-if="!creating"
              :disabled="!dirty || saving"
              @click="discard"
              >放弃更改</el-button
            ><el-button :loading="saving" :disabled="!!form.managedKey" @click="save('DRAFT')">保存草稿并停用</el-button>
            <el-button type="primary" :loading="saving" @click="save('ENABLED')">保存并启用</el-button>
          </el-footer>
    </template>
    <el-dialog class="application-settings-dialog" top="5vh" :model-value="creating" title="创建应用" width="min(1080px, 94vw)" destroy-on-close @update:model-value="value => { if (!value && creating) navigate(); }">
      <ClientCreate :client="form" @change="replaceDraft" />
      <template #footer><el-button :disabled="saving" @click="navigate()">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createDraft">创建</el-button></template>
    </el-dialog>
    <el-dialog class="application-settings-dialog" top="5vh"
      v-model="issuedOpen"
      destroy-on-close
      title="请保存本次客户端密钥"
      width="min(560px, 94vw)"
      @closed="issuedSecret = ''"
    >
      <p>密钥只在本次操作展示。关闭后无法读取，可通过重置生成新密钥。</p>
      <el-input
        :model-value="issuedSecret"
        type="password"
        show-password
        readonly
      /><el-button @click="copyText(issuedSecret)">复制密钥</el-button
      ><template #footer
        ><el-button @click="issuedOpen = false"
          >已保存，关闭</el-button
        ></template
      >
    </el-dialog>
  </el-container>
</template>
<script>
export default { name: "oauth2Client" };
</script>
<script setup>
import { ensureProtocol, protocolDefaults } from "@/utils/applicationProtocolRegistry";
import {
  reactive,
  ref,
  computed,
  watch,
  onBeforeUnmount,
  onDeactivated,
} from "vue";
import {
  useRoute,
  useRouter,
  onBeforeRouteLeave,
  onBeforeRouteUpdate,
} from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import api, {
  oauthClientErrorMessage,
  invalidSelectedBrowserProviders,
  loginCapabilitySummary,
  providerCandidatesForCodeType,
} from "@/api/model/system/oauth2Client";
import tenantApi from "@/api/model/system/tenant";
import {
  applicationTemplate,
  clientPayload,
  configurationIssues,
} from "@/utils/oauthClientConfiguration";
import {isWebApplication} from '@/utils/identitySourceCompatibility';
import ResourceServerSettings from "./components/ResourceServerSettings.vue";
import ClientCreate from "./components/ClientCreate.vue";
import ClientApplication from "./components/ClientApplication.vue";
import ClientProtocol from "./components/ClientProtocol.vue";
import ClientLoginControl from "./components/ClientLoginControl.vue";
import ClientGuide from "./components/ClientGuide.vue";
import ClientAccessAuthorization from "./components/ClientAccessAuthorization.vue";
const route = useRoute(),
  router = useRouter();
const form = reactive({}),
  original = ref(null),
  snapshot = ref("");
const rows = ref([]),
  total = ref(0),
  tenants = ref([]),
  loading = ref(false),
  saving = ref(false),
  detailError = ref("");
const filter = reactive({ keyword: "", pageNum: 1, pageSize: 20 });
const catalog = ref({ protocols: [], scopes: [], sources: [] }),
  catalogLoaded = ref(false),
  catalogLoading = ref(false),
  catalogError = ref("");
const metadata = ref({}),
  metadataLoading = ref({}),
  issuedSecret = ref(""),
  issuedOpen = ref(false),
  formRef = ref();
const editing = computed(() => !!route.query.id && route.query.id !== "new"),
  creating = computed(() => route.query.id === "new");
const activeTab = computed(() =>
  ["quickstart", "application", "protocol", "login", "access"].includes(route.query.tab)
    ? route.query.tab
    : "quickstart"
);
const dirty = computed(
  () =>
    editing.value &&
    !!snapshot.value &&
    JSON.stringify(clientPayload(form)) !== snapshot.value
);
const draft = computed({ get: () => form, set: replaceDraft });
const rules = {
  clientName: [{ required: true, message: "请输入应用名称" }],
  tenantId: [{ required: true, message: "请选择认证租户" }],
};
let requestVersion = 0,
  disposed = false;
const unwrap = (response) => {
  if (!response?.success) throw new Error(oauthClientErrorMessage(response));
  return response.data;
};
function replaceDraft(value) {
  for (const key of Object.keys(form)) delete form[key];
  Object.assign(form, value);
}
function markSaved() {
  if(form.purpose!=='RESOURCE_SERVER' && form.protocol)ensureProtocol(form,form.protocol);
  snapshot.value = JSON.stringify(clientPayload(form));
}
function search() {
  filter.pageNum = 1;
  load();
}
async function load() {
  loading.value = true;
  try {
    const result = unwrap(await api.list({ ...filter }));
    if (!disposed) {
      rows.value = result.records || [];
      total.value = Number(result.total || 0);
    }
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    loading.value = false;
  }
}
async function loadCatalog() {
  catalogLoading.value = true;
  catalogError.value = "";
  try {
    const value = unwrap(await api.configurationCatalog());
    if (!disposed) {
      catalog.value = value;
      catalogLoaded.value = true;
    }
  } catch (error) {
    catalogLoaded.value = false;
    catalogError.value = "身份源目录加载失败，请重试：" + error.message;
  } finally {
    catalogLoading.value = false;
  }
}
async function loadMetadata(protocol) {
  if (!protocol) return Promise.all(['OIDC','OAUTH2_1'].map(value=>loadMetadata(value)));
  metadataLoading.value={...metadataLoading.value,[protocol]:true};
  try {
    const value=unwrap(await api.metadata(protocol));
    if(!disposed)metadata.value={...metadata.value,[protocol]:value};
  } catch(error) {
    if(!disposed)metadata.value={...metadata.value,[protocol]:{available:false,message:'接入地址获取失败：'+error.message}};
  } finally {
    if(!disposed)metadataLoading.value={...metadataLoading.value,[protocol]:false};
  }
}

async function loadDetail() {
  const version = ++requestVersion;
  issuedSecret.value = "";
  issuedOpen.value = false;
  detailError.value = "";
  snapshot.value = "";
  replaceDraft({});
  original.value = null;
  if (!route.query.id) {
    await load();
    return;
  }
  if (creating.value) {
    try {
      replaceDraft(
        applicationTemplate(
          {

            clientName: "",
            tenantId: null,
            userType: "employee",
            allowedOrigins: [],
            scopes: "openid",
            authorizationCodeTimeToLive: 300,
            accessTokenTimeToLive: 3600,
            tokenFormat: "self-contained",
            reuseRefreshTokens: false,
            refreshTokenTimeToLive: 2592000,
            idTokenSignatureAlgorithm: "RS256",
            requireAuthorizationConsent: false,
          },
          "WEB_SERVER"
        )
      );
      markSaved();
    } catch (error) {
      detailError.value = error.message;
    }
  } else {
    loading.value = true;
    try {
      const value = unwrap(await api.get(route.query.id));
      if (disposed || version !== requestVersion) return;
      original.value = structuredClone(value);
      const prepared = {...value};
      if(value.purpose==='RESOURCE_SERVER') {
        prepared.settings={kind:'oauth2',clientAuthenticationMethods:'client_secret_basic',authorizationGrantTypes:'',scopes:'',signingAlgorithm:'RS256',...value.settings};
      } else if(value.protocol && value.settings && !value.settings.clientAuthenticationMethods) {
        prepared.settings={...protocolDefaults(value,value.protocol),...Object.fromEntries(Object.entries(value.settings).filter(([,item])=>item!=null))};
      }
      replaceDraft({ ...prepared, clientSecret: "" });
      markSaved();
    } catch (error) {
      if (version === requestVersion) detailError.value = error.message;
    } finally {
      if (version === requestVersion) loading.value = false;
    }
    loadMetadata();
  }
}
async function confirmLeave() {
  if (saving.value) return false;
  if (!dirty.value) return true;
  try {
    await ElMessageBox.confirm(
      "存在未保存的配置，离开将丢弃更改。",
      "未保存更改",
      { type: "warning" }
    );
    return true;
  } catch {
    return false;
  }
}
onBeforeRouteLeave(confirmLeave);
onBeforeRouteUpdate((to, from) =>
  String(to.query.id || "") !== String(from.query.id || "")
    ? confirmLeave()
    : true
);
function navigate(id) {
  router.push({ query: id ? { id: String(id), tab: "application" } : {} });
}
async function discard() {
  if (await confirmLeave()) {
    replaceDraft({ ...structuredClone(original.value), clientSecret: "" });
    markSaved();
  }
}
async function copyText(value) {
  try {
    if (!navigator.clipboard?.writeText)
      throw new Error("当前环境无法复制，请在 HTTPS 或 localhost 下操作");
    await navigator.clipboard.writeText(value);
    ElMessage.success("已复制");
  } catch (error) {
    ElMessage.error(error.message || "复制失败，请手动复制");
  }
}
async function createDraft() {
  if (saving.value || !form.clientName?.trim()) return ElMessage.warning('请输入应用名称');
  saving.value = true;
  try {
    const payload = {clientName:form.clientName.trim(),applicationType:form.applicationType,miniappPlatform:form.miniappPlatform,purpose:form.purpose};
    if (form.purpose !== 'RESOURCE_SERVER' && isWebApplication(form)) payload.webLoginMode = form.webLoginMode ?? 'BROWSER';
    const value = unwrap(await api.createDraft(payload));
    // 创建请求已提交完成，允许现有离开守卫进入新详情。
    saving.value = false;
    await router.replace({query:{id:String(value.id),tab:'quickstart'}});
  } catch (error) { ElMessage.error(oauthClientErrorMessage(error?.data,error?.message || '创建失败')); }
  finally { saving.value = false; }
}
async function save(status = form.configurationStatus) {
  if (saving.value) return;
  if (!form.clientName?.trim()) return ElMessage.error('请输入应用名称');
  if (status === 'ENABLED') {
    if (!(await formRef.value.validate().catch(() => false))) return;
    const issue = configurationIssues(form, original.value)[0];
    if (issue) return ElMessage.error(issue);
    if (form.purpose!=='RESOURCE_SERVER' && form.applicationType!=='SERVICE' && !form.userType) return ElMessage.error('请在访问授权中选择用户类型');
  }
  if (
    form.externalCodeTypes?.length &&
    (!catalogLoaded.value ||
      form.externalCodeTypes.some(
        (code) =>
          !providerCandidatesForCodeType(code, catalog.value).some(
            (provider) =>
              String(provider.id) ===
              String(form.externalProviderBindings?.[code])
          )
      ))
  )
    return ElMessage.error("请为每种外部凭证选择启用的兼容身份源");
  if (
    form.browserLoginProviderTypes?.length &&
    (!catalogLoaded.value ||
      invalidSelectedBrowserProviders(form, catalog.value).length)
  )
    return ElMessage.error(
      "请为每个网页登录方式选择具体的启用身份源"
    );
  saving.value = true;
  try {
    const wasCreating = creating.value,
      secret = form.clientSecret || "";
    const value = unwrap(
      await (wasCreating
        ? api.create(clientPayload(form))
        : api.update(form.id, clientPayload({...form,configurationStatus:status})))
    );
    original.value = structuredClone(value);
    replaceDraft({ ...value, clientSecret: "" });
    markSaved();
    ElMessage.success("保存成功");
    saving.value = false;
    if (wasCreating) {
      await router.replace({
        query: { id: String(value.id), tab: "application" },
      });
      // 路由加载完成后一次性展示本次生成的原文；普通详情始终无法读取。
    }
    if (secret && value.secretConfigured) {
      issuedSecret.value = secret;
      issuedOpen.value = true;
    }
  } catch (error) {
    ElMessage.error(
      oauthClientErrorMessage(error?.data, error?.message || "保存失败")
    );
  } finally {
    saving.value = false;
  }
}
async function remove(row) {
  try {
    unwrap(await api.remove(row.id, row.configurationVersion));
    ElMessage.success("删除成功");
    await load();
  } catch (error) {
    ElMessage.error(error.message);
  }
}
function beforeUnload(event) {
  if (dirty.value) {
    event.preventDefault();
    event.returnValue = "";
  }
}
window.addEventListener("beforeunload", beforeUnload);
onDeactivated(() => {
  issuedSecret.value = "";
  issuedOpen.value = false;
  form.clientSecret = "";
});
onBeforeUnmount(() => {
  disposed = true;
  requestVersion++;
  issuedSecret.value = "";
  form.clientSecret = "";
  window.removeEventListener("beforeunload", beforeUnload);
});
watch(() => route.query.id, loadDetail, { immediate: true });
loadCatalog();
tenantApi.tenant.options
  .get({ filterMe: 0 })
  .then((response) => {
    const value = unwrap(response);
    if (!disposed) tenants.value = value || [];
  })
  .catch((error) => ElMessage.error("租户目录加载失败：" + error.message));
</script>
<style scoped>
:global(.application-settings-dialog) { display: flex; flex-direction: column; max-height: 90dvh; }
:global(.application-settings-dialog > .el-dialog__header),
:global(.application-settings-dialog > .el-dialog__footer) { flex: 0 0 auto; }
:global(.application-settings-dialog > .el-dialog__body) { min-height: 0; overflow-y: auto; }

.client-page.el-container {
  display: flex;
  flex-direction: column;
  max-width: 100%;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: var(--el-bg-color);
}
.client-list-main {
  min-height: 0;
  overflow: hidden;
}
.client-pagination {
  display: flex;
  align-items: center;
  flex: none;
}
.client-page > .detail-header {
  height: auto;
  min-height: 80px;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 32px;
  flex: none;
}
.detail-tabs { flex: none; padding: 0 32px; border-bottom: 1px solid var(--el-border-color-lighter); min-width: 0; }
.detail-tabs :deep(.el-tabs__header) { margin: 0; }
.detail-tabs :deep(.el-tabs__nav-wrap::after) { display: none; }
.detail-tabs :deep(.el-tabs__item) { height: 48px; padding: 0 20px; font-size: 14px; font-weight: 500; }
.detail-tabs :deep(.el-tabs__item:nth-child(2)) { padding-left: 0; }
.detail-header > div {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
}
h2 {
  margin: 0 0 4px;
  font-size: 18px;
}
.page-description,
.hint {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}
.client-detail-main {
  flex: 1 1 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 0;
  min-width: 0;
  min-height: 0;
  background: var(--el-bg-color);
}
.configuration-form {
  min-width: 0;
  padding: 24px 32px 40px;
  width: 100%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}
:deep(.el-form-item__content) {
  min-width: 0;
  gap: 8px;
}
:deep(.el-select) {
  min-width: 0;
  width: 100%;
  max-width: 640px;
}
:deep(.el-form-item__label) {
  font-weight: 500;
}
:deep(.el-alert) {
  margin-bottom: 16px;
}
:deep(.el-collapse) {
  margin: 16px 0 24px;
}
.client-page > .save-bar {
  margin-top: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  border-top: 1px solid var(--el-border-color-light);
  flex: 0 0 auto;
  padding: 14px 32px;
  box-shadow: 0 -2px 8px rgb(0 0 0 / 3%);
  z-index: 2;
  background: var(--el-bg-color);
}
.save-bar .hint {
  margin: 0 auto 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
:deep(.el-tabs), :deep(.el-tabs__content), :deep(.el-tab-pane) { min-width: 0; max-width: 100%; }
:deep(.el-form-item) { margin-bottom: 20px; }
:deep(.el-form-item__content > p.hint) { flex-basis: 100%; margin: 8px 0 0; }
:deep(.configuration-panel) { min-width: 0; padding: 0; }
.configuration-form > * { width: 100%; max-width: 1120px; box-sizing: border-box; }
:deep(.el-form-item__label) { font-size: 13px; line-height: 20px; color: var(--el-text-color-regular); padding-bottom: 8px; }
:deep(.configuration-panel > .el-form-item > .el-form-item__content > .el-input) { max-width: 640px; }
:deep(.el-input-number) { width: 180px; max-width: 100%; }
:deep(.token-policy .el-form-item) { align-self: start; }
:deep(.form-grid) { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 32px; }
:deep(.form-grid > *) { min-width: 0; }
:deep(.section-heading) { margin: 0 0 20px; }
:deep(.section-heading h3) { margin: 0 0 6px; font-size: 15px; font-weight: 600; line-height: 24px; }
:deep(.section-heading p) { max-width: 760px; margin: 0; font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.7; }
:deep(.el-radio-group), :deep(.el-checkbox-group) { gap: 8px 24px; }
@media (max-width: 1000px) {
 :deep(.form-grid) { grid-template-columns: minmax(0, 1fr); }
}
@media (max-width: 600px) {
  .configuration-form { padding: 20px 16px 28px; }
  .detail-tabs { padding: 0 16px; }
  .client-page > .detail-header { padding: 16px; gap: 12px; }
  .detail-header > div { flex-basis: 50%; }
  .detail-header .el-tag { margin-left: 0; }
  .client-page > .save-bar { padding: 12px 16px; gap: 8px; display: grid; grid-template-columns: auto auto auto; }
  .save-bar .hint { grid-column: 1 / -1; margin: 0; }
  .client-page > .save-bar > .el-button { margin: 0; padding: 8px 10px; min-width: 0; }
  .detail-header,
  .save-bar {
    flex-wrap: wrap;
  }
  :deep(.el-form-item__content > .el-input),
  :deep(.el-select) {
    width: 100%;
    min-width: 0;
  }
  :deep(.el-tabs__item) {
    padding: 0 12px;
  }
}
</style>
