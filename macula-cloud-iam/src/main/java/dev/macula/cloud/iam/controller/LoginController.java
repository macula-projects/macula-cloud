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
import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService;
import dev.macula.cloud.iam.enums.ThirdPartyProviderType;
import dev.macula.cloud.iam.authentication.wecom.WecomLoginMode;
import dev.macula.cloud.iam.handler.AuthenticationFailureReason;
import dev.macula.cloud.iam.service.support.SysOAuth2ClientService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

/**
 * The type LoginController.
 *
 * @author felord.cn
 * @since 1.0.0
 */
@Controller
@RequiredArgsConstructor
public class LoginController {
    private final SysOAuth2ClientService clientService;
    private final IdentityProviderConfigService providerConfigService;

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
        model.addAttribute("githubLoginEnabled",
            providerConfigService.isVisible(ThirdPartyProviderType.GITHUB.providerType()));
        model.addAttribute("wecomLoginEnabled",
            providerConfigService.isVisible(ThirdPartyProviderType.WECOM.providerType()));
        model.addAttribute("githubLoginPath", ThirdPartyProviderType.GITHUB.loginPath());
        model.addAttribute("wecomLoginPath", ThirdPartyProviderType.WECOM.loginPath());
        model.addAttribute("wecomBrowserPath", WecomLoginMode.BROWSER.loginPath());
        model.addAttribute("wecomWebviewPath", WecomLoginMode.WEBVIEW.loginPath());
        if (StrUtil.isNotBlank(authenticationError)) {
            model.addAttribute("authenticationError",
                AuthenticationFailureReason.fromCode(authenticationError).message());
        }
        model.addAttribute("enableSmsLogin", enableSmsLogin);
        return "login";
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
