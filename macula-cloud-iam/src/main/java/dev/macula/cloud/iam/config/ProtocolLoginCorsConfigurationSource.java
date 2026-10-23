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
 * 依据单值 Client 提示与 Client 的精确 Origin allowlist 生成验证码端点 CORS 策略。
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

    @Override
    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
        if (!PATHS.contains(applicationPath(request))) {
            return null;
        }
        String origin = singleHeader(request, HttpHeaders.ORIGIN);
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return preflightConfiguration(request, origin);
        }
        String clientId = singleHeader(request, ProtocolVerificationCodeController.CLIENT_ID_HEADER);
        RegisteredClient client = clientId == null ? null : clients.findByClientId(clientId);
        if (client == null || origin == null || !allowedOrigins(client).contains(origin)) {
            return null;
        }
        return configuration(origin);
    }

    private String applicationPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            return path.substring(contextPath.length());
        }
        return path;
    }

    private CorsConfiguration preflightConfiguration(HttpServletRequest request, String origin) {
        if (origin == null || !declaresRequiredHeaders(request) || !clientService.hasAllowedOrigin(origin)) {
            return null;
        }
        return configuration(origin);
    }

    private boolean declaresRequiredHeaders(HttpServletRequest request) {
        String requestedHeaders = request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
        if (requestedHeaders == null) {
            return false;
        }
        Set<String> names = List.of(requestedHeaders.split(",")).stream()
            .map(String::trim).map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        return names.contains(HttpHeaders.CONTENT_TYPE.toLowerCase(Locale.ROOT))
            && names.contains(ProtocolVerificationCodeController.CLIENT_ID_HEADER.toLowerCase(Locale.ROOT));
    }

    private CorsConfiguration configuration(String origin) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(origin));
        configuration.setAllowedMethods(List.of(HttpMethod.POST.name(), HttpMethod.OPTIONS.name()));
        configuration.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE,
            ProtocolVerificationCodeController.CLIENT_ID_HEADER));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(MAX_AGE);
        return configuration;
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
