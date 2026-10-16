<template>
  <div class="message-page" v-loading="loading">
    <h2>{{ $t("adminSettings.message") }}</h2>
    <p>{{ $t("adminSettings.messageDescription") }}</p>
    <el-tabs v-model="active" @tab-change="tabChanged">
      <el-tab-pane
        :label="$t('adminSettings.mailTemplate')"
        name="MAIL_TEMPLATE"
      />
      <el-tab-pane
        :label="$t('adminSettings.mailService')"
        name="MAIL_PROVIDER"
      />
      <el-tab-pane
        :label="$t('adminSettings.smsTemplate')"
        name="SMS_TEMPLATE"
      />
      <el-tab-pane
        :label="$t('adminSettings.smsService')"
        name="SMS_PROVIDER"
      />
    </el-tabs>
    <template v-if="isTemplate">
      <div class="toolbar">
        <el-select
          v-model="templateProviderType"
          placeholder="服务商"
          @change="load"
          style="width: 210px"
        >
          <el-option label="通用默认" value="" />
          <el-option
            v-for="option in providerTypes"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </div>
      <el-table :data="templates" empty-text="暂无模板" stripe>
        <el-table-column prop="templateKey" label="场景" width="170" />
        <el-table-column prop="title" label="标题" width="160" />
        <el-table-column prop="content" label="内容" show-overflow-tooltip />
        <el-table-column
          prop="providerTemplateCode"
          label="供应商模板编码"
          width="180"
        />
        <el-table-column label="状态" width="100"
          ><template #default="{ row }">{{
            row.enabled ? "启用" : "停用"
          }}</template></el-table-column
        >
        <el-table-column label="来源" width="90"
          ><template #default="{ row }">{{
            row.overridden ? "数据库" : "枚举默认"
          }}</template></el-table-column
        >
        <el-table-column label="操作" width="90"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="editTemplate(row)"
              >编辑</el-button
            ></template
          ></el-table-column
        >
      </el-table>
    </template>
    <template v-else>
      <div class="toolbar">
        <el-button type="primary" @click="editProvider()">添加服务</el-button>
      </div>
      <el-table :data="providers" empty-text="暂无服务配置" stripe>
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="providerType" label="服务商" />
        <el-table-column
          prop="endpoint"
          label="服务器 / API 地址"
          show-overflow-tooltip
        />
        <el-table-column label="凭据" width="95"
          ><template #default="{ row }">{{
            row.credentialConfigured ? "已配置" : "未配置"
          }}</template></el-table-column
        >
        <el-table-column label="状态" width="90"
          ><template #default="{ row }">{{
            row.enabled ? "已启用" : "已停用"
          }}</template></el-table-column
        >
        <el-table-column label="操作" width="230"
          ><template #default="{ row }">
            <el-button link type="primary" @click="editProvider(row)"
              >编辑</el-button
            >
            <el-button
              link
              type="primary"
              :disabled="!row.enabled"
              @click="openTest(row)"
              >测试发送</el-button
            >
            <el-popconfirm title="确认删除服务？" @confirm="removeProvider(row)"
              ><template #reference
                ><el-button link type="danger">删除</el-button></template
              ></el-popconfirm
            >
          </template></el-table-column
        >
      </el-table>
    </template>

    <el-drawer
      v-model="providerDrawer"
      :title="providerForm.id ? '编辑消息服务' : '添加消息服务'"
      size="min(740px, 100vw)"
      direction="rtl"
    >
      <el-form label-width="160px">
        <el-form-item label="名称"
          ><el-input v-model="providerForm.name"
        /></el-form-item>
        <el-form-item label="服务商"
          ><el-select v-model="providerForm.providerType" style="width: 100%"
            ><el-option
              v-for="option in providerTypes"
              :key="option.value"
              :label="option.label"
              :value="option.value" /></el-select
        ></el-form-item>
        <el-form-item :label="channel === 'MAIL' ? 'SMTP 地址' : 'API 地址'"
          ><el-input
            v-model="providerForm.endpoint"
            :placeholder="
              channel === 'SMS' && providerForm.providerType !== 'CUSTOM'
                ? 'SDK 默认端点，无需填写'
                : ''
            "
        /></el-form-item>
        <el-form-item
          v-if="channel === 'MAIL' || providerForm.providerType !== 'CUSTOM'"
          :label="channel === 'MAIL' ? 'SMTP 用户名' : 'Access Key / Secret ID'"
          ><el-input v-model="providerForm.account"
        /></el-form-item>
        <el-form-item
          :label="
            channel === 'MAIL' ? 'SMTP 密码' : 'Access Secret / Secret Key'
          "
          ><el-input
            v-model="providerForm.credential"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="providerForm.id ? '留空保留已配置凭据' : ''"
        /></el-form-item>
        <el-form-item
          v-if="channel === 'MAIL' || providerForm.providerType !== 'CUSTOM'"
          :label="channel === 'MAIL' ? '发件邮箱' : '签名'"
          ><el-input v-model="providerForm.sender"
        /></el-form-item>
        <el-form-item v-if="channel === 'MAIL'" label="端口"
          ><el-input-number v-model="mailOptions.port" :min="1" :max="65535"
        /></el-form-item>
        <el-form-item v-if="channel === 'MAIL'" label="STARTTLS"
          ><el-switch v-model="mailOptions.startTls"
        /></el-form-item>
        <el-form-item v-if="channel === 'MAIL'" label="SSL"
          ><el-switch v-model="mailOptions.ssl"
        /></el-form-item>
        <el-form-item
          v-if="channel === 'SMS' && providerForm.providerType === 'TENCENT'"
          label="短信 SDK App ID"
          ><el-input v-model="tencentOptions.smsSdkAppId"
        /></el-form-item>
        <el-form-item
          v-if="channel === 'SMS' && providerForm.providerType === 'TENCENT'"
          label="地域"
          ><el-input
            v-model="providerForm.regionCode"
            placeholder="ap-guangzhou"
        /></el-form-item>
        <template
          v-if="channel === 'SMS' && providerForm.providerType === 'CUSTOM'"
        >
          <el-alert
            title="仅允许 HTTPS POST，目标主机还需配置在服务端允许列表。固定参数、固定请求头和鉴权前缀不得填写密钥；鉴权值仅填写在上方加密凭据字段。"
            type="warning"
            :closable="false"
          />
          <el-form-item label="手机号参数名"
            ><el-input v-model="customOptions.phoneParameter"
          /></el-form-item>
          <el-form-item label="内容参数名"
            ><el-input v-model="customOptions.contentParameter"
          /></el-form-item>
          <el-form-item label="模板编码参数名"
            ><el-input v-model="customOptions.templateParameter"
          /></el-form-item>
          <el-form-item label="鉴权请求头"
            ><el-input
              v-model="customOptions.authHeader"
              placeholder="Authorization"
          /></el-form-item>
          <el-form-item label="鉴权前缀"
            ><el-input v-model="customOptions.authPrefix" placeholder="Bearer "
          /></el-form-item>
          <el-form-item label="固定参数 JSON"
            ><el-input v-model="fixedParametersText" type="textarea"
          /></el-form-item>
          <el-form-item label="固定请求头 JSON（仅 Accept）"
            ><el-input v-model="headersText" type="textarea"
          /></el-form-item>
          <el-form-item label="成功响应字段"
            ><el-input v-model="customOptions.successField"
          /></el-form-item>
          <el-form-item label="成功响应值"
            ><el-input v-model="customOptions.successValue"
          /></el-form-item>
        </template>
        <el-form-item label="启用"
          ><el-switch v-model="providerForm.enabled"
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button @click="providerDrawer = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="saveProvider"
          >保存</el-button
        ></template
      >
    </el-drawer>

    <el-drawer
      v-model="templateDrawer"
      title="编辑消息模板"
      size="min(680px, 100vw)"
      direction="rtl"
    >
      <el-form label-width="130px">
        <el-form-item label="场景">{{ templateForm.templateKey }}</el-form-item>
        <el-form-item label="可用变量">{{
          templateVariables.join(", ")
        }}</el-form-item>
        <el-form-item v-if="channel === 'MAIL'" label="标题"
          ><el-input v-model="templateForm.title"
        /></el-form-item>
        <el-form-item label="内容"
          ><el-input v-model="templateForm.content" type="textarea" :rows="6"
        /></el-form-item>
        <el-form-item
          v-if="channel === 'SMS' && templateProviderType !== 'CUSTOM'"
          label="供应商模板编码"
          ><el-input v-model="templateForm.providerTemplateCode"
        /></el-form-item>
        <el-form-item label="启用"
          ><el-switch v-model="templateForm.enabled"
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button @click="templateDrawer = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="saveTemplate"
          >保存</el-button
        ></template
      >
    </el-drawer>

    <el-dialog v-model="testDialog" title="测试发送" width="560px">
      <el-form label-width="130px">
        <el-form-item
          :label="channel === 'MAIL' ? '临时收件邮箱' : '临时手机号'"
          ><el-input v-model="testForm.target"
        /></el-form-item>
        <el-form-item label="模板场景"
          ><el-select
            v-model="testForm.templateKey"
            style="width: 100%"
            @change="testTypeChanged"
            ><el-option
              v-for="item in testTemplates"
              :key="item.templateKey"
              :label="item.templateKey"
              :value="item.templateKey" /></el-select
        ></el-form-item>
        <el-form-item v-for="key in testVariables" :key="key" :label="key"
          ><el-input v-model="testForm.variables[key]"
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button @click="testDialog = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="sendTest"
          >提交测试</el-button
        ></template
      >
    </el-dialog>
  </div>
