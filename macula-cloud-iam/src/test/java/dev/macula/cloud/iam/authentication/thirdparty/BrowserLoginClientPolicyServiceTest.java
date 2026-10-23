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

import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.service.support.SysOAuth2ClientService;
import dev.macula.cloud.system.enums.BrowserLoginProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OAuth Client 浏览器第三方登录策略解析与拒绝边界测试。
 *
 * @author Rain
 * @since 6.1.0
 */
class BrowserLoginClientPolicyServiceTest {
    private static final String CLIENT_ID = "portal";
    private final SysOAuth2ClientService clients = mock(SysOAuth2ClientService.class);
    private BrowserLoginClientPolicyService policies;

    @BeforeEach
    void setUp() {
        AuthorizationServerSettings settings = AuthorizationServerSettings.builder()
            .issuer("https://iam.example").build();
        policies = new BrowserLoginClientPolicyService(clients, settings);
    }

    @Test
    void resolvesOnlyTheClientFromSavedAuthorizationRequest() {
        MockHttpSession session = new MockHttpSession();
        saveAuthorizationRequest(session, CLIENT_ID);
        when(clients.getClientByClientId(CLIENT_ID)).thenReturn(client("GITHUB,WECOM", "WECOM"));
        MockHttpServletRequest login = loginRequest(session);
        login.addParameter(OAuth2ParameterNames.CLIENT_ID, "forged-client");
        login.setCookies(new jakarta.servlet.http.Cookie(OAuth2ParameterNames.CLIENT_ID, "forged-cookie"));

        BrowserLoginClientPolicyService.ClientPolicy policy = policies.resolve(login,
            new MockHttpServletResponse()).orElseThrow();

        assertThat(policy.clientId()).isEqualTo(CLIENT_ID);
        assertThat(policy.allowedProviders()).containsExactlyInAnyOrder(
            BrowserLoginProviderType.GITHUB, BrowserLoginProviderType.WECOM);
        assertThat(policy.directProvider()).isEqualTo(BrowserLoginProviderType.WECOM);
    }

    @Test
    void rejectsMissingMalformedOrDuplicateSavedClient() {
        assertThat(policies.resolve(loginRequest(new MockHttpSession()), new MockHttpServletResponse())).isEmpty();

        MockHttpSession wrongPath = new MockHttpSession();
        saveRequest(wrongPath, "/private", new String[] {CLIENT_ID});
        assertThatThrownBy(() -> policies.resolve(loginRequest(wrongPath), new MockHttpServletResponse()))
            .isInstanceOf(AuthenticationServiceException.class);

        MockHttpSession duplicate = new MockHttpSession();
        saveRequest(duplicate, "/oauth2/authorize", new String[] {CLIENT_ID, "other"});
        assertThatThrownBy(() -> policies.resolve(loginRequest(duplicate), new MockHttpServletResponse()))
            .isInstanceOf(AuthenticationServiceException.class);
    }

    @Test
    void rejectsUnknownOrInconsistentPersistedPolicy() {
        MockHttpSession session = new MockHttpSession();
        saveAuthorizationRequest(session, CLIENT_ID);
        MockHttpServletRequest login = loginRequest(session);

        when(clients.getClientByClientId(CLIENT_ID)).thenReturn(client("UNKNOWN", null));
        assertThatThrownBy(() -> policies.resolve(login, new MockHttpServletResponse()))
            .isInstanceOf(AuthenticationServiceException.class);

        when(clients.getClientByClientId(CLIENT_ID)).thenReturn(client("GITHUB", "WECOM"));
        assertThatThrownBy(() -> policies.resolve(login, new MockHttpServletResponse()))
            .isInstanceOf(AuthenticationServiceException.class);
    }

    @Test
    void revalidatesProviderAndClientAtCallback() {
        MockHttpSession session = new MockHttpSession();
        saveAuthorizationRequest(session, CLIENT_ID);
        MockHttpServletRequest callback = loginRequest(session);
        when(clients.getClientByClientId(CLIENT_ID)).thenReturn(client("GITHUB", null));

        assertThat(policies.requireAllowed(callback, new MockHttpServletResponse(), CLIENT_ID,
            BrowserLoginProviderType.GITHUB).clientId()).isEqualTo(CLIENT_ID);
        assertThatThrownBy(() -> policies.requireAllowed(callback, new MockHttpServletResponse(), "other",
            BrowserLoginProviderType.GITHUB)).isInstanceOf(AuthenticationServiceException.class);
        assertThatThrownBy(() -> policies.requireAllowed(callback, new MockHttpServletResponse(), CLIENT_ID,
            BrowserLoginProviderType.WECOM)).isInstanceOf(AuthenticationServiceException.class);
    }

    private SysOAuth2Client client(String allowedProviders, String directProvider) {
        SysOAuth2Client client = new SysOAuth2Client();
        client.setClientId(CLIENT_ID);
        client.setAuthorizationGrantTypes("authorization_code,refresh_token");
        client.setRedirectUris("https://app.example/callback");
        client.setBrowserLoginProviderTypes(allowedProviders);
        client.setDirectLoginProviderType(directProvider);
        return client;
    }

    private MockHttpServletRequest loginRequest(MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
        request.setSession(session);
        return request;
    }

    private void saveAuthorizationRequest(MockHttpSession session, String clientId) {
        saveRequest(session, "/oauth2/authorize", new String[] {clientId});
    }

    private void saveRequest(MockHttpSession session, String path, String[] clientIds) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setSession(session);
        request.setScheme("https");
        request.setServerName("iam.example");
        request.setServerPort(443);
        request.setQueryString("response_type=code&client_id=" + String.join("&client_id=", clientIds));
        request.addParameter(OAuth2ParameterNames.RESPONSE_TYPE, "code");
        request.addParameter(OAuth2ParameterNames.CLIENT_ID, clientIds);
        new HttpSessionRequestCache().saveRequest(request, new MockHttpServletResponse());
    }
}
