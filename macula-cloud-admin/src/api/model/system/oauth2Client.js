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

export const CLIENT_TERMINALS = Object.freeze([
  { value: "BROWSER", label: "浏览器", description: "网站、管理后台和普通 H5" },
  { value: "APP", label: "App", description: "原生 App 或 AppLink" },
  {
    value: "WECHAT_MINIAPP",
    label: "微信小程序",
    description: "微信小程序端取得凭证",
  },
  {
    value: "WECOM_MINIAPP",
    label: "企微小程序",
    description: "企业微信小程序端取得凭证",
  },
  {
    value: "WEBVIEW",
    label: "端内网页",
    description: "企微、微信、飞书或钉钉内嵌网页",
  },
]);

export const TERMINAL_LOGIN_METHODS = Object.freeze({
  BROWSER: Object.freeze([
    {
      value: "BROWSER_IAM",
      label: "账号、手机或邮箱登录",
      authorizationCode: true,
    },
    {
      value: "BROWSER_GITHUB",
      label: "GitHub 登录",
      browserProviderType: "GITHUB",
    },
    {
      value: "BROWSER_WECOM",
      label: "企业微信登录",
      browserProviderType: "WECOM",
    },
    {
      value: "BROWSER_FEISHU",
      label: "飞书登录",
      browserProviderType: "FEISHU",
    },
    {
      value: "BROWSER_DINGTALK",
      label: "钉钉登录",
      browserProviderType: "DINGTALK",
    },
    {
      value: "BROWSER_WECHAT",
      label: "微信登录",
      browserProviderType: "WECHAT",
    },
  ]),
  APP: Object.freeze([
    { value: "APP_IAM", label: "IAM 统一登录", authorizationCode: true },
    {
      value: "APP_FEISHU",
      label: "飞书 AppLink",
      externalCodeType: "FEISHU_APP_CODE",
    },
    {
      value: "APP_WECOM",
      label: "企业微信 SDK",
      externalCodeType: "WECOM_APP_CODE",
    },
    {
      value: "APP_DINGTALK",
      label: "钉钉 SDK",
      externalCodeType: "DINGTALK_APP_CODE",
    },
    {
      value: "APP_WECHAT",
      label: "微信开放平台 SDK",
      externalCodeType: "WECHAT_OPEN_APP_CODE",
    },
    {
      value: "APP_MOBILE",
      label: "运营商一键登录",
      externalCodeType: "ALIYUN_MOBILE_TOKEN",
    },
  ]),
  WECHAT_MINIAPP: Object.freeze([
    {
      value: "WECHAT_MINIAPP_IDENTITY",
      label: "微信身份登录",
      externalCodeType: "WECHAT_MINIAPP_LOGIN_CODE",
    },
    {
      value: "WECHAT_MINIAPP_MOBILE",
      label: "微信手机号登录",
      externalCodeType: "WECHAT_MINIAPP_PHONE_CODE",
    },
  ]),
  WECOM_MINIAPP: Object.freeze([
    {
      value: "WECOM_MINIAPP_IDENTITY",
      label: "企业成员身份登录",
      externalCodeType: "WECOM_MINIAPP_CODE",
    },
  ]),
  WEBVIEW: Object.freeze([
    {
      value: "WEBVIEW_FEISHU",
      label: "飞书网页免登",
      externalCodeType: "FEISHU_WEBVIEW_CODE",
    },
    {
      value: "WEBVIEW_DINGTALK",
      label: "钉钉网页免登",
      externalCodeType: "DINGTALK_WEBVIEW_CODE",
    },
    {
      value: "WEBVIEW_WECOM",
      label: "企业微信网页授权",
      browserProviderType: "WECOM",
      sharedWebCapability: true,
    },
    {
      value: "WEBVIEW_WECHAT",
      label: "微信公众号网页授权",
      browserProviderType: "WECHAT",
      sharedWebCapability: true,
    },
  ]),
});

export const COMMON_EXTERNAL_CODE_TYPES = Object.freeze(
  Object.values(TERMINAL_LOGIN_METHODS)
    .flat()
    .map((method) => method.externalCodeType)
    .filter(Boolean)
);

export const EXTERNAL_CODE_GRANT =
  "urn:macula:params:oauth:grant-type:external_code";
export const AUTHORIZATION_CODE_GRANT = "authorization_code";

