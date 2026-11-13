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

import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.macula.boot.constants.GlobalConstants;
import dev.macula.boot.constants.SecurityConstants;
import dev.macula.cloud.iam.protocol.oauth2.token.claims.AccessTokenClaimsService;
import dev.macula.cloud.iam.protocol.oauth2.token.claims.DefaultAccessTokenClaimsService;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import dev.macula.cloud.system.security.OAuth2ResourcePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 验证 OAuth2 access token 的平台身份 claim。
 *
 * @author Rain
 * @since 6.1.0
 */
class CustomOAuth2TokenCustomizerTest {

    @Test
    void authenticatedUserAccessClaimsContainTrustedTenantAndExistingClaims() {
        SysUserDetails user = new SysUserDetails();
        user.setUsername("fixture-user");
        user.setTenantId(12L);
        user.setNickname("Fixture");
        user.setDeptId(8L);
        user.setDataScope(2);
        user.setEnabled(true);
        user.setAuthorities(List.of(new SimpleGrantedAuthority("ROLE_USER")));
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                user, null, user.getAuthorities());

        Map<String, Object> claims = accessClaims(authentication);

        assertEquals(12L, claims.get(GlobalConstants.TENANT_ID_NAME));
        assertEquals("fixture-client", claims.get("client_id"));
        assertFalse(claims.containsKey(SecurityConstants.JWT_NICKNAME_KEY));
        assertEquals(8L, claims.get(SecurityConstants.JWT_DEPTID_KEY));
        assertEquals(2, claims.get(SecurityConstants.JWT_DATASCOPE_KEY));
        assertEquals(List.of("ROLE_USER"), claims.get(SecurityConstants.AUTHORITIES_KEY));
    }

    @Test
    void nonUserPrincipalDoesNotCreateTenantClaim() {
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                "application", null, List.of(new SimpleGrantedAuthority("SCOPE_api")));

        Map<String, Object> claims = accessClaims(authentication);

        assertFalse(claims.containsKey(GlobalConstants.TENANT_ID_NAME));
        assertEquals("fixture-client", claims.get("client_id"));
    }

    /**
     * 通过声明服务端口测试，协议适配器不再暴露内部规则的静态入口。
     */
    private Map<String, Object> accessClaims(Authentication principal) {
        var settings = ClientSettings.builder()
                .setting(ResourceAuthorizationPolicy.PERMISSIONS,
                        Map.of("api.read", OAuth2ResourcePolicy.SYSTEM_RESOURCE))
                .setting(OAuth2ResourcePolicy.AUTHORIZATION_VERSION, 1L).build();
        var client = RegisteredClient.withId(
                        "fixture-client")
                .clientId("fixture-client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(
                        AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://fixture.example/callback").clientSettings(settings).scope("api.read").build();
        AccessTokenClaimsService claims =
                new DefaultAccessTokenClaimsService();
        return claims.create(principal, client, Set.of("api.read"), "authorization_code");
    }
}
