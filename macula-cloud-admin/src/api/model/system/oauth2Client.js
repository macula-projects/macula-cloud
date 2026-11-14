import {ensureProtocol} from '@/utils/applicationProtocolRegistry';
import config from "@/config";
import http from "@/utils/request";

const url = `${config.API_URL}/${config.MODEL.system}/api/v1/oauth2-clients`;

export const BROWSER_LOGIN_PROVIDERS = Object.freeze([
  { value: "GITHUB", label: "GitHub" },
  { value: "WECOM", label: "企业微信" },
  { value: "FEISHU", label: "飞书" },
  { value: "DINGTALK", label: "钉钉" },
  { value: "WECHAT", label: "微信" },
]);

export const APPLICATION_TYPES = Object.freeze([
  {
    value: "WEB_SERVER",
    label: "标准 Web",
    description: "由应用服务端保管凭据、换取令牌",
  },
  {
    value: "SPA",
    label: "单页 Web",
    description: "浏览器运行，使用授权码与 S256 PKCE",
  },
  {
    value: "NATIVE",
    label: "移动客户端",
    description: "选择一个移动SDK或AppLink来源，可并存IAM内置登录",
  },
  {
    value: "SERVICE",
    label: "后端服务",
    description: "服务自身访问 API，不包含用户登录",
  },
  {
    value: "MINIAPP",
    label: "小程序",
    description: "使用小程序凭证及固定身份源",
  },
]);

export const EXTERNAL_CODE_GRANT =
  "urn:macula:params:oauth:grant-type:external_code";
export const AUTHORIZATION_CODE_GRANT = "authorization_code";

function grantTypes(value) {
  return new Set(
    (value || "")
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean)
  );
}

export function oauthClientErrorMessage(response, fallback = "操作失败") {
  const cause = typeof response?.cause === "string" ? response.cause : "";
  const businessCause = cause.match(/^4\d{2} [A-Z_]+ "([^"\r\n]+)"$/);
  return businessCause?.[1] || response?.msg || fallback;
}

export function providerCandidatesForCodeType(codeTypeValue, catalog) {
  return (catalog.sources || []).filter(source => source.available &&
    source.capabilities?.some(capability => capability.codeType === codeTypeValue));
}

export function reconcileExternalProviderBindings(client, catalog) {
  const selectedTypes = new Set(client.externalCodeTypes || []);
  const next = {};
  for (const codeType of selectedTypes) {
    const definition = (catalog.sources || []).flatMap(source => source.capabilities || []).find(
      item => item.codeType === codeType
    );
    if (!definition) {
      continue;
    }
    const candidates = providerCandidatesForCodeType(codeType, catalog);
    if (candidates.length === 1) {
      next[codeType] = candidates[0].id;
      continue;
    }
    const current = client.externalProviderBindings?.[codeType];
    if (candidates.some((candidate) => candidate.id === current)) {
      next[codeType] = current;
    }
  }
  return next;
}

export function updateLoginMethodConfiguration(
  client,
  method,
  selected,
  candidates = []
) {
  const next = {
    ...client,
    externalCodeTypes: [...(client.externalCodeTypes || [])],
    externalProviderBindings: {
      ...(client.externalProviderBindings || {}),
    },
    browserLoginProviderTypes: [...(client.browserLoginProviderTypes || [])],
  };
  next.settings=client.settings ? JSON.parse(JSON.stringify(client.settings)) : undefined;
  const code=method.authorizationCode || method.browserProviderType ? client.protocol || 'OIDC' : 'OAUTH2_1';
  const profile=ensureProtocol(next,code);
  const grants = grantTypes(profile.settings.authorizationGrantTypes);
  if (method.authorizationCode) {
    if (!selected && next.browserLoginProviderTypes.length) return next;
    if (selected) grants.add(AUTHORIZATION_CODE_GRANT);
    else grants.delete(AUTHORIZATION_CODE_GRANT);
  } else if (method.browserProviderType) {
    const providers = new Set(next.browserLoginProviderTypes);
    if (selected) {
      providers.add(method.browserProviderType);
      grants.add(AUTHORIZATION_CODE_GRANT);
    } else {
      providers.delete(method.browserProviderType);
      if (next.directLoginProviderType === method.browserProviderType) {
        next.directLoginProviderType = "";
      }
    }
    next.browserLoginProviderTypes = [...providers];
  } else {
    const codeTypes = new Set(next.externalCodeTypes);
    if (selected) {
      codeTypes.add(method.externalCodeType);
      if (candidates.length === 1) {
        next.externalProviderBindings[method.externalCodeType] =
          candidates[0].id;
      } else if (
        !candidates.some(
          (candidate) =>
            candidate.id ===
            next.externalProviderBindings[method.externalCodeType]
        )
      ) {
        delete next.externalProviderBindings[method.externalCodeType];
      }
    } else {
      codeTypes.delete(method.externalCodeType);
      delete next.externalProviderBindings[method.externalCodeType];
    }
    next.externalCodeTypes = [...codeTypes];
    if (next.externalCodeTypes.length) grants.add(EXTERNAL_CODE_GRANT);
    else grants.delete(EXTERNAL_CODE_GRANT);
  }
  profile.settings.authorizationGrantTypes = [...grants].join(",");
  return next;
}

export function unsupportedExternalCodeTypes(client, catalog, catalogLoaded) {
  if (!catalogLoaded) return [];
  const supported = new Set(
    (catalog.sources || []).flatMap(source => source.capabilities || []).map(definition => definition.codeType)
  );
  return (client.externalCodeTypes || []).filter(
    (value) => !supported.has(value)
  );
}

export function invalidSelectedBrowserProviders(client, catalog) {
  return (client.browserLoginProviderTypes || []).filter(providerType =>
    !(catalog.sources || []).some(source => source.available && source.browser && source.providerType === providerType &&
      String(source.id) === String(client.browserProviderBindings?.[providerType])));
}

export function loginCapabilitySummary(client) {
  if(client.purpose==='RESOURCE_SERVER')return '资源服务器';
  return (
    APPLICATION_TYPES.find((type) => type.value === client.applicationType)
      ?.label || "历史应用 · 待确认类型"
  );
}

export default {
  list: (params) => http.get(url, params),
  get: (id) => http.get(`${url}/${id}`),
  metadata: (protocol = "OIDC") => http.get(`${url}/metadata`,{protocol}),
  configurationCatalog: () => http.get(`${url}/configuration-catalog`),
  createDraft: (data) => http.post(`${url}/drafts`, data),
  create: (data) => http.post(url, data),
  update: (id, data) => http.put(`${url}/${id}`, data),
  resetSecret: (id, secret, configurationVersion) => http.put(`${url}/${id}/secret`, { secret, configurationVersion }),
  remove: (id, configurationVersion) => http.delete(`${url}/${id}?configurationVersion=${encodeURIComponent(configurationVersion)}`),
};

export const RESOURCE_USER_TYPES = Object.freeze([
  {value:'employee',label:'行政员工'}, {value:'member',label:'会员'},
  {value:'odc',label:'外包人员'}, {value:'supplier',label:'供应商'},
]);
