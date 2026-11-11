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

import dev.macula.cloud.iam.protocol.oauth2.binding.ExternalIdentityBindingController;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 协议失败适配器：Token返回JSON，绑定使用Bearer错误，浏览器才进入托管登录。
 *
 * @author Macula
 * @since 6.1.0
 */
public final class OAuth2ProtocolAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final RequestMatcher token;
    private final RequestMatcher binding = PathPatternRequestMatcher.pathPattern(HttpMethod.POST,
            ExternalIdentityBindingController.BINDING_PATH);
    private final AuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
    private final AuthenticationEntryPoint browser = new OAuth2AuthenticationExceptionEntryPoint("/login");

    /**
     * 使用受信任端点配置，不依据请求参数选择错误处理策略。
     */
    public OAuth2ProtocolAuthenticationEntryPoint(AuthorizationServerSettings settings) {
        token = PathPatternRequestMatcher.pathPattern(HttpMethod.POST, settings.getTokenEndpoint());
    }

    /**
     * Token认证失败不能产生登录Cookie或重定向。
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException failure)
            throws IOException, ServletException {
        if (token.matches(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"invalid_client\"}");
        } else if (binding.matches(request)) {
            bearer.commence(request, response, failure);
        } else {
            browser.commence(request, response, failure);
        }
    }
}
