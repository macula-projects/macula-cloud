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
import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2ClientRegistrationService;
import dev.macula.cloud.system.security.OAuth2ResourcePolicy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 原生内省Provider与跨业务Client查询的内存组合验证；不代表真实HTTP/Redis集成。
 *
 * @author Rain
 * @since 6.1.0
 */
class ResourceAuthorizationIntegrationTest {
    /**
     * 多资源Token保留真实aud/Scope，不要求额外配置查询资源。
     */
    @Test
    void authenticatedIntrospectionPreservesTokenResourcesAndScopes() {
        var result = introspect(Set.of("profile", "system:access"), "RESOURCE_SERVER");
        assertTrue(result.getTokenClaims().isActive());
        assertEquals(Set.of("urn:macula:system", "urn:macula:iam:profile"),
                Set.copyOf(result.getTokenClaims().getAudience()));
        assertEquals(Set.of("system:access", "profile"), Set.copyOf(result.getTokenClaims().getScopes()));
        assertFalse(result.getTokenClaims().getClaims().containsKey("nickname"));
        assertFalse(result.getTokenClaims().getClaims().containsKey("preferred_username"));
    }

    /**
     * 已认证的内省身份可以查询Profile-only Token，业务应用仍不能冒充内省身份。
     */
    @Test
    void callerNeedsAuthenticationButNoResourceAllowlist() {
        assertTrue(introspect(Set.of("profile"), "RESOURCE_SERVER").getTokenClaims().isActive());
        assertThrows(OAuth2AuthenticationException.class, () -> introspect(Set.of("profile"), "APPLICATION"));
    }

    /**
     * 版本或用途变化不能沿用先前的用户授权。
     */
    @Test
    void authorizationVersionRequiresAnEnabledApplicationOwner() {
        var registered = client("owner");
        var authorization = OAuth2Authorization.withRegisteredClient(registered).principalName("user")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .attribute(OAuth2ResourcePolicy.AUTHORIZATION_VERSION, 1L).build();
        var owner = new OAuth2ClientRegistration();
        owner.setPurpose("APPLICATION");
        owner.setConfigurationStatus("ENABLED");
        owner.setAuthorizationVersion(1L);
        assertTrue(ResourceAuthorizationPolicy.current(authorization, owner));
        owner.setAuthorizationVersion(2L);
        assertFalse(ResourceAuthorizationPolicy.current(authorization, owner));
        owner.setAuthorizationVersion(1L);
        owner.setPurpose("RESOURCE_SERVER");
        assertFalse(ResourceAuthorizationPolicy.current(authorization, owner));
    }

    /**
     * 使用真实原生Provider和授权模型，替身仅承担客户端配置读取。
     */
    private OAuth2TokenIntrospectionAuthenticationToken introspect(Set<String> scopes, String purpose) {
        var owner = client("owner");
        var caller = client("resource");
        var clients = mock(OAuth2ClientRegistrationService.class);
        var resource = new OAuth2ClientRegistration();
        resource.setPurpose(purpose);
        resource.setAuthorizationVersion(1L);
        resource.setClientSecret(caller.getClientSecret());

        when(clients.getClientByClientId("resource")).thenReturn(resource);
        var target = new OAuth2ClientRegistration();
        target.setResourcePermissions(
                "{\"profile\":\"urn:macula:iam:profile\",\"system:access\":\"urn:macula:system\"}");
        target.setAuthorizationVersion(1L);
        when(clients.getClientByClientId("owner")).thenReturn(target);
        var store = new InMemoryOAuth2AuthorizationService();
        var audiences = OAuth2ResourcePolicy.audiences(scopes,
                Map.of("profile", "urn:macula:iam:profile", "system:access", "urn:macula:system"));
        var token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "fixture-token", Instant.now(),
                Instant.now().plusSeconds(60), scopes);
        store.save(OAuth2Authorization.withRegisteredClient(owner).principalName("fixture-user")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).authorizedScopes(scopes)
                .token(token, metadata -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                        Map.of("sub", "fixture-user", "aud", new ArrayList<>(audiences), "nickname", "hidden-profile",
                                "preferred_username", "hidden-user", "tenantId", 1L)))
                .build());
        var provider = new ResourceTokenIntrospectionAuthenticationProvider(
                new InMemoryRegisteredClientRepository(owner, caller), store, clients);
        var authentication = new OAuth2ClientAuthenticationToken(caller, ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                "fixture");
        return (OAuth2TokenIntrospectionAuthenticationToken) provider.authenticate(
                new OAuth2TokenIntrospectionAuthenticationToken("fixture-token", authentication, null, Map.of()));
    }

    /**
     * 测试中的机密身份彼此独立。
     */
    private RegisteredClient client(String id) {
        return RegisteredClient.withId(id).clientId(id).clientSecret("{noop}fixture-" + id)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientSettings(
                        ClientSettings.builder()
                                .setting(OAuth2ResourcePolicy.AUTHORIZATION_VERSION, 1L).build()).build();
    }
}
