/*
 * Copyright (c) 2023 Macula
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

import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.service.support.SysOAuth2ClientService;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 证明草稿、协议集合和原授权Scope不可被默认协议或刷新缩小范围绕过。
 * @author Rain
 * @since 6.1.0
 */
class OAuth2ClientProtocolPolicyTest {
    /** 草稿/未知状态/默认不在集合时不能进入认证。 */
    @Test
    void draftsAndInvalidDefaultsAreClosed() {
        var client = client();
        assertTrue(OAuth2ClientProtocolPolicy.enabled(client));
        client.setConfigurationStatus("DRAFT");
        assertFalse(OAuth2ClientProtocolPolicy.enabled(client));
        client.setConfigurationStatus("ENABLED");
        var only = client.getProtocolConfigurations().get(0);
        client.setProtocolConfigurations(java.util.List.of(only, only));
        assertFalse(OAuth2ClientProtocolPolicy.enabled(client));
        client.setProtocolConfigurations(java.util.List.of(only));
        assertTrue(OAuth2ClientProtocolPolicy.enabled(client));
    }
    /** 默认仅影响入口；显式Scope决定协议且纯OAuth不能冒用profile。 */
    @Test
    void explicitScopesSelectEnabledProtocolIndependentlyOfDefault() {
        var client = client();
        OAuth2ClientProtocolPolicy.require(client,Set.of("openid","profile"),"authorization_code");
        assertThrows(OAuth2AuthenticationException.class,()->OAuth2ClientProtocolPolicy.require(client,Set.of("profile"),"authorization_code"));
        client.setEnabledProtocols("OAUTH2_1");
        client.setDefaultProtocol("OAUTH2_1");
        client.setAuthorizationGrantTypes("authorization_code,refresh_token");
        ProtocolTestFixtures.configure(client);
        OAuth2ClientProtocolPolicy.require(client,Set.of("profile"),"authorization_code");
        assertThrows(OAuth2AuthenticationException.class,()->OAuth2ClientProtocolPolicy.require(client,Set.of("openid"),"authorization_code"));
    }
    /** 纯OIDC不能调用直接Token的OAuth2.1能力。 */
    @Test
    void directGrantsRequireOauthProtocolAndCannotCarryOidcScope() {
        var client = client();
        client.setEnabledProtocols("OAUTH2_1");
        client.setDefaultProtocol("OAUTH2_1");
        client.setAuthorizationGrantTypes("client_credentials");
        ProtocolTestFixtures.configure(client);
        client.setApplicationType("SERVICE");
        OAuth2ClientProtocolPolicy.require(client,Set.of("api.read"),"client_credentials");
        assertThrows(OAuth2AuthenticationException.class,()->OAuth2ClientProtocolPolicy.require(client,Set.of("openid"),"client_credentials"));
        client.setEnabledProtocols("OIDC");
        ProtocolTestFixtures.configure(client);
        assertThrows(OAuth2AuthenticationException.class,()->OAuth2ClientProtocolPolicy.require(client,Set.of("api.read"),"client_credentials"));
    }
    /** refresh缩小范围不能把原OIDC授权转换为仍启用的纯OAuth。 */
    @Test
    @SuppressWarnings("unchecked")
    void generatorReadsCurrentStateAndOriginalRefreshScopes() {
        var current = client();
        var clients = mock(SysOAuth2ClientService.class);
        when(clients.getClientByClientId("client")).thenReturn(current);
        var delegate = (OAuth2TokenGenerator<OAuth2Token>) mock(OAuth2TokenGenerator.class);
        var generator = new ProtocolCheckingTokenGenerator(delegate,clients);
        var registered = RegisteredClient.withId("client").clientId("client")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("https://client.example/callback").build();
        var context = mock(OAuth2TokenContext.class);
        when(context.getRegisteredClient()).thenReturn(registered);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.REFRESH_TOKEN);
        when(context.getAuthorizedScopes()).thenReturn(Set.of("api.read"));
        when(context.getAuthorization()).thenReturn(OAuth2Authorization.withRegisteredClient(registered)
            .principalName("user").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizedScopes(Set.of("openid","api.read")).build());
        current.setEnabledProtocols("OAUTH2_1");
        ProtocolTestFixtures.configure(current);
        current.setDefaultProtocol("OAUTH2_1");
        assertThrows(OAuth2AuthenticationException.class,()->generator.generate(context));
        current.setConfigurationStatus("DRAFT");
        assertThrows(OAuth2AuthenticationException.class,()->generator.generate(context));
        verifyNoInteractions(delegate);
    }
    /** 明确完整新模型测试素材。 */
    private SysOAuth2Client client() {
        var result = new SysOAuth2Client();
        result.setClientId("client");
        result.setConfigurationStatus("ENABLED");
        result.setEnabledProtocols("OIDC,OAUTH2_1");
        result.setDefaultProtocol("OIDC");
        result.setScopes("openid,profile,api.read");
        result.setAuthorizationGrantTypes("authorization_code,refresh_token,client_credentials");
        return dev.macula.cloud.iam.protocol.oauth2.ProtocolTestFixtures.configure(result);
    }
}
