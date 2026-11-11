/*
 * Copyright (c) 2023 Macula
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

package dev.macula.cloud.iam.protocol.oauth2.persistence;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import cn.hutool.core.util.StrUtil;
import dev.macula.boot.constants.CacheConstants;
import dev.macula.cloud.iam.mapper.SysOAuth2ClientExternalProviderMapper;
import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.protocol.oauth2.OAuth2ClientProtocolValidator;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeClientSettings;
import dev.macula.cloud.iam.service.client.SysOAuth2ClientService;
import dev.macula.cloud.system.enums.ExternalCodeType;
import dev.macula.cloud.system.enums.OAuth2ProtocolMode;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.JwsAlgorithm;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.util.StringUtils;

/**
 * {@code MaculaRegisteredClientRepository} 查询客户端相关信息实现
 *
 * @author lengleng
 * @since 2022/5/29
 */
@RequiredArgsConstructor
public class MaculaRegisteredClientRepository implements RegisteredClientRepository {

    /**
     * 刷新令牌有效期默认 30 天
     */
    private final static Duration refreshTokenValiditySeconds = Duration.ofDays(30);

    /**
     * 请求令牌有效期默认 12 小时
     */
    private final static Duration accessTokenValiditySeconds = Duration.ofHours(12);

    /**
     * 请求的CODE的默认有效期，5分钟
     */
    private final static Duration authorizationCodeValiditySeconds = Duration.ofMinutes(5);

    private final SysOAuth2ClientService oauth2ClientService;

    private final SysOAuth2ClientExternalProviderMapper externalProviderMapper;

