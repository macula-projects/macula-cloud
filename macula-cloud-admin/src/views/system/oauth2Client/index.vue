<template>
  <div class="client-page" v-loading="loading">
    <h2>{{ $t("adminSettings.oauth") }}</h2>
    <el-alert
      :title="$t('adminSettings.oauthWarning')"
      type="info"
      :closable="false"
    />
    <div class="toolbar">
      <el-input
        v-model="filter.keyword"
        placeholder="Client ID / 名称"
        clearable
        @keyup.enter="load"
      /><el-button @click="load">查询</el-button
      ><el-button type="primary" @click="open()">新增接入应用</el-button>
    </div>
    <el-table :data="rows" empty-text="暂无接入应用" stripe>
      <el-table-column prop="clientName" label="应用名称" />
      <el-table-column
        prop="clientId"
        label="Client ID"
        show-overflow-tooltip
      />
      <el-table-column prop="tenantId" label="租户 ID" width="100" />
      <el-table-column prop="userType" label="用户类型" width="100" />
      <el-table-column label="登录能力" min-width="260">
        <template #default="{ row }">{{
          loginCapabilitySummary(row)
        }}</template>
      </el-table-column>
      <el-table-column label="PKCE" width="80"
        ><template #default="{ row }">{{
          row.requireProofKey ? "是" : "否"
        }}</template></el-table-column
      >
      <el-table-column label="密钥" width="90"
        ><template #default="{ row }">{{
          row.secretConfigured ? "已配置" : "无"
        }}</template></el-table-column
      >
      <el-table-column label="操作" width="220"
        ><template #default="{ row }">
          <el-button link type="primary" @click="open(row)">编辑</el-button>
          <el-button
            link
            type="primary"
            :disabled="
              !row.clientAuthenticationMethods.includes('client_secret_')
            "
            @click="resetSecret(row)"
            >重置密钥</el-button
          >
          <el-popconfirm title="确认删除客户端？" @confirm="remove(row)"
            ><template #reference
              ><el-button link type="danger">删除</el-button></template
            ></el-popconfirm
          >
        </template></el-table-column
      >
    </el-table>
    <el-pagination
      v-model:current-page="filter.pageNum"
      v-model:page-size="filter.pageSize"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="load"
    />
    <el-drawer
      v-model="drawer"
      :title="form.id ? '编辑接入应用' : '新增接入应用'"
      size="min(800px, 100vw)"
      direction="rtl"
      @closed="reset"
    >
      <el-form ref="formRef" :model="form" label-width="180px" :rules="rules">
        <el-form-item label="应用名称" prop="clientName"
          ><el-input v-model="form.clientName"
        /></el-form-item>
        <el-form-item label="Client ID" prop="clientId"
          ><el-input v-model="form.clientId" :disabled="!!form.id"
        /></el-form-item>
        <el-form-item label="所属租户" prop="tenantId"
          ><el-select v-model="form.tenantId" filterable style="width: 100%"
            ><el-option
              v-for="item in tenants"
              :key="item.value"
              :label="item.label"
              :value="item.value" /></el-select
        ></el-form-item>
        <el-form-item label="用户类型" prop="userType"
          ><el-select v-model="form.userType"
            ><el-option
              v-for="item in userTypes"
              :key="item.value"
              :label="item.label"
              :value="item.value" /></el-select
        ></el-form-item>
        <el-divider content-position="left">登录能力</el-divider>
        <el-alert
          title="先选择终端，再选择该终端允许的登录方式。底层协议参数由系统自动配置。"
          type="info"
          :closable="false"
          show-icon
        />
        <el-form-item label="终端类型">
          <el-segmented
            v-model="activeTerminal"
            :options="terminalOptions"
            @change="terminalChanged"
          />
        </el-form-item>
        <div class="terminal-description">
          {{ activeTerminalDefinition.description }}
        </div>
        <el-form-item label="允许的登录方式">
          <div class="login-method-grid">
            <el-checkbox
              v-for="method in activeLoginMethods"
              :key="method.value"
              :model-value="isLoginMethodSelected(method)"
              :disabled="loginMethodState(method).disabled"
              :data-login-method="method.value"
              border
              @change="(selected) => toggleLoginMethod(method, selected)"
            >
              <span class="login-method-label">{{ method.label }}</span>
              <small>{{ loginMethodState(method).hint }}</small>
            </el-checkbox>
          </div>
        </el-form-item>
        <template
          v-for="method in selectedExternalMethodsForActiveTerminal"
          :key="method.value"
        >
          <el-form-item
            v-if="providerCandidates(method).length > 1"
            :label="`${method.label}身份源`"
          >
            <el-select
              v-model="form.externalProviderBindings[method.externalCodeType]"
              placeholder="请选择身份源"
              style="width: 100%"
            >
              <el-option
                v-for="provider in providerCandidates(method)"
                :key="provider.id"
                :label="provider.name"
                :value="provider.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item
            v-else-if="providerCandidates(method).length === 1"
            :label="`${method.label}身份源`"
          >
            <span class="auto-binding"
              >{{ providerCandidates(method)[0]?.name }}（自动绑定）</span
            >
          </el-form-item>
          <el-alert
            v-else
            :title="`${method.label}缺少可用身份源，请先完成身份源配置`"
            type="error"
            :closable="false"
            show-icon
          />
        </template>
        <el-form-item
          v-if="requiresAuthorizationCode"
          label="重定向 URI"
          prop="redirectUris"
        >
          <el-input
            v-model="form.redirectUris"
            type="textarea"
            placeholder="多个地址用逗号分隔"
          />
        </el-form-item>
        <el-form-item
          v-if="selectedBrowserLoginProviders.length"
          label="进入登录页后"
        >
          <el-select
            v-model="form.directLoginProviderType"
            clearable
            placeholder="展示可选登录方式"
            style="width: 100%"
          >
            <el-option
              v-for="provider in selectedBrowserLoginProviders"
              :key="provider.value"
              :label="`直接进入${provider.label}认证`"
              :value="provider.value"
            />
          </el-select>
        </el-form-item>
        <el-collapse class="advanced-settings">
          <el-collapse-item title="高级协议配置" name="protocol">
            <el-alert
              title="仅用于兼容特殊接入。通常无需直接修改 grant、code type 或 Token 参数。"
              type="warning"
              :closable="false"
              show-icon
            />
            <el-form-item label="认证方式"
              ><el-select
                v-model="form.clientAuthenticationMethods"
                style="width: 100%"
                @change="authChanged"
                ><el-option label="公共客户端（none）" value="none" /><el-option
                  label="client_secret_basic"
                  value="client_secret_basic" /><el-option
                  label="client_secret_post"
                  value="client_secret_post" /><el-option
                  label="private_key_jwt"
                  value="private_key_jwt" /></el-select
            ></el-form-item>
            <el-form-item
              v-if="
                form.clientAuthenticationMethods.startsWith('client_secret_') &&
                !form.id
              "
              label="客户端密钥"
              prop="clientSecret"
              ><el-input
                v-model="form.clientSecret"
                type="password"
                show-password
                autocomplete="new-password"
                placeholder="至少 16 个字符，仅创建时传输"
            /></el-form-item>
            <el-form-item label="授权方式"
              ><el-input
                v-model="form.authorizationGrantTypes"
                placeholder="authorization_code,refresh_token"
            /></el-form-item>
            <el-form-item v-if="!requiresAuthorizationCode" label="重定向 URI">
              <el-input
                v-model="form.redirectUris"
                type="textarea"
                placeholder="多个地址用逗号分隔"
              />
            </el-form-item>
            <el-form-item label="Scopes"
              ><el-input v-model="form.scopes" placeholder="openid,profile"
            /></el-form-item>
            <el-form-item
              v-if="compatibleBrowserCodeTypes.length"
              label="浏览器兼容凭证"
            >
              <el-checkbox-group
                v-model="form.externalCodeTypes"
                @change="externalTypesChanged"
              >
                <el-checkbox
                  v-for="item in compatibleBrowserCodeTypes"
                  :key="item.value"
                  :value="item.value"
                  >{{ item.value }}</el-checkbox
                >
              </el-checkbox-group>
            </el-form-item>
            <el-form-item
              v-for="codeType in selectedCompatibleBrowserCodeTypes"
              :key="codeType.value"
              :label="`${codeType.value} 身份源`"
            >
              <el-select
                v-model="form.externalProviderBindings[codeType.value]"
                placeholder="请选择身份源"
                style="width: 100%"
              >
                <el-option
                  v-for="provider in providersFor(codeType)"
                  :key="provider.id"
                  :label="provider.name"
                  :value="provider.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="验证码允许 Origin">
              <el-input
                v-model="form.allowedOriginsText"
                type="textarea"
                :rows="3"
                placeholder="每行一个精确 origin，例如 https://app.example.com"
              />
            </el-form-item>
            <el-form-item label="强制 PKCE"
              ><el-switch
                v-model="form.requireProofKey"
                :disabled="form.clientAuthenticationMethods === 'none'"
              /><span class="hint">公共客户端必须使用 S256</span></el-form-item
            >
            <el-form-item label="授权确认"
              ><el-switch v-model="form.requireAuthorizationConsent"
            /></el-form-item>
            <el-form-item label="JWK URL"
              ><el-input v-model="form.jwkSetUrl"
            /></el-form-item>
            <el-form-item label="认证签名算法"
              ><el-input
                v-model="form.signingAlgorithm"
                placeholder="仅 JWT 客户端认证使用"
            /></el-form-item>
            <el-form-item label="授权码 TTL（秒）"
              ><el-input-number
                v-model="form.authorizationCodeTimeToLive"
                :min="30"
                :max="3600"
            /></el-form-item>
            <el-form-item label="Access Token TTL（秒）"
              ><el-input-number
                v-model="form.accessTokenTimeToLive"
                :min="60"
                :max="86400"
            /></el-form-item>
            <el-form-item label="Refresh Token TTL（秒）"
              ><el-input-number
                v-model="form.refreshTokenTimeToLive"
                :min="300"
                :max="31536000"
            /></el-form-item>
            <el-form-item label="Token 格式"
              ><el-select v-model="form.tokenFormat"
                ><el-option
                  label="self-contained"
                  value="self-contained" /><el-option
                  label="reference"
                  value="reference" /></el-select
            ></el-form-item>
            <el-form-item label="复用 Refresh Token"
              ><el-switch v-model="form.reuseRefreshTokens"
            /></el-form-item>
            <el-form-item label="ID Token 签名算法"
              ><el-select v-model="form.idTokenSignatureAlgorithm"
                ><el-option label="RS256" value="RS256" /><el-option
                  label="RS384"
                  value="RS384" /><el-option
                  label="RS512"
                  value="RS512" /></el-select
            ></el-form-item>
          </el-collapse-item>
        </el-collapse>
      </el-form>
      <template #footer
        ><el-button @click="drawer = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save"
          >保存</el-button
        ></template
      >
    </el-drawer>
  </div>
