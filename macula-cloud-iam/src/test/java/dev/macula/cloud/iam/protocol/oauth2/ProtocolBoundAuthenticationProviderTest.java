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
import static org.mockito.Mockito.*;

/**
 * grant进入原生provider前再次核对当前有效配置，拒绝认证后发生的配置漂移。
 * @author Rain
 * @since 6.1.0
 */
class ProtocolBoundAuthenticationProviderTest {
    /** 旧认证快照不能继续签发已删除Scope或已更改策略的Token。 */
    @Test
    void rejectsChangedProfileBeforeDelegateIssuance() {
        var original = RegisteredClient.withId("client").clientId("client")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("https://client.example/callback")
            .scope("api.read").build();
        var current = RegisteredClient.from(original).scopes(values -> values.clear()).build();
        var clients = mock(RegisteredClientRepository.class);
        when(clients.findByClientId("client")).thenReturn(current);
        var delegate = mock(AuthenticationProvider.class);
        var grant = mock(OAuth2AuthorizationGrantAuthenticationToken.class);
        when(grant.getPrincipal()).thenReturn(new OAuth2ClientAuthenticationToken(original, ClientAuthenticationMethod.CLIENT_SECRET_BASIC, "synthetic"));
        when(grant.getGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThrows(OAuth2AuthenticationException.class, () -> new ProtocolBoundAuthenticationProvider(delegate, clients).authenticate(grant));
        verifyNoInteractions(delegate);
    }
}
