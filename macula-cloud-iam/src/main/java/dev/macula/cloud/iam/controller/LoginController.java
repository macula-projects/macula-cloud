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

package dev.macula.cloud.iam.controller;

import cn.hutool.core.util.StrUtil;
import dev.macula.cloud.iam.authentication.dingtalk.DingtalkLoginMode;
import dev.macula.cloud.iam.authentication.feishu.FeishuLoginMode;
import dev.macula.cloud.iam.authentication.thirdparty.BrowserLoginClientPolicyService;
import dev.macula.cloud.iam.authentication.thirdparty.BrowserLoginClientPolicyService.ClientPolicy;
import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService;
import dev.macula.cloud.iam.authentication.wecom.WecomLoginMode;
import dev.macula.cloud.iam.authentication.wechat.WechatLoginMode;
import dev.macula.cloud.iam.enums.ThirdPartyProviderType;
import dev.macula.cloud.iam.handler.AuthenticationFailureReason;
import dev.macula.cloud.system.enums.BrowserLoginProviderType;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The type LoginController.
 *
 * @author felord.cn
 * @since 1.0.0
 */
@Controller
@RequiredArgsConstructor
public class LoginController {
    private static final String WECOM_USER_AGENT_MARKER = "wxwork";
    private static final String WECHAT_USER_AGENT_MARKER = "micromessenger";
    private final IdentityProviderConfigService providerConfigService;
    private final BrowserLoginClientPolicyService clientPolicyService;

    /**
     * login page.
     *
     * @param model              the model
     * @param authentication     the authentication
     * @param enableSmsLogin the enable code login
     * @param clientId           the client id
     * @param csrfToken          the csrf token
     * @return the string
     */
    @GetMapping("/login")
    public String loginPage(Model model,
        @CurrentSecurityContext(expression = "authentication") Authentication authentication,
        @Value("${spring.security.oauth2.server.login.sms.enabled:true}") boolean enableSmsLogin,
        @RequestParam(name = OAuth2ParameterNames.CLIENT_ID, required = false) String clientId,
        @RequestParam(name = OAuth2ParameterNames.ERROR, required = false) String authenticationError,
        HttpServletRequest request,
        HttpServletResponse response,
        @RequestAttribute(name = "org.springframework.security.web.csrf.CsrfToken",
            required = false) CsrfToken csrfToken) {

        if (authentication != null && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/";
        }
        if (csrfToken != null) {
            model.addAttribute("_csrfToken", csrfToken);
        }
        String resolvedClientId = resolveClientId(clientId, request);
        if (StrUtil.isNotBlank(resolvedClientId)) {
            model.addAttribute(OAuth2ParameterNames.CLIENT_ID, resolvedClientId);
        }
        Optional<ClientPolicy> policy = resolvePolicy(request, response, model);
        model.addAttribute("githubLoginEnabled", isVisible(policy, BrowserLoginProviderType.GITHUB));
        model.addAttribute("wecomLoginEnabled", isVisible(policy, BrowserLoginProviderType.WECOM));
        model.addAttribute("feishuLoginEnabled", isVisible(policy, BrowserLoginProviderType.FEISHU));
        model.addAttribute("dingtalkLoginEnabled", isVisible(policy, BrowserLoginProviderType.DINGTALK));
        model.addAttribute("wechatLoginEnabled", isVisible(policy, BrowserLoginProviderType.WECHAT));
        model.addAttribute("githubLoginPath", ThirdPartyProviderType.GITHUB.loginPath());
        model.addAttribute("wecomLoginPath", ThirdPartyProviderType.WECOM.loginPath());
        model.addAttribute("wecomBrowserPath", WecomLoginMode.BROWSER.loginPath());
        model.addAttribute("wecomWebviewPath", WecomLoginMode.WEBVIEW.loginPath());
        model.addAttribute("feishuLoginPath", ThirdPartyProviderType.FEISHU.loginPath());
        model.addAttribute("feishuBrowserPath", FeishuLoginMode.BROWSER.loginPath());
        model.addAttribute("dingtalkLoginPath", ThirdPartyProviderType.DINGTALK.loginPath());
        model.addAttribute("dingtalkBrowserPath", DingtalkLoginMode.BROWSER.loginPath());
        model.addAttribute("wechatLoginPath", ThirdPartyProviderType.WECHAT.loginPath());
        model.addAttribute("wechatBrowserPath", WechatLoginMode.BROWSER.loginPath());
        model.addAttribute("wechatWebviewPath", WechatLoginMode.WEBVIEW.loginPath());
        if (StrUtil.isNotBlank(authenticationError)) {
            model.addAttribute("authenticationError",
                AuthenticationFailureReason.fromCode(authenticationError).message());
        } else if (policy.isPresent()) {
            String automaticLoginPath = automaticLoginPath(request, policy.get());
            if (automaticLoginPath != null) {
                return "redirect:" + automaticLoginPath;
            }
        }
        model.addAttribute("enableSmsLogin", enableSmsLogin);
        return "login";
    }

