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
package dev.macula.cloud.iam.controller;

import dev.macula.cloud.iam.service.profile.ProfileService;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import dev.macula.cloud.system.security.OAuth2ResourcePolicy;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 资料字段白名单、用户上下文、Scope与凭据输入边界。
 * @author Rain
 * @since 6.1.0
 */
class ProfileControllerTest {
    /** 已授权用户只能读取自己的三项资料；缺Scope和跨租户均拒绝。 */
    @Test
    void profileRequiresUserScopeAudienceAndMatchingTenant() {
        var service = profiles(Set.of("profile"), 1L, true);
        var profile = service.profile("fixture");
        assertEquals("1:42", profile.sub());
        assertEquals("fixture-user", profile.preferredUsername());
        assertEquals("fixture-nickname", profile.nickname());
        assertThrows(ResponseStatusException.class, () -> profiles(Set.of("system:access"), 1L, true).profile("fixture"));
        assertThrows(ResponseStatusException.class, () -> profiles(Set.of("profile"), 2L, true).profile("fixture"));
        assertThrows(ResponseStatusException.class, () -> profiles(Set.of("profile"), 1L, false).profile("fixture"));
        assertThrows(ResponseStatusException.class, () -> service.profile("unknown"));
    }

    /** 重复Authorization或Query Token不能模糊凭据来源。 */
    @Test
    void controllerRejectsAmbiguousCredentials() {
        var controller = new ProfileController(profiles(Set.of("profile"), 1L, true));
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer fixture");
        request.addHeader("Authorization", "Bearer other");
        assertThrows(ResponseStatusException.class, () -> controller.profile(request));
    }

    /** 测试最小授权存储，不冒充真实令牌签发或Redis集成。 */
    private ProfileService profiles(Set<String> scopes, long tenant, boolean userPresent) {
        var store = new InMemoryOAuth2AuthorizationService();
        var client = RegisteredClient.withId("client").clientId("client")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://client.example/cb").build();
        var token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "fixture", Instant.now(), Instant.now().plusSeconds(60), scopes);
        var authorization = OAuth2Authorization.withRegisteredClient(client).principalName("fixture-user")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).authorizedScopes(scopes)
            .token(token, metadata -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                Map.of("aud", List.of(OAuth2ResourcePolicy.PROFILE_RESOURCE), "tenantId", tenant)));
        if (userPresent) {
            var user = new SysUserDetails();
            user.setUserId(42L);
            user.setTenantId(1L);
            user.setUsername("fixture-user");
            user.setNickname("fixture-nickname");
            authorization.attribute(Principal.class.getName(), UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));
        }
        store.save(authorization.build());
        return new ProfileService(store);
    }
}
