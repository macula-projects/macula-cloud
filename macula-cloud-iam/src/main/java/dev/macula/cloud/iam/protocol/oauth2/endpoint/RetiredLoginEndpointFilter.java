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
package dev.macula.cloud.iam.protocol.oauth2.endpoint;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 对退役验证码协议端点返回固定结果，阻止落入托管认证或触发发码副作用。
 *
 * @author Macula
 * @since 6.1.0
 */
public final class RetiredLoginEndpointFilter extends OncePerRequestFilter {
    private static final Set<String> PATHS = Set.of("/api/v1/oauth2/sms-codes", "/api/v1/oauth2/email-codes",
            "/api/v1/oauth2/login-challenges/captcha");

    /**
     * 匹配精确应用路径，不扩大到托管或绑定发码端点。
     */
    public static boolean matches(HttpServletRequest request) {
        return PATHS.contains(request.getRequestURI().substring(request.getContextPath().length()));
    }

    /**
     * 在CORS、CSRF和认证之前短路；不访问Session、Client或请求正文。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!matches(request)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_GONE);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"error\":\"endpoint_retired\"}");
    }
}
