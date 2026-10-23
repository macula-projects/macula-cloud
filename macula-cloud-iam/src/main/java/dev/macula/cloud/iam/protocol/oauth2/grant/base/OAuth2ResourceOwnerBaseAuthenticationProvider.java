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
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.SpringSecurityMessageSource;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.util.CollectionUtils;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 资源所有者类自定义 grant 的公共 Client、scope、认证和签发流程。
 *
 * @param <T> 自定义 grant 认证请求类型
 * @author jumuning
 * @since 0.2.3
 */
@Slf4j
public abstract class OAuth2ResourceOwnerBaseAuthenticationProvider<T extends OAuth2ResourceOwnerBaseAuthenticationToken>
    implements org.springframework.security.authentication.AuthenticationProvider, MessageSourceAware {

    private final AuthenticationManager authenticationManager;
    private final OAuth2UserGrantTokenIssuer tokenIssuer;
    protected MessageSourceAccessor messages = SpringSecurityMessageSource.getAccessor();

    protected OAuth2ResourceOwnerBaseAuthenticationProvider(AuthenticationManager authenticationManager,
        OAuth2AuthorizationService authorizationService, OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator) {
        this.authenticationManager = authenticationManager;
        this.tokenIssuer = new OAuth2UserGrantTokenIssuer(authorizationService, tokenGenerator);
    }

    public abstract AbstractAuthenticationToken buildToken(Map<String, Object> requestParameters,
        RegisteredClient registeredClient);

    public abstract void checkClient(RegisteredClient registeredClient);

    @Override
    @SuppressWarnings("unchecked")
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        T grantAuthentication = (T) authentication;
        OAuth2ClientAuthenticationToken clientPrincipal = authenticatedClient(grantAuthentication);
        RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();
        checkClient(registeredClient);
        Set<String> authorizedScopes = validateScopes(registeredClient, grantAuthentication.getScopes());
        try {
            Authentication userPrincipal = authenticationManager.authenticate(
                buildToken(grantAuthentication.getAdditionalParameters(), registeredClient));
            return tokenIssuer.issue(registeredClient, clientPrincipal, userPrincipal, grantAuthentication,
                grantAuthentication.getAuthorizationGrantType(), authorizedScopes, allowRefreshToken());
        } catch (AuthenticationException ex) {
            throw translate(ex);
        } catch (RuntimeException ex) {
            log.warn("OAuth2 user grant failed category=SERVER_ERROR exception={}", ex.getClass().getSimpleName());
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR,
                "Authentication could not be completed.", null));
        }
    }

    @Override
    public void setMessageSource(MessageSource messageSource) {
        this.messages = new MessageSourceAccessor(messageSource);
    }

    private Set<String> validateScopes(RegisteredClient registeredClient, Set<String> requestedScopes) {
        if (CollectionUtils.isEmpty(requestedScopes)) {
            throw new ScopeException(OAuth2ErrorCodesExpand.SCOPE_IS_EMPTY);
        }
        if (!supportsOpenIdScope() && requestedScopes.contains(OidcScopes.OPENID)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_SCOPE);
        }
        for (String requestedScope : requestedScopes) {
            if (!registeredClient.getScopes().contains(requestedScope)) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_SCOPE);
            }
        }
        return new LinkedHashSet<>(requestedScopes);
    }

    protected boolean allowRefreshToken() {
        return true;
    }

    protected boolean supportsOpenIdScope() {
        return true;
    }

    private OAuth2ClientAuthenticationToken authenticatedClient(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof OAuth2ClientAuthenticationToken clientPrincipal &&
            clientPrincipal.isAuthenticated()) {
            return clientPrincipal;
        }
        throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
    }

    private OAuth2AuthenticationException translate(AuthenticationException exception) {
        if (exception instanceof UsernameNotFoundException) {
            return error(OAuth2ErrorCodesExpand.USERNAME_NOT_FOUND, "User credentials are invalid.");
        }
        if (exception instanceof BadCredentialsException) {
            return error(OAuth2ErrorCodesExpand.BAD_CREDENTIALS,
                messages.getMessage("AbstractUserDetailsAuthenticationProvider.badCredentials", "Bad credentials"));
        }
        if (exception instanceof LockedException) {
            return error(OAuth2ErrorCodesExpand.USER_LOCKED,
                messages.getMessage("AbstractUserDetailsAuthenticationProvider.locked", "User account is locked"));
        }
        if (exception instanceof DisabledException) {
            return error(OAuth2ErrorCodesExpand.USER_DISABLE,
                messages.getMessage("AbstractUserDetailsAuthenticationProvider.disabled", "User is disabled"));
        }
        if (exception instanceof AccountExpiredException) {
            return error(OAuth2ErrorCodesExpand.USER_EXPIRED,
                messages.getMessage("AbstractUserDetailsAuthenticationProvider.expired", "User account has expired"));
        }
        if (exception instanceof CredentialsExpiredException) {
            return error(OAuth2ErrorCodesExpand.CREDENTIALS_EXPIRED, messages.getMessage(
                "AbstractUserDetailsAuthenticationProvider.credentialsExpired", "User credentials have expired"));
        }
        if (exception instanceof ScopeException) {
            return error(OAuth2ErrorCodes.INVALID_SCOPE, "Invalid scope.");
        }
        log.warn("OAuth2 user grant rejected category=AUTHENTICATION_REJECTED exception={}",
            exception.getClass().getSimpleName());
        return error(OAuth2ErrorCodes.SERVER_ERROR, "Authentication could not be completed.");
    }

    private OAuth2AuthenticationException error(String code, String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(code, description, null));
    }
}
