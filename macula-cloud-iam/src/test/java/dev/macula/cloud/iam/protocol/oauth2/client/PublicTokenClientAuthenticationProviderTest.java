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
package dev.macula.cloud.iam.protocol.oauth2.client;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 public Token Client 必须使用 none 且登记当前 grant。
 *
 * @author Rain
 * @since 6.1.0
 */
class PublicTokenClientAuthenticationProviderTest {
    private final RegisteredClientRepository clients = mock(RegisteredClientRepository.class);
    private final PublicTokenClientAuthenticationProvider provider =
        new PublicTokenClientAuthenticationProvider(clients);

    @Test
    void authenticatesRegisteredPublicGrant() {
        when(clients.findByClientId("public-client")).thenReturn(client(ClientAuthenticationMethod.NONE, "sms"));

        OAuth2ClientAuthenticationToken result =
            (OAuth2ClientAuthenticationToken)provider.authenticate(request("sms"));

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getRegisteredClient().getClientId()).isEqualTo("public-client");
    }

    @Test
    void rejectsUnknownConfidentialOrUnregisteredGrant() {
        assertThatThrownBy(() -> provider.authenticate(request("sms")))
            .isInstanceOf(OAuth2AuthenticationException.class);

        when(clients.findByClientId("public-client"))
            .thenReturn(client(ClientAuthenticationMethod.CLIENT_SECRET_POST, "sms"));
        assertThatThrownBy(() -> provider.authenticate(request("sms")))
            .isInstanceOf(OAuth2AuthenticationException.class);

        when(clients.findByClientId("public-client"))
            .thenReturn(client(ClientAuthenticationMethod.NONE, "password"));
        assertThatThrownBy(() -> provider.authenticate(request("sms")))
            .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void leavesAuthorizationCodeAndConfidentialAuthenticationToFramework() {
        assertThat(provider.authenticate(request("authorization_code"))).isNull();
        OAuth2ClientAuthenticationToken confidential = new OAuth2ClientAuthenticationToken(
            client(ClientAuthenticationMethod.CLIENT_SECRET_POST, "sms"),
            ClientAuthenticationMethod.CLIENT_SECRET_POST, "fixture");
        assertThat(provider.authenticate(confidential)).isNull();
    }

    private OAuth2ClientAuthenticationToken request(String grantType) {
        return new OAuth2ClientAuthenticationToken("public-client", ClientAuthenticationMethod.NONE, null,
            Map.of(OAuth2ParameterNames.GRANT_TYPE, grantType));
    }

    private RegisteredClient client(ClientAuthenticationMethod method, String grantType) {
        RegisteredClient.Builder builder = RegisteredClient.withId("public-client").clientId("public-client")
            .clientAuthenticationMethod(method).authorizationGrantType(new AuthorizationGrantType(grantType))
            .scope("profile");
        if (!ClientAuthenticationMethod.NONE.equals(method)) {
            builder.clientSecret("{noop}fixture");
        }
        return builder.build();
    }
}
