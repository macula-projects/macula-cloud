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

import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeClientSettings;
import dev.macula.cloud.system.enums.IamApplicationType;
import java.util.Set;
import java.util.function.Consumer;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationValidator;

/**
 * 保留框架回调/Scope校验，并对全部授权码强制S256及明确协议语义。
 *
 * @author Rain
 * @since 6.1.0
 */
public class OAuth2ClientProtocolValidator implements Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> {
    public static final String ENABLED_PROTOCOLS = "macula.enabled-protocols";
    public static final String DEFAULT_PROTOCOL = "macula.default-protocol";

    /**
     * 先使用框架安全校验，错误不能重定向到未经校验的URI。
     */
    @Override
    public void accept(OAuth2AuthorizationCodeRequestAuthenticationContext context) {
        OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_REDIRECT_URI_VALIDATOR.accept(context);
        OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_SCOPE_VALIDATOR.accept(context);
        OAuth2AuthorizationCodeRequestAuthenticationToken request = context.getAuthentication();
        String applicationType = context.getRegisteredClient().getClientSettings().getSetting(
                ExternalCodeClientSettings.APPLICATION_TYPE);
        if (applicationType != null &&
                !IamApplicationType.valueOf(applicationType)
                        .supportsGrant("authorization_code", context.getRegisteredClient().getClientSettings().getSetting(
                            ExternalCodeClientSettings.WEB_LOGIN_MODE))) {
            throw rejected(request);
        }
        Object configured = context.getRegisteredClient().getClientSettings().getSetting(ENABLED_PROTOCOLS);
        String protocol = request.getScopes().contains("openid") ? "OIDC" : "OAUTH2_1";
        if (request.getScopes().isEmpty()) {
            throw new OAuth2AuthorizationCodeRequestAuthenticationException(
                    new OAuth2Error(OAuth2ErrorCodes.INVALID_SCOPE), request);
        }
        if (!(configured instanceof Set<?> values) || !values.contains(protocol)) {
            throw rejected(request);
        }
        Object challenge = request.getAdditionalParameters().get("code_challenge");
        Object method = request.getAdditionalParameters().get("code_challenge_method");
        if (!"S256".equals(method) || !(challenge instanceof String text) || !text.matches("[A-Za-z0-9_-]{43}")) {
            throw rejected(request);
        }
    }

    /**
     * 返回协议错误，不包含核验材料或客户端凭据。
     */
    private OAuth2AuthorizationCodeRequestAuthenticationException rejected(
            OAuth2AuthorizationCodeRequestAuthenticationToken request) {
        return new OAuth2AuthorizationCodeRequestAuthenticationException(
                new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST,
                        "Authorization code flow requires explicit protocol and S256 PKCE.", null), request);
    }
}
