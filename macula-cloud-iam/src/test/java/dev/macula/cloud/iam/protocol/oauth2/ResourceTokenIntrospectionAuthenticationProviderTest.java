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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 用户目录准入、缺失声明拒绝和机器原始grant信任边界。
 * @author Rain
 * @since 6.1.0
 */
class ResourceTokenIntrospectionAuthenticationProviderTest {
    /** 单选、多选允许匹配目录；不允许、缺失、未知及空策略只返回失活。 */
    @Test
    void userTypesMustBeExplicitlyAllowed() {
        assertThat(introspect(AuthorizationGrantType.AUTHORIZATION_CODE, "employee", Set.of("employee"), false)
            .getTokenClaims().isActive()).isTrue();
        assertThat(introspect(AuthorizationGrantType.AUTHORIZATION_CODE, "member", Set.of("employee", "member"), false)
            .getTokenClaims().isActive()).isTrue();
        for (String type : new String[] {"member", "", "unknown"}) {
            var result = introspect(AuthorizationGrantType.AUTHORIZATION_CODE, type, Set.of("employee"), false);
            assertThat(result.getTokenClaims().getClaims()).containsOnlyKeys("active");
            assertThat(result.getTokenClaims().isActive()).isFalse();
        }
        assertThat(introspect(AuthorizationGrantType.AUTHORIZATION_CODE, "employee", Set.of(), false)
            .getTokenClaims().isActive()).isFalse();
    }

    /** 机器原始grant绕过用户策略，响应grant必须来自授权记录而非Token同名字段。 */
    @Test
    void machinesIgnoreUserTypeConfiguration() {
        for (Set<String> allowed : java.util.List.of(Set.<String>of(), Set.of("employee"))) {
            var machine = introspect(AuthorizationGrantType.CLIENT_CREDENTIALS, "", allowed, false);
            assertThat(machine.getTokenClaims().isActive()).isTrue();
            assertThat(machine.getTokenClaims().getClaims()).containsEntry("grant_type", "client_credentials")
                .doesNotContainKey("userType");
        }
        assertThat(introspect(AuthorizationGrantType.AUTHORIZATION_CODE, "", Set.of("employee"), false)
            .getTokenClaims().isActive()).isFalse();
    }

    /** 目录允许不能绕过过期或撤销，失败响应不泄漏用户信息。 */
    @Test
    void invalidatedTokenRemainsInactive() {
        assertThat(introspect(AuthorizationGrantType.AUTHORIZATION_CODE, "employee", Set.of("employee"), true)
            .getTokenClaims().isActive()).isFalse();
    }

    /** 原生Provider真实组合授权模型，模拟的仅为服务端配置读取。 */
    private OAuth2TokenIntrospectionAuthenticationToken introspect(AuthorizationGrantType grant, String userType,
        Set<String> allowed, boolean invalidated) {
        var owner = registered("owner");
        var caller = registered("resource");
        var configuration = new OAuth2ClientRegistration();
        configuration.setPurpose("RESOURCE_SERVER");
        configuration.setAuthorizationVersion(1L);
        configuration.setClientSecret(caller.getClientSecret());
        configuration.setAllowedUserTypes(allowed);
        var directory = mock(OAuth2ClientRegistrationService.class);
        when(directory.getClientByClientId("resource")).thenReturn(configuration);
        var target = new OAuth2ClientRegistration();
        target.setAuthorizationVersion(1L);
        when(directory.getClientByClientId("owner")).thenReturn(target);
        var claims = new HashMap<String, Object>();
        claims.put("sub", "alice");
        // 刻意写入与原始授权相反的grant，证明不能以此绕过分类。
        claims.put("grant_type", "client_credentials");
        if (!userType.isEmpty()) {
            claims.put("userType", userType);
        }
        var token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "fixture", Instant.now(),
            Instant.now().plusSeconds(60), Set.of("profile"));
        var store = new InMemoryOAuth2AuthorizationService();
        store.save(OAuth2Authorization.withRegisteredClient(owner).principalName("alice")
            .authorizationGrantType(grant).authorizedScopes(Set.of("profile"))
            .token(token, metadata -> {
                metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claims);
                metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, invalidated);
            }).build());
        var provider = new ResourceTokenIntrospectionAuthenticationProvider(
            new InMemoryRegisteredClientRepository(owner, caller), store, directory);
        var authentication = new OAuth2ClientAuthenticationToken(caller, ClientAuthenticationMethod.CLIENT_SECRET_BASIC, "test");
        return (OAuth2TokenIntrospectionAuthenticationToken) provider.authenticate(
            new OAuth2TokenIntrospectionAuthenticationToken("fixture", authentication, null, Map.of("grant_type", "client_credentials")));
    }

    /** 使用独立的机密客户端身份。 */
    private RegisteredClient registered(String id) {
        return RegisteredClient.withId(id).clientId(id).clientSecret("{noop}fixture-" + id)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .clientSettings(ClientSettings.builder().setting(OAuth2ResourcePolicy.AUTHORIZATION_VERSION, 1L).build()).build();
    }
}
