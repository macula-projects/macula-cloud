<template>
  <el-container class="identity-page">
    <el-header>
      <div class="left-panel">
        <el-button type="primary" :disabled="!catalogReady" @click="open()">添加认证源</el-button>
      </div>
      <div class="right-panel">
        <div class="right-panel-search">
          <el-input
              v-model="filter.name"
              placeholder="认证源名称"
              clearable
              @clear="resetFilter"
              @keyup.enter="search"
          />
          <el-button type="primary" @click="search">查询</el-button>
        </div>
      </div>
    </el-header>
    <el-main class="nopadding identity-main">
      <template v-if="catalogReady">
      <div class="identity-tabs">
        <el-tabs
            v-model="activeCategory"
            class="category-tabs"
            @tab-change="switchCategory"
        >
          <el-tab-pane
              v-for="category in categories"
              :key="category.code"
              :label="category.label"
              :name="category.code"
          />
        </el-tabs>
        <span class="runtime-hint">{{ $t("adminSettings.identityPending") }}</span>
      </div>

      <scTable
          :key="activeCategory"
          ref="tableRef"
          class="identity-table"
          :apiObj="tableApi"
          :params="{ category: activeCategory }"
          row-key="id"
          stripe
      >
        <el-table-column label="#" type="index" width="55"/>
        <el-table-column label="认证源名称" min-width="220">
          <template #default="{ row }">
            <div class="provider-cell">
              <el-icon class="provider-icon" :size="32">
                <component :is="`sc-icon-${row.providerType.toLowerCase()}`" />
              </el-icon>
              <span>{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="认证源类型" width="130">
          <template #default="{ row }">{{ provider(row.providerType).label }}</template>
        </el-table-column>
        <el-table-column label="应用类型" min-width="180">
          <template #default="{ row }"><div>{{ credentials.find(item => item.code === row.credentialType)?.label || sources.find(item => item.code === row.sourceType)?.label || '需重新配置' }}</div><small v-if="row.loginCapabilities" class="field-help">{{ row.loginCapabilities.map(capabilityLabel).join('、') || '未启用登录能力' }}</small></template>
        </el-table-column>
        <el-table-column label="首次自动绑定" min-width="180">
          <template #default="{ row }">{{ row.autoBindEnabled ? bindingLabel(row) : '关闭 · 手动绑定' }}</template>
        </el-table-column>
        <el-table-column label="密钥" width="100">
          <template #default="{ row }">
            {{
              Object.values(row.configuredSecrets || {}).some(Boolean)
                  ? "已配置"
                  : "未配置"
            }}
          </template>
        </el-table-column>
        <el-table-column label="登录页显示" width="110">
          <template #default="{ row }">{{ row.visible ? "是" : "否" }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? "已启用" : "已禁用" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" align="right" width="180">
          <template #default="{ row }">
            <el-button-group>
              <el-button text type="primary" size="small" @click="toggle(row)">{{
                  row.enabled ? "禁用" : "启用"
                }}
              </el-button>
              <el-button text type="primary" size="small" @click="open(row)"
              >修改
              </el-button
              >
              <el-popconfirm title="确认删除此认证源？" @confirm="remove(row)">
                <template #reference>
                  <el-button text type="danger" size="small">删除</el-button>
                </template>
              </el-popconfirm>
            </el-button-group>
          </template>
        </el-table-column>
      </scTable>
      </template>
      <div v-else class="catalog-state">
        <span v-if="catalogLoading">正在加载认证源类型…</span>
        <template v-else>
          <span>{{ catalogError }}</span>
          <el-button type="primary" text @click="loadCatalog">重试</el-button>
        </template>
      </div>
    </el-main>

    <el-drawer
        v-model="drawer"
        :title="form.id ? '修改认证源' : '创建认证源'"
        size="min(720px, 100vw)"
        direction="rtl"
        @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="135px" class="identity-form">
        <el-form-item label="认证源类型" prop="providerType">
          <el-radio-group v-model="form.providerType" :disabled="Boolean(form.id)" @change="changeType" class="source-radios">
            <el-radio v-for="item in categoryProviders" :key="item.code" :value="item.code">{{ item.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="credentials.length && availableCredentials.length > 1" label="应用类型" prop="credentialType" required>
          <el-radio-group v-model="form.credentialType" :disabled="Boolean(form.id)" @change="changeCredential" class="source-radios">
            <el-radio v-for="item in availableCredentials" :key="item.code" :value="item.code">{{ item.label }}</el-radio>
          </el-radio-group>
          <span class="field-help">一条身份源对应一个真实应用。微信网站、移动、公众号和小程序分别创建，各用自己的凭据。</span>
        </el-form-item>
        <el-form-item v-if="credentials.length && form.credentialType" label="登录能力">
          <el-checkbox-group v-model="form.loginCapabilities" class="source-radios">
            <el-checkbox v-for="item in selectedCredential.capabilities || []" :key="item.code" :value="item.code"
              :disabled="!item.available && !originalCapabilities.includes(item.code)">{{ item.label }}</el-checkbox>
          </el-checkbox-group>
          <span class="field-help">同一真实应用可启用多种能力，共用下面的应用凭据。<template v-if="form.providerType === 'WECOM'">企微小程序须关联此企微应用。</template><template v-if="form.providerType === 'DINGTALK' && form.loginCapabilities.includes('DINGTALK_SDK')">移动 SDK 使用同一应用的 Client ID／AppKey，回跳信息在业务客户端和钉钉后台配置。</template></span>
        </el-form-item>
        <el-form-item v-else-if="!credentials.length" label="接入类型" prop="sourceType" required>
          <el-radio-group v-model="form.sourceType" :disabled="Boolean(originalSourceType)" @change="changeSource" class="source-radios">
            <el-radio v-for="source in availableSources" :key="source.code" :value="source.code">{{ source.label }}</el-radio>
          </el-radio-group>
          <span class="field-help">各类型独立使用自身凭据；保存后类型不可修改。</span>
          <span v-if="sourceSceneHelpKey" class="field-help">{{ $t(sourceSceneHelpKey) }}</span>
        </el-form-item>
        <el-alert v-if="!sourceAvailable" type="warning" :closable="false"
          :title="selectedSource.unavailableReason || $t('identitySourceIntegration.sourceUnavailable')" />
        <p v-if="originalSourceType && ['REQUIRED','BACKGROUND'].includes(form.directoryPolicy)" class="field-help">
          {{ $t('identitySourceIntegration.legacyAdmission') }}
        </p>
        <p v-if="selectedSource.providerType === 'WECOM'" class="field-help">{{ $t('identitySourceIntegration.wecomMemberOnly') }}</p>
        <el-form-item v-if="form.organizationId || selectedSource.providerType === 'FEISHU'" :label="$t('identitySourceIntegration.organization')" required>
          <el-input v-model="form.organizationId" :disabled="Boolean(originalOrganizationId)" maxlength="255" />
          <span class="field-help">{{ $t('identitySourceIntegration.organizationHelp') }}</span>
        </el-form-item>
        <el-form-item v-if="selectedProvider.createUrl" label="创建应用">
          <a
              class="provider-create-link"
              :href="selectedProvider.createUrl"
              target="_blank"
              rel="noopener noreferrer"
          >前往{{ selectedProvider.label }}开放平台 ↗</a>
          <span class="field-help">{{ selectedProvider.createHint }}</span>
        </el-form-item>
        <el-form-item label="认证源名称" prop="name"
        >
          <el-input v-model="form.name" maxlength="100"
          />
        </el-form-item>
        <el-form-item
            v-for="field in fields"
            :key="field.key"
            :label="field.label"
            :required="field.required"
        >
          <el-input
              v-model="form.config[field.key]"
              :readonly="Boolean(originalSourceType) && selectedSource.identityFields?.includes(field.key)"
              :type="field.multiline ? 'textarea' : field.secret ? 'password' : 'text'"
              :rows="field.multiline ? 5 : undefined"
              :show-password="field.secret && !field.multiline"
              :autocomplete="field.secret ? 'new-password' : 'off'"
              :placeholder="
              field.secret && form.id
                ? '留空保留已配置凭据'
                : `请输入${field.label}`
            "
          />
          <span
              v-if="field.secret && form.id && configuredSecrets[field.key]"
              class="field-hint"
          >已配置，留空不修改</span
          >
          <span class="field-help">{{ field.help }}</span>
        </el-form-item>
        <el-form-item v-if="callbackUrl" label="回调地址">
          <span class="callback-url">{{ callbackUrl }}</span>
          <span class="field-help">只读地址，供第三方平台配置 IAM 登录回调。</span>
        </el-form-item>
        <el-divider content-position="left">首次登录账号绑定</el-divider>
        <el-form-item label="自动绑定">
          <el-switch v-model="form.autoBindEnabled" :disabled="!bindingMappings.length"
            @change="value => { form.bindingMappings = value ? [{sourceField:null,targetField:null}] : []; }" />
          <span class="field-help">仅影响尚未绑定的身份；已有有效绑定可正常登录。</span>
        </el-form-item>
        <template v-if="form.autoBindEnabled">
          <div v-for="(rule, index) in form.bindingMappings" :key="index" class="binding-rule" data-binding-rule>
            <el-form-item :label="$t('identitySourceIntegration.sourceField')" required>
              <el-select v-model="rule.sourceField" :placeholder="$t('identitySourceIntegration.selectSourceField')"
                style="width:100%" @change="rule.targetField = null">
                <el-option v-for="field in bindingSourceFields" :key="field.value" :value="field.value" :label="field.label" />
              </el-select>
            </el-form-item>
            <span class="binding-arrow" aria-hidden="true">→</span>
            <el-form-item :label="$t('identitySourceIntegration.targetField')" required>
              <el-select v-model="rule.targetField" :placeholder="$t('identitySourceIntegration.selectTargetField')" style="width:100%">
                <el-option v-for="mapping in targetMappings(rule.sourceField)" :key="mapping.targetField" :value="mapping.targetField"
                  :disabled="form.bindingMappings.some((other, position) => position !== index && other.sourceField === rule.sourceField && other.targetField === mapping.targetField)"
                  :label="`${mapping.targetLabel} (${mapping.targetField})`" />
              </el-select>
            </el-form-item>
            <el-button class="binding-remove" link type="danger" @click="form.bindingMappings.splice(index, 1)">{{$t('identitySourceIntegration.removeMapping')}}</el-button>
            <span v-if="targetMappings(rule.sourceField).find(item => item.targetField === rule.targetField)?.prerequisite"
              class="field-help binding-rule-help">{{ targetMappings(rule.sourceField).find(item => item.targetField === rule.targetField)?.prerequisite }}</span>
          </div>
          <el-form-item>
            <el-button :disabled="form.bindingMappings.length >= bindingMappings.length"
              @click="form.bindingMappings.push({sourceField:null,targetField:null})">{{$t('identitySourceIntegration.addMapping')}}</el-button>
            <span class="field-help">{{$t('identitySourceIntegration.mappingHelp')}}</span>
          </el-form-item>
        </template>
        <el-alert :closable="false" type="info" :title="selectedSource.autoBindUnavailableReason ||
          '关闭自动绑定，或本次授权未返回匹配属性、无法唯一匹配时，进入绑定页面验证已有账号。'" />
        <el-form-item label="是否显示"
        >
          <el-switch v-model="form.visible"/>
          <span class="field-hint"
          >在登录页展示认证源图标</span
          ></el-form-item
        >
        <el-form-item label="启用"
        >
          <el-switch v-model="form.enabled"
          />
        </el-form-item>
        <el-form-item label="备注"
        >
          <el-input v-model="form.remark" type="textarea" maxlength="500"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawer = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save"
        >确定
        </el-button
        >
      </template>
    </el-drawer>
  </el-container>
</template>

<script>
export default {name: "IdentityProviderPage"};
</script>
<script setup>
import {computed, onMounted, reactive, ref} from "vue";
import {ElMessage} from "element-plus";
import api from "@/api/model/system/identityProvider";
import config from "@/config";

const categories = ref([]);
const providers = ref([]);
const sources = ref([]);
const credentials = ref([]), originalCapabilities = ref([]);
const availableCredentials = computed(() => credentials.value.filter(item => item.providerType === form.providerType));
const selectedCredential = computed(() => credentials.value.find(item => item.code === form.credentialType) || {});

function changeCredential() {
  form.sourceType = selectedCredential.value.sourceType || '';
  changeSource();
  form.loginCapabilities = (selectedCredential.value.capabilities || []).filter(item => item.available).slice(0,1).map(item => item.code);
}

// 仅初始化新建/切换平台；编辑回显不能触发清空凭据、组织和绑定策略的changeCredential。
function selectOnlyCredential() {
  if (availableCredentials.value.length !== 1) return;
  form.credentialType = availableCredentials.value[0].code;
  changeCredential();
}

function capabilityLabel(value) { return credentials.value.flatMap(item => item.capabilities || []).find(item => item.code === value)?.label || value; }
const originalSourceType = ref(null);
const catalogReady = ref(false);
const catalogLoading = ref(false);
const catalogError = ref("");
const provider = (value) =>
    providers.value.find((item) => item.code === value) || {
      label: value,
      fields: [],
    };
const activeCategory = ref("");
const categoryProviders = computed(() =>
    providers.value.filter((item) => item.category === activeCategory.value)
);
const selectedProvider = computed(() => provider(form.providerType));
const availableSources = computed(() => sources.value.filter(source => source.providerType === form.providerType));
const originalOrganizationId = ref(null);
const selectedSource = computed(() => sources.value.find(source => source.code === form.sourceType) || {});
const fields = computed(() => credentials.value.length ? selectedCredential.value.fields || [] : selectedSource.value.fields || []);
// 场景说明仅解释已有来源合同，不派生新的来源值或混用平台凭据。
const sourceSceneHelpKey = computed(() => ({
  WECHAT_WEBSITE: 'identitySourceIntegration.wechatWebsiteHelp',
  WECHAT_OFFICIAL_ACCOUNT: 'identitySourceIntegration.wechatOfficialHelp',
  WECOM_WEB: 'identitySourceIntegration.wecomWebHelp',
}[form.sourceType]));
const legacyMappings = {
  MEMBER_ID_TO_USERNAME: {sourceField:'member_id',sourceLabel:'成员 ID',targetField:'username',targetLabel:'用户名'},
  EMPLOYEE_NO_TO_USERNAME: {sourceField:'employee_no',sourceLabel:'业务工号',targetField:'username',targetLabel:'用户名'},
  VERIFIED_MOBILE_TO_MOBILE: {sourceField:'mobile',sourceLabel:'手机号',targetField:'mobile',targetLabel:'手机号'},
};
const bindingMappings = computed(() => selectedSource.value.bindingMappings
  || (selectedSource.value.bindingRules || []).map(rule => ({...legacyMappings[rule.value],prerequisite:rule.prerequisite})).filter(item => item.sourceField));
const bindingSourceFields = computed(() => [...new Map(bindingMappings.value.map(item => [item.sourceField,{value:item.sourceField,label:item.sourceLabel}])).values()]);
const targetMappings = source => bindingMappings.value.filter(item => item.sourceField === source);
const sourceAvailable = computed(() => credentials.value.length ? !!form.credentialType : selectedSource.value.defaultAvailable ??
  (!selectedSource.value.flows?.length || selectedSource.value.flows.some(flow => flow.available)));
const rowMappings = row => Array.isArray(row.bindingMappings) ? row.bindingMappings.map(item => ({...item}))
  : row.bindingSourceField ? [{sourceField:row.bindingSourceField,targetField:row.bindingTargetField}]
    : legacyMappings[row.bindingRule] ? [{sourceField:legacyMappings[row.bindingRule].sourceField,targetField:legacyMappings[row.bindingRule].targetField}] : [];
const bindingLabel = row => rowMappings(row).map(rule => {
  const mapping = sources.value.flatMap(source => source.bindingMappings || [])
    .find(item => item.sourceField === rule.sourceField && item.targetField === rule.targetField);
  return mapping ? `${mapping.sourceLabel} → ${mapping.targetLabel}` : `${rule.sourceField} → ${rule.targetField}`;
}).join('；') || '需重新配置匹配字段';
const callbackUrl = computed(() => {
  const browserSource = credentials.value.length ? (selectedCredential.value.capabilities || []).some(item => item.browser && form.loginCapabilities.includes(item.code))
    : selectedSource.value.scene === "BROWSER" || selectedSource.value.code === "WECHAT_OFFICIAL_ACCOUNT";
  const path = browserSource ? selectedProvider.value.callbackUrl : null;
  return path ? new URL(path, `${config.IAM_URL.replace(/\/$/, "")}/`).href : "";
});
const tableApi = {
  async get(params) {
    try {
      const response = await api.list(params);
      // 先检查业务失败，避免通用表格读取空data时掩盖服务端错误原因。
      const page = unwrap(response);
      if (!Array.isArray(page?.records)) throw new Error("认证源列表数据无效");
      return response;
    } catch (error) {
      const message = error.message || error.data?.msg || "认证源加载失败";
      ElMessage.error(message);
      const failure = new Error(message);
      failure.statusText = message;
      throw failure;
    }
  },
};
const filter = reactive({name: ""});
const tableRef = ref();
const saving = ref(false),
    drawer = ref(false),
    formRef = ref();
const configuredSecrets = ref({});
const blank = () => ({
  id: null,
  providerType: categoryProviders.value[0]?.code || "",
  category: activeCategory.value,
  sourceType: "",
  credentialType: "",
  loginCapabilities: [],
  autoBindEnabled: false,
  bindingRule: null,
  bindingMappings: [],
  bindingSourceField: null,
  bindingTargetField: null,
  identityFlow: null,
  directoryPolicy: 'IDENTITY_ONLY',
  organizationId: null,
  oauthRedirectUri: null,
  version: null,
  name: "",
  remark: "",
  config: {},
  enabled: true,
  visible: true,
});
const form = reactive(blank());
const rules = {
  name: [{required: true, message: "请输入认证源名称", trigger: "blur"}],
  sourceType: [{required:true, message:"请选择具体接入类型", trigger:"change"}],
  providerType: [
    {required: true, message: "请选择认证源", trigger: "change"},
  ],
};
const unwrap = (res) => {
  if (!res?.success) throw new Error(res?.msg || res?.cause || "操作失败");
  return res.data;
};

async function loadCatalog() {
  catalogLoading.value = true;
  catalogError.value = "";
  try {
    const data = unwrap(await api.catalog());
    if (!Array.isArray(data?.categories) || !Array.isArray(data?.providers) ||
        !data.categories.length || !data.providers.length) {
      throw new Error("认证源类型数据无效");
    }
    categories.value = data.categories;
    providers.value = data.providers;
    sources.value = data.sources || [];
    credentials.value = data.credentials || [];
    activeCategory.value = data.categories[0].code;
    catalogReady.value = true;
  } catch (error) {
    catalogError.value = error.message || "认证源类型加载失败";
  } finally {
    catalogLoading.value = false;
  }
}

onMounted(loadCatalog);

function switchCategory() {
  filter.name = "";
}

function search() {
  tableRef.value?.upData({name: filter.name});
}

function resetFilter() {
  filter.name = "";
  search();
}

function resetForm() {
  Object.assign(form, blank());
  configuredSecrets.value = {};
  formRef.value?.clearValidate();
}

function changeType() {
  form.sourceType = "";
  form.credentialType = '';
  form.loginCapabilities = [];
  changeSource();
  selectOnlyCredential();
}

function changeSource() {
  form.identityFlow = selectedSource.value.providerType === 'FEISHU' ? null
    : selectedSource.value.flows?.filter(flow => flow.available).length === 1
      ? selectedSource.value.flows.find(flow => flow.available).value : null;
  form.directoryPolicy = 'IDENTITY_ONLY';
  form.organizationId = null;
  form.oauthRedirectUri = null;
  originalOrganizationId.value = null;
  form.autoBindEnabled = false;
  form.bindingRule = null;
  form.bindingMappings = [];
  form.bindingSourceField = null;
  form.bindingTargetField = null;
  form.config = {};
  configuredSecrets.value = {};
}

function open(row) {
  originalOrganizationId.value = row?.organizationId || null;
  originalSourceType.value = row?.sourceType || null;
  originalCapabilities.value = [...(row?.loginCapabilities || [])];
  Object.assign(form, blank());
  if (row) {
    Object.assign(form, row, {
      config: {...(row.config || {})},
      bindingMappings: rowMappings(row),
      loginCapabilities: [...(row.loginCapabilities || [])],
      category: activeCategory.value,
      bindingSourceField: row.bindingSourceField || legacyMappings[row.bindingRule]?.sourceField || null,
      bindingTargetField: row.bindingTargetField || legacyMappings[row.bindingRule]?.targetField || null,
    });
    configuredSecrets.value = row.sourceType ? {...(row.configuredSecrets || {})} : {};
    if (!form.credentialType && availableCredentials.value.length === 1) {
      form.credentialType = availableCredentials.value[0].code;
    }
  } else {
    configuredSecrets.value = {};
    selectOnlyCredential();
  }
  drawer.value = true;
}

function payload() {
  const organizationId = form.organizationId?.trim() || null;
  if (form.enabled && !sourceAvailable.value) throw new Error('该接入方式尚待核实，只能禁用保存');
  if (form.enabled && selectedSource.value.providerType === 'FEISHU' && !String(form.organizationId || '').trim()) {
    throw new Error('请配置固定组织标识');
  }
  if (form.organizationId && form.config.corpId && form.organizationId !== form.config.corpId) {
    throw new Error('组织标识必须与该应用的企业标识一致');
  }
  if (!form.sourceType) throw new Error("请选择应用类型");
  const mappings = form.autoBindEnabled ? form.bindingMappings : [];
  if (form.autoBindEnabled && (!mappings.length || mappings.some(rule => !bindingMappings.value.some(item =>
    item.sourceField === rule.sourceField && item.targetField === rule.targetField)))) throw new Error('请选择受支持的源字段和IAM字段');
  if (new Set(mappings.map(rule => `${rule.sourceField}:${rule.targetField}`)).size !== mappings.length) throw new Error('绑定规则不能重复');
  const config = {};
  for (const field of fields.value) {
    const value = form.config[field.key] || "";
    if (
        field.required &&
        !String(value).trim() &&
        !(field.secret && form.id && configuredSecrets.value[field.key])
    ) {
      throw new Error(`请输入${field.label}`);
    }
    if (value) config[field.key] = value;
  }
  return {
    name: form.name,
    providerType: form.providerType,
    sourceType: form.sourceType,
    ...(credentials.value.length ? {credentialType:form.credentialType,loginCapabilities:form.loginCapabilities} : {}),
    autoBindEnabled: form.autoBindEnabled,
    bindingMappings: mappings.map(rule => ({sourceField:rule.sourceField,targetField:rule.targetField})),
    organizationId,
    version: form.version,
    category: activeCategory.value,
    remark: form.remark,
    config,
    enabled: form.enabled,
    visible: form.visible,
  };
}

async function save() {
  if (saving.value) return;
  saving.value = true;
  try {
    if (!(await formRef.value.validate().catch(() => false))) return;
    const data = payload();
    unwrap(await (form.id ? api.update(form.id, data) : api.create(data)));
    ElMessage.success("保存成功");
    drawer.value = false;
    tableRef.value?.refresh();
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    saving.value = false;
  }
}

async function toggle(row) {
  try {
    unwrap(
        await api.update(row.id, {
          name: row.name,
          providerType: row.providerType,
          sourceType: row.sourceType,
          autoBindEnabled: !!row.autoBindEnabled,
          bindingMappings: row.autoBindEnabled ? rowMappings(row) : [],
          organizationId: row.organizationId,
          version: row.version,
          category: activeCategory.value,
          remark: row.remark,
          config: {...(row.config || {})},
          enabled: !row.enabled,
          visible: row.visible,
        })
    );
    ElMessage.success("状态已更新");
    tableRef.value?.refresh();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

async function remove(row) {
  try {
    unwrap(await api.remove(row.id));
    ElMessage.success("删除成功");
    tableRef.value?.refresh();
  } catch (error) {
    ElMessage.error(error.message);
  }
}

</script>

<style scoped>
.identity-form {padding:0 20px 20px;}
.identity-form :deep(.el-form-item__label) {
  height: auto;
  min-height: 32px;
  line-height: 22px;
  padding-top: 5px;
  overflow-wrap: anywhere;
}
.identity-form :deep(.el-form-item__content) {min-width: 0;}
.binding-rule {
  display:grid;
  grid-template-columns:minmax(0,1fr) auto minmax(0,1fr) auto;
  align-items:start;
  gap:6px 8px;
  margin:0 0 18px 135px;
}
.binding-rule :deep(.el-form-item) {display:flex;flex-direction:column;min-width:0;margin:0;}
.binding-rule :deep(.el-form-item__label) {
  display:block;
  width:auto !important;
  height:20px;
  min-height:20px;
  line-height:20px;
  padding:0;
  margin-bottom:4px;
  text-align:left;
  white-space:nowrap;
  font-size:12px;
}
.binding-rule :deep(.el-form-item__content) {margin-left:0 !important;width:100%;}
.binding-arrow {margin-top:24px;line-height:32px;color:var(--el-text-color-secondary);}
.binding-remove {margin:24px 0 0;min-height:32px;white-space:nowrap;}
.binding-rule-help {grid-column:1/-1;}
.source-radios {display:flex;flex-wrap:wrap;gap:4px 20px;width:100%;min-width:0;}
.source-radios :deep(.el-radio) {margin-right:0;height:auto;min-height:32px;max-width:100%;}
.source-radios :deep(.el-radio__label) {white-space:normal;overflow-wrap:anywhere;}
.identity-page {
  min-height: 100%;
}

.identity-main {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.identity-table {
  flex: 1;
  min-height: 0;
}

.catalog-state {
  padding: 20px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 992px) {
  .identity-main {
    display: block;
    overflow: visible;
  }
}

.identity-tabs {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 48px;
  padding: 0 15px;
  flex: none;
}

.category-tabs {
  flex: 1;
  min-width: 0;
}

.category-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.runtime-hint {
  position: absolute;
  right: 15px;
  pointer-events: none;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

@media (max-width: 600px) {
  .identity-form :deep(.el-form-item) {display:block;}
  .identity-form :deep(.el-form-item__label) {
    display:block;
    width:auto !important;
    text-align:left;
    padding:0 0 6px;
    min-height:0;
  }
  .identity-form :deep(.el-form-item__content) {margin-left:0 !important;}
  .runtime-hint {
    display: none;
  }
}

.provider-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.provider-icon {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
}

.provider-icon :deep(svg) {
  width: 32px;
  height: 32px;
}

@media (max-width:600px) {
  .binding-rule {margin-left:0;gap:6px;}
  .binding-rule :deep(.el-form-item__label) {height:20px;min-height:20px;padding:0;margin-bottom:4px;}
}

.field-hint {
  color: #909399;
  margin-left: 10px;
  font-size: 12px;
}

.provider-create-link {
  color: var(--el-color-primary);
  text-decoration: none;
}

.provider-create-link:hover {
  text-decoration: underline;
}

.field-help {
  display: block;
  width: 100%;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.callback-url {
  overflow-wrap: anywhere;
  user-select: all;
}

</style>
