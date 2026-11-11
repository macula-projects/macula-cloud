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

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import dev.macula.cloud.iam.protocol.oauth2.ApplicationProtocolResolver;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeClientSettings;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import dev.macula.cloud.system.enums.ExternalCodeType;
import dev.macula.cloud.system.protocol.ApplicationProtocolRegistry;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
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

/**
 * 使用真实 grant type 签发用户令牌；OIDC第三方Code同时持久化ID Token。
 *
 * @author Rain
 * @since 6.1.0
 */
public class OAuth2UserGrantTokenIssuer {
    private static final String ERROR_URI = "https://datatracker.ietf.org/doc/html/rfc6749#section-5.2";
    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;
    private dev.macula.cloud.iam.authentication.mfa.MfaCompletionService mfaCompletion =
            dev.macula.cloud.iam.authentication.mfa.MfaCompletionService.noOp();
    public OAuth2UserGrantTokenIssuer(OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator) {
        Assert.notNull(authorizationService, "authorizationService cannot be null");
        Assert.notNull(tokenGenerator, "tokenGenerator cannot be null");
        this.authorizationService = authorizationService;
        this.tokenGenerator = tokenGenerator;
    }

    /**
     * 手动创建的扩展grant同样使用Spring中的统一完成策略。
     */
    public OAuth2UserGrantTokenIssuer(OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
            dev.macula.cloud.iam.authentication.mfa.MfaCompletionService completion) {
        this(authorizationService, tokenGenerator);
        setMfaCompletion(completion);
    }

    /**
     * Spring生产装配必须注入统一策略；显式构造的存储/生成器单测保留默认行为。
     */
    @org.springframework.beans.factory.annotation.Autowired
    public void setMfaCompletion(dev.macula.cloud.iam.authentication.mfa.MfaCompletionService completion) {
        this.mfaCompletion = java.util.Objects.requireNonNull(completion);
    }

    /**
     * 仅OIDC第三方Code扩展额外签发ID Token；全部令牌生成成功后才保存授权。
     */
    public OAuth2AccessTokenAuthenticationToken issue(RegisteredClient registeredClient,
            OAuth2ClientAuthenticationToken clientPrincipal, Authentication userPrincipal,
            Authentication authorizationGrant, AuthorizationGrantType grantType, Set<String> authorizedScopes,
            boolean allowRefreshToken) {
        var sourceProof = dev.macula.cloud.iam.authentication.mfa.MfaCompletionService.proof(userPrincipal);
        mfaCompletion.checkToken(userPrincipal, registeredClient,
                dev.macula.cloud.iam.authentication.mfa.AuthenticationCheckpoint.USER_TOKEN, grantType.getValue(),
                sourceProof);
        boolean oidcExternal = ExternalCodeType.GRANT_TYPE.equals(grantType.getValue())
                && ApplicationProtocolRegistry.OIDC.equals(registeredClient.getClientSettings().getSetting(
                ApplicationProtocolResolver.EFFECTIVE_PROTOCOL))
                && authorizedScopes.contains(OidcScopes.OPENID);
        if (oidcExternal) {
            requireCanonicalUser(registeredClient, userPrincipal);
        }
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
        if (!sourceProof.isEmpty()) {
            authorizationBuilder.attribute(dev.macula.cloud.iam.authentication.mfa.MfaCompletionService.SOURCE_PROOF,
                    sourceProof);
        }

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
                            metadata -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                                    claims.getClaims()))
                    .attribute(Principal.class.getName(), userPrincipal);
        } else {
            authorizationBuilder.id(accessToken.getTokenValue()).accessToken(accessToken);
        }

        OAuth2RefreshToken refreshToken = null;
        if (allowRefreshToken && registeredClient.getAuthorizationGrantTypes().contains(
                AuthorizationGrantType.REFRESH_TOKEN)) {
            tokenContext = tokenContextBuilder.tokenType(OAuth2TokenType.REFRESH_TOKEN).build();
            OAuth2Token generatedRefreshToken = tokenGenerator.generate(tokenContext);
            if (!(generatedRefreshToken instanceof OAuth2RefreshToken generated)) {
                throw serverError("The token generator failed to generate the refresh token.");
            }
            refreshToken = generated;
            authorizationBuilder.refreshToken(refreshToken);
        }

        OidcIdToken idToken = null;
        if (oidcExternal) {
            // 先有完整用户和Access Token上下文，但不能在ID Token失败前发布半份授权。
            tokenContext = tokenContextBuilder.authorization(authorizationBuilder.build())
                    .tokenType(new OAuth2TokenType(OidcParameterNames.ID_TOKEN)).build();
            OAuth2Token generatedIdToken = tokenGenerator.generate(tokenContext);
            if (!(generatedIdToken instanceof ClaimAccessor idClaims)) {
                throw serverError("The token generator failed to generate the ID token.");
            }
            idToken = new OidcIdToken(generatedIdToken.getTokenValue(), generatedIdToken.getIssuedAt(),
                    generatedIdToken.getExpiresAt(), idClaims.getClaims());
            authorizationBuilder.token(idToken, metadata ->
                    metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, idClaims.getClaims()));
            if (idClaims.getClaims().get(OidcParameterNames.NONCE) instanceof String nonce) {
                authorizationBuilder.attribute(OidcParameterNames.NONCE, nonce);
            }
        }
        OAuth2Authorization authorization = authorizationBuilder.build();
        authorizationService.save(authorization);
        Map<String, Object> claims = authorization.getAccessToken().getClaims() == null
                ? Map.of() : authorization.getAccessToken().getClaims();
        Map<String, Object> additional = new HashMap<>(claims);
        if (idToken != null) {
            additional.put(OidcParameterNames.ID_TOKEN, idToken.getTokenValue());
        }
        return new OAuth2AccessTokenAuthenticationToken(registeredClient, clientPrincipal, accessToken,
                refreshToken, additional);
    }

    /**
     * 不允许将缺少稳定本地身份的厂商结果退化成用户名subject。
     */
    private void requireCanonicalUser(RegisteredClient client, Authentication principal) {
        Object tenant = client.getClientSettings().getSetting(
                ExternalCodeClientSettings.TENANT_ID);
        if (!(principal.getPrincipal() instanceof SysUserDetails user)
                || user.getUserId() == null || user.getUserId() <= 0 || user.getTenantId() == null
                || !(tenant instanceof Number number) || number.longValue() <= 0 ||
                number.longValue() != user.getTenantId()) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT));
        }
    }

    /**
     * 生成器失败不包含Token或上游凭证。
     */
    private OAuth2AuthenticationException serverError(String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR,
                description, ERROR_URI));
    }
}
