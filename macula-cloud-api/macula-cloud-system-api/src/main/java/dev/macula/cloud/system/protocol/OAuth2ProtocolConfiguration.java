/*
 * Copyright (c) 2026 Macula
 *   macula.dev, China
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.macula.cloud.system.protocol;

/**
 * OAuth家族的安全类型投影，不为未来CAS/SAML强加OAuth配置。
 * @author Rain
 * @since 6.1.0
 */
public final class OAuth2ProtocolConfiguration {
    /** CSV是现有协议边界格式，拒绝重复/空片段以免隐藏无效授权。 */
    public static java.util.Set<String> csv(String value) {
        if (value == null || value.isBlank()) {
            return java.util.Set.of();
        }
        var values = java.util.Arrays.stream(value.split(",", -1)).map(String::trim).toList();
        if (values.stream().anyMatch(String::isEmpty) || java.util.Set.copyOf(values).size() != values.size()) {
            throw new IllegalArgumentException("协议字段结构无效");
        }
        return java.util.Set.copyOf(values);
    }

    private static final java.util.Set<String> AUTH = java.util.Set.of("none", "client_secret_basic", "client_secret_post", "private_key_jwt");
    private static final java.util.Set<String> GRANTS = java.util.Set.of("authorization_code", "refresh_token", "client_credentials", "sms", "email", "urn:macula:params:oauth:grant-type:external_code");

    /** 纯协议校验可在Flyway运行，业务Scope目录与身份源由System服务额外校验。 */
    public static void validate(ApplicationProtocolConfiguration configuration, String applicationType) {
        validate(configuration, applicationType, false);
    }

    /** 当前运行时允许OAuth profile及OIDC第三方Code；历史迁移仍使用旧合同。 */
    public static void validateRuntime(ApplicationProtocolConfiguration configuration, String applicationType) {
        validate(configuration, applicationType, true);
    }

    /** 明确隔离迁移与当前能力，避免修改历史迁移的可重复执行语义。 */
    private static void validate(ApplicationProtocolConfiguration configuration, String applicationType, boolean currentRuntime) {
        var settings = settings(configuration);
        var auth = csv(settings.getClientAuthenticationMethods());
        var grants = csv(settings.getAuthorizationGrantTypes());
        var scopes = csv(settings.getScopes());
        if (!AUTH.containsAll(auth) || !GRANTS.containsAll(grants) || auth.contains("none") && auth.size() != 1) {
            throw new IllegalArgumentException("认证或授权方式不受支持");
        }
        boolean oidc = ApplicationProtocolRegistry.OIDC.equals(configuration.protocol());
        var oidcGrants = currentRuntime
            ? java.util.Set.of("authorization_code", "refresh_token", dev.macula.cloud.system.enums.ExternalCodeType.GRANT_TYPE)
            : java.util.Set.of("authorization_code", "refresh_token");
        boolean external = currentRuntime && grants.contains(dev.macula.cloud.system.enums.ExternalCodeType.GRANT_TYPE);
        if (currentRuntime && "SERVICE".equals(applicationType) && (oidc || auth.contains("none")
            || grants.stream().anyMatch(grant -> !"client_credentials".equals(grant))
            || scopes.stream().anyMatch(scope -> java.util.Set.of("openid", "profile", "email", "phone", "address", "offline_access").contains(scope)))) {
            throw new IllegalArgumentException("后端服务仅允许OAuth2.1客户端凭据及业务Scope");
        }
        boolean publicApp = java.util.Set.of("SPA", "NATIVE", "MINIAPP").contains(applicationType);
        if (publicApp && !auth.isEmpty() && !auth.equals(java.util.Set.of("none"))
            || auth.contains("none") && (grants.contains("client_credentials") || grants.contains("refresh_token"))
            || !oidc && (scopes.contains("openid") || !currentRuntime && scopes.contains("profile"))
            || oidc && grants.stream().anyMatch(grant -> !oidcGrants.contains(grant))) {
            throw new IllegalArgumentException("协议草稿包含不适用的认证或授权");
        }
        if (external && !auth.equals(java.util.Set.of("none"))) {
            throw new IllegalArgumentException("第三方Code登录必须使用公共客户端none认证");
        }
        if (currentRuntime && scopes.contains("profile") && grants.contains("client_credentials")) {
            throw new IllegalArgumentException("服务授权不能申请用户基础资料");
        }
        if (!configuration.enabled()) {
            return;
        }
        if (settings.getAllowedOrigins() == null || settings.getRequireProofKey() == null
            || settings.getRequireAuthorizationConsent() == null || settings.getReuseRefreshTokens() == null
            || settings.getAccessTokenTimeToLive() == null || settings.getAccessTokenTimeToLive() < 60 || settings.getAccessTokenTimeToLive() > 86400
            || settings.getAuthorizationCodeTimeToLive() == null || settings.getAuthorizationCodeTimeToLive() < 30 || settings.getAuthorizationCodeTimeToLive() > 3600
            || settings.getRefreshTokenTimeToLive() == null || settings.getRefreshTokenTimeToLive() < 300 || settings.getRefreshTokenTimeToLive() > 31536000
            || !java.util.Set.of("reference", "self-contained").contains(settings.getTokenFormat())
            || auth.isEmpty() || grants.isEmpty() || publicApp && !auth.equals(java.util.Set.of("none"))
            || ("SERVICE".equals(applicationType) || !currentRuntime && "MINIAPP".equals(applicationType)) && oidc
            || auth.contains("none") && (grants.contains("client_credentials") || grants.contains("refresh_token"))
            || oidc && (!grants.contains("authorization_code") && !external || !scopes.contains("openid")
                || grants.stream().anyMatch(grant -> !oidcGrants.contains(grant)))
            || !oidc && (scopes.contains("openid") || !currentRuntime && scopes.contains("profile"))
            || grants.contains("authorization_code") && (!Boolean.TRUE.equals(settings.getRequireProofKey())
                || csv(settings.getRedirectUris()).isEmpty() || scopes.isEmpty())
            || Boolean.FALSE.equals(settings.getRequireProofKey()) && auth.contains("none")) {
            throw new IllegalArgumentException("当前协议或应用不允许此认证授权组合");
        }
    }

    /** 禁止实例化转换工具。 */
    private OAuth2ProtocolConfiguration() { }

    /** 校验协议与结构版本后返回OAuth配置，未知类型一律拒绝。 */
    public static OAuth2ProtocolSettings settings(ApplicationProtocolConfiguration configuration) {
        ApplicationProtocolRegistry.require(configuration);
        return (OAuth2ProtocolSettings) configuration.settings();
    }
}
