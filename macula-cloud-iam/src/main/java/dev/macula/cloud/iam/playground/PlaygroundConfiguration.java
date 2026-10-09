/*
 * Copyright (c) 2023 Macula
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

package dev.macula.cloud.iam.playground;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 将演示页面、资源和 API 放在独立过滤链中，关闭时统一 404。
 * @author Rain
 * @since 6.1.0
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PlaygroundProperties.class)
@Import({PlaygroundApiController.class, PlaygroundPageController.class})
public class PlaygroundConfiguration implements WebMvcConfigurer {
    /** Run a separately configured framework filter only for demo device authorizations. */
    public static final class DeviceVerificationFilter extends OncePerRequestFilter {
        private final PlaygroundAccessPolicy policy;
        private final String issuer;
        private final org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService authorizations;
        private final org.springframework.security.oauth2.server.authorization.web.OAuth2DeviceVerificationEndpointFilter delegate;
        private final CsrfFilter csrf = new CsrfFilter(new org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository());

        public DeviceVerificationFilter(PlaygroundAccessPolicy policy, String issuer, RegisteredClientRepository clients,
            org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService authorizations,
            org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService consents) {
            this.policy = policy;
            this.issuer = issuer;
            this.authorizations = authorizations;
            var verification = new org.springframework.security.oauth2.server.authorization.authentication.OAuth2DeviceVerificationAuthenticationProvider(
                clients, authorizations, consents);
            // Each device connection requires deliberate confirmation, even after earlier consent.
            verification.setAuthorizationConsentRequired(context -> true);
            var consent = new org.springframework.security.oauth2.server.authorization.authentication.OAuth2DeviceAuthorizationConsentAuthenticationProvider(
                clients, authorizations, consents);
            consent.setAuthorizationConsentCustomizer(context -> {
                var token = (org.springframework.security.oauth2.server.authorization.authentication.OAuth2DeviceAuthorizationConsentAuthenticationToken)
                    context.getAuthentication();
                if ("deny".equals(token.getAdditionalParameters().get("decision"))) {
                    context.getAuthorizationConsent().authorities(java.util.Set::clear);
                }
            });
            delegate = new org.springframework.security.oauth2.server.authorization.web.OAuth2DeviceVerificationEndpointFilter(
                new org.springframework.security.authentication.ProviderManager(verification, consent));
            delegate.setConsentPage("/playground/device");
            delegate.setAuthenticationSuccessHandler((request, response, authentication) ->
                response.sendRedirect(request.getContextPath() + "/playground/device?result=approved"));
            delegate.setAuthenticationFailureHandler((request, response, exception) ->
                response.sendRedirect(request.getContextPath() + "/playground/device?result="
                    + (exception instanceof org.springframework.security.oauth2.core.OAuth2AuthenticationException oauth
                        && "access_denied".equals(oauth.getError().getErrorCode()) ? "denied" : "invalid")));
            // AS intentionally skips its own CSRF filter; this separate name prevents OncePerRequestFilter skipping.
            csrf.setBeanName("playgroundDeviceCsrf");
        }

        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (!"/oauth2/device_verification".equals(path)) { chain.doFilter(request, response); return; }
            String code = normalizeUserCode(request.getParameter("user_code"));
            var authorization = code == null ? null : authorizations.findByToken(code,
                new org.springframework.security.oauth2.server.authorization.OAuth2TokenType("user_code"));
            boolean demo = PlaygroundRegisteredClientRepository.DEVICE.equals(request.getParameter("client_id"))
                || (authorization != null && PlaygroundRegisteredClientRepository.DEVICE.equals(authorization.getRegisteredClientId()));
            if (!demo) { chain.doFilter(request, response); return; }
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Referrer-Policy", "strict-origin");
            if (!policy.isEnabled()) { response.setStatus(404); return; }
            // Never let a forged demo client_id route a business device record to this adapter.
            if (authorization == null || !PlaygroundRegisteredClientRepository.DEVICE.equals(authorization.getRegisteredClientId())) {
                response.setStatus(400); return;
            }
            var principal = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (principal == null || !principal.isAuthenticated()
                || principal instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
                response.sendRedirect(request.getContextPath() + "/playground/device?user_code=" + code);
                return;
            }
            if ("POST".equals(request.getMethod()) && !policy.issuer(issuer).toString().equals(request.getHeader("Origin"))) {
                response.setStatus(403); return;
            }
            csrf.doFilter(request, response, (req, res) -> delegate.doFilter(req, res, chain));
        }

        public static String normalizeUserCode(String value) {
            if (value == null || value.length() > 32) return null;
            String normalized = value.toUpperCase(java.util.Locale.ENGLISH).replaceAll("[^A-Z0-9]", "");
            return normalized.length() == 8 ? normalized.substring(0, 4) + "-" + normalized.substring(4) : null;
        }
    }

    @Bean
    PlaygroundProtocolClient playgroundProtocolClient(PlaygroundAccessPolicy policy,
        @Value("${macula.cloud.iam.issuer-uri}") String issuer) {
        return new PlaygroundProtocolClient(policy, issuer);
    }

    @Bean
    PlaygroundSessionStore playgroundSessionStore() { return new PlaygroundSessionStore(); }

    @Bean
    PlaygroundOidcVerifier playgroundOidcVerifier(PlaygroundProtocolClient client) { return new PlaygroundOidcVerifier(client); }

    @Bean
    PlaygroundFlowService playgroundFlowService(PlaygroundProtocolClient client, PlaygroundOidcVerifier verifier,
        PlaygroundSessionStore sessions, RegisteredClientRepository clients, PlaygroundProperties properties) {
        return new PlaygroundFlowService(client, verifier, sessions, clients, properties);
    }

    @Bean
    PlaygroundAccessPolicy playgroundAccessPolicy(PlaygroundProperties properties, Environment environment) {
        return new PlaygroundAccessPolicy(properties, environment);
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    SecurityFilterChain playgroundSecurityFilterChain(HttpSecurity http, PlaygroundAccessPolicy policy,
        @Value("${macula.cloud.iam.issuer-uri}") String issuer) throws Exception {
        http.securityMatcher("/playground", "/playground/**", "/api/v1/iam-playground", "/api/v1/iam-playground/**")
            .authorizeHttpRequests(auth -> auth.requestMatchers("/playground/device").authenticated()
                .anyRequest().permitAll())
            .exceptionHandling(errors -> errors.authenticationEntryPoint(
                new org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint("/login")))
            .addFilterBefore(new PlaygroundGateFilter(policy, issuer), CsrfFilter.class);
        return http.build();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/playground/assets/**").addResourceLocations("classpath:/playground/")
            .setCachePeriod(0);
    }

    /** Kept inside the security chain (not a servlet bean), so it cannot run twice. */
    static final class PlaygroundGateFilter extends OncePerRequestFilter {
        private final PlaygroundAccessPolicy policy;
        private final String issuer;

        PlaygroundGateFilter(PlaygroundAccessPolicy policy, String issuer) {
            this.policy = policy;
            this.issuer = issuer;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
            response.setHeader("Cache-Control", "no-store");
            // no-referrer makes browsers send Origin: null on native form POSTs.
            // Device forms need an origin-only referrer policy for exact-origin enforcement.
            response.setHeader("Referrer-Policy", request.getRequestURI().endsWith("/playground/device")
                ? "strict-origin" : "no-referrer");
            if (!policy.isEnabled()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            if (!Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())) {
                String origin = request.getHeader("Origin");
                URI allowed = policy.issuer(issuer);
                String expected = allowed.getScheme() + "://" + allowed.getRawAuthority();
                // Browser mutations must be same-origin as the configured external issuer.
                if (!expected.equals(origin)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
            }
            chain.doFilter(request, response);
        }
    }
}
