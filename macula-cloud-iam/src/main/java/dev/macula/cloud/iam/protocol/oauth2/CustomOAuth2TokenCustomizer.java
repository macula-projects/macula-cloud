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

import dev.macula.cloud.iam.protocol.oauth2.token.claims.AccessTokenClaimsService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenClaimsContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

/**
 * 不透明Token的框架适配器；声明业务规则统一委托AccessTokenClaimsService。
 *
 * @author Macula
 * @since 6.1.0
 */
public final class CustomOAuth2TokenCustomizer implements OAuth2TokenCustomizer<OAuth2TokenClaimsContext> {
    private final AccessTokenClaimsService claimsService;

    /**
     * 注入与JWT共用的声明服务，不在协议适配器中复制资源权限规则。
     */
    public CustomOAuth2TokenCustomizer(AccessTokenClaimsService claimsService) {
        this.claimsService = claimsService;
    }

    /**
     * 只增强Access Token，不向其他类型写入资源权限声明。
     */
    @Override
    public void customize(OAuth2TokenClaimsContext context) {
        if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            context.getClaims().claims(claims -> claims.putAll(claimsService.create(context.getPrincipal(),
                    context.getRegisteredClient(), context.getAuthorizedScopes(),
                    context.getAuthorizationGrantType().getValue())));
        }
    }
}
