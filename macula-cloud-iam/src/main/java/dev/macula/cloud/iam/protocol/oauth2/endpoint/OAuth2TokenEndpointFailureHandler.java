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

import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalBindingRequiredException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2ErrorAuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;

/**
 * 只为 binding_required 增加 opaque ticket，其余 Token 错误保持框架原生输出。
 *
 * @author Rain
 * @since 6.1.0
 */
public class OAuth2TokenEndpointFailureHandler implements AuthenticationFailureHandler {
    private static final String BINDING_REQUIRED_RESPONSE = "{\"error\":\"binding_required\","
        + "\"error_description\":\"The verified identity must be bound to an existing account.\","
        + "\"binding_ticket\":\"%s\"}";

    private final AuthenticationFailureHandler delegate = new OAuth2ErrorAuthenticationFailureHandler();

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
        AuthenticationException exception) throws IOException, ServletException {
        if (!(exception instanceof ExternalBindingRequiredException bindingRequired)) {
            delegate.onAuthenticationFailure(request, response, exception);
            return;
        }
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.getWriter().write(BINDING_REQUIRED_RESPONSE.formatted(bindingRequired.getBindingTicket()));
    }
}
