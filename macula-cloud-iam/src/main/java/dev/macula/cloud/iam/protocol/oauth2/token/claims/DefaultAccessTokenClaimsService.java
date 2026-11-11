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
package dev.macula.cloud.iam.protocol.oauth2.token.claims;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import dev.macula.boot.constants.GlobalConstants;
import dev.macula.boot.constants.SecurityConstants;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeClientSettings;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import dev.macula.cloud.system.security.OAuth2ResourcePolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/**
 * 按授权资源构造最小访问声明，统一处理可信租户、权限与版本，不输出用户资料。
 *
 * @author Macula
 * @since 6.1.0
 */
public final class DefaultAccessTokenClaimsService implements AccessTokenClaimsService {
    /**
     * 访问令牌统一绑定资源、配置版本及可信租户；服务令牌不能从请求参数取租户。
     */
    @Override
    public Map<String, Object> create(Authentication principal,
            RegisteredClient client, Set<String> scopes, String grant) {
        Map<String, Object> claims = accessClaims(principal, client.getClientId());
        Set<String> audience;
        try {
            audience = OAuth2ResourcePolicy.audiences(scopes,
                    client.getClientSettings().getSetting(OAuth2ResourcePolicy.PERMISSIONS_SETTING));
        } catch (IllegalArgumentException failure) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_SCOPE);
        }
        claims.put(JwtClaimNames.AUD, new ArrayList<>(audience));
        if (!audience.contains(OAuth2ResourcePolicy.SYSTEM_RESOURCE)) {
            claims.remove(SecurityConstants.AUTHORITIES_KEY);
            claims.remove(SecurityConstants.JWT_DEPTID_KEY);
            claims.remove(SecurityConstants.JWT_DATASCOPE_KEY);
        }
        claims.put(OAuth2ResourcePolicy.AUTHORIZATION_VERSION,
                client.getClientSettings().getSetting(OAuth2ResourcePolicy.AUTHORIZATION_VERSION));
        claims.put(OAuth2ParameterNames.GRANT_TYPE, grant);
        if (AuthorizationGrantType.CLIENT_CREDENTIALS.getValue().equals(grant)) {
            Object tenant = client.getClientSettings().getSetting(
                    ExternalCodeClientSettings.TENANT_ID);
            if (tenant == null) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
            }
            claims.put(GlobalConstants.TENANT_ID_NAME, tenant);
        }
        return claims;
    }

    /**
     * 从受信主体提取基础鉴权声明；这些字段不能写入ID Token。
     */
    private Map<String, Object> accessClaims(Authentication principal,
            String clientId) {
        Map<String, Object> claims = new HashMap<>();
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
                    .filter(authority -> !(authority instanceof FactorGrantedAuthority))
                    .map(GrantedAuthority::getAuthority).collect(Collectors.toList()));
        }
        return claims;
    }
}
