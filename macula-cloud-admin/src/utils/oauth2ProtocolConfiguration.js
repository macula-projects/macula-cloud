import { isInApp } from "./identitySourceCompatibility";

// 仅OAuth协议策略使用这些grant；展示/校验保留旧输入，不能静默启用已移除扩展。
export const RETIRED_GRANTS = Object.freeze(["password", "sms", "email"]);
const csv = (value) =>
  (value || "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);

export const oauth2ProtocolSupported = (client, code) =>
  !(code === "OIDC" && client.applicationType === "SERVICE");

export function oauth2ProtocolDefaults(client, protocol) {
  const publicApp = ["SPA", "NATIVE", "MINIAPP"].includes(
    client.applicationType
  );
  const service = client.applicationType === "SERVICE",
    mini = client.applicationType === "MINIAPP";
  return {
    kind: "oauth2",
    clientAuthenticationMethods: publicApp ? "none" : "client_secret_basic",
    authorizationGrantTypes: service
      ? "client_credentials"
      : mini
      ? "urn:macula:params:oauth:grant-type:external_code"
      : isInApp(client)
      ? ""
      : "authorization_code",
    scopes: protocol === "OIDC" ? "openid,profile" : service ? "" : "profile",
    redirectUris: "",
    allowedOrigins: [],
    requireProofKey: !service,
    requireAuthorizationConsent: false,
    authorizationCodeTimeToLive: 300,
    accessTokenTimeToLive: 3600,
    tokenFormat: "reference",
    reuseRefreshTokens: false,
    refreshTokenTimeToLive: 3600,
    idTokenSignatureAlgorithm: protocol === "OIDC" ? "RS256" : null,
    signingAlgorithm: null,
    jwkSetUrl: null,
  };
}
export function normalizeOAuth2Protocol(client, code) {
  const settings = client.settings,
    old = JSON.stringify(settings);
  if (["SPA", "NATIVE", "MINIAPP"].includes(client.applicationType))
    settings.clientAuthenticationMethods = "none";
  let grants = csv(settings.authorizationGrantTypes);
  const retired = grants.filter((value) => RETIRED_GRANTS.includes(value));
  if (code === "OIDC") {
    grants = grants.filter((item) =>
      [
        "authorization_code",
        "refresh_token",
        "urn:macula:params:oauth:grant-type:external_code",
      ].includes(item)
    );
    // 默认授权在创建模板中设置；移除SDK来源后保持空授权，不自动变成网页登录。
    const scopes = csv(settings.scopes);
    if (!scopes.includes("openid")) scopes.unshift("openid");
    settings.scopes = scopes.join(",");
    settings.idTokenSignatureAlgorithm ||= "RS256";
  } else
    settings.scopes = csv(settings.scopes)
      .filter((item) => item !== "openid")
      .join(",");
  if (settings.clientAuthenticationMethods === "none") {
    grants = grants.filter(
      (item) => !["client_credentials", "refresh_token"].includes(item)
    );
    settings.requireProofKey = true;
  } else
    grants = grants.filter(
      (item) =>
        !["sms", "email"].includes(item) &&
        (item !== "urn:macula:params:oauth:grant-type:external_code" ||
          isInApp(client))
    );
  if (isInApp(client))
    grants = grants.filter((item) =>
      [
        "authorization_code",
        "urn:macula:params:oauth:grant-type:external_code",
      ].includes(item)
    );
  if (client.applicationType === "SERVICE") grants = ["client_credentials"];
  if (grants.includes("client_credentials"))
    settings.scopes = csv(settings.scopes)
      .filter((item) => item !== "profile")
      .join(",");
  if (grants.includes("authorization_code")) settings.requireProofKey = true;
  if (settings.clientAuthenticationMethods === "private_key_jwt")
    settings.signingAlgorithm ||= "RS256";
  settings.authorizationGrantTypes = [...new Set([...grants, ...retired])].join(
    ","
  );
  return old !== JSON.stringify(settings);
}
