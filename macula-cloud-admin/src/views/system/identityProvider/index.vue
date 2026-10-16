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
      <el-form ref="formRef" :model="form" :rules="rules" label-width="135px">
        <el-form-item label="认证源类型" prop="providerType">
          <el-select
              v-model="form.providerType"
              :disabled="Boolean(form.id)"
              style="width: 100%"
              @change="changeType"
          >
            <el-option
                v-for="item in categoryProviders"
                :key="item.code"
                :label="item.label"
                :value="item.code"
            />
          </el-select>
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
          <span class="field-help">只读地址，供第三方平台配置；IAM 登录回调尚未接入。</span>
        </el-form-item>
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
const fields = computed(() => selectedProvider.value.fields);
const callbackUrl = computed(() => {
  const path = selectedProvider.value.callbackUrl;
  return path ? new URL(path, `${config.IAM_URL.replace(/\/$/, "")}/`).href : "";
});
const tableApi = {
  async get(params) {
    try {
      return await api.list(params);
    } catch (error) {
      ElMessage.error(error.message || "认证源加载失败");
      throw error;
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
  name: "",
  remark: "",
  config: {},
  enabled: true,
  visible: true,
});
const form = reactive(blank());
const rules = {
  name: [{required: true, message: "请输入认证源名称", trigger: "blur"}],
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
  form.config = {};
  configuredSecrets.value = {};
}

function open(row) {
  Object.assign(form, blank());
  if (row) {
    Object.assign(form, row, {
      config: {...(row.config || {})},
      category: activeCategory.value,
    });
    configuredSecrets.value = {...(row.configuredSecrets || {})};
  } else configuredSecrets.value = {};
  drawer.value = true;
}

function payload() {
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
