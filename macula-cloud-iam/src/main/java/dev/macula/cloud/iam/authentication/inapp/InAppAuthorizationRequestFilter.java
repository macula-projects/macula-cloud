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
package dev.macula.cloud.iam.authentication.inapp;

import java.io.IOException;

import dev.macula.cloud.iam.authentication.mfa.MfaCompletionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 在SAS授权处理前隔离缺少端内证明的本次请求，保留框架校验及原Session中的其他SSO。
 *
 * @author Rain
 * @since 6.1.0
 */
public final class InAppAuthorizationRequestFilter extends OncePerRequestFilter {
    private final InAppLoginPolicyService policy;
    private final AuthorizationServerSettings settings;

    /**
     * 只在授权服务器Security链安装，不作为Servlet全局过滤器注册。
     */
    public InAppAuthorizationRequestFilter(InAppLoginPolicyService policy, AuthorizationServerSettings settings) {
        this.policy = policy;
        this.settings = settings;
    }

    /**
     * 同意POST与初始GET使用同一个授权入口，不干预Token或其他应用请求。
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !settings.getAuthorizationEndpoint()
                .equals(request.getRequestURI().substring(request.getContextPath().length()));
    }

    /**
     * 无证明时交给SAS先校验请求再走RequestCache，绝不直接使用未校验的回调URL。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String[] ids = request.getParameterValues("client_id");
        if (ids == null || ids.length != 1 || ids[0].isBlank()) {
            chain.doFilter(request, response);
            return;
        }
        var client = policy.current(ids[0]);
        var original = SecurityContextHolder.getContext();
        if (client == null || !"IN_APP".equals(client.getApplicationType())
                || policy.validProof(client, original.getAuthentication(),
                MfaCompletionService.proof(original.getAuthentication()), "authorization_code")) {
            chain.doFilter(request, response);
            return;
        }
        // 不修改original对象，不调用saveContext；其他应用的Session认证不会被匿名覆盖。
        SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.setContext(original);
        }
    }
}
