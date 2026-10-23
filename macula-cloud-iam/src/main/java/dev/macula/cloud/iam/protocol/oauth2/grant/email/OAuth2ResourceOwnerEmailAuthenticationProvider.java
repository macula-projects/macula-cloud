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
package dev.macula.cloud.iam.protocol.oauth2.grant.email;

import dev.macula.cloud.iam.authentication.email.EmailAuthenticationToken;
import dev.macula.cloud.iam.protocol.oauth2.grant.base.OAuth2ResourceOwnerBaseAuthenticationProvider;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.util.Map;
import java.util.Set;

/**
 * 校验 Client 的邮箱 grant 权限并委托邮箱认证 Provider。
 *
 * @author Rain
 * @since 6.1.0
 */
public class OAuth2ResourceOwnerEmailAuthenticationProvider
    extends OAuth2ResourceOwnerBaseAuthenticationProvider<OAuth2ResourceOwnerEmailAuthenticationToken> {

    public OAuth2ResourceOwnerEmailAuthenticationProvider(AuthenticationManager authenticationManager,
        OAuth2AuthorizationService authorizationService, OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator) {
        super(authenticationManager, authorizationService, tokenGenerator);
    }

    @Override
    public AbstractAuthenticationToken buildToken(Map<String, Object> requestParameters,
        RegisteredClient registeredClient) {
        return new EmailAuthenticationToken(
            (String)requestParameters.get(OAuth2ResourceOwnerEmailAuthenticationConverter.EMAIL_PARAMETER),
            (String)requestParameters.get(OAuth2ParameterNames.CODE), registeredClient.getClientId());
    }

    @Override
    public void checkClient(RegisteredClient registeredClient) {
        if (!registeredClient.getAuthorizationGrantTypes().contains(
            OAuth2ResourceOwnerEmailAuthenticationToken.GRANT_TYPE)
            || !registeredClient.getClientAuthenticationMethods().equals(Set.of(ClientAuthenticationMethod.NONE))
            || !registeredClient.getClientSettings().isRequireProofKey()) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2ResourceOwnerEmailAuthenticationToken.class.isAssignableFrom(authentication);
    }

    @Override
    protected boolean allowRefreshToken() {
        return false;
    }

    @Override
    protected boolean supportsOpenIdScope() {
        return false;
    }
}