</template>

<script>
export default { name: "MessageSettingsPage" };
</script>
<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import api from "@/api/model/system/messageSettings";

const active = ref("MAIL_TEMPLATE"),
  channel = computed(() => (active.value.startsWith("MAIL") ? "MAIL" : "SMS")),
  isTemplate = computed(() => active.value.endsWith("TEMPLATE"));
const mailTypes = [
  { value: "CUSTOM", label: "自定义" },
  { value: "EXCHANGE", label: "Exchange" },
  { value: "ALIYUN", label: "阿里" },
  { value: "TENCENT", label: "腾讯" },
  { value: "NETEASE", label: "网易" },
];
const smsTypes = [
  { value: "ALIYUN", label: "阿里" },
  { value: "TENCENT", label: "腾讯" },
  { value: "CUSTOM", label: "自定义" },
];
const providerTypes = computed(() =>
  channel.value === "MAIL" ? mailTypes : smsTypes
);
const templateProviderType = ref(""),
  providers = ref([]),
  templates = ref([]),
  testTemplates = ref([]);
const loading = ref(false),
  saving = ref(false),
  providerDrawer = ref(false),
  templateDrawer = ref(false),
  testDialog = ref(false);
const blankProvider = () => ({
  id: null,
  channel: channel.value,
  providerType: "CUSTOM",
  name: "",
  endpoint: "",
  account: "",
  credential: "",
  regionCode: "",
  sender: "",
  enabled: true,
});
const providerForm = reactive(blankProvider()),
  mailOptions = reactive({ port: 587, startTls: true, ssl: false }),
  tencentOptions = reactive({ smsSdkAppId: "" });
