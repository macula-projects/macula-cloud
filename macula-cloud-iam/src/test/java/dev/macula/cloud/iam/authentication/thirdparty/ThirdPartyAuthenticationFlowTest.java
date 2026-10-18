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
package dev.macula.cloud.iam.authentication.thirdparty;

import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.service.support.SysOAuth2ClientService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 验证第三方 state 绑定原始授权请求，并只恢复对应的服务端目标。
 *
 * @author Rain
 * @since 6.1.0
 */
class ThirdPartyAuthenticationFlowTest {
    @Test
    void initiationBindsTheMatchingSavedAuthorizationRequest() throws Exception {
        Fixture fixture = fixture("portal");
        MockHttpSession session = new MockHttpSession();
        saveAuthorizationRequest(session, "portal", "original-state");
        MockHttpServletRequest request = loginRequest(session, "portal");
        MockHttpServletResponse response = new MockHttpServletResponse();

        fixture.filter.attemptAuthentication(request, response);

        ArgumentCaptor<ThirdPartyLoginContext> context = ArgumentCaptor.forClass(ThirdPartyLoginContext.class);
        verify(fixture.states).create(eq(request), context.capture());
        assertEquals("portal", context.getValue().clientId());
        assertTrue(context.getValue().savedRequestTarget().startsWith("/oauth2/authorize?"));
        assertTrue(context.getValue().savedRequestTarget().contains("client_id=portal"));
    }

    @Test
    void initiationRejectsAClientDifferentFromTheSavedRequest() {
        Fixture fixture = fixture("portal");
        MockHttpSession session = new MockHttpSession();
        saveAuthorizationRequest(session, "other-client", "other-state");

        assertThrows(AuthenticationServiceException.class,
            () -> fixture.filter.attemptAuthentication(loginRequest(session, "portal"),
                new MockHttpServletResponse()));
        verifyNoInteractions(fixture.states);
    }

    @Test
    void successIgnoresAReplacedSharedSavedRequest() throws Exception {
        MockHttpSession session = new MockHttpSession();
        saveAuthorizationRequest(session, "other-client", "other-state");
        ThirdPartyLoginContext context = new ThirdPartyLoginContext("GITHUB", "portal", 7L,
            UserTypeEnum.EMPLOYEE, Instant.now(),
            "/oauth2/authorize?response_type=code&client_id=portal&state=original-state");
        ThirdPartyAuthenticationToken token = ThirdPartyAuthenticationToken.authenticated("GITHUB",
            User.withUsername("alice").password("n/a").authorities("USER").build(), context);
        MockHttpServletRequest callback = new MockHttpServletRequest();
        callback.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ThirdPartyAuthenticationSuccessHandler().onAuthenticationSuccess(callback, response, token);

        assertEquals(context.savedRequestTarget(), response.getRedirectedUrl());
        assertNull(new HttpSessionRequestCache().getRequest(callback, response));
    }

    private Fixture fixture(String clientId) {
        SysOAuth2ClientService clients = mock(SysOAuth2ClientService.class);
        IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
        ThirdPartyLoginStateRepository states = mock(ThirdPartyLoginStateRepository.class);
        SysOAuth2Client client = new SysOAuth2Client();
        client.setTenantId(7L);
        client.setUserType(UserTypeEnum.EMPLOYEE);
        when(clients.getClientByClientId(clientId)).thenReturn(client);
        when(configs.requireEnabled("GITHUB")).thenReturn(
            new IdentityProviderConfigService.ProviderConfig("GITHUB", "id", "secret",
                java.util.Map.of("clientId", "id")));
        when(states.create(any(), any())).thenReturn("generated-state");
        FixtureFilter filter = new FixtureFilter();
        filter.configure(clients, configs, states, "https://iam.example");
        return new Fixture(filter, states);
    }

    private void saveAuthorizationRequest(MockHttpSession session, String clientId, String state) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        request.setSession(session);
        request.setQueryString("response_type=code&client_id=" + clientId + "&state=" + state);
        request.addParameter("response_type", "code");
        request.addParameter("client_id", clientId);
        request.addParameter("state", state);
        new HttpSessionRequestCache().saveRequest(request, new MockHttpServletResponse());
    }

    private MockHttpServletRequest loginRequest(MockHttpSession session, String clientId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/github");
        request.setSession(session);
        request.addParameter("client_id", clientId);
        return request;
    }

    private record Fixture(FixtureFilter filter, ThirdPartyLoginStateRepository states) {
    }

    private static final class FixtureFilter extends AbstractThirdPartyAuthenticationFilter {
        private FixtureFilter() {
            super("GITHUB", "/login/github");
        }

        @Override
        protected String authorizationUri(IdentityProviderConfigService.ProviderConfig config, String state,
            String redirectUri) {
            return "https://github.example/authorize?state=" + state;
        }
    }
}
