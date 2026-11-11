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

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * grant进入原生provider前再次核对当前有效配置，拒绝认证后发生的配置漂移。
 *
 * @author Rain
 * @since 6.1.0
 */
class ProtocolBoundAuthenticationProviderTest {
    /**
     * 退役原授权Refresh在进入框架刷新Provider前失败，不能签发替代Token。
     */
    @Test
    void refusesRefreshFromEveryRetiredGrant() {
        for (String retired : java.util.List.of("password", "sms", "email")) {
            var client = RegisteredClient.withId("client").clientId("client")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN).scope("api.read").build();
            var clients = mock(RegisteredClientRepository.class);
            var delegate = mock(AuthenticationProvider.class);
            var authorizations = mock(
                    org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService.class);
            var original =
                    org.springframework.security.oauth2.server.authorization.OAuth2Authorization.withRegisteredClient(
                            client)
                    .principalName("fixture-user").authorizationGrantType(new AuthorizationGrantType(retired)).build();
            when(authorizations.findByToken("old-refresh",
                    org.springframework.security.oauth2.server.authorization.OAuth2TokenType.REFRESH_TOKEN)).thenReturn(
                    original);
            var principal = new OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                    "synthetic");
            var request =
                    new org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken(
                    "old-refresh", principal, java.util.Set.of(), java.util.Map.of());
            var error = assertThrows(OAuth2AuthenticationException.class,
                    () -> new ProtocolBoundAuthenticationProvider(delegate, clients, authorizations).authenticate(
                            request));
            org.junit.jupiter.api.Assertions.assertEquals("invalid_grant", error.getError().getErrorCode());
            verifyNoInteractions(delegate);
        }
    }

    /**
     * 旧认证快照不能继续签发已删除Scope或已更改策略的Token。
     */
    @Test
    void rejectsChangedProfileBeforeDelegateIssuance() {
        var original = RegisteredClient.withId("client").clientId("client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://client.example/callback")
                .scope("api.read").build();
        var current = RegisteredClient.from(original).scopes(values -> values.clear()).build();
        var clients = mock(RegisteredClientRepository.class);
        when(clients.findByClientId("client")).thenReturn(current);
        var delegate = mock(AuthenticationProvider.class);
        var grant = mock(OAuth2AuthorizationGrantAuthenticationToken.class);
        when(grant.getPrincipal()).thenReturn(
                new OAuth2ClientAuthenticationToken(original, ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                        "synthetic"));
        when(grant.getGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThrows(OAuth2AuthenticationException.class,
                () -> new ProtocolBoundAuthenticationProvider(delegate, clients,
                        mock(org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService.class)).authenticate(
                        grant));
        verifyNoInteractions(delegate);
    }
}