    private Optional<ClientPolicy> resolvePolicy(HttpServletRequest request, HttpServletResponse response,
        Model model) {
        try {
            return clientPolicyService.resolve(request, response);
        } catch (AuthenticationServiceException ex) {
            model.addAttribute("authenticationError", "当前应用登录配置无效，请联系管理员。");
            return Optional.empty();
        }
    }

    private boolean isVisible(Optional<ClientPolicy> policy, BrowserLoginProviderType providerType) {
        return policy.filter(value -> value.allowed(providerType)).isPresent()
            && providerConfigService.isVisible(providerType.name());
    }

    private String automaticLoginPath(HttpServletRequest request, ClientPolicy policy) {
        String userAgent = Optional.ofNullable(request.getHeader(HttpHeaders.USER_AGENT)).orElse("")
            .toLowerCase(Locale.ROOT);
        if (userAgent.contains(WECOM_USER_AGENT_MARKER) && canStart(policy, BrowserLoginProviderType.WECOM)) {
            return WecomLoginMode.WEBVIEW.loginPath();
        }
        if (!userAgent.contains(WECOM_USER_AGENT_MARKER) && userAgent.contains(WECHAT_USER_AGENT_MARKER)
            && canStart(policy, BrowserLoginProviderType.WECHAT)) {
            return WechatLoginMode.WEBVIEW.loginPath();
        }
        BrowserLoginProviderType direct = policy.directProvider();
        return direct != null && canStart(policy, direct) ? loginPath(direct) : null;
    }

    private boolean canStart(ClientPolicy policy, BrowserLoginProviderType providerType) {
        return policy.allowed(providerType) && providerConfigService.isEnabled(providerType.name());
    }

    private String loginPath(BrowserLoginProviderType providerType) {
        return switch (providerType) {
            case GITHUB -> ThirdPartyProviderType.GITHUB.loginPath();
            case WECOM -> ThirdPartyProviderType.WECOM.loginPath();
            case FEISHU -> ThirdPartyProviderType.FEISHU.loginPath();
            case DINGTALK -> ThirdPartyProviderType.DINGTALK.loginPath();
            case WECHAT -> ThirdPartyProviderType.WECHAT.loginPath();
        };
    }

    private String resolveClientId(String parameter, HttpServletRequest request) {
        if (StrUtil.isNotBlank(parameter)) {
            return parameter;
        }
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
            .filter(cookie -> OAuth2ParameterNames.CLIENT_ID.equals(cookie.getName()))
            .map(Cookie::getValue).findFirst().orElse(null);
    }

    /**
     * oauth2中间页.
     *
     * @param model          the model
     * @param authentication the authentication
     * @param csrfToken      the csrf token
     * @return the string
     */
    @GetMapping("/")
    public String indexPage(Model model,
        @CurrentSecurityContext(expression = "authentication") Authentication authentication,
        @RequestAttribute(name = "org.springframework.security.web.csrf.CsrfToken",
            required = false) CsrfToken csrfToken) {

        if (csrfToken != null) {
            model.addAttribute("_csrfToken", csrfToken);
        }
        model.addAttribute("principal", authentication.getName());

        return "index";
    }

}
