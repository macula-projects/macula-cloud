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
package dev.macula.cloud.iam.protocol.oauth2;

import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2ClientRegistration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 原授权绑定与管理认证选择不得受默认协议、目标Token或缩小Scope影响。
 *
 * @author Rain
 * @since 6.1.0
 */
class ApplicationProtocolResolverTest {
    private final OAuth2AuthorizationService authorizations = mock(OAuth2AuthorizationService.class);
    private final ApplicationProtocolResolver resolver = new ApplicationProtocolResolver(authorizations);

    /**
     * code/refresh必须读取已持久化协议，拒绝旧未绑定授权。
     */
    @Test
    void restoredBindingControlsRefreshWithoutRequestScopeGuessing() {
        var client = client();
        var registered = RegisteredClient.withId("client").clientId("client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://client.example/callback").build();
        var authorization = OAuth2Authorization.withRegisteredClient(registered).principalName("user")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(Set.of("openid", "api.read"))
                .attribute(ApplicationProtocolResolver.PROTOCOL_ATTRIBUTE, "OIDC")
                .attribute(ApplicationProtocolResolver.SCHEMA_ATTRIBUTE, 1).build();
        when(authorizations.findByToken("refresh", OAuth2TokenType.REFRESH_TOKEN)).thenReturn(authorization);
        var request = new MockHttpServletRequest("POST", "/oauth2/token");
        request.addParameter("grant_type", "refresh_token");
        request.addParameter("refresh_token", "refresh");
        request.addParameter("scope", "api.read");

        assertEquals("OIDC", resolver.resolve(client, request).protocol());
        var old = OAuth2Authorization.from(authorization)
                .attributes(values -> values.remove(ApplicationProtocolResolver.SCHEMA_ATTRIBUTE)).build();
        when(authorizations.findByToken("refresh", OAuth2TokenType.REFRESH_TOKEN)).thenReturn(old);
        assertEquals("OIDC", resolver.resolve(client, request).protocol());
        verifyNoInteractions(authorizations);
    }

    /**
     * 管理调用方的配置与目标Token及默认选择完全分离。
     */
    @Test
    void managementDoesNotInspectTargetOrRetryOtherProfile() {
        var client = client();
        var request = new MockHttpServletRequest("POST", "/oauth2/introspect");
        request.addParameter("token", "target-oidc-token");
        request.addParameter("protocol", "OIDC");
        assertEquals("OIDC", resolver.resolve(client, request).protocol());

        assertEquals("OIDC", resolver.resolve(client, request).protocol());
        verifyNoInteractions(authorizations);
    }

    /**
     * 重复Scope不能让多个阶段解析不同协议。
     */
    @Test
    void rejectsAmbiguousAuthorizationParameters() {
        var request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        request.addParameter("scope", "openid", "api.read");
        assertThrows(OAuth2AuthenticationException.class, () -> resolver.resolve(client(), request));
    }

    /**
     * 真实能力规则下的两份独立策略。
     */
    private OAuth2ClientRegistration client() {
        var row = new OAuth2ClientRegistration();
        row.setClientId("client");
        ProtocolTestFixtures.configure(row, "OIDC,OAUTH2_1");

        row.setAuthorizationGrantTypes("authorization_code,refresh_token");
        return ProtocolTestFixtures.configure(row);
    }
}
