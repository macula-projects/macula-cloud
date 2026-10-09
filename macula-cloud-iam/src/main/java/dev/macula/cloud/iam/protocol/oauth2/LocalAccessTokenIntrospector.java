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

import java.util.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/**
 * IAM UserInfo 使用本地授权状态验证 opaque/JWT access token，撤销即时生效。
 * @author rain
 * @since 6.1.0
 */
public final class LocalAccessTokenIntrospector implements OpaqueTokenIntrospector {
    private final OAuth2AuthorizationService authorizations;

    public LocalAccessTokenIntrospector(OAuth2AuthorizationService authorizations) {
        this.authorizations = authorizations;
    }

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        OAuth2Authorization authorization = authorizations.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
        if (authorization == null || authorization.getAccessToken() == null
            || !authorization.getAccessToken().isActive()) throw new InvalidBearerTokenException("Invalid access token");
        var access = authorization.getAccessToken();
        Map<String, Object> claims = new HashMap<>();
        if (access.getClaims() != null) claims.putAll(access.getClaims());
        claims.putIfAbsent("sub", authorization.getPrincipalName());
        claims.put("scope", access.getToken().getScopes());
        claims.put("iat", access.getToken().getIssuedAt());
        claims.put("exp", access.getToken().getExpiresAt());
        return new DefaultOAuth2AuthenticatedPrincipal(authorization.getPrincipalName(), claims,
            access.getToken().getScopes().stream().<org.springframework.security.core.GrantedAuthority>map(
                scope -> new SimpleGrantedAuthority("SCOPE_" + scope)).toList());
    }
}
