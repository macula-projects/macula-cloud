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
package dev.macula.cloud.iam.authentication.hosted;

import java.util.Set;

import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.service.client.SysOAuth2ClientService;
import dev.macula.cloud.system.enums.OAuth2HostedLoginMethod;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 受控授权上下文、三态托管方式与全局关闭优先。
 *
 * @author Rain
 * @since 6.1.0
 */
class HostedLoginClientPolicyServiceTest {
    private final SysOAuth2ClientService clients = mock(SysOAuth2ClientService.class);
    private final HostedLoginClientPolicyService service = new HostedLoginClientPolicyService(clients,
            AuthorizationServerSettings.builder().build(), true, true, true);

    /**
     * 缺少SavedRequest的Client声明不能降级为门户。
     */
    @Test
    void rejectsForgedContextAndKeepsPlainPortal() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertEquals(
                Set.of(OAuth2HostedLoginMethod.PASSWORD, OAuth2HostedLoginMethod.SMS, OAuth2HostedLoginMethod.EMAIL),
                service.resolve(request, new MockHttpServletResponse()).methods());
        request.addParameter("client_id", "test");
        assertThrows(AuthenticationServiceException.class,
                () -> service.resolve(request, new MockHttpServletResponse()));
    }

    /**
     * 策略空值继承与显式关闭不同，参数不能覆盖已保存的Client。
     */
    @Test
    void enforcesCallbackAndPolicyIntersection() {
        SysOAuth2Client client = new SysOAuth2Client();
        client.setClientId("test");
        client.setAuthorizationGrantTypes("authorization_code");
        client.setRedirectUris("https://client.example/callback");
        dev.macula.cloud.iam.protocol.oauth2.ProtocolTestFixtures.configure(client);
        when(clients.getClientByClientId("test")).thenReturn(client);
        MockHttpServletRequest request = request("https://client.example/callback");
        request.addParameter("client_id", "attacker");
        assertEquals("test", service.resolve(request, new MockHttpServletResponse()).clientId());
        client.setHostedLoginMethods("SMS");
        assertEquals(Set.of(OAuth2HostedLoginMethod.SMS),
                service.resolve(request, new MockHttpServletResponse()).methods());
        var globallyDisabled = new HostedLoginClientPolicyService(clients,
                AuthorizationServerSettings.builder().build(), true, false, true);
        assertTrue(globallyDisabled.resolve(request, new MockHttpServletResponse()).methods().isEmpty());
        client.setHostedLoginMethods("");
        assertTrue(service.resolve(request, new MockHttpServletResponse()).methods().isEmpty());
        client.setHostedLoginMethods("UNKNOWN");
        assertThrows(AuthenticationServiceException.class,
                () -> service.resolve(request, new MockHttpServletResponse()));
        client.setHostedLoginMethods(null);
        assertThrows(AuthenticationServiceException.class,
                () -> service.resolve(request("https://attacker.example"), new MockHttpServletResponse()));
        client.setApplicationType("SERVICE");
        assertThrows(AuthenticationServiceException.class,
                () -> service.resolve(request, new MockHttpServletResponse()));
    }

    /**
     * SavedRequest也要校验参数唯一性、授权端点及grant，不能把任意受保护页面当授权。
     */
    @Test
    void rejectsPollutedAndNonAuthorizationSavedRequests() {
        SysOAuth2Client client = new SysOAuth2Client();
        client.setClientId("test");
        client.setAuthorizationGrantTypes("authorization_code");
        client.setRedirectUris("https://client.example/callback");
        dev.macula.cloud.iam.protocol.oauth2.ProtocolTestFixtures.configure(client);
        when(clients.getClientByClientId("test")).thenReturn(client);
        for (String field : Set.of("client_id", "response_type", "redirect_uri")) {
            MockHttpServletRequest saved = new MockHttpServletRequest("GET", "/oauth2/authorize");
            saved.addParameter("client_id", "test");
            saved.addParameter("response_type", "code");
            saved.addParameter("scope", "openid");
            saved.addParameter("redirect_uri", "https://client.example/callback");
            saved.addParameter(field, "duplicate");
            MockHttpSession session = new MockHttpSession();
            saved.setSession(session);
            new HttpSessionRequestCache().saveRequest(saved, new MockHttpServletResponse());
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setSession(session);
            assertThrows(AuthenticationServiceException.class,
                    () -> service.resolve(request, new MockHttpServletResponse()));
        }
        ((dev.macula.cloud.system.protocol.OAuth2ProtocolSettings) client.getProtocolConfigurations().get(0).settings())
                .setAuthorizationGrantTypes("client_credentials");
        assertThrows(AuthenticationServiceException.class,
                () -> service.resolve(request("https://client.example/callback"), new MockHttpServletResponse()));
    }

    /**
     * 模拟Security RequestCache保存的原始授权请求。
     */
    private MockHttpServletRequest request(String callback) {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest saved = new MockHttpServletRequest("GET", "/oauth2/authorize");
        saved.setSession(session);
        saved.addParameter("client_id", "test");
        saved.addParameter("response_type", "code");
        saved.addParameter("scope", "openid");
        saved.addParameter("redirect_uri", callback);
        new HttpSessionRequestCache().saveRequest(saved, new MockHttpServletResponse());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        return request;
    }
}