const customOptions = reactive({
  phoneParameter: "phone",
  contentParameter: "content",
  templateParameter: "templateCode",
  authHeader: "Authorization",
  authPrefix: "Bearer ",
  successField: "",
  successValue: "",
});
const fixedParametersText = ref("{}"),
  headersText = ref("{}");
const templateForm = reactive({
  templateKey: "",
  providerType: "",
  languageCode: "zh-CN",
  title: "",
  content: "",
  providerTemplateCode: "",
  enabled: true,
});
const templateVariables = ref([]),
  testVariables = ref([]);
const testForm = reactive({
  providerId: null,
  templateKey: "",
  target: "",
  variables: {},
});
const unwrap = (res) => {
  if (!res?.success) throw new Error(res?.msg || res?.cause || "操作失败");
  return res.data;
};
async function load() {
  loading.value = true;
  try {
    if (isTemplate.value)
      templates.value =
        unwrap(
          await api.templates(channel.value, templateProviderType.value)
        ) || [];
    else providers.value = unwrap(await api.providers(channel.value)) || [];
  } catch (error) {
    ElMessage.error(error.message);
    if (isTemplate.value) templates.value = [];
    else providers.value = [];
  } finally {
    loading.value = false;
  }
}
function tabChanged() {
  templateProviderType.value = "";
  load();
}
function editProvider(row) {
  Object.assign(providerForm, blankProvider(), row || {}, { credential: "" });
  Object.assign(mailOptions, { port: 587, startTls: true, ssl: false });
  Object.assign(tencentOptions, { smsSdkAppId: "" });
  Object.assign(customOptions, {
    phoneParameter: "phone",
    contentParameter: "content",
    templateParameter: "templateCode",
    authHeader: "Authorization",
    authPrefix: "Bearer ",
    successField: "",
    successValue: "",
  });
  fixedParametersText.value = "{}";
  headersText.value = "{}";
  if (row?.configJson) {
    try {
      const value = JSON.parse(row.configJson);
      if (channel.value === "MAIL") Object.assign(mailOptions, value);
      else if (row.providerType === "TENCENT")
        Object.assign(tencentOptions, value);
      else if (row.providerType === "CUSTOM") {
        Object.assign(customOptions, value);
        fixedParametersText.value = JSON.stringify(
          value.fixedParameters || {},
          null,
          2
        );
        headersText.value = JSON.stringify(value.headers || {}, null, 2);
      }
    } catch {
      ElMessage.warning("已有服务参数不是有效 JSON");
    }
  }
  providerDrawer.value = true;
}
async function saveProvider() {
  if (saving.value) return;
  if (
    !providerForm.name ||
    ((channel.value === "MAIL" || providerForm.providerType !== "CUSTOM") &&
      (!providerForm.account ||
        !providerForm.sender ||
        (!providerForm.id && !providerForm.credential))) ||
    (channel.value === "SMS" &&
      providerForm.providerType === "CUSTOM" &&
      (!customOptions.authHeader || (!providerForm.id && !providerForm.credential)))
  )
    return ElMessage.error("请填写必需字段");
  saving.value = true;
  try {
    const data = { ...providerForm };
    delete data.id;
    data.configJson = JSON.stringify(
      channel.value === "MAIL"
        ? mailOptions
        : data.providerType === "TENCENT"
        ? tencentOptions
        : data.providerType === "CUSTOM"
        ? {
            ...customOptions,
            fixedParameters: JSON.parse(fixedParametersText.value),
            headers: JSON.parse(headersText.value),
          }
        : {}
    );
    unwrap(
      await (providerForm.id
        ? api.updateProvider(providerForm.id, data)
        : api.createProvider(data))
    );
    ElMessage.success("保存成功");
    providerDrawer.value = false;
    await load();
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    saving.value = false;
  }
}
async function removeProvider(row) {
  try {
    unwrap(await api.removeProvider(row.id));
    ElMessage.success("删除成功");
    await load();
  } catch (error) {
    ElMessage.error(error.message);
  }
}
function editTemplate(row) {
  Object.assign(templateForm, {
    templateKey: row.templateKey,
    providerType: templateProviderType.value,
    languageCode: "zh-CN",
    title: row.title || "",
    content: row.content,
    providerTemplateCode: row.providerTemplateCode || "",
    enabled: row.enabled,
  });
  templateVariables.value = row.variables || [];
  templateDrawer.value = true;
}
async function saveTemplate() {
  if (saving.value || !templateForm.content.trim()) return;
  saving.value = true;
  try {
    unwrap(await api.saveTemplate(channel.value, { ...templateForm }));
    ElMessage.success("模板已保存");
    templateDrawer.value = false;
    await load();
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    saving.value = false;
  }
}
async function openTest(row) {
  try {
    testTemplates.value =
      unwrap(await api.templates(channel.value, row.providerType)) || [];
    Object.assign(testForm, {
      providerId: row.id,
      templateKey: testTemplates.value[0]?.templateKey || "",
      target: "",
      variables: {},
    });
    testTypeChanged();
    testDialog.value = true;
  } catch (error) {
    ElMessage.error(error.message);
  }
}
function testTypeChanged() {
  const template = testTemplates.value.find(
    (item) => item.templateKey === testForm.templateKey
  );
  testVariables.value = template?.variables || [];
  testForm.variables = Object.fromEntries(
    testVariables.value.map((key) => [
      key,
      key === "platform" ? "Macula Cloud" : "",
    ])
  );
}
async function sendTest() {
  if (
    saving.value ||
    !testForm.target ||
    testVariables.value.some((key) => !testForm.variables[key])
  )
    return ElMessage.error("请输入临时目标和模板变量");
  saving.value = true;
  try {
    unwrap(await api.test({ ...testForm }));
    ElMessage.success("供应商已接受投递请求，请在收件端确认");
    testDialog.value = false;
  } catch (error) {
    ElMessage.error(error.message);
  } finally {
    saving.value = false;
  }
}
onMounted(load);
</script>

<style scoped>
.message-page {
  padding: 24px;
}
.toolbar {
  display: flex;
  gap: 12px;
  margin: 20px 0;
}
.message-page p {
  color: #666;
}
</style>
