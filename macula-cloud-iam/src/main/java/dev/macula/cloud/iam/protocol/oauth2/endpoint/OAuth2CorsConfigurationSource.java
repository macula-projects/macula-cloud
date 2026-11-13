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

import dev.macula.boot.constants.GlobalConstants;
import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2ClientRegistrationService;
import dev.macula.cloud.iam.protocol.oauth2.profile.ProfileController;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * 为 Token、元数据和用户资料端点生成受控 CORS 策略。
 *
 * @author Rain
 * @since 6.1.0
 */
@RequiredArgsConstructor
public class OAuth2CorsConfigurationSource implements CorsConfigurationSource {
    public static final String DISCOVERY_PATH = "/.well-known/openid-configuration";
    public static final String OAUTH_METADATA_PATH = "/.well-known/oauth-authorization-server";
    public static final String JWKS_PATH = "/oauth2/jwks";
    private static final Set<String> METADATA_PATHS = Set.of(DISCOVERY_PATH, OAUTH_METADATA_PATH, JWKS_PATH);
    private static final Duration MAX_AGE = Duration.ofMinutes(10);

    private final OAuth2ClientRegistrationService clientService;
    private final String tokenEndpoint;

    /**
     * 公开元数据只扩展 GET，仍绑定已启用 Client 的精确 Origin。
     */
    @Override
    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
        String path = applicationPath(request);
        if (ProfileController.PATH.equals(path)) {
            return profileConfiguration(request);
        }
        if (METADATA_PATHS.contains(path)) {
            return metadataConfiguration(request);
        }
        if (!tokenEndpoint.equals(path)) {
            return null;
        }
        String origin = singleHeader(request, HttpHeaders.ORIGIN);
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return preflightConfiguration(request, origin);
        }
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return hasOriginHeader(request) ? deniedConfiguration() : null;
        }
        if (origin == null) {
            return hasOriginHeader(request) ? deniedConfiguration() : null;
        }
        return clientService.allowedOrigins().contains(origin) ? tokenConfiguration(origin) : deniedConfiguration();
    }

    /**
     * Profile只允许已登记Origin的GET，不允许Cookie凭据或任意请求头。
     */
    private CorsConfiguration profileConfiguration(HttpServletRequest request) {
        if (!hasOriginHeader(request)) {
            return null;
        }
        String origin = singleHeader(request, HttpHeaders.ORIGIN);
        boolean preflight = HttpMethod.OPTIONS.matches(request.getMethod());
        String requested = request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
        if (origin == null || !clientService.allowedOrigins().contains(origin)
                || !(HttpMethod.GET.matches(request.getMethod()) || preflight)
                || preflight && (!HttpMethod.GET.matches(request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD))
                ||
                requested != null && !Set.of("authorization").containsAll(Arrays.stream(requested.split(","))
                        .map(String::trim).map(value -> value.toLowerCase(Locale.ROOT)).toList()))) {
            return deniedConfiguration();
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(origin));
        configuration.setAllowedMethods(List.of("GET", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(MAX_AGE);
        return configuration;
    }

    /**
     * 无 Origin 保持公共元数据行为；跨域 GET/OPTIONS 禁止凭据和其他请求头。
     */
    private CorsConfiguration metadataConfiguration(HttpServletRequest request) {
        if (!hasOriginHeader(request)) {
            return null;
        }
        String origin = singleHeader(request, HttpHeaders.ORIGIN);
        boolean preflight = HttpMethod.OPTIONS.matches(request.getMethod());
        if (origin == null || !clientService.allowedOrigins().contains(origin)
                || !(HttpMethod.GET.matches(request.getMethod()) || preflight)
                || preflight && (!HttpMethod.GET.matches(request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD))
                || StringUtils.hasText(request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS)))) {
            return deniedConfiguration();
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(origin));
        configuration.setAllowedMethods(List.of(HttpMethod.GET.name(), HttpMethod.OPTIONS.name()));
        configuration.setAllowedHeaders(List.of());
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(MAX_AGE);
        return configuration;
    }

    private String applicationPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            return path.substring(contextPath.length());
        }
        return path;
    }

    /**
     * 仅允许受控Token预检；已退役协议端点不再查询Client。
     */
    private CorsConfiguration preflightConfiguration(HttpServletRequest request, String origin) {
        if (origin == null) {
            return hasOriginHeader(request) ? deniedConfiguration() : null;
        }
        return declaresRequiredHeaders(request) && clientService.allowedOrigins().contains(origin)
                ? tokenConfiguration(origin) : deniedConfiguration();
    }

    /**
     * Token预检只接受既有的明确请求头集合。
     */
    private boolean declaresRequiredHeaders(HttpServletRequest request) {
        String requestedHeaders = request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
        if (requestedHeaders == null || requestedHeaders.isBlank()) {
            return true;
        }
        Set<String> names = Arrays.stream(requestedHeaders.split(","))
                .map(String::trim).map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        Set<String> allowed = Set.of(HttpHeaders.CONTENT_TYPE.toLowerCase(Locale.ROOT),
                HttpHeaders.AUTHORIZATION.toLowerCase(Locale.ROOT),
                GlobalConstants.TENANT_ID_NAME.toLowerCase(Locale.ROOT));
        return names.stream().noneMatch(String::isBlank) && allowed.containsAll(names);
    }

    private CorsConfiguration tokenConfiguration(String origin) {
        return configuration(origin, List.of(HttpHeaders.CONTENT_TYPE, HttpHeaders.AUTHORIZATION,
                GlobalConstants.TENANT_ID_NAME));
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

}
