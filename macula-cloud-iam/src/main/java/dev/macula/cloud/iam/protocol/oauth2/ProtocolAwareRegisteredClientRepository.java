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
package dev.macula.cloud.iam.protocol.oauth2;

import dev.macula.cloud.iam.service.support.SysOAuth2ClientService;
import dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration;
import dev.macula.cloud.system.protocol.OAuth2ProtocolSettings;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeClientSettings;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.time.Duration;
import java.util.Set;

/**
 * 无请求缓存的协议视图；基础仓库仅恢复授权，不能反向依赖此适配器。
 * @author Rain
 * @since 6.1.0
 */
public final class ProtocolAwareRegisteredClientRepository implements RegisteredClientRepository {
    private final RegisteredClientRepository base;
    private final SysOAuth2ClientService clients;
    private final ObjectProvider<OAuth2AuthorizationService> authorizations;

    /** 延迟获取授权服务打断装配环，实际授权恢复仍只使用基础仓库。 */
    public ProtocolAwareRegisteredClientRepository(RegisteredClientRepository base, SysOAuth2ClientService clients,
        ObjectProvider<OAuth2AuthorizationService> authorizations) {
        this.base = base;
        this.clients = clients;
        this.authorizations = authorizations;
    }

    /** 配置只能由System受控服务保存，协议适配器不写配置。 */
    @Override
    public void save(RegisteredClient client) {
        throw new UnsupportedOperationException();
    }

    /** 本平台的registeredClient id与clientId一致；任何请求视图都不进入全局缓存。 */
    @Override
    public RegisteredClient findById(String id) {
        return findByClientId(id);
    }

