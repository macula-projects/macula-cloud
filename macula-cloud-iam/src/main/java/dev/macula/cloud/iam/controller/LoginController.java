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
import dev.macula.cloud.iam.authentication.hosted.HostedLoginClientPolicyService;
import dev.macula.cloud.system.enums.OAuth2HostedLoginMethod;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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
    private dev.macula.cloud.iam.authentication.inapp.InAppLoginPolicyService inApp;
    /** 保持既有测试构造；生产将当前端内策略注入控制器。 */
    @org.springframework.beans.factory.annotation.Autowired
    public void setInAppPolicy(dev.macula.cloud.iam.authentication.inapp.InAppLoginPolicyService value) { inApp = value; }
    /** 缺策略时仅非端内旧门户可继续，不从query猜测端内来源。 */
    private dev.macula.cloud.iam.authentication.inapp.InAppLoginPolicyService inAppPolicy() {
        if (inApp == null) { throw new IllegalStateException("端内策略未装配"); }
        return inApp;
    }
    private static final String WECOM_USER_AGENT_MARKER = "wxwork";
    private static final String WECHAT_USER_AGENT_MARKER = "micromessenger";
    private final IdentityProviderConfigService providerConfigService;
    private final BrowserLoginClientPolicyService clientPolicyService;
    private final HostedLoginClientPolicyService hostedPolicy;

    /**
     * login page.
     *
     * @param model              the model
     * @param authentication     the authentication
     * @param csrfToken          the csrf token
     * @return the string
     */
    @GetMapping("/login")
    public String loginPage(Model model,
        @CurrentSecurityContext(expression = "authentication") Authentication authentication,
        @RequestParam(name = OAuth2ParameterNames.ERROR, required = false) String authenticationError,
        HttpServletRequest request,
        HttpServletResponse response,
        @RequestAttribute(name = "org.springframework.security.web.csrf.CsrfToken",
            required = false) CsrfToken csrfToken) {

        try {
            var inApp = this.inApp == null ? java.util.Optional.<dev.macula.cloud.iam.pojo.entity.SysOAuth2Client>empty() : inAppPolicy().savedClient(request, response);
            if (inApp.isPresent()) {
                if (StrUtil.isNotBlank(authenticationError)) { return "in-app-login-error"; }
                var source = inAppPolicy().source(inApp.get());
                var capability = dev.macula.cloud.system.enums.IdentityLoginCapability.fromValue(source.loginCapability());
                if (!capability.browser() || !capability.environments().contains(
                    dev.macula.cloud.system.enums.IdentityBrowserEnvironment.fromUserAgent(request.getHeader(HttpHeaders.USER_AGENT)))) {
                    return "in-app-login-error";
                }
                return "redirect:" + (capability == dev.macula.cloud.system.enums.IdentityLoginCapability.WECOM_WEBVIEW
                    ? WecomLoginMode.WEBVIEW.loginPath() : WechatLoginMode.WEBVIEW.loginPath());
            }
        } catch (RuntimeException ex) { return "in-app-login-error"; }
        if (authentication != null && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/";
        }
        if (csrfToken != null) {
            model.addAttribute("_csrfToken", csrfToken);
        }
        for (String key : java.util.List.of("githubLoginEnabled", "wecomLoginEnabled", "feishuLoginEnabled",
            "dingtalkLoginEnabled", "wechatLoginEnabled")) {
            model.addAttribute(key, false);
        }
        try {
            HostedLoginClientPolicyService.Context context = hostedPolicy.resolve(request, response);
            model.addAttribute("enablePasswordLogin", context.methods().contains(OAuth2HostedLoginMethod.PASSWORD));
            model.addAttribute("enableSmsLogin", context.methods().contains(OAuth2HostedLoginMethod.SMS));
            model.addAttribute(OAuth2ParameterNames.CLIENT_ID, context.clientId());
        } catch (AuthenticationServiceException ex) {
            model.addAttribute("enablePasswordLogin", false);
            model.addAttribute("enableSmsLogin", false);
            model.addAttribute("authenticationError", "当前应用登录配置无效，请重新发起授权。");
            return "login";
        }
        Optional<ClientPolicy> policy = resolvePolicy(request, response, model);
        model.addAttribute("githubLoginEnabled", isVisible(request, policy, BrowserLoginProviderType.GITHUB));
        model.addAttribute("wecomLoginEnabled", isVisible(request, policy, BrowserLoginProviderType.WECOM));
        model.addAttribute("feishuLoginEnabled", isVisible(request, policy, BrowserLoginProviderType.FEISHU));
        model.addAttribute("dingtalkLoginEnabled", isVisible(request, policy, BrowserLoginProviderType.DINGTALK));
        boolean wechatVisible = isVisible(request, policy, BrowserLoginProviderType.WECHAT);
        boolean officialAccount = isOfficialAccount(policy);
        String userAgent = Optional.ofNullable(request.getHeader(HttpHeaders.USER_AGENT)).orElse("").toLowerCase(Locale.ROOT);
        boolean insideWechat = userAgent.contains(WECHAT_USER_AGENT_MARKER) && !userAgent.contains(WECOM_USER_AGENT_MARKER);
        model.addAttribute("wechatLoginEnabled", wechatVisible && (!officialAccount || insideWechat));
        model.addAttribute("wechatNeedsWebview", officialAccount && !insideWechat);
        var environment = dev.macula.cloud.system.enums.IdentityBrowserEnvironment.fromUserAgent(userAgent);
        model.addAttribute("browserEnvironmentNotice", environment == dev.macula.cloud.system.enums.IdentityBrowserEnvironment.MOBILE_BROWSER
            && policy.isPresent() && policy.get().allowedProviders().stream().anyMatch(provider -> !canStart(request, policy.get(), provider)));
        model.addAttribute("githubLoginPath", ThirdPartyProviderType.GITHUB.loginPath());
        model.addAttribute("wecomLoginPath", insideWecom(request) ? WecomLoginMode.WEBVIEW.loginPath() : ThirdPartyProviderType.WECOM.loginPath());
        model.addAttribute("wecomBrowserPath", WecomLoginMode.BROWSER.loginPath());
        model.addAttribute("wecomWebviewPath", WecomLoginMode.WEBVIEW.loginPath());
        model.addAttribute("feishuLoginPath", FeishuLoginMode.BROWSER.loginPath());
        model.addAttribute("feishuBrowserPath", FeishuLoginMode.BROWSER.loginPath());
        model.addAttribute("dingtalkLoginPath", DingtalkLoginMode.BROWSER.loginPath());
        model.addAttribute("dingtalkBrowserPath", DingtalkLoginMode.BROWSER.loginPath());
        model.addAttribute("wechatLoginPath", officialAccount ? WechatLoginMode.WEBVIEW.loginPath() : WechatLoginMode.BROWSER.loginPath());
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

    /** 按当前交互环境显示已关联来源，SDK能力不转换成网页登录按钮。 */
    private boolean isVisible(HttpServletRequest request, Optional<ClientPolicy> policy, BrowserLoginProviderType providerType) {
        if (policy.isEmpty() || !canStart(request, policy.get(), providerType)) { return false; }
        try { return clientPolicyService.requireSource(policy.get(), providerType.name()).visible(); }
        catch (AuthenticationServiceException ex) { return false; }
    }

    /** 即使当前环境不能发起，也保留公众号需微信内打开的业务提示。 */
    private boolean isOfficialAccount(Optional<ClientPolicy> policy) {
        if (policy.isEmpty() || !policy.get().allowed(BrowserLoginProviderType.WECHAT)) { return false; }
        try {
            return clientPolicyService.requireSource(policy.get(), "WECHAT").sourceType()
                == dev.macula.cloud.system.enums.IdentitySourceType.WECHAT_OFFICIAL_ACCOUNT;
        } catch (AuthenticationServiceException ex) { return false; }
    }

    /** 企业微信容器优先于其携带的普通微信或手机标记。 */
    private boolean insideWecom(HttpServletRequest request) {
        return dev.macula.cloud.system.enums.IdentityBrowserEnvironment.fromUserAgent(request.getHeader(HttpHeaders.USER_AGENT))
            == dev.macula.cloud.system.enums.IdentityBrowserEnvironment.WECOM_WEBVIEW;
    }

    /** Web/SPA只执行管理员明确设置的通用网页直登；UA不触发端内自动分流。 */
    private String automaticLoginPath(HttpServletRequest request, ClientPolicy policy) {
        if (!java.util.Set.of("WEB_SERVER", "SPA").contains(policy.applicationType() == null ? "" : policy.applicationType())) { return null; }
        BrowserLoginProviderType direct = policy.directProvider();
        if (direct == BrowserLoginProviderType.WECHAT && canStart(request, policy, direct)
            && clientPolicyService.requireSourceForEnvironment(policy, "WECHAT", dev.macula.cloud.system.enums.IdentityBrowserEnvironment.fromUserAgent(request.getHeader(HttpHeaders.USER_AGENT))).sourceType()
                == dev.macula.cloud.system.enums.IdentitySourceType.WECHAT_OFFICIAL_ACCOUNT) { return null; }
        return direct != null && canStart(request, policy, direct) ? loginPath(direct) : null;
    }

    /** UA只决定体验入口；来源准入及最终身份由受控Client、平台Code和state决定。 */
    private boolean canStart(HttpServletRequest request, ClientPolicy policy, BrowserLoginProviderType providerType) {
        if (!policy.allowed(providerType)) { return false; }
        try {
            var source = clientPolicyService.requireSourceForEnvironment(policy, providerType.name(), dev.macula.cloud.system.enums.IdentityBrowserEnvironment.fromUserAgent(request.getHeader(HttpHeaders.USER_AGENT)));
            var environment = dev.macula.cloud.system.enums.IdentityBrowserEnvironment.fromUserAgent(request.getHeader(HttpHeaders.USER_AGENT));
            String mode = switch (source.sourceType()) {
                case GITHUB_OAUTH -> "";
                case WECHAT_OFFICIAL_ACCOUNT -> "WEBVIEW";
                case WECOM_WEB -> insideWecom(request) ? "WEBVIEW" : "BROWSER";
                default -> "BROWSER";
            };
            return source.sourceType().supportsBrowserEnvironment(environment, mode);
        } catch (AuthenticationServiceException ex) { return false; }
    }

    /** 返回当前已实现网页登录的稳定路径。 */
    private String loginPath(BrowserLoginProviderType providerType) {
        return switch (providerType) {
            case GITHUB -> ThirdPartyProviderType.GITHUB.loginPath();
            case WECOM -> ThirdPartyProviderType.WECOM.loginPath();
            case FEISHU -> FeishuLoginMode.BROWSER.loginPath();
            case DINGTALK -> DingtalkLoginMode.BROWSER.loginPath();
            case WECHAT -> WechatLoginMode.BROWSER.loginPath();
        };
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
