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
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;

/**
 * 第三方登录发起与回调公共过滤器，绑定并一次性消费 OAuth client 上下文。
 *
 * @author Rain
 * @since 6.1.0
 */
public abstract class AbstractThirdPartyAuthenticationFilter extends AbstractAuthenticationProcessingFilter {
    private final String providerType;
    private final String loginPath;
    private SysOAuth2ClientService clientService;
    private IdentityProviderConfigService configService;
    private ThirdPartyLoginStateRepository stateRepository;
    private String issuer;
    private final RequestCache requestCache = new HttpSessionRequestCache();

    protected AbstractThirdPartyAuthenticationFilter(String providerType, String loginPath) {
        super(PathPatternRequestMatcher.withDefaults().matcher(loginPath));
        this.providerType = providerType;
        this.loginPath = loginPath;
    }

    public void configure(SysOAuth2ClientService clientService, IdentityProviderConfigService configService,
        ThirdPartyLoginStateRepository stateRepository, String issuer) {
        this.clientService = clientService;
        this.configService = configService;
        this.stateRepository = stateRepository;
        this.issuer = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
        throws AuthenticationException, IOException {
        String state = request.getParameter("state");
        String code = request.getParameter("code");
        if (StringUtils.hasText(state) || StringUtils.hasText(code) || request.getParameter("error") != null) {
            ThirdPartyLoginContext context = stateRepository.consume(request, providerType, state);
            if (!StringUtils.hasText(code) || request.getParameter("error") != null) {
                throw new AuthenticationServiceException("第三方授权失败");
            }
            validateClient(context);
            return getAuthenticationManager().authenticate(ThirdPartyAuthenticationToken.unauthenticated(
                providerType, code, context, callbackUri()));
        }

        String clientId = resolveClientId(request);
        SysOAuth2Client client = requireClient(clientId);
        IdentityProviderConfigService.ProviderConfig config = configService.requireEnabled(providerType);
        ThirdPartyLoginContext context = new ThirdPartyLoginContext(providerType, clientId,
            client.getTenantId(), client.getUserType(), Instant.now(),
            resolveSavedRequestTarget(request, response, clientId));
        String generatedState = stateRepository.create(request, context);
        response.sendRedirect(authorizationUri(config, generatedState, callbackUri()));
        return null;
    }

    protected abstract String authorizationUri(IdentityProviderConfigService.ProviderConfig config,
        String state, String redirectUri);

    private void validateClient(ThirdPartyLoginContext context) {
        SysOAuth2Client client = requireClient(context.clientId());
        if (!context.tenantId().equals(client.getTenantId()) || context.userType() != client.getUserType()) {
            throw new AuthenticationServiceException("OAuth client 配置已变化");
        }
        configService.requireEnabled(providerType);
    }

    private SysOAuth2Client requireClient(String clientId) {
        if (!StringUtils.hasText(clientId)) throw new AuthenticationServiceException("OAuth client 无效");
        SysOAuth2Client client = clientService.getClientByClientId(clientId);
        if (client == null || client.getTenantId() == null || client.getUserType() == null) {
            throw new AuthenticationServiceException("OAuth client 无效");
        }
        return client;
    }

    private String resolveClientId(HttpServletRequest request) {
        String clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
        if (!StringUtils.hasText(clientId) && request.getCookies() != null) {
            clientId = Arrays.stream(request.getCookies())
                .filter(cookie -> OAuth2ParameterNames.CLIENT_ID.equals(cookie.getName()))
                .map(Cookie::getValue).findFirst().orElse(null);
        }
        return clientId;
    }

    private String resolveSavedRequestTarget(HttpServletRequest request, HttpServletResponse response,
        String clientId) {
        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest == null) return request.getContextPath() + "/";
        try {
            URI uri = URI.create(savedRequest.getRedirectUrl());
            String expectedPath = request.getContextPath() + "/oauth2/authorize";
            String savedClientId = UriComponentsBuilder.fromUri(uri).build().getQueryParams()
                .getFirst(OAuth2ParameterNames.CLIENT_ID);
            if (!expectedPath.equals(uri.getRawPath()) || !clientId.equals(savedClientId)) {
                throw new AuthenticationServiceException("OAuth 授权请求与客户端不匹配");
            }
            return uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        } catch (IllegalArgumentException ex) {
            throw new AuthenticationServiceException("OAuth 授权请求无效");
        }
    }

    private String callbackUri() {
        return issuer + loginPath;
    }
}
