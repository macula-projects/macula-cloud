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
package dev.macula.cloud.iam.protocol.oauth2.configuration.impl;

import dev.macula.cloud.iam.protocol.oauth2.configuration.AuthorizationServerExtension;

import dev.macula.cloud.iam.protocol.oauth2.OAuth2ClientProtocolValidator;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationConsentAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationConsentAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationConsentAuthenticationToken;

/**
 * 授权码端点适配：协议校验与本次同意拒绝，不混入Token或客户端认证配置。
 *
 * @author Macula
 * @since 6.1.0
 */
public final class AuthorizationEndpointExtension implements AuthorizationServerExtension {
    private static final String CONSENT_PAGE = "/oauth2/consent";
    private final OAuth2AuthorizationService authorizations;

    /**
     * 只撤销本次被拒绝请求，历史同意由框架继续管理。
     */
    public AuthorizationEndpointExtension(OAuth2AuthorizationService authorizations) {
        this.authorizations = authorizations;
    }

    /**
     * 框架先校验state、principal及回调；扩展只增加协议与显式拒绝规则。
     */
    @Override
    public void configure(OAuth2AuthorizationServerConfigurer server) {
        server.authorizationEndpoint(endpoint -> endpoint.consentPage(CONSENT_PAGE)
                .authenticationProviders(providers -> providers.forEach(provider -> {
                    if (provider instanceof OAuth2AuthorizationCodeRequestAuthenticationProvider code) {
                        code.setAuthenticationValidator(new OAuth2ClientProtocolValidator());
                    }
                    if (provider instanceof OAuth2AuthorizationConsentAuthenticationProvider consent) {
                        consent.setAuthorizationConsentCustomizer(this::denyExplicitRequest);
                    }
                })));
    }

    /**
     * 拒绝当前请求，即使以前已同意，也不能用历史同意覆盖这次明确拒绝。
     */
    private void denyExplicitRequest(OAuth2AuthorizationConsentAuthenticationContext context) {
        OAuth2AuthorizationConsentAuthenticationToken consent = context.getAuthentication();
        if (!"deny".equals(consent.getAdditionalParameters().get("decision"))) {
            return;
        }
        var request = context.getAuthorizationRequest();
        String redirect = request.getRedirectUri() != null ? request.getRedirectUri()
                : context.getRegisteredClient().getRedirectUris().iterator().next();
        authorizations.remove(context.getAuthorization());
        throw new OAuth2AuthorizationCodeRequestAuthenticationException(new OAuth2Error(OAuth2ErrorCodes.ACCESS_DENIED),
                new OAuth2AuthorizationCodeRequestAuthenticationToken(consent.getAuthorizationUri(),
                        consent.getClientId(),
                        (Authentication) consent.getPrincipal(), redirect, request.getState(), request.getScopes(),
                        null));
    }
}
