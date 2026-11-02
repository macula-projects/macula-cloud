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
package dev.macula.cloud.iam.authentication.thirdparty;

import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.service.support.SysOAuth2ClientService;
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

import java.net.URI;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
        if (client == null || !csv(client.getAuthorizationGrantTypes()).contains(
            AuthorizationGrantType.AUTHORIZATION_CODE.getValue()) || !StringUtils.hasText(client.getRedirectUris())) {
            throw unavailable();
        }
        Set<BrowserLoginProviderType> allowedProviders = browserProviders(
            client.getBrowserLoginProviderTypes());
        BrowserLoginProviderType directProvider = directProvider(
            client.getDirectLoginProviderType(), allowedProviders);
        return Optional.of(new ClientPolicy(client.getClientId(), allowedProviders, directProvider));
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
                               BrowserLoginProviderType directProvider) {
        public ClientPolicy {
            allowedProviders = Set.copyOf(allowedProviders);
        }

        public boolean allowed(BrowserLoginProviderType providerType) {
            return allowedProviders.contains(providerType);
        }
    }
}
