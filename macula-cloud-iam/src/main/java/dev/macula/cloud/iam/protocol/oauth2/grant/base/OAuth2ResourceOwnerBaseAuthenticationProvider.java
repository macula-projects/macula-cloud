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

package dev.macula.cloud.iam.protocol.oauth2.grant.base;

import dev.macula.cloud.iam.utils.OAuth2ErrorCodesExpand;
import dev.macula.cloud.iam.utils.ScopeException;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.SpringSecurityMessageSource;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.*;
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
import org.springframework.util.CollectionUtils;

import java.security.Principal;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * {@code OAuth2ResourceOwnerBaseAuthenticationProvider} GrantType扩展基类
 *
 * @author jumuning
 *
 *     处理自定义授权
 */
@Slf4j
public abstract class OAuth2ResourceOwnerBaseAuthenticationProvider<T extends OAuth2ResourceOwnerBaseAuthenticationToken>
    implements AuthenticationProvider, MessageSourceAware {

    private static final Logger LOGGER = LogManager.getLogger(OAuth2ResourceOwnerBaseAuthenticationProvider.class);

    private static final String ERROR_URI = "https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2.1";

    private final OAuth2AuthorizationService authorizationService;

    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;

    private final AuthenticationManager authenticationManager;

    protected MessageSourceAccessor messages = SpringSecurityMessageSource.getAccessor();

    /**
     * Constructs an {@code OAuth2AuthorizationCodeAuthenticationProvider} using the provided parameters.
     *
     * @param authenticationManager the authentication manager
     * @param authorizationService  the authorization service
     * @param tokenGenerator        the token generator
     * @since 0.2.3
     */
    public OAuth2ResourceOwnerBaseAuthenticationProvider(AuthenticationManager authenticationManager,
        OAuth2AuthorizationService authorizationService, OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator) {
        Assert.notNull(authorizationService, "authorizationService cannot be null");
        Assert.notNull(tokenGenerator, "tokenGenerator cannot be null");
        this.authenticationManager = authenticationManager;
        this.authorizationService = authorizationService;
        this.tokenGenerator = tokenGenerator;
    }

    public abstract AbstractAuthenticationToken buildToken(Map<String, Object> reqParameters);

    /**
     * 当前provider是否支持此令牌类型
     *
     * @param authentication 认证类
     * @return 是否支持
     */
    @Override
    public abstract boolean supports(Class<?> authentication);

    /**
     * 当前的请求客户端是否支持此模式
     *
     * @param registeredClient 客户端
     */
    public abstract void checkClient(RegisteredClient registeredClient);

    /**
     * Performs authentication with the same contract as {@link AuthenticationManager#authenticate(Authentication)} .
     *
     * @param authentication the authentication request object.
     * @return a fully authenticated object including credentials. May return
     *     <code>null</code> if the <code>AuthenticationProvider</code> is unable to support
     *     authentication of the passed <code>Authentication</code> object. In such a case, the next
     *     <code>AuthenticationProvider</code> that supports the presented
     *     <code>Authentication</code> class will be tried.
     * @throws AuthenticationException if authentication fails.
     */
    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        T resourceOwnerBaseAuthentication = (T)authentication;

        OAuth2ClientAuthenticationToken clientPrincipal =
            getAuthenticatedClientElseThrowInvalidClient(resourceOwnerBaseAuthentication);

        RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();
        checkClient(registeredClient);

        Set<String> authorizedScopes;
        // Default to configured scopes
        if (!CollectionUtils.isEmpty(resourceOwnerBaseAuthentication.getScopes())) {
            for (String requestedScope : resourceOwnerBaseAuthentication.getScopes()) {
                if (registeredClient == null || !registeredClient.getScopes().contains(requestedScope)) {
                    throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_SCOPE);
                }
            }
            authorizedScopes = new LinkedHashSet<>(resourceOwnerBaseAuthentication.getScopes());
        } else {
            throw new ScopeException(OAuth2ErrorCodesExpand.SCOPE_IS_EMPTY);
        }

        Map<String, Object> reqParameters = resourceOwnerBaseAuthentication.getAdditionalParameters();
        try {

            AbstractAuthenticationToken authenticationToken = buildToken(reqParameters);

            LOGGER.debug("got authenticationToken=" + authenticationToken);

            Authentication usernamePasswordAuthentication = authenticationManager.authenticate(authenticationToken);

            // @formatter:off
            DefaultOAuth2TokenContext.Builder tokenContextBuilder = DefaultOAuth2TokenContext.builder()
                    .registeredClient(registeredClient)
                    .principal(usernamePasswordAuthentication)
                    .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                    .authorizedScopes(authorizedScopes)
                    .authorizationGrantType(AuthorizationGrantType.PASSWORD)
                    .authorizationGrant(resourceOwnerBaseAuthentication);
            // @formatter:on

            OAuth2Authorization.Builder authorizationBuilder =
                OAuth2Authorization.withRegisteredClient(registeredClient)
                    .principalName(usernamePasswordAuthentication.getName())
                    .authorizationGrantType(AuthorizationGrantType.PASSWORD)
                    // 0.4.0 新增的方法
                    .authorizedScopes(authorizedScopes);

            // ----- Access token -----
            OAuth2TokenContext tokenContext = tokenContextBuilder.tokenType(OAuth2TokenType.ACCESS_TOKEN).build();
            OAuth2Token generatedAccessToken = this.tokenGenerator.generate(tokenContext);
            if (generatedAccessToken == null) {
                OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR,
                    "The token generator failed to generate the access token.", ERROR_URI);
                throw new OAuth2AuthenticationException(error);
            }
            OAuth2AccessToken accessToken =
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, generatedAccessToken.getTokenValue(),
                    generatedAccessToken.getIssuedAt(), generatedAccessToken.getExpiresAt(),
                    tokenContext.getAuthorizedScopes());
            if (generatedAccessToken instanceof ClaimAccessor) {
                authorizationBuilder.id(accessToken.getTokenValue()).token(accessToken,
                        (metadata) -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                            ((ClaimAccessor)generatedAccessToken).getClaims()))
                    // 0.4.0 新增的方法
                    .authorizedScopes(authorizedScopes)
                    .attribute(Principal.class.getName(), usernamePasswordAuthentication);
            } else {
                authorizationBuilder.id(accessToken.getTokenValue()).accessToken(accessToken);
            }

            // ----- Refresh token -----
            OAuth2RefreshToken refreshToken = null;
            if (registeredClient.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN) &&
                // Do not issue refresh token to public client
                !clientPrincipal.getClientAuthenticationMethod().equals(ClientAuthenticationMethod.NONE)) {

                tokenContext = tokenContextBuilder.tokenType(OAuth2TokenType.REFRESH_TOKEN).build();
                OAuth2Token generatedRefreshToken = this.tokenGenerator.generate(tokenContext);
                if (!(generatedRefreshToken instanceof OAuth2RefreshToken)) {
                    OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR,
                        "The token generator failed to generate the refresh token.", ERROR_URI);
                    throw new OAuth2AuthenticationException(error);
                }
                refreshToken = (OAuth2RefreshToken)generatedRefreshToken;
            }
            authorizationBuilder.refreshToken(refreshToken);

            OAuth2Authorization authorization = authorizationBuilder.build();

            this.authorizationService.save(authorization);

            LOGGER.debug("returning OAuth2AccessTokenAuthenticationToken");

            return new OAuth2AccessTokenAuthenticationToken(registeredClient, clientPrincipal, accessToken,
                refreshToken, Objects.requireNonNull(authorization.getAccessToken().getClaims()));

        } catch (Exception ex) {
            if (ex instanceof AuthenticationException) {
                throw oAuth2AuthenticationException(authentication, (AuthenticationException)ex);
            }
            throw oAuth2AuthenticationException(authentication, new BadCredentialsException("unknown auth error", ex));
        }

    }

    public void setMessageSource(MessageSource messageSource) {
        this.messages = new MessageSourceAccessor(messageSource);
    }

    /**
     * 登录异常转换为oauth2异常
     *
     * @param authentication          身份验证
     * @param authenticationException 身份验证异常
     * @return {@link OAuth2AuthenticationException}
     */
    private OAuth2AuthenticationException oAuth2AuthenticationException(Authentication authentication,
        AuthenticationException authenticationException) {
        if (authenticationException instanceof UsernameNotFoundException) {
            return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodesExpand.USERNAME_NOT_FOUND,
                this.messages.getMessage("JdbcDaoImpl.notFound", new Object[] {authentication.getName()},
                    "Username {0} not found"), ""));
        }
        if (authenticationException instanceof BadCredentialsException) {
            return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodesExpand.BAD_CREDENTIALS,
                this.messages.getMessage("AbstractUserDetailsAuthenticationProvider.badCredentials", "Bad credentials"),
                ""));
        }
        if (authenticationException instanceof LockedException) {
            return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodesExpand.USER_LOCKED,
                this.messages.getMessage("AbstractUserDetailsAuthenticationProvider.locked", "User account is locked"),
                ""));
        }
        if (authenticationException instanceof DisabledException) {
            return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodesExpand.USER_DISABLE,
                this.messages.getMessage("AbstractUserDetailsAuthenticationProvider.disabled", "User is disabled"),
                ""));
        }
        if (authenticationException instanceof AccountExpiredException) {
            return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodesExpand.USER_EXPIRED,
                this.messages.getMessage("AbstractUserDetailsAuthenticationProvider.expired",
                    "User account has expired"), ""));
        }
        if (authenticationException instanceof CredentialsExpiredException) {
            return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodesExpand.CREDENTIALS_EXPIRED,
                this.messages.getMessage("AbstractUserDetailsAuthenticationProvider.credentialsExpired",
                    "User credentials have expired"), ""));
        }
        if (authenticationException instanceof ScopeException) {
            return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_SCOPE,
                this.messages.getMessage("AbstractAccessDecisionManager.accessDenied", "invalid_scope"), ""));
        }

        log.error(authenticationException.getLocalizedMessage());
        return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR),
            authenticationException.getLocalizedMessage(), authenticationException);
    }

    private OAuth2ClientAuthenticationToken getAuthenticatedClientElseThrowInvalidClient(
        Authentication authentication) {

        OAuth2ClientAuthenticationToken clientPrincipal = null;

        if (OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication.getPrincipal().getClass())) {
            clientPrincipal = (OAuth2ClientAuthenticationToken)authentication.getPrincipal();
        }

        if (clientPrincipal != null && clientPrincipal.isAuthenticated()) {
            return clientPrincipal;
        }

        throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
    }

}