    /**
     * Saves the registered client.
     *
     * <p>
     * IMPORTANT: Sensitive information should be encoded externally from the implementation, e.g.
     * {@link RegisteredClient#getClientSecret()}
     *
     * @param registeredClient the {@link RegisteredClient}
     */
    @Override
    public void save(RegisteredClient registeredClient) {
        // 需要过滤掉OidcScopes.OPENID
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the registered client identified by the provided {@code id}, or {@code null} if not found.
     *
     * @param id the registration identifier
     * @return the {@link RegisteredClient} if found, otherwise {@code null}
     */
    @Override
    @Cacheable(value = CacheConstants.OAUTH2_CLIENT_CACHE_KEY, key = "'" +
            dev.macula.cloud.system.security.OAuth2ClientCacheNames.CONFIGURATION_VERSION +
            ":' + #id", unless = "#result == null")
    public RegisteredClient findById(String id) {
        return this.findByClientId(id);
    }

    /**
     * 重写原生方法支持redis缓存
     *
     * @param clientId 客户端ID
     * @return RegisteredClient
     */
    @Override
    @SneakyThrows
    @Cacheable(value = CacheConstants.OAUTH2_CLIENT_CACHE_KEY, key = "'" +
            dev.macula.cloud.system.security.OAuth2ClientCacheNames.CONFIGURATION_VERSION +
            ":' + #clientId", unless = "#result == null")
    public RegisteredClient findByClientId(String clientId) {
        // @formatter:off
        SysOAuth2Client oauth2Client = oauth2ClientService.getClientByClientId(clientId);
        if (oauth2Client == null) {
            return null;
        }

        if ("RESOURCE_SERVER".equals(oauth2Client.getPurpose())) {
            ClientSettings.Builder resourceSettings = ClientSettings.builder();
            if (StringUtils.hasText(oauth2Client.getJwkSetUrl())) {
                resourceSettings.jwkSetUrl(oauth2Client.getJwkSetUrl()).tokenEndpointAuthenticationSigningAlgorithm(
                    SignatureAlgorithm.from(oauth2Client.getSigningAlgorithm()));
            }
            RegisteredClient.Builder resource = RegisteredClient.withId(clientId).clientId(clientId)
                .clientName(oauth2Client.getClientName()).clientSecret(oauth2Client.getClientSecret())
                .clientSecretExpiresAt(oauth2Client.getClientSecretExpiresAt())
                .authorizationGrantType(new AuthorizationGrantType(dev.macula.cloud.system.security.OAuth2ResourcePolicy.INTROSPECTION_CAPABILITY));
            immutableCsv(oauth2Client.getClientAuthenticationMethods()).forEach(method -> resource.clientAuthenticationMethod(new ClientAuthenticationMethod(method)));
            return resource.clientSettings(resourceSettings.build()).build();
        }
        Set<OAuth2ProtocolMode> protocols = dev.macula.cloud.iam.protocol.oauth2.OAuth2ClientProtocolPolicy.protocols(oauth2Client);
        java.util.List<dev.macula.cloud.system.protocol.OAuth2ProtocolSettings> settings = oauth2Client.getProtocolConfigurations().stream()
            .filter(dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration::enabled)
            .map(dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration::settings).toList();
        // 基础对象只用于恢复持久化授权；运行时会在无缓存的请求视图中再次收窄。
        oauth2Client = copyForBase(oauth2Client, settings);
        RegisteredClient.Builder builder = RegisteredClient
            .withId(oauth2Client.getClientId())
            .clientId(oauth2Client.getClientId())
            .clientIdIssuedAt(oauth2Client.getClientIdIssuedAt())
            .clientSecret(oauth2Client.getClientSecret())
            .clientSecretExpiresAt(oauth2Client.getClientSecretExpiresAt())
            .clientName(oauth2Client.getClientName());

        // 客户端授权方式
        Optional.ofNullable(oauth2Client.getClientAuthenticationMethods()).ifPresent(
            clientAuthMethods -> StringUtils.commaDelimitedListToSet(clientAuthMethods)
                .forEach(s -> builder.clientAuthenticationMethod(new ClientAuthenticationMethod(s)))
        );

        // 授权模式
        Optional.ofNullable(oauth2Client.getAuthorizationGrantTypes()).ifPresent(
            configuredGrants -> StringUtils.commaDelimitedListToSet(configuredGrants)
                .forEach(s -> builder.authorizationGrantType(new AuthorizationGrantType(s)))
        );

        // 回调地址
        Optional.ofNullable(oauth2Client.getRedirectUris()).ifPresent(
            redirectUri -> Arrays.stream(redirectUri.split(StrUtil.COMMA))
                .filter(StrUtil::isNotBlank)
                .forEach(builder::redirectUri)
        );

        // 授权范围
        Optional.ofNullable(oauth2Client.getScopes()).ifPresent(
            scope -> Arrays.stream(scope.split(StrUtil.COMMA))
                .filter(StrUtil::isNotBlank)
                .forEach(builder::scope)
        );


        // Client Settings
        ClientSettings.Builder clientSettingsBuilder = ClientSettings.builder()
            .requireProofKey(oauth2Client.isRequireProofKey() ||
                StringUtils.commaDelimitedListToSet(oauth2Client.getClientAuthenticationMethods()).contains("none"))
            .requireAuthorizationConsent(oauth2Client.isRequireAuthorizationConsent());
        clientSettingsBuilder.setting(OAuth2ClientProtocolValidator.ENABLED_PROTOCOLS,
            protocols.stream().map(OAuth2ProtocolMode::value).collect(java.util.stream.Collectors.toUnmodifiableSet()));
        clientSettingsBuilder.setting(OAuth2ClientProtocolValidator.DEFAULT_PROTOCOL, protocols.iterator().next().value());
        Set<String> externalCodeTypes = parseExternalCodeTypes(oauth2Client.getExternalCodeTypes());
        clientSettingsBuilder.setting(ExternalCodeClientSettings.CODE_TYPES, externalCodeTypes);
        if (oauth2Client.getApplicationType() != null) {
            clientSettingsBuilder.setting(ExternalCodeClientSettings.APPLICATION_TYPE, oauth2Client.getApplicationType());
        }
        if (oauth2Client.getMiniappPlatform() != null) {
            clientSettingsBuilder.setting(ExternalCodeClientSettings.MINIAPP_PLATFORM, oauth2Client.getMiniappPlatform());
        }
        clientSettingsBuilder.setting(ExternalCodeClientSettings.ALLOWED_ORIGINS,
            immutableCsv(oauth2Client.getAllowedOrigins()));
        clientSettingsBuilder.setting(ExternalCodeClientSettings.PROVIDER_BINDINGS,
            oauth2Client.getExternalProviderBindings().keySet().equals(externalCodeTypes) ? oauth2Client.getExternalProviderBindings() : Map.of());
        if (oauth2Client.getTenantId() != null) {
            clientSettingsBuilder.setting(ExternalCodeClientSettings.TENANT_ID, oauth2Client.getTenantId());
        }
        if (oauth2Client.getUserType() != null) {
            clientSettingsBuilder.setting(ExternalCodeClientSettings.USER_TYPE, oauth2Client.getUserType().getType());
        }
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.from(oauth2Client.getSigningAlgorithm());
        JwsAlgorithm jwsAlgorithm = signatureAlgorithm == null ? MacAlgorithm.from(oauth2Client.getSigningAlgorithm()) : signatureAlgorithm;
        if (jwsAlgorithm != null) {
            clientSettingsBuilder.tokenEndpointAuthenticationSigningAlgorithm(jwsAlgorithm);
        }
        if (StringUtils.hasText(oauth2Client.getJwkSetUrl())) {
            clientSettingsBuilder.jwkSetUrl(oauth2Client.getJwkSetUrl());
        }

        // Token Settings
        TokenSettings.Builder tokenSettingsBuilder = TokenSettings.builder()
            .authorizationCodeTimeToLive(Optional.ofNullable(oauth2Client.getAuthorizationCodeTimeToLive())
                .orElse(authorizationCodeValiditySeconds))
            .accessTokenTimeToLive(Optional.ofNullable(oauth2Client.getAccessTokenTimeToLive())
                .orElse(accessTokenValiditySeconds))
            .accessTokenFormat(Optional.ofNullable(oauth2Client.getTokenFormat())
                .map(OAuth2TokenFormat::new)
                .orElse(OAuth2TokenFormat.REFERENCE))
            .reuseRefreshTokens(oauth2Client.isReuseRefreshTokens())
            .refreshTokenTimeToLive(Optional.ofNullable(oauth2Client.getRefreshTokenTimeToLive())
                .orElse(refreshTokenValiditySeconds))
            .idTokenSignatureAlgorithm(Optional.ofNullable(oauth2Client.getIdTokenSignatureAlgorithm())
                .map(SignatureAlgorithm::from)
                .orElse(SignatureAlgorithm.RS256));

        return builder
            .tokenSettings(tokenSettingsBuilder.build())
            .clientSettings(clientSettingsBuilder.build())
            .build();

        // @formatter:on
    }

    /**
     * 构造基础能力并集，保留原对象中的子配置，绝不写回数据库或请求缓存。
     */
    private SysOAuth2Client copyForBase(SysOAuth2Client source,
            java.util.List<dev.macula.cloud.system.protocol.OAuth2ProtocolSettings> settings) {
        SysOAuth2Client result = new SysOAuth2Client();
        org.springframework.beans.BeanUtils.copyProperties(source, result);
        result.setClientAuthenticationMethods(union(settings,
                dev.macula.cloud.system.protocol.OAuth2ProtocolSettings::getClientAuthenticationMethods));
        result.setAuthorizationGrantTypes(
                union(settings, dev.macula.cloud.system.protocol.OAuth2ProtocolSettings::getAuthorizationGrantTypes));
        result.setRedirectUris(
                union(settings, dev.macula.cloud.system.protocol.OAuth2ProtocolSettings::getRedirectUris));
        result.setScopes(union(settings, dev.macula.cloud.system.protocol.OAuth2ProtocolSettings::getScopes));
        result.setAllowedOrigins(
                settings.stream().flatMap(value -> value.getAllowedOrigins().stream()).distinct().sorted()
                        .collect(java.util.stream.Collectors.joining(",")));
        // 这里只给序列化恢复一个合法默认；任何签发必须经协议视图选择真实设置。
        result.setRequireProofKey(true);
        result.setRequireAuthorizationConsent(false);
        result.setAuthorizationCodeTimeToLive(java.time.Duration.ofSeconds(300));
        result.setAccessTokenTimeToLive(java.time.Duration.ofSeconds(3600));
        result.setRefreshTokenTimeToLive(java.time.Duration.ofSeconds(3600));
        result.setTokenFormat("reference");
        result.setReuseRefreshTokens(false);
        result.setIdTokenSignatureAlgorithm("RS256");
        result.setJwkSetUrl(null);
        result.setSigningAlgorithm(null);
        return result;
    }

    /**
     * 基础并集不授予额外协议权限，只用于框架反序列化所需字段。
     */
    private String union(java.util.List<dev.macula.cloud.system.protocol.OAuth2ProtocolSettings> values,
            java.util.function.Function<dev.macula.cloud.system.protocol.OAuth2ProtocolSettings, String> getter) {
        return values.stream().flatMap(
                        value -> dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration.csv(getter.apply(value)).stream())
                .distinct().sorted().collect(java.util.stream.Collectors.joining(","));
    }

    private Set<String> parseExternalCodeTypes(String value) {
        Set<String> values = immutableCsv(value);
        for (String codeType : values) {
            ExternalCodeType.fromValue(codeType);
        }
        return values;
    }

    private Set<String> immutableCsv(String value) {
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        return Arrays.stream(value.split(StrUtil.COMMA)).map(String::trim).filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
    }

}
