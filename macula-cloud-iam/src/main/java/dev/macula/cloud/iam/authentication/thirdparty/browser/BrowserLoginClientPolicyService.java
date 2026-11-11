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
package dev.macula.cloud.iam.authentication.thirdparty.browser;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.service.client.SysOAuth2ClientService;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService;
import dev.macula.cloud.system.enums.BrowserLoginProviderType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponseType;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 从受控授权请求恢复 OAuth Client 的浏览器第三方登录策略。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class BrowserLoginClientPolicyService {
    private final SysOAuth2ClientService clientService;
    private final AuthorizationServerSettings authorizationServerSettings;
    private final IdentityProviderConfigService configs;
    private final tools.jackson.databind.json.JsonMapper json = tools.jackson.databind.json.JsonMapper.builder()
            .build();
    private final RequestCache requestCache = new HttpSessionRequestCache();

    /**
     * 从当前 Session 的 SavedRequest 解析登录策略。
     *
     * @param request 当前请求
     * @param response 当前响应
     * @return 没有受控授权请求时为空
     */
    public Optional<ClientPolicy> resolve(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest == null) {
            return Optional.empty();
        }
        validateAuthorizationRequest(request, savedRequest);
        String[] clientIds = savedRequest.getParameterValues(OAuth2ParameterNames.CLIENT_ID);
        String[] responseTypes = savedRequest.getParameterValues(OAuth2ParameterNames.RESPONSE_TYPE);
        if (clientIds == null || clientIds.length != 1 || !StringUtils.hasText(clientIds[0])
                || responseTypes == null || responseTypes.length != 1
                || !OAuth2AuthorizationResponseType.CODE.getValue().equals(responseTypes[0])) {
            throw unavailable();
        }
        SysOAuth2Client client;
        try {
            client = clientService.getClientByClientId(clientIds[0]);
        } catch (RuntimeException ex) {
            throw unavailable();
        }
        if (client == null) {
            throw unavailable();
        }
        String[] savedScopes = savedRequest.getParameterValues("scope");
        if (savedScopes == null || savedScopes.length != 1 || savedScopes[0].isBlank()) {
            throw unavailable();
        }
        dev.macula.cloud.system.protocol.OAuth2ProtocolSettings settings;
        try {
            var configuration = dev.macula.cloud.iam.protocol.oauth2.OAuth2ClientProtocolPolicy.configuration(client,
                    dev.macula.cloud.iam.protocol.oauth2.ApplicationProtocolResolver.requested(
                            Set.of(savedScopes[0].trim().split("\\s+"))));
            settings = dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration.settings(configuration);
        } catch (RuntimeException ex) {
            throw unavailable();
        }
        if (!csv(settings.getAuthorizationGrantTypes()).contains(
                AuthorizationGrantType.AUTHORIZATION_CODE.getValue()) ||
                !StringUtils.hasText(settings.getRedirectUris())) {
            throw unavailable();
        }
        if (java.util.Set.of("NATIVE", "MINIAPP", "SERVICE")
                .contains(client.getApplicationType() == null ? "" : client.getApplicationType())) {
            return Optional.of(new ClientPolicy(client.getClientId(), Set.of(), null, Map.of(), client.getTenantId(),
                    client.getUserType(), client.getApplicationType(), Map.of(), client.getAuthorizationVersion()));
        }
        Set<BrowserLoginProviderType> allowedProviders = browserProviders(
                client.getBrowserLoginProviderTypes());
        BrowserLoginProviderType directProvider = directProvider(
                client.getDirectLoginProviderType(), allowedProviders);
        Map<String, Long> fixed = client.getBrowserProviderBindings() == null ? Map.of()
                : json.readValue(client.getBrowserProviderBindings(), new tools.jackson.core.type.TypeReference<>() {
        });
        if (client.getBrowserCapabilityBindings() == null
                && !fixed.keySet().equals(allowedProviders.stream().map(Enum::name).collect(Collectors.toSet()))
                || !dev.macula.cloud.iam.protocol.oauth2.OAuth2ClientProtocolPolicy.enabled(client)) {
            throw unavailable();
        }
        return Optional.of(new ClientPolicy(client.getClientId(), allowedProviders, directProvider,
                Map.copyOf(fixed), client.getTenantId(), client.getUserType(), client.getApplicationType(),
                client.getBrowserCapabilityBindings() == null ? Map.of() :
                        json.readValue(client.getBrowserCapabilityBindings(),
                                new tools.jackson.core.type.TypeReference<Map<String, Long>>() {
                                }), client.getAuthorizationVersion()));
    }

    /**
     * 要求当前受控授权请求允许指定第三方提供商。
     *
     * @param request 当前请求
     * @param response 当前响应
     * @param providerType 第三方提供商
     * @return 当前 Client 策略
     */
    public ClientPolicy requireAllowed(HttpServletRequest request, HttpServletResponse response,
            BrowserLoginProviderType providerType) {
        ClientPolicy policy = resolve(request, response).orElseThrow(this::unavailable);
        if (!policy.allowed(providerType)) {
            throw unavailable();
        }
        return policy;
    }

    /**
     * 回调阶段重新校验 Client、provider 与发起阶段 state 一致。
     *
     * @param request 当前请求
     * @param response 当前响应
     * @param clientId state 绑定的 Client ID
     * @param providerType state 绑定的提供商
     * @return 当前 Client 策略
     */
    public ClientPolicy requireAllowed(HttpServletRequest request, HttpServletResponse response,
            String clientId, BrowserLoginProviderType providerType) {
        ClientPolicy policy = requireAllowed(request, response, providerType);
        if (!policy.clientId().equals(clientId)) {
            throw unavailable();
        }
        return policy;
    }

    /**
     * 按该Client固定关联取得来源，不允许query选择别的registration。
     */
    public IdentityProviderConfigService.ProviderConfig requireSource(ClientPolicy policy, String providerType) {
        IdentityProviderConfigService.ProviderConfig source = configs.requireEnabledById(
                policy.sources().get(providerType), providerType);
        if (!source.sourceType().browser()) {
            throw unavailable();
        }
        if (policy.applicationType() != null) {
            try {
                if (!dev.macula.cloud.system.enums.OAuth2ClientApplicationType.valueOf(policy.applicationType())
                        .supportsBrowserSource(source.sourceType())) {
                    throw unavailable();
                }
            } catch (IllegalArgumentException ex) {
                throw unavailable();
            }
        }
        return source;
    }

    /**
     * 服务端按已登记能力及既定模式选择固定来源，回调不重新读取UA。
     */
    public IdentityProviderConfigService.ProviderConfig requireSource(ClientPolicy policy, String providerType,
            String mode) {
        if (!policy.allowed(BrowserLoginProviderType.fromValue(providerType))) {
            throw unavailable();
        }
        if ("IN_APP".equals(policy.applicationType()) && policy.capabilitySources().size() != 1) {
            throw unavailable();
        }
        if (policy.capabilitySources().isEmpty()) {
            var legacy = requireSource(policy, providerType).forBrowser(mode);
            if (policy.applicationType() != null &&
                    !dev.macula.cloud.system.enums.OAuth2ClientApplicationType.valueOf(policy.applicationType())
                            .supportsBrowserCapability(dev.macula.cloud.system.enums.IdentityLoginCapability.fromValue(
                                    legacy.loginCapability()))) {
                throw unavailable();
            }
            return legacy;
        }
        var candidates = policy.capabilitySources().entrySet().stream().filter(entry -> {
            var capability = dev.macula.cloud.system.enums.IdentityLoginCapability.fromValue(entry.getKey());
            return capability.browser() && capability.source().providerType().equals(providerType) &&
                    capability.mode().equals(mode);
        }).toList();
        if (candidates.size() != 1) {
            throw unavailable();
        }
        var candidate = candidates.get(0);
        var source = configs.requireEnabledById(candidate.getValue(), providerType).forCapability(candidate.getKey());
        if (policy.applicationType() != null && !dev.macula.cloud.system.enums.OAuth2ClientApplicationType.valueOf(
                policy.applicationType()).supportsBrowserCapability(
                dev.macula.cloud.system.enums.IdentityLoginCapability.fromValue(source.loginCapability()))) {
            throw unavailable();
        }
        return source;
    }

    /**
     * 环境只筛选体验入口；最终身份仍由固定来源Code及一次性state核验。
     */
    public IdentityProviderConfigService.ProviderConfig requireSourceForEnvironment(ClientPolicy policy,
            String providerType,
            dev.macula.cloud.system.enums.IdentityBrowserEnvironment environment) {
        if (policy.capabilitySources().isEmpty()) {
            var source = requireSource(policy, providerType);
            String mode = source.sourceType() == dev.macula.cloud.system.enums.IdentitySourceType.WECOM_WEB
                    ?
                    environment == dev.macula.cloud.system.enums.IdentityBrowserEnvironment.WECOM_WEBVIEW ? "WEBVIEW" :
                            "BROWSER"
                    : source.sourceType() == dev.macula.cloud.system.enums.IdentitySourceType.WECHAT_OFFICIAL_ACCOUNT ?
                    "WEBVIEW"
                    : source.sourceType() == dev.macula.cloud.system.enums.IdentitySourceType.GITHUB_OAUTH ? "" :
                    "BROWSER";
            return requireSource(policy, providerType, mode);
        }
        var candidates = policy.capabilitySources().keySet().stream()
                .map(dev.macula.cloud.system.enums.IdentityLoginCapability::fromValue)
                .filter(item -> item.browser() && item.source().providerType().equals(providerType) &&
                        item.environments().contains(environment)).toList();
        if (candidates.size() != 1) {
            throw unavailable();
        }
        return requireSource(policy, providerType, candidates.get(0).mode());
    }

    private void validateAuthorizationRequest(HttpServletRequest request, SavedRequest savedRequest) {
        try {
            URI uri = URI.create(savedRequest.getRedirectUrl());
            String expectedPath = request.getContextPath() + authorizationServerSettings.getAuthorizationEndpoint();
            if (!expectedPath.equals(uri.getRawPath()) || uri.getRawFragment() != null) {
                throw unavailable();
            }
        } catch (IllegalArgumentException ex) {
            throw unavailable();
        }
    }

    private Set<String> csv(String value) {
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<BrowserLoginProviderType> browserProviders(String value) {
        try {
            return csv(value).stream().map(BrowserLoginProviderType::fromValue)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException ex) {
            throw unavailable();
        }
    }

    private BrowserLoginProviderType directProvider(String value,
            Set<BrowserLoginProviderType> allowedProviders) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            BrowserLoginProviderType direct = BrowserLoginProviderType.fromValue(value);
            if (!allowedProviders.contains(direct)) {
                throw unavailable();
            }
            return direct;
        } catch (IllegalArgumentException ex) {
            throw unavailable();
        }
    }

    private AuthenticationServiceException unavailable() {
        return new AuthenticationServiceException("当前应用不允许该第三方登录方式");
    }

    /**
     * 从受控授权请求加载的不可变 Client 登录策略。
     *
     * @since 6.1.0
     */
    public record ClientPolicy(String clientId, Set<BrowserLoginProviderType> allowedProviders,
                               BrowserLoginProviderType directProvider, Map<String, Long> sources, Long tenantId,
                               dev.macula.cloud.iam.enums.UserTypeEnum userType, String applicationType,
                               Map<String, Long> capabilitySources, Long authorizationVersion) {
        /**
         * 兼容原显式能力策略构造；缺少授权版本的证明不得用于新端内会话。
         */
        public ClientPolicy(String clientId, Set<BrowserLoginProviderType> allowedProviders,
                BrowserLoginProviderType directProvider,
                Map<String, Long> sources, Long tenantId, dev.macula.cloud.iam.enums.UserTypeEnum userType,
                String applicationType, Map<String, Long> capabilitySources) {
            this(clientId, allowedProviders, directProvider, sources, tenantId, userType, applicationType,
                    capabilitySources, null);
        }

        /**
         * 兼容旧固定平台来源构造，旧关联不产生额外能力。
         */
        public ClientPolicy(String clientId, Set<BrowserLoginProviderType> allowedProviders,
                BrowserLoginProviderType directProvider, Map<String, Long> sources, Long tenantId,
                dev.macula.cloud.iam.enums.UserTypeEnum userType, String applicationType) {
            this(clientId, allowedProviders, directProvider, sources, tenantId, userType, applicationType, Map.of());
        }

        /**
         * 兼容历史未分类策略构造，不由调用方猜测应用类型。
         */
        public ClientPolicy(String clientId, Set<BrowserLoginProviderType> allowedProviders,
                BrowserLoginProviderType directProvider, Map<String, Long> sources, Long tenantId,
                dev.macula.cloud.iam.enums.UserTypeEnum userType) {
            this(clientId, allowedProviders, directProvider, sources, tenantId, userType, null);
        }

        public ClientPolicy {
            allowedProviders = Set.copyOf(allowedProviders);
            sources = Map.copyOf(sources);
            capabilitySources = Map.copyOf(capabilitySources);
        }

        public boolean allowed(BrowserLoginProviderType providerType) {
            return allowedProviders.contains(providerType);
        }
    }
}
