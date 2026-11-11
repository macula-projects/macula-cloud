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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import dev.macula.cloud.iam.service.client.SysOAuth2ClientService;
import dev.macula.cloud.system.security.OAuth2ResourcePolicy;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenIntrospection;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * 通过机密Client认证的内省返回Token原始资源与Scope；不增加业务Client间的资源许可配置。
 *
 * @author Rain
 * @since 6.1.0
 */
public final class ResourceTokenIntrospectionAuthenticationProvider implements AuthenticationProvider {
    private static final Set<String> COMMON = Set.of("active", "client_id", "sub", "iss", "iat", "exp", "nbf",
            "token_type");
    private static final Set<String> SYSTEM = Set.of("tenantId", "authorities", "deptId", "dataScope", "grant_type");
    private final SysOAuth2ClientService clients;
    private final OAuth2AuthorizationService authorizations;
    private final AuthenticationProvider delegate;

    /**
     * 原生provider验证并恢复claims；响应仅包含既有安全元数据。
     */
    public ResourceTokenIntrospectionAuthenticationProvider(RegisteredClientRepository base,
            OAuth2AuthorizationService authorizations, SysOAuth2ClientService clients) {
        this.clients = clients;
        this.authorizations = authorizations;
        this.delegate = new OAuth2TokenIntrospectionAuthenticationProvider(base, authorizations);
    }

    /**
     * 校验调用方及Token有效性；业务资源授权由API自己执行，不在内省处额外配置许可。
     */
    @Override
    public Authentication authenticate(Authentication authentication) {
        var request = (OAuth2TokenIntrospectionAuthenticationToken) authentication;
        if (!(request.getPrincipal() instanceof OAuth2ClientAuthenticationToken caller) || !caller.isAuthenticated()) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }
        var client = clients.getClientByClientId(caller.getName());
        if (client == null || !"RESOURCE_SERVER".equals(client.getPurpose())) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
        }
        Number authenticatedVersion = caller.getRegisteredClient().getClientSettings()
                .getSetting(OAuth2ResourcePolicy.AUTHORIZATION_VERSION);
        if (authenticatedVersion == null || client.getAuthorizationVersion() == null
                || authenticatedVersion.longValue() != client.getAuthorizationVersion()
                ||
                !java.util.Objects.equals(caller.getRegisteredClient().getClientSecret(), client.getClientSecret())) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }
        var authorization = authorizations.findByToken(request.getToken(), OAuth2TokenType.ACCESS_TOKEN);
        var access = authorization == null ? null : authorization.getAccessToken();
        if (access == null || !access.isActive()) {
            return inactive(request, caller);
        }
        var nativeResult = (OAuth2TokenIntrospectionAuthenticationToken) delegate.authenticate(authentication);
        if (!nativeResult.getTokenClaims().isActive()) {
            return inactive(request, caller);
        }
        Map<String, Object> projection = new HashMap<>();
        nativeResult.getTokenClaims().getClaims().forEach((key, value) -> {
            if (COMMON.contains(key) || SYSTEM.contains(key) || "aud".equals(key) || "scope".equals(key)) {
                projection.put(key, value);
            }
        });
        var owner = clients.getClientByClientId(authorization.getRegisteredClientId());
        if (owner == null) {
            return inactive(request, caller);
        }
        // Scope来自已签发Token，不按内省调用方再建立一套许可或裁剪。
        projection.put("scope", access.getToken().getScopes().stream().sorted().toList());
        projection.put(OAuth2ResourcePolicy.AUTHORIZATION_VERSION, owner.getAuthorizationVersion());
        return new OAuth2TokenIntrospectionAuthenticationToken(request.getToken(), caller,
                OAuth2TokenIntrospection.withClaims(projection).build());
    }

    /**
     * 无效Token只返回active:false，不附带内部原因。
     */
    private Authentication inactive(OAuth2TokenIntrospectionAuthenticationToken request,
            OAuth2ClientAuthenticationToken caller) {
        return new OAuth2TokenIntrospectionAuthenticationToken(request.getToken(), caller,
                OAuth2TokenIntrospection.builder().build());
    }

    /**
     * 只处理内省，不参与令牌签发。
     */
    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2TokenIntrospectionAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