    /** 先读取基础身份，再按服务端请求/原授权生成独立策略。 */
    @Override
    public RegisteredClient findByClientId(String clientId) {
        RegisteredClient identity = base.findByClientId(clientId);
        var client = clients.getClientByClientId(clientId);
        if (identity == null || client == null) {
            return null;
        }
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }
        String path = attributes.getRequest().getRequestURI().substring(attributes.getRequest().getContextPath().length());
        if ("RESOURCE_SERVER".equals(client.getPurpose())) {
            if (!"/oauth2/introspect".equals(path)) { throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT); }
            // 即使提交后缓存清理失败，也不能用缓存中的旧Secret或JWKS继续认证。
            var resourceSettings = ClientSettings.builder()
                .setting(dev.macula.cloud.system.security.OAuth2ResourcePolicy.AUTHORIZATION_VERSION, client.getAuthorizationVersion())
                .setting(ApplicationProtocolResolver.EFFECTIVE_PROTOCOL, "RESOURCE_SERVER");
            var methods = OAuth2ProtocolConfiguration.csv(client.getClientAuthenticationMethods());
            if (methods.contains("private_key_jwt")) {
                resourceSettings.jwkSetUrl(client.getJwkSetUrl()).tokenEndpointAuthenticationSigningAlgorithm(
                    SignatureAlgorithm.from(client.getSigningAlgorithm()));
            }
            return RegisteredClient.from(identity).clientSecret(client.getClientSecret())
                .clientSecretExpiresAt(client.getClientSecretExpiresAt())
                .clientAuthenticationMethods(values -> {
                    values.clear();
                    methods.forEach(value -> values.add(new ClientAuthenticationMethod(value)));
                }).authorizationGrantTypes(values -> {
                    values.clear();
                    values.add(new AuthorizationGrantType(dev.macula.cloud.system.security.OAuth2ResourcePolicy.INTROSPECTION_CAPABILITY));
                }).scopes(Set::clear).redirectUris(Set::clear).clientSettings(resourceSettings.build()).build();
        }
        if ("/oauth2/introspect".equals(path)) { throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT); }
        if (Set.of(dev.macula.cloud.iam.controller.ProtocolVerificationCodeController.CAPTCHA_PATH,
            dev.macula.cloud.iam.controller.ProtocolVerificationCodeController.SMS_CODE_PATH,
            dev.macula.cloud.iam.controller.ProtocolVerificationCodeController.EMAIL_CODE_PATH).contains(path)
            && client.getProtocolConfigurations().stream().noneMatch(value -> "OAUTH2_1".equals(value.protocol()) && value.enabled())) {
            return null;
        }
        dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration configuration;
        try {
            configuration = new ApplicationProtocolResolver(authorizations.getObject()).resolve(client, attributes.getRequest());
        } catch (OAuth2AuthenticationException | IllegalArgumentException ex) {
            // authorize原生失败处理器要求专用异常；未验证redirect之前不能回跳调用方URL。
            if (attributes.getRequest().getRequestURI().equals(attributes.getRequest().getContextPath() + "/oauth2/authorize")) {
                throw new org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException(
                    new org.springframework.security.oauth2.core.OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST), null);
            }
            if (ex instanceof OAuth2AuthenticationException authentication) { throw authentication; }
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }
        OAuth2ProtocolSettings settings = OAuth2ProtocolConfiguration.settings(configuration);
        var auth = OAuth2ProtocolConfiguration.csv(settings.getClientAuthenticationMethods());
        var grants = OAuth2ProtocolConfiguration.csv(settings.getAuthorizationGrantTypes());
        var scopes = OAuth2ProtocolConfiguration.csv(settings.getScopes());
        ClientSettings.Builder clientSettings = ClientSettings.withSettings(identity.getClientSettings().getSettings())
            .requireProofKey(Boolean.TRUE.equals(settings.getRequireProofKey()) || auth.contains("none"))
            .requireAuthorizationConsent(Boolean.TRUE.equals(settings.getRequireAuthorizationConsent()))
            .setting(ApplicationProtocolResolver.EFFECTIVE_PROTOCOL, configuration.protocol())
            .setting(dev.macula.cloud.system.security.OAuth2ResourcePolicy.AUTHORIZATION_VERSION, client.getAuthorizationVersion())
            .setting(ResourceAuthorizationPolicy.PERMISSIONS, ResourceAuthorizationPolicy.permissions(client))
            .setting(OAuth2ClientProtocolValidator.ENABLED_PROTOCOLS, Set.of(configuration.protocol()))
            .setting(ExternalCodeClientSettings.ALLOWED_ORIGINS, settings.getAllowedOrigins());
        // 身份源与应用类型必须来自本次RR快照，不能复用基础RegisteredClient缓存中的旧能力。
        clientSettings.setting(ExternalCodeClientSettings.APPLICATION_TYPE, client.getApplicationType());
        clientSettings.setting(ExternalCodeClientSettings.CODE_TYPES, OAuth2ProtocolConfiguration.csv(client.getExternalCodeTypes()));
        clientSettings.setting(ExternalCodeClientSettings.PROVIDER_BINDINGS, client.getExternalProviderBindings());
        if (client.getMiniappPlatform() != null) { clientSettings.setting(ExternalCodeClientSettings.MINIAPP_PLATFORM, client.getMiniappPlatform()); }
        clientSettings.setting(ExternalCodeClientSettings.TENANT_ID, client.getTenantId());
        if (client.getUserType() != null) {
            clientSettings.setting(ExternalCodeClientSettings.USER_TYPE, client.getUserType().getType());
        } else {
            // 机器Client不指向用户目录，也不能继承基础缓存中已移除的目录设置。
            clientSettings.settings(values -> values.remove(ExternalCodeClientSettings.USER_TYPE));
        }
        if (auth.contains("private_key_jwt")) {
            SignatureAlgorithm algorithm = SignatureAlgorithm.from(settings.getSigningAlgorithm());
            if (settings.getJwkSetUrl() == null || algorithm == null) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
            }
            clientSettings.jwkSetUrl(settings.getJwkSetUrl()).tokenEndpointAuthenticationSigningAlgorithm(algorithm);
        }
        TokenSettings tokenSettings = TokenSettings.builder()
            .authorizationCodeTimeToLive(seconds(settings.getAuthorizationCodeTimeToLive(), 300))
            .accessTokenTimeToLive(seconds(settings.getAccessTokenTimeToLive(), 3600))
            .accessTokenFormat(new OAuth2TokenFormat(settings.getTokenFormat() == null ? "reference" : settings.getTokenFormat()))
            .refreshTokenTimeToLive(seconds(settings.getRefreshTokenTimeToLive(), 3600))
            .reuseRefreshTokens(Boolean.TRUE.equals(settings.getReuseRefreshTokens()))
            .idTokenSignatureAlgorithm(settings.getIdTokenSignatureAlgorithm() == null ? SignatureAlgorithm.RS256
                : SignatureAlgorithm.from(settings.getIdTokenSignatureAlgorithm())).build();
        return RegisteredClient.from(identity)
            .clientSecret(client.getClientSecret()).clientSecretExpiresAt(client.getClientSecretExpiresAt())
            .clientAuthenticationMethods(values -> {values.clear(); auth.forEach(value -> values.add(new ClientAuthenticationMethod(value)));})
            .authorizationGrantTypes(values -> {values.clear(); grants.forEach(value -> values.add(new AuthorizationGrantType(value)));})
            .redirectUris(values -> {values.clear();
             values.addAll(OAuth2ProtocolConfiguration.csv(settings.getRedirectUris()));
            })
            .scopes(values -> {values.clear();
             values.addAll(scopes);
            })
            .clientSettings(clientSettings.build()).tokenSettings(tokenSettings).build();
    }

    /** 运行时拒绝非法寿命，不以回退默认值掩盖负值配置。 */
    private Duration seconds(Long configured, long fallback) {
        long value = configured == null ? fallback : configured;
        if (value <= 0 || value > 31536000) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }
        return Duration.ofSeconds(value);
    }
}