</template>

<script>
export default { name: "OAuth2ClientPage" };
</script>
<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import api, {
  AUTHORIZATION_CODE_GRANT,
  BROWSER_LOGIN_PROVIDERS,
  CLIENT_TERMINALS,
  COMMON_EXTERNAL_CODE_TYPES,
  EXTERNAL_CODE_GRANT,
  TERMINAL_LOGIN_METHODS,
  invalidSelectedBrowserProviders,
  loginCapabilitySummary,
  providerCandidatesForCodeType,
  reconcileExternalProviderBindings,
  terminalForClient,
  unsupportedExternalCodeTypes as findUnsupportedExternalCodeTypes,
  updateLoginMethodConfiguration,
} from "@/api/model/system/oauth2Client";
import tenantApi from "@/api/model/system/tenant";

const userTypes = [
  { value: "member", label: "会员" },
  { value: "employee", label: "行政员工" },
  { value: "odc", label: "外包人员" },
  { value: "supplier", label: "供应商" },
];
const blank = () => ({
  id: null,
  clientId: "",
  clientName: "",
  clientSecret: "",
  clientAuthenticationMethods: "none",
  authorizationGrantTypes: "authorization_code,refresh_token",
  redirectUris: "",
  scopes: "openid",
  externalCodeTypes: [],
  externalProviderBindings: {},
  allowedOrigins: [],
  allowedOriginsText: "",
  browserLoginProviderTypes: [],
  directLoginProviderType: "",
  requireProofKey: true,
  requireAuthorizationConsent: false,
  jwkSetUrl: "",
  signingAlgorithm: "",
  authorizationCodeTimeToLive: 300,
  accessTokenTimeToLive: 3600,
  tokenFormat: "self-contained",
  reuseRefreshTokens: true,
  refreshTokenTimeToLive: 2592000,
  idTokenSignatureAlgorithm: "RS256",
  userType: "employee",
  tenantId: null,
});
const form = reactive(blank()),
  filter = reactive({ keyword: "", pageNum: 1, pageSize: 20 });
