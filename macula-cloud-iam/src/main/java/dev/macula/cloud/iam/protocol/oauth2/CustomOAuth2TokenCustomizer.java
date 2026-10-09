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

/**
 * {@code CustomOAuth2TokenCustomizer} Token增强
 *
 * @author rain
 * @since 2023/4/17 14:26
 */

import dev.macula.boot.constants.SecurityConstants;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenClaimsContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenClaimsSet;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.util.List;
import java.util.stream.Collectors;

/**
 * token 输出增强
 *
 * @author lengleng
 * @since 2022/6/3
 */
public class CustomOAuth2TokenCustomizer implements OAuth2TokenCustomizer<OAuth2TokenClaimsContext> {

    /**
     * Customize the OAuth 2.0 Token attributes.
     *
     * @param context the context containing the OAuth 2.0 Token attributes
     */
    @Override
    public void customize(OAuth2TokenClaimsContext context) {
        if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            context.getClaims().claims(claims -> claims.putAll(accessClaims(
                context.getPrincipal(), context.getRegisteredClient().getClientId())));
        }
    }

    /** Shared platform access claims for opaque and JWT formats; never add these to ID Tokens. */
    static java.util.Map<String, Object> accessClaims(org.springframework.security.core.Authentication principal,
        String clientId) {
        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        claims.put(OAuth2ParameterNames.CLIENT_ID, clientId);
        // Even a privileged login must not turn a demo token into a business credential.
        if (dev.macula.cloud.iam.playground.PlaygroundRegisteredClientRepository.isPlayground(clientId)) return claims;
        if (principal == null) return claims;
        if (principal.getPrincipal() instanceof SysUserDetails user) {
            if (user.getNickname() != null) claims.put(SecurityConstants.JWT_NICKNAME_KEY, user.getNickname());
            claims.put(SecurityConstants.JWT_DATASCOPE_KEY, user.getDataScope());
            if (user.getDeptId() != null) claims.put(SecurityConstants.JWT_DEPTID_KEY, user.getDeptId());
        }
        if (principal.getAuthorities() != null) {
            claims.put(SecurityConstants.AUTHORITIES_KEY, principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toList()));
        }
        return claims;
    }
}
