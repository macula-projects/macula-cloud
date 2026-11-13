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

import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.service.application.LoginApplicationResolver;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService;
import dev.macula.cloud.system.enums.BrowserLoginProviderType;
import dev.macula.cloud.system.enums.IamApplicationType;
import dev.macula.cloud.system.enums.IamWebLoginMode;
import dev.macula.cloud.system.enums.IdentityBrowserEnvironment;
import dev.macula.cloud.system.enums.IdentityLoginCapability;
import dev.macula.cloud.system.enums.IdentitySourceType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.stereotype.Service;

/**
 * 从协议解析端口恢复应用的浏览器第三方登录策略。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class BrowserLoginClientPolicyService {
    private final LoginApplicationResolver applications;
    private final IdentityProviderConfigService configs;

    /**
     * 从当前 Session 的 SavedRequest 解析登录策略。
     *
     * @param request 当前请求
     * @param response 当前响应
     * @return 没有受控授权请求时为空
     */
    public Optional<ClientPolicy> resolve(HttpServletRequest request, HttpServletResponse response) {
        return applications.resolve(request, response).map(selection -> {
            var app = selection.application();
            var login = app.login();
            Map<String, Long> capabilities = new HashMap<>();
            Map<String, Long> providers = new HashMap<>();
            login.sources().forEach((capability, sourceId) -> {
                if (capability.browser()) {
                    capabilities.put(capability.value(), sourceId);
                    providers.put(capability.source().providerType(), sourceId);
                }
            });
            Set<BrowserLoginProviderType> allowed = providers.keySet().stream()
                .map(BrowserLoginProviderType::fromValue).collect(Collectors.toUnmodifiableSet());
            BrowserLoginProviderType direct = login.directCapability() == null ? null
                : BrowserLoginProviderType.fromValue(login.directCapability().source().providerType());
            if (direct != null && !allowed.contains(direct)) {
                throw unavailable();
            }
            return new ClientPolicy(selection.reference(), allowed, direct, providers, app.tenantId(),
                app.accountUserType(), login.type() == null ? null : login.type().name(), capabilities,
                app.authorizationVersion(), login.webLoginMode() == null ? null : login.webLoginMode().value());
        });
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
                if (!IamApplicationType.valueOf(policy.applicationType())
                        .supportsBrowserSource(source.sourceType(), policy.webLoginMode())) {
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
        if (policy.applicationType() != null && IamWebLoginMode.isInApp(policy.applicationType(), policy.webLoginMode()) && policy.capabilitySources().size() != 1) {
            throw unavailable();
        }
        if (policy.capabilitySources().isEmpty()) {
            var legacy = requireSource(policy, providerType).forBrowser(mode);
            if (policy.applicationType() != null &&
                    !IamApplicationType.valueOf(policy.applicationType())
                            .supportsBrowserCapability(IdentityLoginCapability.fromValue(
                                    legacy.loginCapability()), policy.webLoginMode())) {
                throw unavailable();
            }
            return legacy;
        }
        var candidates = policy.capabilitySources().entrySet().stream().filter(entry -> {
            var capability = IdentityLoginCapability.fromValue(entry.getKey());
            return capability.browser() && capability.source().providerType().equals(providerType) &&
                    capability.mode().equals(mode);
        }).toList();
        if (candidates.size() != 1) {
            throw unavailable();
        }
        var candidate = candidates.get(0);
        var source = configs.requireEnabledById(candidate.getValue(), providerType).forCapability(candidate.getKey());
        if (policy.applicationType() != null && !IamApplicationType.valueOf(
                policy.applicationType()).supportsBrowserCapability(
                IdentityLoginCapability.fromValue(source.loginCapability()), policy.webLoginMode())) {
            throw unavailable();
        }
        return source;
    }

    /**
     * 环境只筛选体验入口；最终身份仍由固定来源Code及一次性state核验。
     */
    public IdentityProviderConfigService.ProviderConfig requireSourceForEnvironment(ClientPolicy policy,
            String providerType,
            IdentityBrowserEnvironment environment) {
        if (policy.capabilitySources().isEmpty()) {
            var source = requireSource(policy, providerType);
            String mode = source.sourceType() == IdentitySourceType.WECOM_WEB
                    ?
                    environment == IdentityBrowserEnvironment.WECOM_WEBVIEW ? "WEBVIEW" :
                            "BROWSER"
                    : source.sourceType() == IdentitySourceType.WECHAT_OFFICIAL_ACCOUNT ?
                    "WEBVIEW"
                    : source.sourceType() == IdentitySourceType.GITHUB_OAUTH ? "" :
                    "BROWSER";
            return requireSource(policy, providerType, mode);
        }
        var candidates = policy.capabilitySources().keySet().stream()
                .map(IdentityLoginCapability::fromValue)
                .filter(item -> item.browser() && item.source().providerType().equals(providerType) &&
                        item.environments().contains(environment)).toList();
        if (candidates.size() != 1) {
            throw unavailable();
        }
        return requireSource(policy, providerType, candidates.get(0).mode());
    }

    /** 来源策略错误使用固定提示，不泄露内部配置。 */
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
                               UserTypeEnum userType, String applicationType,
                               Map<String, Long> capabilitySources, Long authorizationVersion, String webLoginMode) {
        /** 历史内部构造缺省为浏览器；生产始终投影受信任Client模式。 */
        public ClientPolicy(String clientId, Set<BrowserLoginProviderType> allowedProviders,
                BrowserLoginProviderType directProvider, Map<String, Long> sources, Long tenantId,
                UserTypeEnum userType, String applicationType, Map<String, Long> capabilitySources, Long authorizationVersion) {
            this(clientId, allowedProviders, directProvider, sources, tenantId, userType, applicationType,
                capabilitySources, authorizationVersion, null);
        }
        /**
         * 兼容原显式能力策略构造；缺少授权版本的证明不得用于新端内会话。
         */
        public ClientPolicy(String clientId, Set<BrowserLoginProviderType> allowedProviders,
                BrowserLoginProviderType directProvider,
                Map<String, Long> sources, Long tenantId, UserTypeEnum userType,
                String applicationType, Map<String, Long> capabilitySources) {
            this(clientId, allowedProviders, directProvider, sources, tenantId, userType, applicationType,
                    capabilitySources, null, null);
        }

        /**
         * 兼容旧固定平台来源构造，旧关联不产生额外能力。
         */
        public ClientPolicy(String clientId, Set<BrowserLoginProviderType> allowedProviders,
                BrowserLoginProviderType directProvider, Map<String, Long> sources, Long tenantId,
                UserTypeEnum userType, String applicationType) {
            this(clientId, allowedProviders, directProvider, sources, tenantId, userType, applicationType, Map.of());
        }

        /**
         * 兼容历史未分类策略构造，不由调用方猜测应用类型。
         */
        public ClientPolicy(String clientId, Set<BrowserLoginProviderType> allowedProviders,
                BrowserLoginProviderType directProvider, Map<String, Long> sources, Long tenantId,
                UserTypeEnum userType) {
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
