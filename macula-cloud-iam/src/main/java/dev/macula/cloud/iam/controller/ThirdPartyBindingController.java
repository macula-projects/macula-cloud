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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import dev.macula.cloud.iam.authentication.mfa.AdditionalAuthenticationRequiredException;
import dev.macula.cloud.iam.authentication.mfa.AuthenticationCheckpoint;
import dev.macula.cloud.iam.authentication.mfa.AuthenticationMethod;
import dev.macula.cloud.iam.authentication.mfa.MfaCompletionService;
import dev.macula.cloud.iam.authentication.thirdparty.ExternalBindingTicketStore;
import dev.macula.cloud.iam.authentication.thirdparty.PendingThirdPartyBindingRepository;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyAuthenticationToken;
import dev.macula.cloud.iam.authentication.thirdparty.binding.ThirdPartyBindingService;
import dev.macula.cloud.iam.handler.AuthenticationFailureReason;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

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
    private final ExternalBindingTicketStore tickets;
    private final HttpSessionSecurityContextRepository contexts = new HttpSessionSecurityContextRepository();
    private MfaCompletionService mfaCompletion =
            MfaCompletionService.noOp();

    /**
     * Spring生产装配必须注入统一策略；显式构造的存储/生成器单测保留默认行为。
     */
    @Autowired
    public void setMfaCompletion(MfaCompletionService completion) {
        this.mfaCompletion = Objects.requireNonNull(completion);
    }

    @GetMapping(BINDING_PATH)
    public String page(Model model, HttpServletRequest request, HttpServletResponse response,
            @RequestAttribute(name = "org.springframework.security.web.csrf.CsrfToken", required = false) CsrfToken csrf) {
        try {
            pending.current(request);
        } catch (AuthenticationException ex) {
            return "redirect:/login?" + OAuth2ParameterNames.ERROR + "="
                    + AuthenticationFailureReason.AUTHENTICATION_REJECTED.code();
        }
        model.addAttribute("_csrfToken", csrf);
        model.addAttribute("bindingComplete", false);
        model.addAttribute("enableBindingSms", binding.isSmsAvailable());
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        model.addAttribute("enableBindingSession", current != null && current.isAuthenticated()
                && current.getPrincipal() instanceof SysUserDetails);
        secure(response);
        return "third-party-bind";
    }

    /**
     * 已验证外部pending与账号归属后续转；SDK只完成绑定，浏览器保留来源证明和认证时间。
     */
    @PostMapping(BINDING_PATH)
    public String bind(@RequestParam String username, @RequestParam String method,
            @RequestParam(required = false) String password, @RequestParam(required = false) String phone,
            @RequestParam(required = false) String code, HttpServletRequest request, HttpServletResponse response,
            Model model, Authentication current,
            @RequestAttribute(name = "org.springframework.security.web.csrf.CsrfToken", required = false) CsrfToken csrf) {
        secure(response);
        ThirdPartyBindingService.BoundAccount result;
        try {
            Authentication proof = current instanceof AnonymousAuthenticationToken ? null : current;
            result = binding.bind(request, username, method, password,
                    phone, code, proof);
        } catch (AuthenticationException |
                 DuplicateKeyException ex) {
            model.addAttribute("_csrfToken", csrf);
            model.addAttribute("bindingError", "账号绑定失败，请检查信息后重试。");
            model.addAttribute("enableBindingSms", binding.isSmsAvailable());
            model.addAttribute("enableBindingSession", current != null && current.isAuthenticated()
                    && current.getPrincipal() instanceof SysUserDetails);
            return "third-party-bind";
        }
        secure(response);
        if (result.sdk()) {
            model.addAttribute("bindingComplete", true);
            return "third-party-bind";
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(result.user(), null,
                result.user().getAuthorities()));
        try {
            var sourceAuthentication = result.context() == null ? context.getAuthentication()
                    : ThirdPartyAuthenticationToken.authenticated(
                    result.context().providerType(), result.user(), result.context());
            var methodVerified = "sms".equals(result.proofMethod())
                    ? AuthenticationMethod.SMS
                    : "password".equals(result.proofMethod())
                    ? AuthenticationMethod.PASSWORD
                    : AuthenticationMethod.EXISTING_SESSION;
            mfaCompletion.check(context.getAuthentication(),
                    result.context() == null ? null : result.context().clientId(),
                    AuthenticationCheckpoint.BINDING_SESSION, "binding",
                    methodVerified,
                    MfaCompletionService.proof(sourceAuthentication));
        } catch (AdditionalAuthenticationRequiredException ex) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            model.addAttribute("_csrfToken", csrf);
            model.addAttribute("bindingComplete", false);
            model.addAttribute("enableBindingSms", binding.isSmsAvailable());
            model.addAttribute("enableBindingSession", current != null && current.isAuthenticated()
                    && current.getPrincipal() instanceof SysUserDetails);
            model.addAttribute("bindingError", "认证暂不可用，请重新登录。");
            return "third-party-bind";
        }
        if (result.context() != null) {
            var external = ThirdPartyAuthenticationToken.authenticated(
                    result.context().providerType(), result.user(), result.context());
            // 绑定账号证明完成后保留平台认证时间和私有来源证明；SDK分支此前已返回，不建立SSO。
            context.setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(result.user(), null, external.getAuthorities()));
            ((UsernamePasswordAuthenticationToken) context.getAuthentication()).setDetails(
                    new HashMap<>(Map.of(
                            MfaCompletionService.SOURCE_PROOF,
                            MfaCompletionService.proof(external))));
        }
        request.changeSessionId();
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return "redirect:" + result.target();
    }

    /**
     * 交换opaque票据为当前会话pending，立即清除地址栏中的票据。
     */
    @GetMapping(BINDING_PATH + "/external")
    public String external(@RequestParam String ticket, HttpServletRequest request, HttpServletResponse response) {
        secure(response);
        var claimed = tickets.claimForBrowser(ticket);
        try {
            pending.saveSdk(request, claimed);
        } catch (RuntimeException ex) {
            tickets.release(claimed);
            throw ex;
        }
        return "redirect:" + BINDING_PATH;
    }

    /**
     * 专用绑定用途发码，不从SavedRequest或前端clientId取账号路由。
     */
    @PostMapping(BINDING_PATH + "/sms-code")
    @ResponseBody
    public Map<String, String> sendSms(@RequestParam String username, @RequestParam String phone,
            HttpServletRequest request, HttpServletResponse response) {
        secure(response);
        binding.sendSms(request, username, phone);
        return Map.of("status", "submitted");
    }

    /**
     * 绑定页和票据入口禁止缓存及Referer传播。
     */
    private void secure(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
    }

    @PostMapping(BINDING_CANCEL_PATH)
    public String cancel(HttpServletRequest request) {
        pending.clear(request);
        return "redirect:/login";
    }
}
