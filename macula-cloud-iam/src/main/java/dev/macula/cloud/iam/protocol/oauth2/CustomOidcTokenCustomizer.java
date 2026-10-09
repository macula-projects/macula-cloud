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

import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.util.StringUtils;

/**
 * OIDC ID Token 仅添加已授权且真实存在的用户信息，访问令牌不携带该用户资料。
 * @author rain
 * @since 6.1.0
 */
public final class CustomOidcTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {
    @Override
    public void customize(JwtEncodingContext context) {
        if (org.springframework.security.oauth2.server.authorization.OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            context.getClaims().claims(claims -> claims.putAll(CustomOAuth2TokenCustomizer.accessClaims(
                context.getPrincipal(), context.getRegisteredClient().getClientId())));
            return;
        }
        if (!OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) return;
        if (context.getPrincipal().getPrincipal() instanceof SysUserDetails user) {
            if (user.getUserId() != null && user.getTenantId() != null)
                context.getClaims().subject(user.getTenantId() + ":" + user.getUserId());
            if (context.getAuthorizedScopes().contains(OidcScopes.PROFILE)) {
                context.getClaims().claim("preferred_username", user.getUsername());
                if (StringUtils.hasText(user.getNickname())) context.getClaims().claim("nickname", user.getNickname());
            }
        }
    }
}
