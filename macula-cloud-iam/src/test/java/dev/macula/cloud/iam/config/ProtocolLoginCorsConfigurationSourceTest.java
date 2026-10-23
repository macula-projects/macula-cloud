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
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.web.cors.CorsConfiguration;

import java.util.Set;

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
class ProtocolLoginCorsConfigurationSourceTest {
    private static final String ORIGIN = "https://app.example.test";
    private final RegisteredClientRepository clients = mock(RegisteredClientRepository.class);
    private final SysOAuth2ClientService clientService = mock(SysOAuth2ClientService.class);
    private final ProtocolLoginCorsConfigurationSource source =
        new ProtocolLoginCorsConfigurationSource(clients, clientService);

    @Test
    void preflightUsesOriginWithoutRequiringUnavailableClientHeaderValue() {
        MockHttpServletRequest request = request(HttpMethod.OPTIONS);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
            "content-type, " + ProtocolVerificationCodeController.CLIENT_ID_HEADER);
        when(clientService.hasAllowedOrigin(ORIGIN)).thenReturn(true);

        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).containsExactly(ORIGIN);
        verifyNoInteractions(clients);
    }

    @Test
    void actualPostRequiresOriginToBelongToHeaderClient() {
        MockHttpServletRequest request = request(HttpMethod.POST);
        request.addHeader(ProtocolVerificationCodeController.CLIENT_ID_HEADER, "client-a");
        when(clients.findByClientId("client-a")).thenReturn(client("client-a", Set.of(ORIGIN)));

        assertThat(source.getCorsConfiguration(request)).isNotNull();

        when(clients.findByClientId("client-a"))
            .thenReturn(client("client-a", Set.of("https://other.example.test")));
        assertThat(source.getCorsConfiguration(request)).isNull();
    }

    @Test
    void preflightRejectsMissingRequiredHeaderDeclaration() {
        MockHttpServletRequest request = request(HttpMethod.OPTIONS);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, HttpHeaders.CONTENT_TYPE);

        assertThat(source.getCorsConfiguration(request)).isNull();
        verifyNoInteractions(clientService, clients);
    }

    private MockHttpServletRequest request(HttpMethod method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method.name(),
            ProtocolVerificationCodeController.CAPTCHA_PATH);
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
