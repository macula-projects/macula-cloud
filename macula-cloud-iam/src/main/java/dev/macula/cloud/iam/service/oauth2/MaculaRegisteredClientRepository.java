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

package dev.macula.cloud.iam.service.oauth2;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.macula.boot.constants.CacheConstants;
import dev.macula.cloud.iam.mapper.SysOAuth2ClientExternalProviderMapper;
import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.pojo.entity.SysOAuth2ClientExternalProvider;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeClientSettings;
import dev.macula.cloud.iam.service.support.SysOAuth2ClientService;
import dev.macula.cloud.system.enums.ExternalCodeType;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jose.jws.JwsAlgorithm;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

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
    @Cacheable(value = CacheConstants.OAUTH2_CLIENT_CACHE_KEY, key = "#id", unless = "#result == null")
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
    @Cacheable(value = CacheConstants.OAUTH2_CLIENT_CACHE_KEY, key = "#clientId", unless = "#result == null")
    public RegisteredClient findByClientId(String clientId) {
        // @formatter:off
        SysOAuth2Client oauth2Client = oauth2ClientService.getClientByClientId(clientId);
        if (oauth2Client == null) {
            return null;
        }

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
            grants -> StringUtils.commaDelimitedListToSet(grants)
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
        builder.scope(OidcScopes.OPENID);

        // Client Settings
        ClientSettings.Builder clientSettingsBuilder = ClientSettings.builder()
            .requireProofKey(oauth2Client.isRequireProofKey() ||
                StringUtils.commaDelimitedListToSet(oauth2Client.getClientAuthenticationMethods()).contains("none"))
            .requireAuthorizationConsent(oauth2Client.isRequireAuthorizationConsent());
        Set<String> externalCodeTypes = parseExternalCodeTypes(oauth2Client.getExternalCodeTypes());
        clientSettingsBuilder.setting(ExternalCodeClientSettings.CODE_TYPES, externalCodeTypes);
        clientSettingsBuilder.setting(ExternalCodeClientSettings.ALLOWED_ORIGINS,
            immutableCsv(oauth2Client.getAllowedOrigins()));
        clientSettingsBuilder.setting(ExternalCodeClientSettings.PROVIDER_BINDINGS,
            providerBindings(oauth2Client.getClientId(), externalCodeTypes));
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

    private Map<String, Long> providerBindings(String clientId, Set<String> allowedCodeTypes) {
        Map<String, Long> mappings = externalProviderMapper.selectList(
            new LambdaQueryWrapper<SysOAuth2ClientExternalProvider>()
                .eq(SysOAuth2ClientExternalProvider::getClientId, clientId)
                .orderByAsc(SysOAuth2ClientExternalProvider::getCodeType))
            .stream().filter(mapping -> allowedCodeTypes.contains(mapping.getCodeType()))
            .collect(Collectors.toMap(SysOAuth2ClientExternalProvider::getCodeType,
                SysOAuth2ClientExternalProvider::getIdentityProviderId,
                (left, right) -> left, LinkedHashMap::new));
        if (!mappings.keySet().equals(new TreeSet<>(allowedCodeTypes))) {
            return Map.of();
        }
        return Map.copyOf(mappings);
    }
}
