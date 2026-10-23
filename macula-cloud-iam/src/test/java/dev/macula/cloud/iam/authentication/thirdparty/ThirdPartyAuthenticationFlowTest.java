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

import dev.macula.cloud.system.enums.BrowserLoginProviderType;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证第三方登录绑定受控 OAuth Client，并在回调阶段重新校验策略。
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
        assertEquals("portal", context.getValue().clientId());
        assertTrue(context.getValue().savedRequestTarget().startsWith("/oauth2/authorize?"));
        assertTrue(context.getValue().savedRequestTarget().contains("client_id=portal"));
    }

    @Test
    void initiationDoesNotTrustAClientParameter() throws Exception {
        Fixture fixture = fixture();
        MockHttpSession session = new MockHttpSession();
        saveAuthorizationRequest(session, "other-client", "other-state");
        when(fixture.policies.requireAllowed(any(), any(), eq(BrowserLoginProviderType.GITHUB)))
            .thenReturn(new BrowserLoginClientPolicyService.ClientPolicy("other-client",
                java.util.Set.of(BrowserLoginProviderType.GITHUB), null));

        MockHttpServletRequest request = loginRequest(session, "portal");
        fixture.filter.attemptAuthentication(request, new MockHttpServletResponse());
        ArgumentCaptor<ThirdPartyLoginContext> context = ArgumentCaptor.forClass(ThirdPartyLoginContext.class);
        verify(fixture.states).create(eq(request), context.capture());
        assertEquals("other-client", context.getValue().clientId());
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
    void unknownEnvironmentReturnsToLoginChoiceWithoutCreatingState() throws Exception {
        IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
        ThirdPartyLoginStateRepository states = mock(ThirdPartyLoginStateRepository.class);
        when(configs.requireEnabled("FEISHU")).thenReturn(new IdentityProviderConfigService.ProviderConfig(
            "FEISHU", "app", "secret", java.util.Map.of()));
        UnknownFixtureFilter filter = new UnknownFixtureFilter();
        BrowserLoginClientPolicyService policies = mock(BrowserLoginClientPolicyService.class);
        when(policies.requireAllowed(any(), any(), eq(BrowserLoginProviderType.FEISHU)))
            .thenReturn(new BrowserLoginClientPolicyService.ClientPolicy("portal",
                java.util.Set.of(BrowserLoginProviderType.FEISHU), null));
        filter.configure(configs, policies, states, "https://iam.example");
        filter.setAuthenticationManager(mock(AuthenticationManager.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/feishu");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.attemptAuthentication(request, response);

        assertEquals("/login", response.getRedirectedUrl());
        verifyNoInteractions(states);
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
        ThirdPartyLoginContext context = new ThirdPartyLoginContext("GITHUB", "", "portal", Instant.now(),
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
        BrowserLoginClientPolicyService policies = mock(BrowserLoginClientPolicyService.class);
        when(configs.requireEnabled("GITHUB")).thenReturn(
            new IdentityProviderConfigService.ProviderConfig("GITHUB", "id", "secret",
                java.util.Map.of("clientId", "id")));
        when(states.create(any(), any())).thenReturn("generated-state");
        when(policies.requireAllowed(any(), any(), eq(BrowserLoginProviderType.GITHUB)))
            .thenReturn(new BrowserLoginClientPolicyService.ClientPolicy("portal",
                java.util.Set.of(BrowserLoginProviderType.GITHUB), null));
        when(policies.requireAllowed(any(), any(), eq("portal"), eq(BrowserLoginProviderType.GITHUB)))
            .thenReturn(new BrowserLoginClientPolicyService.ClientPolicy("portal",
                java.util.Set.of(BrowserLoginProviderType.GITHUB), null));
        FixtureFilter filter = new FixtureFilter();
        filter.configure(configs, policies, states, "https://iam.example");
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        when(authenticationManager.authenticate(any())).thenAnswer(invocation -> invocation.getArgument(0));
        filter.setAuthenticationManager(authenticationManager);
        return new Fixture(filter, states, policies, authenticationManager);
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

    /**
     * 测试保存请求使用的受信来源。
     *
     * @since 6.1.0
     */
    private record SavedRequestOrigin(String scheme, String host, int port) { }

    private MockHttpServletRequest loginRequest(MockHttpSession session, String clientId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/github");
        request.setSession(session);
        request.addParameter("client_id", clientId);
        return request;
    }

    /**
     * 第三方认证过滤器测试夹具。
     *
     * @since 6.1.0
     */
    private record Fixture(FixtureFilter filter, ThirdPartyLoginStateRepository states,
                           BrowserLoginClientPolicyService policies,
                           AuthenticationManager authenticationManager) {
    }

    /**
     * 固定 GitHub 授权地址的测试过滤器。
     *
     * @since 6.1.0
     */
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

    /**
     * 模拟无法识别运行环境的测试过滤器。
     *
     * @since 6.1.0
     */
    private static final class UnknownFixtureFilter extends AbstractThirdPartyAuthenticationFilter {
        private UnknownFixtureFilter() {
            super("FEISHU", "/login/feishu");
        }

        @Override
        protected String loginMode(jakarta.servlet.http.HttpServletRequest request) {
            return null;
        }

        @Override
        protected String authorizationUri(IdentityProviderConfigService.ProviderConfig config, String state,
            String redirectUri, ThirdPartyLoginContext context) {
            throw new AssertionError("未知环境不得构造授权地址");
        }
    }
}
