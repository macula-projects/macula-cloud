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
package dev.macula.cloud.iam.protocol.oauth2.profile;

import java.security.Principal;
import java.util.Collection;

import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import dev.macula.cloud.iam.vo.ProfileVO;
import dev.macula.cloud.system.security.OAuth2ResourcePolicy;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 从仍有效的用户授权读取最小资料，禁止指定其他用户或租户。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
public final class ProfileService {
    private final OAuth2AuthorizationService authorizations;

    /**
     * 注入具有当前授权版本校验的令牌存储。
     */
    public ProfileService(OAuth2AuthorizationService authorizations) {
        this.authorizations = authorizations;
    }

    /**
     * 资料权限与用户上下文都通过后才返回白名单字段。
     */
    public ProfileVO profile(String token) {
        var authorization = authorizations.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
        var access = authorization == null ? null : authorization.getAccessToken();
        if (access == null || !access.isActive()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        Object aud = access.getClaims() == null ? null : access.getClaims().get("aud");
        if (!access.getToken().getScopes().contains(OAuth2ResourcePolicy.PROFILE_SCOPE)
                || !(aud instanceof Collection<?> values) || !values.contains(OAuth2ResourcePolicy.PROFILE_RESOURCE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        Object principal = authorization.getAttribute(Principal.class.getName());
        if (!(principal instanceof Authentication authentication) ||
                !(authentication.getPrincipal() instanceof SysUserDetails user)
                || user.getTenantId() == null || user.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        Object tenant = access.getClaims().get("tenantId");
        if (tenant == null || !user.getTenantId().toString().equals(tenant.toString())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return new ProfileVO(user.getTenantId() + ":" + user.getUserId(), user.getUsername(), user.getNickname());
    }
}
