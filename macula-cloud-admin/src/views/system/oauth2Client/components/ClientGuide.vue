<template>
  <section class="configuration-panel">
    <div class="section-heading"><h3>快速开始</h3><p>按当前协议生成接入步骤和请求示例。</p></div>
    <el-alert v-if="client.configurationStatus !== 'ENABLED'" title="应用尚未启用，请完成协议、登录能力及访问授权配置。完成配置并启用后显示对应接入示例。" type="info" :closable="false" />
    <p v-if="!client.protocol" class="hint">尚未选择接入协议，请先在协议配置中完成设置。</p>
    <template v-else>
    <p>接入协议：{{client.protocol==='OIDC'?'OIDC':'OAuth 2.1'}}</p>
    <el-alert v-if="hasRetiredGrant" type="error" :closable="false" title="配置包含已退役授权，请先显式整改；接入示例暂停复制。" />
    <p v-if="grant===EXTERNAL_CODE_GRANT" class="hint">{{ $t('identitySourceIntegration.codeTypeHelp') }}</p>
    <p v-if="client.applicationType === 'IN_APP'" class="hint">端内应用只使用固定平台能力。企业微信、微信公众号在对应容器直接进入平台授权；飞书、钉钉由业务前端JSAPI取Code后换取Token。不提供IAM标准登录页。</p>
    <p v-if="['NATIVE','MINIAPP'].includes(client.applicationType)" class="hint">第三方直接登录只使用已关联的唯一来源。另开IAM登录页时仅提供内置账号密码、短信登录。</p>
    <h4>接入步骤</h4>
    <p v-if="selectedProtocol==='OIDC' && grant===EXTERNAL_CODE_GRANT">此流程为Macula第三方Code扩展登录：请求openid返回ID Token和Access Token。验证ID Token签名、issuer、client_id audience与有效期；请求携带nonce时必须匹配。该Code不是IAM授权码，不使用授权码PKCE参数，也不建立浏览器SSO会话。</p>
    <p v-if="selectedProtocol==='OIDC' && grant===EXTERNAL_CODE_GRANT">{{ $t('identitySourceIntegration.nonceBoundary') }}</p>
    <p v-else-if="selectedProtocol==='OIDC'">OIDC授权码流程需保存state/PKCE并验证ID Token签名、issuer、audience、时间及请求中提供的nonce。</p>
    <p v-else-if="client.applicationType !== 'SERVICE'">此流程使用OAuth 2.1：不请求 openid、不要求 ID Token。选择 profile 后可携带访问令牌调用 IAM /api/v1/profile，读取用户名与昵称。</p>
    <p v-if="management">令牌内省/撤销的调用方认证使用{{management}}配置，由资源服务器用途单独管理。</p>
    <ol>
      <li v-for="step in steps" :key="step">{{ step }}</li>
    </ol>
    <el-alert
      v-if="!selectedMetadata?.available"
      title="平台接入地址暂不可用，请在协议配置底部重新获取；获取成功后生成请求示例。"
      type="warning"
      :closable="false"
    />
    <template v-else-if="client.configurationStatus === 'ENABLED'">
      <el-form-item label="请求示例"
        ><el-select v-model="grant"
          ><el-option
            v-for="value in grants"
            :key="value"
            :label="value"
            :value="value" /></el-select
      ></el-form-item>
      <el-form-item v-if="grant === EXTERNAL_CODE_GRANT" :label="$t('identitySourceIntegration.codeType')" required>
        <el-radio-group v-model="codeType">
          <el-radio v-for="item in codeChoices" :key="item.code" :value="item.code">{{ item.source.name }} · {{ item.label }}</el-radio>
        </el-radio-group>
        <span v-if="!codeType" class="field-help">{{ $t('identitySourceIntegration.codeTypeRequired') }}</span>
      </el-form-item>
      <el-form-item v-if="usesSecret(view)" label="临时示例密钥"
        ><el-input
          v-model="exampleSecret"
          type="password"
          show-password
          autocomplete="off"
          placeholder="可临时输入已知密钥，不会保存到服务端"
      /></el-form-item>
      <el-checkbox v-if="exampleSecret" v-model="reveal"
        >在示例中显示密钥内容</el-checkbox
      >
      <el-button :disabled="hasRetiredGrant || (grant === EXTERNAL_CODE_GRANT && !selectedSource)" @click="$emit('copy', example(true))">复制请求内容</el-button>
      <p v-if="exampleSecret" class="hint">
        复制内容包含本次输入的密钥，请妥善保管。离开此分区将清除临时值。
      </p>
      <pre>{{ example(reveal) }}</pre>
      <el-alert v-if="client.externalCodeTypes?.length" type="info" :closable="false"
        title="首次登录需要绑定时，Token接口返回 binding_required 和 binding_uri。打开该短期入口证明已有账号；成功后返回应用，重新取得平台CODE再登录，不重复使用原CODE。自动绑定策略在身份源管理配置。" />
      <el-collapse
        ><el-collapse-item
          v-if="grant==='authorization_code' && view.requireProofKey"
          title="生成 S256 PKCE 参数"
        >
          <pre>{{ pkceExample() }}</pre></el-collapse-item
        ><el-collapse-item
          v-if="usesPrivateKey(view)"
          title="在客户端生成 private_key_jwt 签名"
        >
          <pre>{{ privateKeyExample(view) }}</pre>
        </el-collapse-item></el-collapse
      >
    </template>
    </template>
  </section>
