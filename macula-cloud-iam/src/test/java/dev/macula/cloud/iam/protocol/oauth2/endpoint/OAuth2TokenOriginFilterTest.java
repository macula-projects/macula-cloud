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

import dev.macula.cloud.iam.service.support.SysOAuth2ClientService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证浏览器 Token 请求在 Client 认证后绑定精确 Origin。
 *
 * @author Rain
 * @since 6.1.0
 */
class OAuth2TokenOriginFilterTest {
    private static final String ORIGIN = "https://app.example.test";
    private final SysOAuth2ClientService clientService = mock(SysOAuth2ClientService.class);
    private final OAuth2TokenOriginFilter filter = new OAuth2TokenOriginFilter("/oauth2/token", clientService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsOriginOwnedByAuthenticatedClient() throws Exception {
        authenticate(client());
        when(clientService.isAllowedOrigin("client-a", ORIGIN)).thenReturn(true);
        MockHttpServletRequest request = tokenRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void rejectsOriginOwnedOnlyByAnotherClient() throws Exception {
        authenticate(client());
        MockHttpServletRequest request = tokenRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("invalid_request").doesNotContain(ORIGIN);
        verifyNoInteractions(chain);
    }

    @Test
    void rejectsDuplicateOriginHeaders() throws Exception {
        authenticate(client());
        MockHttpServletRequest request = tokenRequest();
        request.addHeader(HttpHeaders.ORIGIN, "https://other.example.test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("invalid_request").doesNotContain(ORIGIN);
        verifyNoInteractions(chain);
    }

    @Test
    void rejectsOriginBeforeClientAuthenticationCompletes() throws Exception {
        MockHttpServletRequest request = tokenRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("invalid_request").doesNotContain(ORIGIN);
        verifyNoInteractions(chain);
    }

    @Test
    void bypassesServerRequestWithoutOrigin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    private MockHttpServletRequest tokenRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/token");
        request.addHeader(HttpHeaders.ORIGIN, ORIGIN);
        return request;
    }

    private void authenticate(RegisteredClient client) {
        SecurityContextHolder.getContext().setAuthentication(new OAuth2ClientAuthenticationToken(
            client, ClientAuthenticationMethod.CLIENT_SECRET_POST, "credential"));
    }

    private RegisteredClient client() {
        return RegisteredClient.withId("client-a").clientId("client-a")
            .clientSecret("{noop}secret")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .scope("profile").build();
    }
}
