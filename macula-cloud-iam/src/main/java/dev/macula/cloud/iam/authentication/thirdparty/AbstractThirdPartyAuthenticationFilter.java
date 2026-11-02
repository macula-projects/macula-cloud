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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import dev.macula.cloud.system.enums.BrowserLoginProviderType;
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

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;

/**
 * 第三方登录发起与回调公共过滤器，只绑定并一次性消费外部身份核验状态。
 *
 * @author Rain
 * @since 6.1.0
 */
public abstract class AbstractThirdPartyAuthenticationFilter extends AbstractAuthenticationProcessingFilter {
    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";
    private static final int HTTP_DEFAULT_PORT = 80;
    private static final int HTTPS_DEFAULT_PORT = 443;

    private final String providerType;
    private final String loginPath;
    private IdentityProviderConfigService configService;
    private BrowserLoginClientPolicyService clientPolicyService;
    private ThirdPartyLoginStateRepository stateRepository;
    private String issuer;
    private URI issuerUri;
    private final RequestCache requestCache = new HttpSessionRequestCache();

    protected AbstractThirdPartyAuthenticationFilter(String providerType, String loginPath) {
        super(PathPatternRequestMatcher.withDefaults().matcher(loginPath));
        this.providerType = providerType;
        this.loginPath = loginPath;
    }

    public void configure(IdentityProviderConfigService configService,
        BrowserLoginClientPolicyService clientPolicyService,
        ThirdPartyLoginStateRepository stateRepository, String issuer) {
        this.configService = configService;
        this.clientPolicyService = clientPolicyService;
        this.stateRepository = stateRepository;
        this.issuer = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
        this.issuerUri = URI.create(this.issuer);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
        throws AuthenticationException, IOException {
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        String code = request.getParameter(OAuth2ParameterNames.CODE);
        if (StringUtils.hasText(state) || StringUtils.hasText(code)
            || request.getParameter(OAuth2ParameterNames.ERROR) != null) {
            if (!loginPath.equals(request.getRequestURI().substring(request.getContextPath().length()))) {
                throw new AuthenticationServiceException("第三方回调地址无效");
            }
            ThirdPartyLoginContext context = stateRepository.consume(request, providerType, state);
            if (!StringUtils.hasText(code) || request.getParameter(OAuth2ParameterNames.ERROR) != null) {
                throw new AuthenticationServiceException("第三方授权失败");
            }
            validateSavedRequestTarget(request, response, context);
            clientPolicyService.requireAllowed(request, response, context.clientId(), provider());
            configService.requireEnabled(providerType);
            return getAuthenticationManager().authenticate(ThirdPartyAuthenticationToken.unauthenticated(
                providerType, code, context, callbackUri()));
        }

        IdentityProviderConfigService.ProviderConfig config = configService.requireEnabled(providerType);
        BrowserLoginClientPolicyService.ClientPolicy clientPolicy =
            clientPolicyService.requireAllowed(request, response, provider());
        String mode = loginMode(request);
        if (mode == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return null;
        }
        ThirdPartyLoginContext context = new ThirdPartyLoginContext(providerType, mode, clientPolicy.clientId(),
            Instant.now(),
            resolveSavedRequestTarget(request, response));
        String generatedState = stateRepository.create(request, context);
        response.sendRedirect(authorizationUri(config, generatedState, callbackUri(), context));
        return null;
    }

    protected abstract String authorizationUri(IdentityProviderConfigService.ProviderConfig config,
        String state, String redirectUri, ThirdPartyLoginContext context);

    protected String loginMode(HttpServletRequest request) {
        return "";
    }

    private BrowserLoginProviderType provider() {
        return BrowserLoginProviderType.fromValue(providerType);
    }

    private void validateSavedRequestTarget(HttpServletRequest request, HttpServletResponse response,
        ThirdPartyLoginContext context) {
        if (!resolveSavedRequestTarget(request, response).equals(context.savedRequestTarget())) {
            throw new AuthenticationServiceException("登录续转目标无效");
        }
    }

    private String resolveSavedRequestTarget(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest == null) {
            return request.getContextPath() + "/";
        }
        try {
            URI uri = URI.create(savedRequest.getRedirectUrl());
            String path = uri.getRawPath();
            String contextRoot = request.getContextPath() + "/";
            if (!hasTrustedOrigin(uri) || path == null || !path.startsWith(contextRoot)
                || path.startsWith(contextRoot + "/")
                || path.indexOf('\\') >= 0 || uri.getRawFragment() != null
                || !URI.create(path).normalize().getRawPath().equals(path)) {
                throw new AuthenticationServiceException("登录续转目标无效");
            }
            String lowerPath = path.toLowerCase(Locale.ROOT);
            if (lowerPath.contains("%2f") || lowerPath.contains("%5c") || lowerPath.contains("%2e")
                || lowerPath.contains("%00") || lowerPath.contains("%0d") || lowerPath.contains("%0a")) {
                throw new AuthenticationServiceException("登录续转目标无效");
            }
            return path + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        } catch (IllegalArgumentException ex) {
            throw new AuthenticationServiceException("登录续转目标无效");
        }
    }

    private boolean hasTrustedOrigin(URI uri) {
        return uri.isAbsolute() && uri.getRawUserInfo() == null
            && issuerUri.isAbsolute() && issuerUri.getRawUserInfo() == null
            && uri.getScheme().equalsIgnoreCase(issuerUri.getScheme())
            && uri.getHost() != null && issuerUri.getHost() != null
            && uri.getHost().equalsIgnoreCase(issuerUri.getHost())
            && normalizedPort(uri) == normalizedPort(issuerUri);
    }

    private int normalizedPort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        if (HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme())) {
            return HTTPS_DEFAULT_PORT;
        }
        if (HTTP_SCHEME.equalsIgnoreCase(uri.getScheme())) {
            return HTTP_DEFAULT_PORT;
        }
        return -1;
    }

    private String callbackUri() {
        return issuer + loginPath;
    }
}
