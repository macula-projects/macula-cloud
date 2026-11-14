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
package dev.macula.cloud.iam.protocol.oauth2.token.claims.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.macula.boot.constants.SecurityConstants;
import dev.macula.cloud.iam.authentication.email.EmailCodeAuthenticationToken;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import dev.macula.cloud.system.security.OAuth2ResourcePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 认证因子只提供真实认证时间，不能成为业务角色；资源权限仍按Scope控制。
 *
 * @author Macula
 * @since 6.1.0
 */
class DefaultAccessTokenClaimsServiceTest {
    /**
     * 授权业务资源保留USER角色，认证时间元数据不会进入authorities。
     */
    @Test
    void businessRolesExcludeFrameworkFactors() {
        var principal = new SysUserDetails();
        principal.setUsername("alice");
        principal.setUserType("employee");
        principal.setTenantId(1L);
        var authentication = new EmailCodeAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("USER"),
                        FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.OTT_AUTHORITY)));
        var client = RegisteredClient.withId("fixture").clientId("fixture")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://client.example/callback")
                .scope("system:access")
                .clientSettings(ClientSettings.builder().setting("macula.user-route.user-type", "employee").setting(OAuth2ResourcePolicy.PERMISSIONS_SETTING,
                        Map.of("system:access", OAuth2ResourcePolicy.SYSTEM_RESOURCE)).build()).build();
        var claims = new DefaultAccessTokenClaimsService().create(authentication, client, Set.of("system:access"),
                "authorization_code");
        assertThat(claims.get(SecurityConstants.AUTHORITIES_KEY)).isEqualTo(List.of("USER"));
    }
    /** 缺失主体目录或跨目录SSO不得签发新的用户Token。 */
    @Test
    void rejectsMissingOrDifferentPrincipalDirectory() {
        var user = new SysUserDetails();
        user.setUsername("alice");
        var authentication = new EmailCodeAuthenticationToken(user, null, List.of());
        var client = RegisteredClient.withId("fixture").clientId("fixture")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://fixture.example/callback")
            .clientSettings(ClientSettings.builder().setting("macula.user-route.user-type", "employee").build()).build();
        var claims = new DefaultAccessTokenClaimsService();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> claims.create(authentication, client, Set.of(), "authorization_code"))
            .isInstanceOf(org.springframework.security.oauth2.core.OAuth2AuthenticationException.class);
        user.setUserType("member");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> claims.create(authentication, client, Set.of(), "authorization_code"))
            .isInstanceOf(org.springframework.security.oauth2.core.OAuth2AuthenticationException.class);
    }

}
