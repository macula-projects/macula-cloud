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
        <el-form-item label="重定向 URI"
          ><el-input
            v-model="form.redirectUris"
            type="textarea"
            placeholder="多个地址用逗号分隔"
        /></el-form-item>
        <el-form-item label="Scopes"
          ><el-input v-model="form.scopes" placeholder="openid,profile"
        /></el-form-item>
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
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import api from "@/api/model/system/oauth2Client";
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
  formRef = ref();
const rules = {
  clientName: [{ required: true, message: "请输入应用名称" }],
  clientId: [{ required: true, message: "请输入 Client ID" }],
  tenantId: [{ required: true, message: "请选择租户" }],
  userType: [{ required: true, message: "请选择用户类型" }],
  clientSecret: [{ required: true, message: "请输入密钥" }],
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
function reset() {
  Object.assign(form, blank());
  formRef.value?.clearValidate();
}
function authChanged(value) {
  if (value === "none") form.requireProofKey = true;
  if (!value.startsWith("client_secret_")) form.clientSecret = "";
}
function open(row) {
  Object.assign(form, blank(), row || {}, { clientSecret: "" });
  drawer.value = true;
}
async function save() {
  if (saving.value) return;
  saving.value = true;
  try {
    if (!(await formRef.value.validate().catch(() => false))) return;
    if (form.clientAuthenticationMethods === "none" && !form.requireProofKey)
      return ElMessage.error("公共客户端必须启用 PKCE");
    const data = { ...form };
    delete data.id;
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
.el-pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>
