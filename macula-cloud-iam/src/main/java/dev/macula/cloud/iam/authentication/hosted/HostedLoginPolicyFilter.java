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
package dev.macula.cloud.iam.authentication.hosted;

import dev.macula.cloud.system.enums.OAuth2HostedLoginMethod;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 在认证与发码前执行同一托管策略；禁止把发码拒绝重定向为200登录页。
 *
 * @author Rain
 * @since 6.1.0
 */
public class HostedLoginPolicyFilter extends OncePerRequestFilter {
    private static final String SMS_CODE_PATH = "/api/v1/sms-code";
    private static final Set<String> PATHS = Set.of("/login", "/login/sms", SMS_CODE_PATH);
    private final HostedLoginClientPolicyService policy;
    private final AuthenticationFailureHandler failureHandler;

    /** 过滤器只由Security链安装，避免Servlet容器重复注册。 */
    public HostedLoginPolicyFilter(HostedLoginClientPolicyService policy, AuthenticationFailureHandler failureHandler) {
        this.policy = policy;
        this.failureHandler = failureHandler;
    }

    /** 同时适用于根Servlet映射和反向代理contextPath，避免空servletPath绕过策略。 */
    private String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    /** 不干预Token扩展、第三方回调和既有SSO会话。 */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !PATHS.contains(path(request));
    }

    /** 拒绝发生在密码/SMS认证和验证码发送副作用之前。 */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        try {
            HostedLoginClientPolicyService.Context context = policy.resolve(request, response);
            OAuth2HostedLoginMethod method = "/login".equals(path(request))
                ? OAuth2HostedLoginMethod.PASSWORD : OAuth2HostedLoginMethod.SMS;
            if (!context.methods().contains(method)) {
                throw new org.springframework.security.authentication.AuthenticationServiceException("当前应用不允许该登录方式");
            }
            request.setAttribute(HostedLoginClientPolicyService.CONTEXT_ATTRIBUTE, context);
        } catch (AuthenticationException ex) {
            if (SMS_CODE_PATH.equals(path(request))) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "当前应用不允许短信登录");
            } else {
                failureHandler.onAuthenticationFailure(request, response, ex);
            }
            return;
        }
        chain.doFilter(request, response);
    }
}
