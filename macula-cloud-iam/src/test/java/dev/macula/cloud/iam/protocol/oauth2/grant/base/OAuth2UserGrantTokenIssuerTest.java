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
package dev.macula.cloud.iam.protocol.oauth2.grant.base;

import java.time.Instant;
import java.util.Set;

import dev.macula.cloud.iam.protocol.oauth2.ApplicationProtocolResolver;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeClientSettings;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.OAuth2ExternalCodeAuthenticationToken;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * ID Token失败或本地身份缺失时，不得发布半份OIDC授权。
 *
 * @author Rain
 * @since 6.1.0
 */
class OAuth2UserGrantTokenIssuerTest {
    /**
     * 独立签发器需要明确的授权服务器上下文。
     */
    @org.junit.jupiter.api.BeforeEach
    void context() {
        org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder.setContext(
                mock(org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext.class));
    }

    /**
     * 线程上下文不能泄漏到其他测试。
     */
    @org.junit.jupiter.api.AfterEach
    void clearContext() {
        org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder.resetContext();
    }

    /**
     * Access Token已生成但ID Token失败，仍不能保存授权。
     */
    @Test
    void identityFailureDoesNotSavePartialAuthorization() {
        var service = mock(OAuth2AuthorizationService.class);
        OAuth2TokenGenerator<OAuth2Token> generator = context -> {
            if (!"access_token".equals(context.getTokenType().getValue())) {
                return null;
            }
            return Jwt.withTokenValue("fixture-access").header("alg", "RS256").subject("1:42")
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        };
        var issuer = new OAuth2UserGrantTokenIssuer(service, generator);
        var client = client();
        var authenticated = new OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.NONE, null);
        var user = user();
        var principal = UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
        assertThatThrownBy(() -> issuer.issue(client, authenticated, principal, principal,
                OAuth2ExternalCodeAuthenticationToken.GRANT_TYPE, Set.of("openid"), false))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        error -> assertThat(error.getError().getErrorCode()).isEqualTo("server_error"));
        verifyNoInteractions(service);
    }

    /**
     * 缺失ID或租户不匹配必须在生成任何令牌前失败。
     */
    @Test
    @SuppressWarnings("unchecked")
    void rejectsMissingStableUserAndMismatchedTenant() {
        var service = mock(OAuth2AuthorizationService.class);
        OAuth2TokenGenerator<OAuth2Token> generator = mock(OAuth2TokenGenerator.class);
        var issuer = new OAuth2UserGrantTokenIssuer(service, generator);
        var client = client();
        var authenticated = new OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.NONE, null);
        for (Long id : new Long[]{null, 0L, -1L, 42L}) {
            var user = user();
            user.setUserId(id);
            if (Long.valueOf(42L).equals(id)) {
                user.setTenantId(2L);
            }
            var principal = UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
            assertThatThrownBy(() -> issuer.issue(client, authenticated, principal, principal,
                    OAuth2ExternalCodeAuthenticationToken.GRANT_TYPE, Set.of("openid"), false))
                    .isInstanceOf(OAuth2AuthenticationException.class);
        }
        verifyNoInteractions(service, generator);
    }

    /**
     * 固定本地用户只用于测试身份。
     */
    private SysUserDetails user() {
        var user = new SysUserDetails();
        user.setUserId(42L);
        user.setTenantId(1L);
        user.setUsername("fixture-user");
        return user;
    }

    /**
     * 使用明确的协议和可信租户。
     */
    private RegisteredClient client() {
        return RegisteredClient.withId("sdk").clientId("sdk")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(OAuth2ExternalCodeAuthenticationToken.GRANT_TYPE).scope("openid")
                .clientSettings(ClientSettings.builder().setting(ApplicationProtocolResolver.EFFECTIVE_PROTOCOL, "OIDC")
                        .setting(ExternalCodeClientSettings.TENANT_ID, 1L).build()).build();
    }
}