const rows = ref([]),
  total = ref(0),
  tenants = ref([]),
  loading = ref(false),
  saving = ref(false),
  drawer = ref(false),
  formRef = ref(),
  activeTerminal = ref("BROWSER"),
  terminalAutoSelected = ref(true),
  catalogLoaded = ref(false);
const externalCatalog = ref({ codeTypes: [], providerRegistrations: [] });
const browserLoginProviders = BROWSER_LOGIN_PROVIDERS;
const terminalOptions = CLIENT_TERMINALS.map((terminal) => ({
  value: terminal.value,
  label: terminal.label,
}));
const activeTerminalDefinition = computed(() =>
  CLIENT_TERMINALS.find((terminal) => terminal.value === activeTerminal.value)
);
const activeLoginMethods = computed(
  () => TERMINAL_LOGIN_METHODS[activeTerminal.value] || []
);
const selectedBrowserLoginProviders = computed(() =>
  browserLoginProviders.filter((provider) =>
    form.browserLoginProviderTypes.includes(provider.value)
  )
);
const requiresAuthorizationCode = computed(() =>
  grantTypes().has(AUTHORIZATION_CODE_GRANT)
);
const selectedExternalMethodsForActiveTerminal = computed(() =>
  activeLoginMethods.value.filter(
    (method) => method.externalCodeType && isLoginMethodSelected(method)
  )
);
const compatibleBrowserCodeTypes = computed(() =>
  (externalCatalog.value.codeTypes || []).filter(
    (item) =>
      item.scene === "BROWSER" &&
      !COMMON_EXTERNAL_CODE_TYPES.includes(item.value)
  )
);
const selectedCompatibleBrowserCodeTypes = computed(() =>
  compatibleBrowserCodeTypes.value.filter((item) =>
    form.externalCodeTypes.includes(item.value)
  )
);
const unsupportedExternalCodeTypes = computed(() => {
  return findUnsupportedExternalCodeTypes(
    form,
    externalCatalog.value,
    catalogLoaded.value
  );
});
const invalidBrowserProviders = computed(() =>
  invalidSelectedBrowserProviders(form, externalCatalog.value)
);
const rules = {
  clientName: [{ required: true, message: "请输入应用名称" }],
  clientId: [{ required: true, message: "请输入 Client ID" }],
  tenantId: [{ required: true, message: "请选择租户" }],
  userType: [{ required: true, message: "请选择用户类型" }],
  clientSecret: [{ required: true, message: "请输入密钥" }],
  redirectUris: [
    {
      validator: (_rule, value, callback) => {
        if (requiresAuthorizationCode.value && !value?.trim()) {
          callback(new Error("使用 IAM 授权登录时必须配置重定向 URI"));
          return;
        }
        callback();
      },
      trigger: "blur",
    },
  ],
};
const unwrap = (res) => {
  if (!res?.success) throw new Error(res?.msg || res?.cause || "操作失败");
  return res.data;
};
async function load() {
  loading.value = true;
  try {
    const data = unwrap(await api.list({ ...filter }));
    rows.value = data.records || [];
    total.value = Number(data.total || 0);
  } catch (error) {
    rows.value = [];
    ElMessage.error(error.message);
  } finally {
    loading.value = false;
  }
}
async function loadTenants() {
  try {
    tenants.value =
      unwrap(await tenantApi.tenant.options.get({ filterMe: 0 })) || [];
  } catch (error) {
    ElMessage.error(error.message);
  }
}
async function loadExternalCatalog() {
  try {
    externalCatalog.value = unwrap(await api.externalCodeCatalog()) || {
      codeTypes: [],
      providerRegistrations: [],
    };
    catalogLoaded.value = true;
    reconcileBindings();
    if (drawer.value && terminalAutoSelected.value) {
      activeTerminal.value = terminalForClient(form, externalCatalog.value);
    }
  } catch (error) {
    catalogLoaded.value = false;
    ElMessage.error(error.message);
  }
}
function reset() {
  Object.assign(form, blank());
  activeTerminal.value = "BROWSER";
  terminalAutoSelected.value = true;
  formRef.value?.clearValidate();
}
function authChanged(value) {
  if (value === "none") form.requireProofKey = true;
  if (!value.startsWith("client_secret_")) form.clientSecret = "";
}
function open(row) {
  const value = row || {};
  Object.assign(form, blank(), value, {
    clientSecret: "",
    externalCodeTypes: [...(value.externalCodeTypes || [])],
    externalProviderBindings: { ...(value.externalProviderBindings || {}) },
    allowedOrigins: [...(value.allowedOrigins || [])],
    allowedOriginsText: (value.allowedOrigins || []).join("\n"),
    browserLoginProviderTypes: [...(value.browserLoginProviderTypes || [])],
    directLoginProviderType: value.directLoginProviderType || "",
  });
  reconcileBindings();
  terminalAutoSelected.value = true;
  activeTerminal.value = terminalForClient(value, externalCatalog.value);
  drawer.value = true;
}
function terminalChanged() {
  terminalAutoSelected.value = false;
}
function externalTypesChanged(values) {
  const selected = new Set(values);
  for (const codeType of Object.keys(form.externalProviderBindings)) {
    if (!selected.has(codeType)) delete form.externalProviderBindings[codeType];
  }
  const grants = new Set(
    form.authorizationGrantTypes
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean)
  );
  if (values.length) grants.add(EXTERNAL_CODE_GRANT);
  else grants.delete(EXTERNAL_CODE_GRANT);
  form.authorizationGrantTypes = [...grants].join(",");
  reconcileBindings();
}
function grantTypes() {
  return new Set(
    form.authorizationGrantTypes
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean)
  );
}
function providersFor(codeType) {
  return (externalCatalog.value.providerRegistrations || []).filter(
    (provider) =>
      provider.providerType === codeType.providerType &&
      (codeType.capability === "DEFAULT" ||
        provider.capabilities?.includes(codeType.capability))
  );
}
function providerCandidates(method) {
  return providerCandidatesForCodeType(
    method.externalCodeType,
    externalCatalog.value
  );
}
function browserProviderCandidates(providerType) {
  return (externalCatalog.value.providerRegistrations || []).filter(
    (provider) => provider.providerType === providerType
  );
}
function isLoginMethodSelected(method) {
  if (method.authorizationCode) {
    return grantTypes().has(AUTHORIZATION_CODE_GRANT);
  }
  if (method.browserProviderType) {
    return form.browserLoginProviderTypes.includes(method.browserProviderType);
  }
  return form.externalCodeTypes.includes(method.externalCodeType);
}
function loginMethodState(method) {
  if (method.authorizationCode) {
    const requiredByWebLogin = form.browserLoginProviderTypes.length > 0;
    return {
      disabled: requiredByWebLogin,
      hint: requiredByWebLogin
        ? "当前网页登录方式依赖 IAM 授权"
        : "由 IAM 展示登录页并完成授权",
    };
  }
  if (method.browserProviderType) {
    const count = browserProviderCandidates(method.browserProviderType).length;
    if (count !== 1) {
      return {
        disabled: !isLoginMethodSelected(method),
        hint: count ? "存在多个启用身份源，请先整改" : "请先配置并启用身份源",
      };
    }
    return {
      disabled: false,
      hint: method.sharedWebCapability
        ? "与普通浏览器共享网页认证能力"
        : "由 IAM 完成网页认证",
    };
  }
  const count = providerCandidates(method).length;
  if (!count) {
    return {
      disabled: !isLoginMethodSelected(method),
      hint: "请先配置匹配的身份源",
    };
  }
  return {
    disabled: false,
    hint: count === 1 ? "身份源将自动绑定" : "选择后需指定身份源",
  };
}
function toggleLoginMethod(method, selected) {
  Object.assign(
    form,
    updateLoginMethodConfiguration(
      form,
      method,
      selected,
      method.externalCodeType ? providerCandidates(method) : []
    )
  );
}
function reconcileBindings() {
  form.externalProviderBindings = reconcileExternalProviderBindings(
    form,
    externalCatalog.value
  );
}
async function save() {
  if (saving.value) return;
  saving.value = true;
  try {
    if (!(await formRef.value.validate().catch(() => false))) return;
    if (form.clientAuthenticationMethods === "none" && !form.requireProofKey)
      return ElMessage.error("公共客户端必须启用 PKCE");
    if (requiresAuthorizationCode.value && !form.redirectUris.trim())
      return ElMessage.error("使用 IAM 授权登录时必须配置重定向 URI");
    if (invalidBrowserProviders.value.length) {
      const labels = BROWSER_LOGIN_PROVIDERS.filter((provider) =>
        invalidBrowserProviders.value.includes(provider.value)
      ).map((provider) => provider.label);
      return ElMessage.error(
        `网页登录方式必须且只能有一个启用身份源：${labels.join("、")}`
      );
    }
    if (unsupportedExternalCodeTypes.value.length)
      return ElMessage.error(
        `存在不受支持的历史凭证类型：${unsupportedExternalCodeTypes.value.join(
          "、"
        )}，请先迁移`
      );
    if (form.externalCodeTypes.length && !catalogLoaded.value)
      return ElMessage.error("外部凭证目录加载失败，请刷新后重试");
    if (
      form.externalCodeTypes.some(
        (codeType) => !form.externalProviderBindings[codeType]
      )
    )
      return ElMessage.error("每个外部 code type 都必须选择身份源配置");
    const data = { ...form };
    delete data.id;
    delete data.allowedOriginsText;
    data.allowedOrigins = form.allowedOriginsText
      .split(/\r?\n|,/)
      .map((item) => item.trim())
      .filter(Boolean);
    unwrap(await (form.id ? api.update(form.id, data) : api.create(data)));
    ElMessage.success("保存成功");
    drawer.value = false;
    await load();
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    saving.value = false;
  }
}
async function resetSecret(row) {
  try {
    const result = await ElMessageBox.prompt(
      "输入新密钥（至少 16 个字符）",
      "重置密钥",
      {
        inputType: "password",
        inputValidator: (value) => value?.length >= 16 || "密钥至少 16 个字符",
      }
    );
    unwrap(await api.resetSecret(row.id, result.value));
    ElMessage.success("密钥已重置");
    await load();
  } catch (error) {
    if (error !== "cancel" && error !== "close") ElMessage.error(error.message);
  }
}
async function remove(row) {
  try {
    unwrap(await api.remove(row.id));
    ElMessage.success("删除成功");
    await load();
  } catch (error) {
    ElMessage.error(error.message);
  }
}
onMounted(() => {
  load();
  loadTenants();
  loadExternalCatalog();
});
</script>

<style scoped>
.client-page {
  padding: 24px;
}
.toolbar {
  display: flex;
  gap: 12px;
  margin: 20px 0;
}
.toolbar .el-input {
  width: 270px;
}
.hint {
  margin-left: 12px;
  color: #999;
}
.terminal-description {
  margin: -8px 0 16px 180px;
  color: var(--el-text-color-secondary);
}
.login-method-grid {
  display: grid;
  width: 100%;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.login-method-grid .el-checkbox {
  width: 100%;
  height: auto;
  min-height: 58px;
  margin: 0;
  padding: 10px 14px;
  align-items: flex-start;
}
.login-method-grid .el-checkbox :deep(.el-checkbox__label) {
  display: flex;
  min-width: 0;
  flex-direction: column;
  white-space: normal;
}
.login-method-label {
  color: var(--el-text-color-primary);
  font-weight: 600;
}
.login-method-grid small,
.auto-binding {
  color: var(--el-text-color-secondary);
}
.advanced-settings {
  margin-top: 20px;
}
@media (max-width: 720px) {
  .login-method-grid {
    grid-template-columns: 1fr;
  }
  .terminal-description {
    margin-left: 0;
  }
}
.el-pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>