</template>
<script setup>
import { RETIRED_GRANTS } from '@/utils/applicationProtocolRegistry';
import {externalCodeAllowed} from "@/utils/identitySourceCompatibility";
import { computed, ref, watch, onBeforeUnmount, onDeactivated } from "vue";
import {EXTERNAL_CODE_GRANT} from '@/api/model/system/oauth2Client';
import {enabledProtocolCodes,projectProtocol,managementProtocol} from '@/utils/applicationProtocolRegistry';
import {
  csvValues,
  usesSecret,
  usesPrivateKey,
  requestExample,
  pkceExample,
  privateKeyExample,
} from "@/utils/oauthClientConfiguration";
const props = defineProps({
  client: { type: Object, required: true },
  metadata: { type: Object, default: null },
  catalog: { type: Object, default: () => ({ sources: [] }) },
});
defineEmits(["copy"]);
const exampleSecret = ref(""),
  reveal = ref(false),
  grant = ref("");
const availableProtocols=computed(()=>enabledProtocolCodes(props.client));
const selectedProtocol=ref(props.client.protocol || availableProtocols.value[0] || 'OIDC');
watch(()=>[props.client.id,props.client.protocol],()=>{selectedProtocol.value=props.client.protocol || availableProtocols.value[0] || 'OIDC'});
watch(availableProtocols,values=>{if(!values.includes(selectedProtocol.value))selectedProtocol.value=values[0] || ''});
const view=computed(()=>projectProtocol(props.client,selectedProtocol.value));
const selectedMetadata=computed(()=>props.metadata?.[selectedProtocol.value]);
const management=computed(()=>managementProtocol(props.client));
const hasRetiredGrant = computed(() => csvValues(view.value.authorizationGrantTypes).some(value => RETIRED_GRANTS.includes(value)));
const grants = computed(() => csvValues(view.value.authorizationGrantTypes).filter(value => !RETIRED_GRANTS.includes(value)));
const codeType = ref('');
const codeChoices = computed(() => (props.client.externalCodeTypes || []).flatMap(code => {
  const id = props.client.externalProviderBindings?.[code];
  if (id == null) return [];
  const source = props.catalog.sources?.find(item => item.available && String(item.id) === String(id));
  const capability = source?.capabilities?.find(item => item.codeType === code);
  return capability && externalCodeAllowed(props.client,code,source) ? [{ code, label: capability.label || code, source }] : [];
}));
const selectedSource = computed(() => codeChoices.value.find(item => item.code === codeType.value)?.source);
watch(() => [props.client.id, props.client.clientId], () => { codeType.value = ''; });
watch(codeChoices, values => { if (!values.some(item => item.code === codeType.value)) codeType.value = ''; });
const platformSteps = computed(() => {
  const instructions = {
    WECHAT_MINIAPP_LOGIN_CODE: ['调用 wx.login() 获取登录 Code；wx.checkSession 不是取 Code 接口。', 'IAM 按此来源的小程序 AppID/Secret 调 code2Session；openid 按 AppID 隔离，session_key 不下发。'],
    WECHAT_MINIAPP_PHONE_CODE: ['由用户主动触发 getPhoneNumber，取得独立手机号 Code；不要传 wx.login 的 Code。', '此流程按已验证手机号策略解析账号，不把手机号证明伪装成永久微信 subject。'],
    WECOM_MINIAPP_CODE: ['调用 wx.qy.login() 获取企微小程序 Code。', 'IAM 使用该企微应用 Token 调企微 code2Session，不能使用普通微信小程序凭据。'],
    WECHAT_OPEN_APP_CODE: ['由微信 OpenSDK 完成移动应用授权，回调取得 Code。', '将 Code 交给 IAM external_code；正常登录无需再打开 IAM 网页。'],
    WECOM_APP_CODE: ['由企业微信移动 SDK 完成 SSO，回调取得 Code。', 'IAM 使用固定企微来源的应用 Token 核验成员身份。'],
    DINGTALK_APP_CODE: ['用该钉钉应用的 Client ID（AppKey）注册移动 SDK，配置对应平台的回跳信息；redirectUri 与开发者后台登记一致。', 'SDK 授权取得 authCode，客户端核对 state 后提交 IAM external_code，code_type 使用 DINGTALK_APP_CODE。', 'IAM 使用同一应用的 AppKey/AppSecret 换用户 Token，再在固定企业内解析成员；Secret 仅保存在服务端。'],
    DINGTALK_WEBVIEW_CODE: ['调用 dd.runtime.permission.requestAuthCode({corpId: 已登记企业})。', 'IAM 使用所关联企业应用 Token 调企业免登接口，不混用个人 OAuth authCode。'],
    FEISHU_APP_CODE: ['生成平台 state 和 S256 PKCE；使用 client_id、代码中配置且已在飞书后台及本Client白名单登记的 redirect_uri、scope、state、code_challenge、code_challenge_method=S256、prompt=consent 拼接飞书授权地址。', '将完整授权地址 URL Encode 后放入 https://applink.feishu.cn/client/web_url/open?mode=window&url=…；这是拉起飞书客户端的 OAuth 授权，不是二维码或通用原生SDK。', '业务 App 接收回调并核对 state；将新 Code、provider_redirect_uri（本次授权原地址）与 provider_code_verifier 交 IAM，IAM 校验Client白名单并使用同一回调向飞书 v3 Token 接口换码，App 不保管平台 Secret。'],
    FEISHU_WEBVIEW_CODE: ['在飞书内调用 tt.requestAccess({appID: 已登记AppID, scopeList: []})，取得3分钟内单次使用的Code；如传state，业务前端核对回调state。', '把Code按 FEISHU_WEBVIEW_CODE 提交IAM external_code；IAM使用本来源平台凭据走v3 Token接口再查询用户身份，不需浏览器回调或AppLink的PKCE参数。', '仅在客户端不支持requestAccess（如errno=103）时按官方说明使用requestAuthCode；用户拒绝或其他错误应停止，不能盲目重试/切换。'],
    ALIYUN_MOBILE_TOKEN: ['由阿里云号码认证 SDK 完成用户授权，取得一次性 Token。', '将 Token 作为本扩展 code 参数交给 IAM；平台 Token 不是 IAM access token。'],
  };
  return codeType.value ? [...(instructions[codeType.value] || []), '使用本页列出的实际 Scope 和 Token 端点兑换 IAM Token；公共客户端不放置 Secret。', '需要绑定时读取 binding_required / binding_uri / binding_ticket；绑定完成后重新获取平台 Code，不能重放已消费 Code。']
    : ['先选择已关联且可用的第三方认证能力，再查看对应平台步骤。'];
});

