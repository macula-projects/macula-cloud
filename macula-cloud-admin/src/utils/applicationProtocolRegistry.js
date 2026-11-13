import {
  oauth2ProtocolDefaults,
  normalizeOAuth2Protocol,
  oauth2ProtocolSupported,
} from "./oauth2ProtocolConfiguration";
export { RETIRED_GRANTS } from "./oauth2ProtocolConfiguration";

const oauth2 = Object.freeze({
  defaults: oauth2ProtocolDefaults,
  normalize: normalizeOAuth2Protocol,
  supports: oauth2ProtocolSupported,
});

// 单应用只保留一个协议；新协议须有独立配置实现后才加入目录。
export const APPLICATION_PROTOCOLS = Object.freeze([
  {
    code: "OIDC",
    label: "OIDC",
    description: "用户登录及身份资料",
    family: "oauth2",
    ...oauth2,
  },
  {
    code: "OAUTH2_1",
    label: "OAuth 2.1",
    description: "授权访问 API",
    family: "oauth2",
    ...oauth2,
  },
]);
const definition = (code) => {
  const found = APPLICATION_PROTOCOLS.find((item) => item.code === code);
  if (!found) throw new Error("当前协议尚未实现");
  return found;
};
export const enabledProtocolCodes = (client) =>
  client.protocol ? [client.protocol] : client.enabledProtocols || [];
export const protocolSupported = (client, code) => {
  const found = APPLICATION_PROTOCOLS.find((item) => item.code === code);
  return !!found && found.supports(client, code);
};
export function protocolDefaults(client, protocol) {
  return definition(protocol).defaults(client, protocol);
}
export function selectProtocol(client, code) {
  if (!protocolSupported(client, code))
    throw new Error("当前应用类型不支持该协议");
  client.protocol = code;
  client.settings = protocolDefaults(client, code);
  client.externalCodeTypes = [];
  client.externalProviderBindings = {};
  client.browserLoginProviderTypes = [];
  client.browserProviderBindings = {};
  if (client.browserCapabilityBindings != null)
    client.browserCapabilityBindings = {};
  client.directLoginProviderType = "";
}
export function ensureProtocol(client, code = client.protocol) {
  if (!client.protocol) selectProtocol(client, code);
  if (client.protocol !== code)
    throw new Error("一个应用只能配置一个协议，请先切换协议");
  client.settings ||= protocolDefaults(client, code);
  return { protocol: code, enabled: true, settings: client.settings };
}
// 保留内部调用名称；启用其他协议必须替换当前配置，不生成第二份配置。
export function setProtocolEnabled(client, code, enabled) {
  if (!enabled) {
    client.configurationStatus = "DRAFT";
    return false;
  }
  const changed = client.protocol !== code;
  if (changed) selectProtocol(client, code);
  return changed;
}
export function projectProtocol(client, code = client.protocol) {
  if (!client.settings || client.protocol !== code)
    return {
      ...client,
      settings: undefined,
      enabledProtocols: [],
      authorizationGrantTypes: "",
      clientAuthenticationMethods: "",
      scopes: "",
      redirectUris: "",
      allowedOrigins: [],
    };
  return {
    ...client,
    ...client.settings,
    settings: undefined,
    enabledProtocols: [code],
    defaultProtocol: code,
    externalCodeTypes: client.externalCodeTypes || [],
    externalProviderBindings: client.externalProviderBindings || {},
  };
}
export function anyProtocolUses(client, predicate) {
  return client.settings ? predicate(client.settings) : predicate(client);
}
export function managementProtocol(client) {
  return client.purpose === "RESOURCE_SERVER" ? "RESOURCE_SERVER" : null;
}
// 注册表只负责调度，协议专有字段和规则由策略实现解释。
export function normalizeProtocol(client, code = client.protocol) {
  ensureProtocol(client, code);
  return definition(code).normalize(client, code);
}
