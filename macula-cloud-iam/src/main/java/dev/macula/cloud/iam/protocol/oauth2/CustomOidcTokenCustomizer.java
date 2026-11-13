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

import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.OAuth2ExternalCodeAuthenticationToken;
import dev.macula.cloud.iam.protocol.oauth2.token.claims.AccessTokenClaimsService;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import dev.macula.cloud.system.enums.ExternalCodeType;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.util.StringUtils;

/**
 * OIDC ID Token按资料Scope输出身份；Access Token仅携带获准资源所需的鉴权声明。
 *
 * @author rain
 * @since 6.1.0
 */
public final class CustomOidcTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {
    private final AccessTokenClaimsService claimsService;

    /**
     * JWT Access Token复用声明应用服务，ID Token保留OIDC专有资料Scope逻辑。
     */
    public CustomOidcTokenCustomizer(AccessTokenClaimsService claimsService) {
        this.claimsService = claimsService;
    }

    /**
     * Access Token使用统一资源声明，ID Token仅按openid/profile输出身份资料。
     */
    @Override
    public void customize(JwtEncodingContext context) {
        if (OAuth2TokenType.ACCESS_TOKEN.equals(
                context.getTokenType())) {
            context.getClaims().claims(claims -> claims.putAll(claimsService.create(
                    context.getPrincipal(), context.getRegisteredClient(), context.getAuthorizedScopes(),
                    context.getAuthorizationGrantType().getValue())));
            return;
        }
        if (!OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
            return;
        }
        // 这里只关联受控请求与ID Token，不声称厂商Code已与nonce绑定。
        if (ExternalCodeType.GRANT_TYPE.equals(context.getAuthorizationGrantType().getValue())
                && context.getAuthorizationGrant() instanceof OAuth2ExternalCodeAuthenticationToken grant) {
            Object nonce = grant.getAdditionalParameters().get(OidcParameterNames.NONCE);
            if (nonce instanceof String value) {
                context.getClaims().claim(OidcParameterNames.NONCE, value);
            }
        }
        if (context.getPrincipal().getPrincipal() instanceof SysUserDetails user) {
            if (user.getUserId() != null && user.getTenantId() != null) {
                context.getClaims().subject(user.getTenantId() + ":" + user.getUserId());
            }
            if (context.getAuthorizedScopes().contains(OidcScopes.PROFILE)) {
                context.getClaims().claim("preferred_username", user.getUsername());
                if (StringUtils.hasText(user.getNickname())) {
                    context.getClaims().claim("nickname", user.getNickname());
                }
            }
        }
    }
}
