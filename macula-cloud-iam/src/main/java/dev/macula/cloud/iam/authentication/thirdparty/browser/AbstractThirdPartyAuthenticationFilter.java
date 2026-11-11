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

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;

import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyAuthenticationToken;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyLoginContext;
import dev.macula.cloud.iam.authentication.thirdparty.provider.ThirdPartyProviderAdapter;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService;
import dev.macula.cloud.system.enums.BrowserLoginProviderType;
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
    private final RequestCache requestCache = new HttpSessionRequestCache();
    private IdentityProviderConfigService configService;
    private BrowserLoginClientPolicyService clientPolicyService;
    private ThirdPartyLoginStateRepository stateRepository;
    private ThirdPartyProviderAdapter adapter;
    private String issuer;
    private URI issuerUri;

    /**
     * 固定厂商和回调主路径，可信来源仍在每次请求复核。
     */
    protected AbstractThirdPartyAuthenticationFilter(String providerType, String loginPath) {
        super(PathPatternRequestMatcher.withDefaults().matcher(loginPath));
        this.providerType = providerType;
        this.loginPath = loginPath;
    }

    /**
     * 装配公共来源/state/issuer依赖，不读取请求参数选择可信配置。
     */
    public void configure(IdentityProviderConfigService configService,
            BrowserLoginClientPolicyService clientPolicyService,
            ThirdPartyLoginStateRepository stateRepository, String issuer) {
        this.configService = configService;
        this.clientPolicyService = clientPolicyService;
        this.stateRepository = stateRepository;
        this.issuer = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
        this.issuerUri = URI.create(this.issuer);
    }

    /**
     * 从受控授权请求发起一次性state，回调复核当前Client版本及固定来源后才验证平台Code。
     */
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
            BrowserLoginClientPolicyService.ClientPolicy policy = clientPolicyService.requireAllowed(
                    request, response, context.clientId(), provider());
            IdentityProviderConfigService.ProviderConfig source = clientPolicyService.requireSource(policy,
                    providerType, context.loginMode());
            if ("IN_APP".equals(policy.applicationType()) &&
                    !java.util.Objects.equals(policy.authorizationVersion(), context.clientAuthorizationVersion())
                    || !java.util.Objects.equals(source.registrationId(), context.registrationId())
                    || !source.sourceType().value().equals(context.sourceType())
                    || !java.util.Objects.equals(source.loginCapability(), context.loginCapability())
                    || !java.util.Objects.equals(source.version(), context.sourceVersion())
                    || !source.flow().value().equals(context.flow())
                    || !callbackUri().equals(context.upstreamRedirectUri())
                    || !java.util.Objects.equals(policy.tenantId(), context.tenantId()) ||
                    policy.userType() != context.userType()) {
                throw new AuthenticationServiceException("身份源或应用路由已变更，请重新登录");
            }
            validateMode(source, context.loginMode());
            return getAuthenticationManager().authenticate(ThirdPartyAuthenticationToken.unauthenticated(
                    providerType, code, context, callbackUri()));
        }

        BrowserLoginClientPolicyService.ClientPolicy clientPolicy =
                clientPolicyService.requireAllowed(request, response, provider());
        String mode = loginMode(request);
        if (mode == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return null;
        }
        IdentityProviderConfigService.ProviderConfig config = clientPolicyService.requireSource(clientPolicy,
                providerType, mode);
        validateMode(config, mode);
        // 仅限制新发起的交互；回调继续核对一次性state，不因UA变化切换兑换合同。
        var environment = dev.macula.cloud.system.enums.IdentityBrowserEnvironment.fromUserAgent(
                request.getHeader("User-Agent"));
        if (!config.sourceType().supportsBrowserEnvironment(environment, mode)) {
            throw new AuthenticationServiceException("当前浏览器环境不支持该第三方登录，请使用适用入口");
        }
        ThirdPartyLoginContext context = new ThirdPartyLoginContext(providerType, mode, clientPolicy.clientId(),
                Instant.now(),
                resolveSavedRequestTarget(request, response), config.registrationId(), config.sourceType().value(),
                clientPolicy.tenantId(), clientPolicy.userType(), config.version(), config.flow().value(),
                callbackUri(), config.loginCapability(), clientPolicy.authorizationVersion());
        String generatedState = stateRepository.create(request, context);
        response.sendRedirect(authorizationUri(config, generatedState, callbackUri(), context));
        return null;
    }

    /**
     * 来源类型固定实际授权模式，公众号不能借用网站凭据。
     */
    private void validateMode(IdentityProviderConfigService.ProviderConfig source, String mode) {
        boolean valid = switch (source.sourceType()) {
            case WECHAT_WEBSITE, FEISHU_WEB, DINGTALK_WEB -> "BROWSER".equals(mode);
            case WECHAT_OFFICIAL_ACCOUNT -> "WEBVIEW".equals(mode);
            case WECOM_WEB -> "BROWSER".equals(mode) || "WEBVIEW".equals(mode);
            case GITHUB_OAUTH -> mode.isEmpty();
            default -> false;
        };
        if (!valid) {
            throw new AuthenticationServiceException("身份源不支持当前登录场景");
        }
    }

    protected String authorizationUri(IdentityProviderConfigService.ProviderConfig config,
            String state, String redirectUri, ThirdPartyLoginContext context) {
        return adapter.authorizationUri(config, state, redirectUri, context);
    }

    /**
     * 装配已唯一注册的厂商策略，不接受请求动态指定适配器。
     */
    public void providerAdapter(ThirdPartyProviderAdapter adapter) {
        this.adapter = java.util.Objects.requireNonNull(adapter);
    }

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