watch(
  grants,
  (values) => {
    if (!values.includes(grant.value)) grant.value = values[0] || "";
  },
  { immediate: true }
);
const steps = computed(() =>
  props.client.applicationType === "SERVICE"
    ? [
        "在服务端妥善保管客户端凭据。",
        "在协议配置中选择已登记的 API Scope，获取服务令牌。",
        "在资源服务校验令牌、scope 与访问权限。",
      ]
    : grant.value === EXTERNAL_CODE_GRANT || props.client.applicationType === "MINIAPP"
    ? platformSteps.value
    : [
        "注册精确的回调 URI，在应用中配置 Client ID 与平台端点。",
        "通过浏览器发起授权；保存 state 和 PKCE verifier，授权请求不携带 Secret。",
        "校验回调 state 后换取令牌；OIDC 客户端还需校验 ID Token。",
      ]
);
function example(withSecret) {
  return requestExample(view.value, {
    baseUrl: selectedMetadata.value?.endpoints?.issuer || "",
    endpoints: selectedMetadata.value?.endpoints,
    grant: grant.value,
    codeType: codeType.value,
    source: selectedSource.value,
    secret: withSecret ? exampleSecret.value : "",
  });
}
onDeactivated(() => {
  exampleSecret.value = "";
  reveal.value = false;
});
onBeforeUnmount(() => {
  exampleSecret.value = "";
  reveal.value = false;
});
</script>
<style scoped>
pre {
  padding: 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  overflow-x: auto;
  max-width: 100%;
  line-height: 1.8;
  margin: 20px 0;
}
li {
  margin: 12px 0;
}
.hint {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
