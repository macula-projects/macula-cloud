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
package dev.macula.cloud.iam.protocol.oauth2.client.impl;

import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2ClientRegistration;
import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2ClientRegistrationService;

import dev.macula.boot.mapstruct.DateTimeConverter;
import dev.macula.cloud.iam.mapper.SysIamApplicationProtocolConfigMapper;
import dev.macula.cloud.iam.pojo.entity.SysIamApplicationProtocolConfig;
import dev.macula.cloud.iam.protocol.oauth2.OAuth2ClientProtocolPolicy;
import dev.macula.cloud.iam.service.application.IamApplicationService;
import dev.macula.cloud.iam.service.application.IamApplicationSnapshot;
import dev.macula.cloud.system.enums.IamApplicationStatus;
import dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration;
import dev.macula.cloud.system.protocol.OAuth2ApplicationProtocolSettings;
import dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration;
import dev.macula.cloud.system.protocol.oauth2.OAuth2ProtocolSettings;
import dev.macula.cloud.system.security.OAuth2ClientCacheNames;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * OAuth发布适配器：在同一RR事务中组合公共快照、专有设置与受保护凭据。
 * 不将OAuth投影重新映射为主表，也不让草稿或未分配槽创建RegisteredClient。
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class OAuth2ClientRegistrationServiceImpl implements OAuth2ClientRegistrationService {
    private final SysIamApplicationProtocolConfigMapper protocols;
    private final IamApplicationService applications;
    private final DateTimeConverter dates;
    private final JsonMapper json = JsonMapper.builder().build();

    /** 公开client_id从唯一OAuth命名空间读取，不能按主表代码或默认协议猜测。 */
    @Override
    public OAuth2ClientRegistration getClientByClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        SysIamApplicationProtocolConfig row = protocols.selectByOAuth2Identifier(clientId);
        if (row == null) {
            return null;
        }
        try {
            IamApplicationSnapshot app = applications.findById(row.getApplicationId());
            if (app == null || app.status() != IamApplicationStatus.ENABLED || app.protocol() == null
                || !app.protocol().assigned() || !app.protocol().enabled()
                || !(app.protocol().settings() instanceof OAuth2ApplicationProtocolSettings settings)) {
                return null;
            }
            var result = project(app, row, settings);
            return OAuth2ClientProtocolPolicy.enabled(result) ? result : null;
        } catch (RuntimeException error) {
            return null;
        }
    }

    /** 权威Origin仍由各有效协议定义；索引只缓存非秘密精确Origin。 */
    @Override
    @Cacheable(value = OAuth2ClientCacheNames.TOKEN_ORIGIN_INDEX,
        key = "'" + OAuth2ClientCacheNames.ORIGIN_INDEX_KEY + "'", sync = true)
    public Set<String> allowedOrigins() {
        Set<String> result = new LinkedHashSet<>();
        for (String identifier : protocols.selectEnabledOAuth2Identifiers()) {
            var client = getClientByClientId(identifier);
            if (client != null) {
                client.getProtocolConfigurations().stream().filter(ApplicationProtocolConfiguration::enabled)
                    .map(OAuth2ProtocolConfiguration::settings).forEach(settings -> result.addAll(settings.getAllowedOrigins()));
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /** 每次校验重新读取权威登记，失效Client不能借旧索引放行。 */
    @Override
    public boolean isAllowedOrigin(String clientId, String origin) {
        if (origin == null || origin.isBlank()) {
            return false;
        }
        var client = getClientByClientId(clientId);
        return client != null && client.getProtocolConfigurations().stream().filter(ApplicationProtocolConfiguration::enabled)
            .map(OAuth2ProtocolConfiguration::settings).anyMatch(settings -> settings.getAllowedOrigins().contains(origin));
    }

    /** 登录来源与许可分别投影；凭据只在OAuth适配器内读取，不进入公共快照。 */
    private OAuth2ClientRegistration project(IamApplicationSnapshot app, SysIamApplicationProtocolConfig row,
        OAuth2ApplicationProtocolSettings payload) {
        var result = new OAuth2ClientRegistration();
        result.setAllowedUserTypes(app.allowedUserTypes());
        result.setId(app.id());
        result.setPurpose(app.purpose().name());
        result.setManagedKey(app.managedKey());
        result.setAuthorizationVersion(app.authorizationVersion());
        result.setResourcePermissions(json.writeValueAsString(app.resourcePermissions()));
        result.setConfigurationStatus(app.status().value());
        result.setClientId(row.getProtocolIdentifier());
        result.setClientIdIssuedAt(dates.toInstant(row.getIdentifierIssuedAt()));
        result.setClientSecret(row.getCredentialHash());
        result.setClientSecretExpiresAt(dates.toInstant(row.getCredentialExpiresAt()));
        result.setClientName(app.applicationName());
        result.setTenantId(app.tenantId());
        result.setUserType(app.accountUserType());
        var login = app.login();
        result.setApplicationType(login.type().name());
        result.setWebLoginMode(login.webLoginMode() == null ? null : login.webLoginMode().value());
        result.setMiniappPlatform(login.miniappPlatform());
        result.setHostedLoginMethods(login.hostedMethods() == null ? null
            : login.hostedMethods().stream().map(Enum::name).sorted().collect(Collectors.joining(",")));
        Map<String, Long> browser = new TreeMap<>();
        Map<String, Long> providers = new TreeMap<>();
        Map<String, Long> external = new TreeMap<>();
        login.sources().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(route -> {
            var capability = route.getKey();
            if (capability.browser()) {
                browser.put(capability.value(), route.getValue());
                providers.putIfAbsent(capability.source().providerType(), route.getValue());
            } else if (capability.code() != null) {
                external.put(capability.code().name(), route.getValue());
            }
        });
        Set<String> permitted = payload.getExternalCodeTypes().stream().map(Enum::name).collect(Collectors.toUnmodifiableSet());
        if (!external.keySet().equals(permitted)) {
            throw new IllegalArgumentException("OAuth Code许可与来源关联不一致");
        }
        result.setExternalCodeTypes(String.join(",", new TreeSet<>(permitted)));
        result.setExternalProviderBindings(Map.copyOf(external));
        result.setBrowserCapabilityBindings(json.writeValueAsString(browser));
        result.setBrowserProviderBindings(json.writeValueAsString(providers));
        result.setBrowserLoginProviderTypes(String.join(",", providers.keySet()));
        result.setDirectLoginProviderType(login.directCapability() == null ? null : login.directCapability().source().providerType());
        OAuth2ProtocolSettings settings = payload.getOauth2();
        BeanUtils.copyProperties(settings, result, "requireProofKey", "requireAuthorizationConsent", "reuseRefreshTokens",
            "authorizationCodeTimeToLive", "accessTokenTimeToLive", "refreshTokenTimeToLive", "allowedOrigins");
        result.setRequireProofKey(Boolean.TRUE.equals(settings.getRequireProofKey()));
        result.setRequireAuthorizationConsent(Boolean.TRUE.equals(settings.getRequireAuthorizationConsent()));
        result.setReuseRefreshTokens(Boolean.TRUE.equals(settings.getReuseRefreshTokens()));
        result.setAllowedOrigins(String.join(",", settings.getAllowedOrigins()));
        result.setAuthorizationCodeTimeToLive(duration(settings.getAuthorizationCodeTimeToLive()));
        result.setAccessTokenTimeToLive(duration(settings.getAccessTokenTimeToLive()));
        result.setRefreshTokenTimeToLive(duration(settings.getRefreshTokenTimeToLive()));
        result.setProtocolConfigurations("RESOURCE_SERVER".equals(result.getPurpose()) ? List.of()
            : List.of(new ApplicationProtocolConfiguration(row.getProtocol(), 1, Boolean.TRUE.equals(row.getEnabled()), settings)));
        return result;
    }

    /** 只转换已验证的秒值，不从旧主表或其他协议补默认。 */
    private Duration duration(Long seconds) {
        return seconds == null ? null : Duration.ofSeconds(seconds);
    }
}
