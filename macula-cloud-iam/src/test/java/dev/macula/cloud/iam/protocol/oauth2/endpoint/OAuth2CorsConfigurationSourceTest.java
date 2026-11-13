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

import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2ClientRegistrationService;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeClientSettings;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证预检按全局 Origin 存在性放行，实际 POST 仍绑定具体 OAuth Client。
 *
 * @author Rain
 * @since 6.1.0
 */
class OAuth2CorsConfigurationSourceTest {
    private static final String ORIGIN = "https://app.example.test";
    private final RegisteredClientRepository clients = mock(RegisteredClientRepository.class);
    private final OAuth2ClientRegistrationService clientService = mock(OAuth2ClientRegistrationService.class);
    private final OAuth2CorsConfigurationSource source =
            new OAuth2CorsConfigurationSource(clientService, "/oauth2/token");

    /**
     * Discovery/JWKS 只对已启用 Client 的精确 Origin 允许无凭据 GET。
     */
    @Test
    void metadataCorsAllowsOnlyRegisteredGetWithoutCredentials() {
        when(clientService.allowedOrigins()).thenReturn(Set.of(ORIGIN));
        for (String path : Set.of("/.well-known/openid-configuration", "/.well-known/oauth-authorization-server",
                "/oauth2/jwks")) {
            var request = new MockHttpServletRequest("GET", path);
            request.addHeader(HttpHeaders.ORIGIN, ORIGIN);
            var allowed = source.getCorsConfiguration(request);
            assertThat(allowed.getAllowedMethods()).containsExactly("GET", "OPTIONS");
            assertThat(allowed.getAllowCredentials()).isFalse();
            assertThat(allowed.getAllowedOrigins()).containsExactly(ORIGIN);
            request.removeHeader(HttpHeaders.ORIGIN);
            request.addHeader(HttpHeaders.ORIGIN, "https://unregistered.example");
            assertThat(source.getCorsConfiguration(request).getAllowedOrigins()).isNull();
            request.removeHeader(HttpHeaders.ORIGIN);
            request.addHeader(HttpHeaders.ORIGIN, ORIGIN);
            request.setMethod("POST");
            assertThat(source.getCorsConfiguration(request).getAllowedOrigins()).isNull();
            request.setMethod("OPTIONS");
            request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");
            assertThat(source.getCorsConfiguration(request).getAllowedOrigins()).containsExactly(ORIGIN);
            request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization");
            assertThat(source.getCorsConfiguration(request).getAllowedOrigins()).isNull();
        }
    }

    /**
     * 已退役端点由退役Filter处理，不恢复旧验证码CORS或读取Client。
     */
    @Test
    void preflightUsesOriginWithoutRequiringUnavailableClientHeaderValue() {
        var request = new MockHttpServletRequest("POST", "/api/v1/oauth2/sms-codes");
        request.addHeader(HttpHeaders.ORIGIN, "https://app.example");
        assertThat(source.getCorsConfiguration(request)).isNull();
        Mockito.verifyNoInteractions(clients);
    }

    @Test
    void actualPostRequiresOriginToBelongToHeaderClient() {
        var request = new MockHttpServletRequest("POST", "/api/v1/oauth2/sms-codes");
        request.addHeader(HttpHeaders.ORIGIN, "https://app.example");
        assertThat(source.getCorsConfiguration(request)).isNull();
        Mockito.verifyNoInteractions(clients);
    }

    @Test
    void preflightRejectsMissingRequiredHeaderDeclaration() {
        MockHttpServletRequest request = request(HttpMethod.OPTIONS);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, HttpHeaders.CONTENT_TYPE);

        assertThat(source.getCorsConfiguration(request)).isNull();
        verifyNoInteractions(clientService, clients);
    }

    @Test
    void tokenCorsUsesGlobalOriginExistenceOnlyAsTransportPermission() {
        MockHttpServletRequest preflight = tokenRequest(HttpMethod.OPTIONS);
        preflight.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                "authorization, content-type, tenantId");
        when(clientService.allowedOrigins()).thenReturn(Set.of(ORIGIN));

        CorsConfiguration preflightConfiguration = source.getCorsConfiguration(preflight);

        assertThat(preflightConfiguration).isNotNull();
        assertThat(preflightConfiguration.getAllowedHeaders())
                .contains(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, "tenantId")
                .doesNotContain("X-OAuth-Client-Id");
        verifyNoInteractions(clients);

        MockHttpServletRequest post = tokenRequest(HttpMethod.POST);
        assertThat(source.getCorsConfiguration(post)).isNotNull();
        verifyNoInteractions(clients);
    }

    @Test
    void tokenCorsRejectsUnknownOriginAndNonPostMethod() {
        MockHttpServletRequest post = tokenRequest(HttpMethod.POST);
        when(clientService.allowedOrigins()).thenReturn(Set.of());
        assertThat(source.getCorsConfiguration(post).getAllowedOrigins()).isNullOrEmpty();

        assertThat(source.getCorsConfiguration(tokenRequest(HttpMethod.GET)).getAllowedOrigins()).isNullOrEmpty();
    }

    @Test
    void tokenCorsRejectsDuplicateOriginHeaders() {
        MockHttpServletRequest post = tokenRequest(HttpMethod.POST);
        post.addHeader(HttpHeaders.ORIGIN, "https://other.example.test");

        assertThat(source.getCorsConfiguration(post).getAllowedOrigins()).isNullOrEmpty();
        verifyNoInteractions(clientService, clients);
    }

    @Test
    void tokenPreflightAcceptsSafelistedAndOptionalHeaderCombinations() {
        when(clientService.allowedOrigins()).thenReturn(Set.of(ORIGIN));

        assertThat(source.getCorsConfiguration(tokenRequest(HttpMethod.OPTIONS))).isNotNull();
        for (String headers : Set.of("authorization", "tenantId", "authorization, tenantId", "content-type")) {
            MockHttpServletRequest request = tokenRequest(HttpMethod.OPTIONS);
            request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, headers);
            assertThat(source.getCorsConfiguration(request)).isNotNull();
        }
    }

    @Test
    void tokenPreflightRejectsUnknownRequestedHeader() {
        MockHttpServletRequest request = tokenRequest(HttpMethod.OPTIONS);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x-unexpected");

        assertThat(source.getCorsConfiguration(request).getAllowedOrigins()).isNullOrEmpty();
        verifyNoInteractions(clientService, clients);
    }

    private MockHttpServletRequest request(HttpMethod method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method.name(),
                "/api/v1/oauth2/login-challenges/captcha");
        request.addHeader(HttpHeaders.ORIGIN, ORIGIN);
        return request;
    }

    private MockHttpServletRequest tokenRequest(HttpMethod method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method.name(), "/oauth2/token");
        request.addHeader(HttpHeaders.ORIGIN, ORIGIN);
        return request;
    }

    private RegisteredClient client(String clientId, Set<String> origins) {
        return RegisteredClient.withId(clientId)
                .clientId(clientId)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://app.example.test/callback")
                .scope("profile")
                .clientSettings(ClientSettings.builder()
                        .setting(ExternalCodeClientSettings.ALLOWED_ORIGINS, origins).build())
                .build();
    }
}
