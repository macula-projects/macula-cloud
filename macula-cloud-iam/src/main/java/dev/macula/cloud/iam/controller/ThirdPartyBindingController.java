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
package dev.macula.cloud.iam.controller;

import dev.macula.cloud.iam.authentication.thirdparty.PendingThirdPartyBindingRepository;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyBindingService;
import dev.macula.cloud.iam.handler.AuthenticationFailureReason;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 第三方身份绑定页面与提交入口，仅在本地账号归属证明成功后建立登录会话。
 *
 * @author Rain
 * @since 6.1.0
 */
@Controller
@RequiredArgsConstructor
public class ThirdPartyBindingController {
    public static final String BINDING_PATH = "/login/bind";
    public static final String BINDING_CANCEL_PATH = BINDING_PATH + "/cancel";

    private final PendingThirdPartyBindingRepository pending;
    private final ThirdPartyBindingService binding;
    private final HttpSessionSecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

    @GetMapping(BINDING_PATH)
    public String page(Model model, HttpServletRequest request,
        @RequestAttribute(name = "org.springframework.security.web.csrf.CsrfToken", required = false) CsrfToken csrf) {
        try {
            pending.current(request);
        } catch (RuntimeException ex) {
            return "redirect:/login?" + OAuth2ParameterNames.ERROR + "="
                + AuthenticationFailureReason.AUTHENTICATION_REJECTED.code();
        }
        model.addAttribute("_csrfToken", csrf);
        return "third-party-bind";
    }

    @PostMapping(BINDING_PATH)
    public String bind(@RequestParam String username, @RequestParam String method,
        @RequestParam(required = false) String password, @RequestParam(required = false) String phone,
        @RequestParam(required = false) String code, HttpServletRequest request, HttpServletResponse response,
        Model model, Authentication current,
        @RequestAttribute(name = "org.springframework.security.web.csrf.CsrfToken", required = false) CsrfToken csrf) {
        ThirdPartyBindingService.BoundAccount result;
        try {
            Authentication proof = current instanceof AnonymousAuthenticationToken ? null : current;
            result = binding.bind(request, username, method, password,
                phone, code, proof);
        } catch (RuntimeException ex) {
            model.addAttribute("_csrfToken", csrf);
            model.addAttribute("bindingError", "账号绑定失败，请检查信息后重试。");
            return "third-party-bind";
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(result.user(), null,
            result.user().getAuthorities()));
        request.changeSessionId();
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return "redirect:" + result.target();
    }

    @PostMapping(BINDING_CANCEL_PATH)
    public String cancel(HttpServletRequest request) {
        pending.clear(request);
        return "redirect:/login";
    }
}
