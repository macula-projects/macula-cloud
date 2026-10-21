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

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 验证第三方登录无需 client，state 只保存服务端受控的原请求续转目标。
 *
 * @author Rain
 * @since 6.1.0
 */
class ThirdPartyAuthenticationFlowTest {
    @Test
    void initiationBindsTheMatchingSavedAuthorizationRequest() throws Exception {
        Fixture fixture = fixture();
        MockHttpSession session = new MockHttpSession();
        saveAuthorizationRequest(session, "portal", "original-state");
        MockHttpServletRequest request = loginRequest(session, "portal");
        MockHttpServletResponse response = new MockHttpServletResponse();

        fixture.filter.attemptAuthentication(request, response);

        ArgumentCaptor<ThirdPartyLoginContext> context = ArgumentCaptor.forClass(ThirdPartyLoginContext.class);
        verify(fixture.states).create(eq(request), context.capture());
        assertEquals("GITHUB", context.getValue().providerType());
        assertTrue(context.getValue().savedRequestTarget().startsWith("/oauth2/authorize?"));
        assertTrue(context.getValue().savedRequestTarget().contains("client_id=portal"));
    }

    @Test
    void initiationDoesNotDependOnAClientParameter() throws Exception {
        Fixture fixture = fixture();
        MockHttpSession session = new MockHttpSession();
        saveAuthorizationRequest(session, "other-client", "other-state");

        MockHttpServletRequest request = loginRequest(session, "portal");
        fixture.filter.attemptAuthentication(request, new MockHttpServletResponse());
        verify(fixture.states).create(eq(request), any());
    }

    @Test
    void initiationRestoresAProtectedNonOauthRequestWithoutClient() throws Exception {
        Fixture fixture = fixture();
        MockHttpSession session = new MockHttpSession();
        saveRequest(session, "/private/profile", "tab=security");
        MockHttpServletRequest request = loginRequest(session, null);

        fixture.filter.attemptAuthentication(request, new MockHttpServletResponse());

        ArgumentCaptor<ThirdPartyLoginContext> context = ArgumentCaptor.forClass(ThirdPartyLoginContext.class);
        verify(fixture.states).create(eq(request), context.capture());
        assertTrue(context.getValue().savedRequestTarget().startsWith("/private/profile?tab=security"));
    }

    @Test
    void directLoginDefaultsToRoot() throws Exception {
        Fixture fixture = fixture();
        MockHttpServletRequest request = loginRequest(new MockHttpSession(), null);

        fixture.filter.attemptAuthentication(request, new MockHttpServletResponse());

        ArgumentCaptor<ThirdPartyLoginContext> context = ArgumentCaptor.forClass(ThirdPartyLoginContext.class);
        verify(fixture.states).create(eq(request), context.capture());
        assertEquals("/", context.getValue().savedRequestTarget());
    }

    @Test
    void rejectsProtocolRelativeSavedRequest() {
        Fixture fixture = fixture();
        MockHttpSession session = new MockHttpSession();
        saveRequest(session, "//attacker.example/steal", null);
        MockHttpServletRequest request = loginRequest(session, null);

        assertThrows(AuthenticationServiceException.class,
            () -> fixture.filter.attemptAuthentication(request, new MockHttpServletResponse()));
        verifyNoInteractions(fixture.states);
    }

    @Test
    void rejectsSavedRequestsFromUntrustedOrigins() {
        for (SavedRequestOrigin origin : new SavedRequestOrigin[]{
            new SavedRequestOrigin("https", "attacker.example", 443),
            new SavedRequestOrigin("http", "iam.example", 80),
            new SavedRequestOrigin("https", "iam.example", 8443)}) {
            Fixture fixture = fixture();
            MockHttpSession session = new MockHttpSession();
            saveRequest(session, "/private/profile", null, origin);
            MockHttpServletRequest request = loginRequest(session, null);

            assertThrows(AuthenticationServiceException.class,
                () -> fixture.filter.attemptAuthentication(request, new MockHttpServletResponse()));
            verifyNoInteractions(fixture.states);
        }
    }

    @Test
    void rejectsEncodedSlashAndCrossContextSavedRequests() {
        for (String unsafePath : new String[]{"/%2Fevil.example", "/other/private"}) {
            Fixture fixture = fixture();
            MockHttpSession session = new MockHttpSession();
            saveRequest(session, unsafePath, null);
            MockHttpServletRequest request = loginRequest(session, null);
            request.setContextPath("/iam");

            assertThrows(AuthenticationServiceException.class,
                () -> fixture.filter.attemptAuthentication(request, new MockHttpServletResponse()));
            verifyNoInteractions(fixture.states);
        }
    }

    @Test
    void acceptsSavedRequestWithinAContextPath() throws Exception {
        Fixture fixture = fixture();
        MockHttpSession session = new MockHttpSession();
        saveRequest(session, "/iam/private/profile", null);
        MockHttpServletRequest request = loginRequest(session, null);
        request.setContextPath("/iam");

        fixture.filter.attemptAuthentication(request, new MockHttpServletResponse());

        ArgumentCaptor<ThirdPartyLoginContext> context = ArgumentCaptor.forClass(ThirdPartyLoginContext.class);
        verify(fixture.states).create(eq(request), context.capture());
        assertTrue(context.getValue().savedRequestTarget().startsWith("/iam/private/profile"));
    }

