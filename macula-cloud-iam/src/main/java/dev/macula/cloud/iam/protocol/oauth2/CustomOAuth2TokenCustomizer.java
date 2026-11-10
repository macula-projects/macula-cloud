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

import dev.macula.boot.constants.GlobalConstants;
import dev.macula.boot.constants.SecurityConstants;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenClaimsContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

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
            context.getClaims().claims(claims -> {
                claims.putAll(resourceClaims(context.getPrincipal(), context.getRegisteredClient(),
                    context.getAuthorizedScopes(), context.getAuthorizationGrantType().getValue()));
            });
        }
    }

    /** 访问令牌统一绑定资源、配置版本及可信租户；服务令牌不能从请求参数取租户。 */
    static java.util.Map<String, Object> resourceClaims(org.springframework.security.core.Authentication principal,
        org.springframework.security.oauth2.server.authorization.client.RegisteredClient client, java.util.Set<String> scopes, String grant) {
        java.util.Map<String, Object> claims = accessClaims(principal, client.getClientId());
        java.util.Set<String> audience = ResourceAuthorizationPolicy.audiences(client, scopes);
        claims.put("aud", new java.util.ArrayList<>(audience));
        if (!audience.contains(dev.macula.cloud.system.security.OAuth2ResourcePolicy.SYSTEM_RESOURCE)) {
            claims.remove(SecurityConstants.AUTHORITIES_KEY);
            claims.remove(SecurityConstants.JWT_DEPTID_KEY);
            claims.remove(SecurityConstants.JWT_DATASCOPE_KEY);
        }
        claims.put(dev.macula.cloud.system.security.OAuth2ResourcePolicy.AUTHORIZATION_VERSION,
            client.getClientSettings().getSetting(dev.macula.cloud.system.security.OAuth2ResourcePolicy.AUTHORIZATION_VERSION));
        claims.put(OAuth2ParameterNames.GRANT_TYPE, grant);
        if ("client_credentials".equals(grant)) {
            Object tenant = client.getClientSettings().getSetting(
                dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeClientSettings.TENANT_ID);
            if (tenant == null) { throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException("unauthorized_client"); }
            claims.put(GlobalConstants.TENANT_ID_NAME, tenant);
        }
        return claims;
    }

    /** Shared platform access claims for opaque and JWT formats; never add these to ID Tokens. */
    static java.util.Map<String, Object> accessClaims(org.springframework.security.core.Authentication principal,
        String clientId) {
        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        claims.put(OAuth2ParameterNames.CLIENT_ID, clientId);
        if (principal == null) {
            return claims;
        }
        if (principal.getPrincipal() instanceof SysUserDetails user) {
            if (user.getTenantId() != null) {
                claims.put(GlobalConstants.TENANT_ID_NAME, user.getTenantId());
            }
            // 资料通过已授权的Profile/UserInfo读取，不能让JWT Access Token绕过profile Scope。
            claims.put(SecurityConstants.JWT_DATASCOPE_KEY, user.getDataScope());
            if (user.getDeptId() != null) {
                claims.put(SecurityConstants.JWT_DEPTID_KEY, user.getDeptId());
            }
        }
        if (principal.getAuthorities() != null) {
            claims.put(SecurityConstants.AUTHORITIES_KEY, principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toList()));
        }
        return claims;
    }
}
