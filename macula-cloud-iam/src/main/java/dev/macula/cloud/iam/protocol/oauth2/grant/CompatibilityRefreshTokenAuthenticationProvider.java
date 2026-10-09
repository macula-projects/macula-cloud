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
package dev.macula.cloud.iam.protocol.oauth2.grant;

import java.security.Principal;
import java.util.Map;
import java.util.Set;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

/**
 * 适配带 openid scope 但不签发 ID Token 的 password/sms 兼容授权刷新。
 * 标准授权和其他刷新请求仍委托原框架 Provider，不修改已授权 scope 或伪造 ID Token。
 *
 * @author rain
 * @since 6.1.0
 */
public final class CompatibilityRefreshTokenAuthenticationProvider implements AuthenticationProvider {
    private final AuthenticationProvider delegate;
    private final OAuth2AuthorizationService authorizations;
    private final OAuth2TokenGenerator<? extends OAuth2Token> generator;

    public CompatibilityRefreshTokenAuthenticationProvider(AuthenticationProvider delegate,
        OAuth2AuthorizationService authorizations, OAuth2TokenGenerator<? extends OAuth2Token> generator) {
        this.delegate = delegate;
        this.authorizations = authorizations;
        this.generator = generator;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        OAuth2RefreshTokenAuthenticationToken request = (OAuth2RefreshTokenAuthenticationToken) authentication;
        if (!(request.getPrincipal() instanceof OAuth2ClientAuthenticationToken client)
            || !client.isAuthenticated() || client.getRegisteredClient() == null) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }
        OAuth2Authorization authorization = authorizations.findByToken(request.getRefreshToken(), OAuth2TokenType.REFRESH_TOKEN);
        if (authorization == null || !Set.of("password", "sms").contains(authorization.getAuthorizationGrantType().getValue())
            || !authorization.getAuthorizedScopes().contains(OidcScopes.OPENID)
            || authorization.getToken(OidcIdToken.class) != null) {
            return delegate.authenticate(authentication);
        }
        var registeredClient = client.getRegisteredClient();
        if (!registeredClient.getId().equals(authorization.getRegisteredClientId())) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }
        if (!registeredClient.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
        }
        // The existing compatibility grants issue refresh tokens only to authenticated confidential clients.
        if (ClientAuthenticationMethod.NONE.equals(client.getClientAuthenticationMethod())) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }
        var storedRefresh = authorization.getRefreshToken();
        Authentication principal = authorization.getAttribute(Principal.class.getName());
        if (storedRefresh == null || !storedRefresh.isActive() || principal == null) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }
        // Do not silently downgrade a sender-constrained token to a bearer token in this compatibility path.
        if (authorization.getAccessToken() != null && authorization.getAccessToken().getClaims() != null
            && authorization.getAccessToken().getClaims().containsKey("cnf")) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }
        Set<String> scopes = request.getScopes();
        if (!authorization.getAuthorizedScopes().containsAll(scopes)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_SCOPE);
        }
        if (scopes.isEmpty()) scopes = authorization.getAuthorizedScopes();
        var context = DefaultOAuth2TokenContext.builder().registeredClient(registeredClient)
            .principal(principal).authorizationServerContext(AuthorizationServerContextHolder.getContext())
            .authorization(authorization).authorizedScopes(scopes)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN).authorizationGrant(request);
        var updated = OAuth2Authorization.from(authorization);
        OAuth2Token generated = generator.generate(context.tokenType(OAuth2TokenType.ACCESS_TOKEN).build());
        if (generated == null) throw new OAuth2AuthenticationException(OAuth2ErrorCodes.SERVER_ERROR);
        OAuth2AccessToken access = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
            generated.getTokenValue(), generated.getIssuedAt(), generated.getExpiresAt(), scopes);
        updated.token(access, metadata -> {
            metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, false);
            if (generated instanceof ClaimAccessor claims) {
                metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claims.getClaims());
            }
        });
        OAuth2RefreshToken refresh = storedRefresh.getToken();
        if (!registeredClient.getTokenSettings().isReuseRefreshTokens()) {
            OAuth2Token next = generator.generate(context.tokenType(OAuth2TokenType.REFRESH_TOKEN)
                .authorization(updated.build()).build());
            if (!(next instanceof OAuth2RefreshToken)) throw new OAuth2AuthenticationException(OAuth2ErrorCodes.SERVER_ERROR);
            refresh = (OAuth2RefreshToken) next;
            updated.refreshToken(refresh);
        }
        // Persist once through the existing versioned/CAS authorization service, preserving the original scopes.
        authorizations.save(updated.build());
        return new OAuth2AccessTokenAuthenticationToken(registeredClient, client, access, refresh, Map.of());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2RefreshTokenAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