    @Test
    void callbackRejectsAReplacedSharedSavedRequest() {
        Fixture fixture = fixture();
        MockHttpSession session = new MockHttpSession();
        saveAuthorizationRequest(session, "other-client", "other-state");
        ThirdPartyLoginContext context = new ThirdPartyLoginContext("GITHUB", "", Instant.now(),
            "/oauth2/authorize?response_type=code&client_id=portal&state=original-state");
        when(fixture.states.consume(any(), eq("GITHUB"), eq("valid-state"))).thenReturn(context);
        MockHttpServletRequest callback = new MockHttpServletRequest("GET", "/login/github");
        callback.setSession(session);
        callback.addParameter("state", "valid-state");
        callback.addParameter("code", "authorization-code");

        assertThrows(AuthenticationServiceException.class,
            () -> fixture.filter.attemptAuthentication(callback, new MockHttpServletResponse()));
        verify(fixture.states).consume(callback, "GITHUB", "valid-state");
    }

    @Test
    void callbackAcceptsTheSameNormalizedSavedRequest() throws Exception {
        Fixture fixture = fixture();
        MockHttpSession session = new MockHttpSession();
        saveAuthorizationRequest(session, "portal", "original-state");
        MockHttpServletRequest initiation = loginRequest(session, null);
        fixture.filter.attemptAuthentication(initiation, new MockHttpServletResponse());
        ArgumentCaptor<ThirdPartyLoginContext> context = ArgumentCaptor.forClass(ThirdPartyLoginContext.class);
        verify(fixture.states).create(eq(initiation), context.capture());
        when(fixture.states.consume(any(), eq("GITHUB"), eq("valid-state"))).thenReturn(context.getValue());

        MockHttpServletRequest callback = new MockHttpServletRequest("GET", "/login/github");
        callback.setSession(session);
        callback.addParameter(OAuth2ParameterNames.STATE, "valid-state");
        callback.addParameter(OAuth2ParameterNames.CODE, "authorization-code");

        assertNotNull(fixture.filter.attemptAuthentication(callback, new MockHttpServletResponse()));
        verify(fixture.authenticationManager).authenticate(any(ThirdPartyAuthenticationToken.class));
    }

    private Fixture fixture() {
        IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
        ThirdPartyLoginStateRepository states = mock(ThirdPartyLoginStateRepository.class);
        when(configs.requireEnabled("GITHUB")).thenReturn(
            new IdentityProviderConfigService.ProviderConfig("GITHUB", "id", "secret",
                java.util.Map.of("clientId", "id")));
        when(states.create(any(), any())).thenReturn("generated-state");
        FixtureFilter filter = new FixtureFilter();
        filter.configure(configs, states, "https://iam.example");
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        when(authenticationManager.authenticate(any())).thenAnswer(invocation -> invocation.getArgument(0));
        filter.setAuthenticationManager(authenticationManager);
        return new Fixture(filter, states, authenticationManager);
    }

    private void saveAuthorizationRequest(MockHttpSession session, String clientId, String state) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        request.setSession(session);
        request.setQueryString("response_type=code&client_id=" + clientId + "&state=" + state);
        request.addParameter("response_type", "code");
        request.addParameter("client_id", clientId);
        request.addParameter("state", state);
        request.setScheme("https");
        request.setServerName("iam.example");
        request.setServerPort(443);
        new HttpSessionRequestCache().saveRequest(request, new MockHttpServletResponse());
    }

    private void saveRequest(MockHttpSession session, String path, String query) {
        saveRequest(session, path, query, new SavedRequestOrigin("https", "iam.example", 443));
    }

    private void saveRequest(MockHttpSession session, String path, String query, SavedRequestOrigin origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setSession(session);
        request.setQueryString(query);
        request.setScheme(origin.scheme());
        request.setServerName(origin.host());
        request.setServerPort(origin.port());
        new HttpSessionRequestCache().saveRequest(request, new MockHttpServletResponse());
    }

    private record SavedRequestOrigin(String scheme, String host, int port) { }

    private MockHttpServletRequest loginRequest(MockHttpSession session, String clientId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/github");
        request.setSession(session);
        request.addParameter("client_id", clientId);
        return request;
    }

    private record Fixture(FixtureFilter filter, ThirdPartyLoginStateRepository states,
                           AuthenticationManager authenticationManager) {
    }

    private static final class FixtureFilter extends AbstractThirdPartyAuthenticationFilter {
        private FixtureFilter() {
            super("GITHUB", "/login/github");
        }

        @Override
        protected String authorizationUri(IdentityProviderConfigService.ProviderConfig config, String state,
            String redirectUri, ThirdPartyLoginContext context) {
            return "https://github.example/authorize?state=" + state;
        }
    }
}
