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
package dev.macula.cloud.iam.config;

import dev.macula.boot.constants.GlobalConstants;
import dev.macula.cloud.iam.controller.ProtocolVerificationCodeController;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeClientSettings;
import dev.macula.cloud.iam.service.support.SysOAuth2ClientService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 为 Token 与验证码协议端点生成受控 CORS 策略。
 *
 * @author Rain
 * @since 6.1.0
 */
@RequiredArgsConstructor
public class ProtocolLoginCorsConfigurationSource implements CorsConfigurationSource {
    private static final Duration MAX_AGE = Duration.ofMinutes(10);
    private static final List<String> PATHS = List.of(
        ProtocolVerificationCodeController.CAPTCHA_PATH,
        ProtocolVerificationCodeController.SMS_CODE_PATH,
        ProtocolVerificationCodeController.EMAIL_CODE_PATH);

    private final RegisteredClientRepository clients;
    private final SysOAuth2ClientService clientService;
    private final String tokenEndpoint;

    @Override
    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
        String path = applicationPath(request);
        boolean tokenRequest = tokenEndpoint.equals(path);
        if (!tokenRequest && !PATHS.contains(path)) {
            return null;
        }
        String origin = singleHeader(request, HttpHeaders.ORIGIN);
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return preflightConfiguration(request, origin, tokenRequest);
        }
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return tokenRequest && hasOriginHeader(request) ? deniedConfiguration() : null;
        }
        if (tokenRequest) {
            if (origin == null) {
                return hasOriginHeader(request) ? deniedConfiguration() : null;
            }
            return clientService.allowedOrigins().contains(origin) ? tokenConfiguration(origin) : deniedConfiguration();
        }
        String clientId = singleHeader(request, ProtocolVerificationCodeController.CLIENT_ID_HEADER);
        RegisteredClient client = clientId == null ? null : clients.findByClientId(clientId);
        if (origin == null || client == null || !allowedOrigins(client).contains(origin)) {
            return null;
        }
        return verificationConfiguration(origin);
    }

    private String applicationPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            return path.substring(contextPath.length());
        }
        return path;
    }

    private CorsConfiguration preflightConfiguration(HttpServletRequest request, String origin,
        boolean tokenRequest) {
        if (origin == null) {
            return tokenRequest && hasOriginHeader(request) ? deniedConfiguration() : null;
        }
        if (!declaresRequiredHeaders(request, tokenRequest) || !clientService.allowedOrigins().contains(origin)) {
            return tokenRequest ? deniedConfiguration() : null;
        }
        return tokenRequest ? tokenConfiguration(origin) : verificationConfiguration(origin);
    }

    private boolean declaresRequiredHeaders(HttpServletRequest request, boolean tokenRequest) {
        String requestedHeaders = request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
        if (requestedHeaders == null || requestedHeaders.isBlank()) {
            return tokenRequest;
        }
        Set<String> names = List.of(requestedHeaders.split(",")).stream()
            .map(String::trim).map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        Set<String> allowed = tokenRequest
            ? Set.of(HttpHeaders.CONTENT_TYPE.toLowerCase(Locale.ROOT),
                HttpHeaders.AUTHORIZATION.toLowerCase(Locale.ROOT),
                GlobalConstants.TENANT_ID_NAME.toLowerCase(Locale.ROOT))
            : Set.of(HttpHeaders.CONTENT_TYPE.toLowerCase(Locale.ROOT),
                ProtocolVerificationCodeController.CLIENT_ID_HEADER.toLowerCase(Locale.ROOT));
        if (names.stream().anyMatch(String::isBlank) || !allowed.containsAll(names)) {
            return false;
        }
        return tokenRequest || names.contains(HttpHeaders.CONTENT_TYPE.toLowerCase(Locale.ROOT))
            && names.contains(ProtocolVerificationCodeController.CLIENT_ID_HEADER.toLowerCase(Locale.ROOT));
    }

    private CorsConfiguration tokenConfiguration(String origin) {
        return configuration(origin, List.of(HttpHeaders.CONTENT_TYPE, HttpHeaders.AUTHORIZATION,
            GlobalConstants.TENANT_ID_NAME));
    }

    private CorsConfiguration verificationConfiguration(String origin) {
        return configuration(origin, List.of(HttpHeaders.CONTENT_TYPE,
            ProtocolVerificationCodeController.CLIENT_ID_HEADER));
    }

    private CorsConfiguration configuration(String origin, List<String> allowedHeaders) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(origin));
        configuration.setAllowedMethods(List.of(HttpMethod.POST.name(), HttpMethod.OPTIONS.name()));
        configuration.setAllowedHeaders(allowedHeaders);
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(MAX_AGE);
        return configuration;
    }

    private CorsConfiguration deniedConfiguration() {
        return new CorsConfiguration();
    }

    private boolean hasOriginHeader(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.ORIGIN) != null;
    }

    private String singleHeader(HttpServletRequest request, String name) {
        List<String> values = Collections.list(request.getHeaders(name));
        if (values.size() != 1 || values.get(0) == null || values.get(0).isBlank()
            || values.get(0).contains(",")) {
            return null;
        }
        return values.get(0).trim();
    }

    private Collection<String> allowedOrigins(RegisteredClient client) {
        Object value = client.getClientSettings().getSetting(ExternalCodeClientSettings.ALLOWED_ORIGINS);
        if (value instanceof Collection<?> values) {
            return values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
    }
}
