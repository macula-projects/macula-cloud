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

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.util.Assert;

import java.security.Principal;
import java.util.Map;
import java.util.Set;

/**
 * 使用调用方给定的真实 grant type 保存授权并签发用户 access token。
 *
 * @author Rain
 * @since 6.1.0
 */
public class OAuth2UserGrantTokenIssuer {
    private static final String ERROR_URI = "https://datatracker.ietf.org/doc/html/rfc6749#section-5.2";

    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;

    public OAuth2UserGrantTokenIssuer(OAuth2AuthorizationService authorizationService,
        OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator) {
        Assert.notNull(authorizationService, "authorizationService cannot be null");
        Assert.notNull(tokenGenerator, "tokenGenerator cannot be null");
        this.authorizationService = authorizationService;
        this.tokenGenerator = tokenGenerator;
    }

    public OAuth2AccessTokenAuthenticationToken issue(RegisteredClient registeredClient,
        OAuth2ClientAuthenticationToken clientPrincipal, Authentication userPrincipal,
        Authentication authorizationGrant, AuthorizationGrantType grantType, Set<String> authorizedScopes,
        boolean allowRefreshToken) {
        DefaultOAuth2TokenContext.Builder tokenContextBuilder = DefaultOAuth2TokenContext.builder()
            .registeredClient(registeredClient)
            .principal(userPrincipal)
            .authorizationServerContext(AuthorizationServerContextHolder.getContext())
            .authorizedScopes(authorizedScopes)
            .authorizationGrantType(grantType)
            .authorizationGrant(authorizationGrant);

        OAuth2Authorization.Builder authorizationBuilder = OAuth2Authorization.withRegisteredClient(registeredClient)
            .principalName(userPrincipal.getName())
            .authorizationGrantType(grantType)
            .authorizedScopes(authorizedScopes);

        OAuth2TokenContext tokenContext = tokenContextBuilder.tokenType(OAuth2TokenType.ACCESS_TOKEN).build();
        OAuth2Token generatedAccessToken = tokenGenerator.generate(tokenContext);
        if (generatedAccessToken == null) {
            throw serverError("The token generator failed to generate the access token.");
        }
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
            generatedAccessToken.getTokenValue(), generatedAccessToken.getIssuedAt(),
            generatedAccessToken.getExpiresAt(), tokenContext.getAuthorizedScopes());
        if (generatedAccessToken instanceof ClaimAccessor claims) {
            authorizationBuilder.id(accessToken.getTokenValue()).token(accessToken,
                    metadata -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claims.getClaims()))
                .attribute(Principal.class.getName(), userPrincipal);
        } else {
            authorizationBuilder.id(accessToken.getTokenValue()).accessToken(accessToken);
        }

        OAuth2RefreshToken refreshToken = null;
        if (allowRefreshToken && registeredClient.getAuthorizationGrantTypes().contains(
            AuthorizationGrantType.REFRESH_TOKEN) &&
            !ClientAuthenticationMethod.NONE.equals(clientPrincipal.getClientAuthenticationMethod())) {
            tokenContext = tokenContextBuilder.tokenType(OAuth2TokenType.REFRESH_TOKEN).build();
            OAuth2Token generatedRefreshToken = tokenGenerator.generate(tokenContext);
            if (!(generatedRefreshToken instanceof OAuth2RefreshToken generated)) {
                throw serverError("The token generator failed to generate the refresh token.");
            }
            refreshToken = generated;
            authorizationBuilder.refreshToken(refreshToken);
        }

        OAuth2Authorization authorization = authorizationBuilder.build();
        authorizationService.save(authorization);
        Map<String, Object> claims = authorization.getAccessToken().getClaims() == null
            ? Map.of() : authorization.getAccessToken().getClaims();
        return new OAuth2AccessTokenAuthenticationToken(registeredClient, clientPrincipal, accessToken,
            refreshToken, claims);
    }

    private OAuth2AuthenticationException serverError(String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR,
            description, ERROR_URI));
    }
}
