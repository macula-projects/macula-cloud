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
import java.util.Collections;
import java.util.List;

import dev.macula.cloud.iam.service.client.SysOAuth2ClientService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2ErrorAuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 在 Client 认证后校验浏览器 Token 请求的精确 Origin。
 *
 * @author Rain
 * @since 6.1.0
 */
public class OAuth2TokenOriginFilter extends OncePerRequestFilter {
    private final PathPatternRequestMatcher tokenEndpointMatcher;
    private final SysOAuth2ClientService clientService;
    private final AuthenticationFailureHandler failureHandler = new OAuth2ErrorAuthenticationFailureHandler();

    public OAuth2TokenOriginFilter(String tokenEndpoint, SysOAuth2ClientService clientService) {
        this.tokenEndpointMatcher = PathPatternRequestMatcher.pathPattern(HttpMethod.POST, tokenEndpoint);
        this.clientService = clientService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!tokenEndpointMatcher.matches(request) || !hasOriginHeader(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String origin = singleOrigin(request);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (origin == null || !(authentication instanceof OAuth2ClientAuthenticationToken client)
                || !client.isAuthenticated() || client.getRegisteredClient() == null
                || !allowedForProtocol(client, origin)) {
            failureHandler.onAuthenticationFailure(request, response,
                    new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST));
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 预检可以用应用Origin并集，实际Token请求只能使用已认证协议视图。
     */
    private boolean allowedForProtocol(OAuth2ClientAuthenticationToken client, String origin) {
        Object allowed = client.getRegisteredClient().getClientSettings().getSetting(
                dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeClientSettings.ALLOWED_ORIGINS);
        return allowed instanceof java.util.Collection<?> values && values.contains(origin);
    }

    private boolean hasOriginHeader(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.ORIGIN) != null;
    }

    private String singleOrigin(HttpServletRequest request) {
        List<String> values = Collections.list(request.getHeaders(HttpHeaders.ORIGIN));
        if (values.size() != 1 || values.get(0) == null || values.get(0).isBlank()
                || values.get(0).contains(",")) {
            return null;
        }
        return values.get(0).trim();
    }

}