const allLoginMethods = Object.values(TERMINAL_LOGIN_METHODS).flat();
const methodByExternalCodeType = new Map(
  allLoginMethods
    .filter((method) => method.externalCodeType)
    .map((method) => [method.externalCodeType, method])
);

function grantTypes(value) {
  return new Set(
    (value || "")
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean)
  );
}

export function providerCandidatesForCodeType(codeTypeValue, catalog) {
  const definition = (catalog.codeTypes || []).find(
    (item) => item.value === codeTypeValue
  );
  if (!definition) return [];
  return (catalog.providerRegistrations || []).filter(
    (provider) =>
      provider.providerType === definition.providerType &&
      (definition.capability === "DEFAULT" ||
        provider.capabilities?.includes(definition.capability))
  );
}

export function reconcileExternalProviderBindings(client, catalog) {
  const selectedTypes = new Set(client.externalCodeTypes || []);
  const next = {};
  for (const codeType of selectedTypes) {
    const definition = (catalog.codeTypes || []).find(
      (item) => item.value === codeType
    );
    if (!definition) {
      if (client.externalProviderBindings?.[codeType]) {
        next[codeType] = client.externalProviderBindings[codeType];
      }
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
  const grants = grantTypes(client.authorizationGrantTypes);
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
  next.authorizationGrantTypes = [...grants].join(",");
  return next;
}

export function terminalForClient(client, catalog) {
  const selectedTypes = new Set(client.externalCodeTypes || []);
  if ([...selectedTypes].some((value) => value.startsWith("WECHAT_MINIAPP_"))) {
    return "WECHAT_MINIAPP";
  }
  if (selectedTypes.has("WECOM_MINIAPP_CODE")) return "WECOM_MINIAPP";
  const definitions = catalog.codeTypes || [];
  const hasScene = (scene) =>
    [...selectedTypes].some(
      (value) =>
        definitions.find((definition) => definition.value === value)?.scene ===
        scene
    );
  if (hasScene("APP")) return "APP";
  if (hasScene("WEBVIEW")) return "WEBVIEW";
  return "BROWSER";
}

export function unsupportedExternalCodeTypes(client, catalog, catalogLoaded) {
  if (!catalogLoaded) return [];
  const supported = new Set(
    (catalog.codeTypes || []).map((definition) => definition.value)
  );
  return (client.externalCodeTypes || []).filter(
    (value) => !supported.has(value)
  );
}

export function invalidSelectedBrowserProviders(client, catalog) {
  return (client.browserLoginProviderTypes || []).filter((providerType) => {
    const count = (catalog.providerRegistrations || []).filter(
      (provider) => provider.providerType === providerType
    ).length;
    return count !== 1;
  });
}

export function loginCapabilitySummary(client) {
  const labels = [];
  const browserTypes = new Set(client.browserLoginProviderTypes || []);
  const sharedTypes = new Set(["WECOM", "WECHAT"]);
  const browserLabels = BROWSER_LOGIN_PROVIDERS.filter(
    (provider) =>
      browserTypes.has(provider.value) && !sharedTypes.has(provider.value)
  ).map((provider) => provider.label);
  const sharedLabels = BROWSER_LOGIN_PROVIDERS.filter(
    (provider) =>
      browserTypes.has(provider.value) && sharedTypes.has(provider.value)
  ).map((provider) => provider.label);
  if (browserLabels.length) labels.push(`浏览器：${browserLabels.join("、")}`);
  if (sharedLabels.length) {
    labels.push(`网页（浏览器/端内）：${sharedLabels.join("、")}`);
  }
  const commonMethods = (client.externalCodeTypes || [])
    .map((value) => methodByExternalCodeType.get(value))
    .filter(Boolean)
    .map((method) => method.label);
  if (commonMethods.length) labels.push(commonMethods.join("、"));
  if (
    (client.externalCodeTypes || []).some(
      (value) => !methodByExternalCodeType.has(value)
    )
  ) {
    labels.push("高级兼容配置");
  }
  return labels.length ? labels.join("；") : "仅本地认证";
}

export default {
  list: (params) => http.get(url, params),
  get: (id) => http.get(`${url}/${id}`),
  externalCodeCatalog: () => http.get(`${url}/external-code-catalog`),
  create: (data) => http.post(url, data),
  update: (id, data) => http.put(`${url}/${id}`, data),
  resetSecret: (id, secret) => http.put(`${url}/${id}/secret`, { secret }),
  remove: (id) => http.delete(`${url}/${id}`),
};
