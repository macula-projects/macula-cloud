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

import dev.macula.cloud.iam.protocol.oauth2.grant.base.OAuth2ResourceOwnerBaseAuthenticationToken;
import dev.macula.cloud.system.security.OAuth2LoginGrantTypes;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.util.Map;
import java.util.Set;

/**
 * 邮箱验证码 OAuth2 grant 认证请求。
 *
 * @author Rain
 * @since 6.1.0
 */
public class OAuth2ResourceOwnerEmailAuthenticationToken extends OAuth2ResourceOwnerBaseAuthenticationToken {
    /** 邮箱验证码 grant 的协议值。 */
    public static final String GRANT_TYPE_VALUE = OAuth2LoginGrantTypes.EMAIL;
    /** 邮箱验证码 grant。 */
    public static final AuthorizationGrantType GRANT_TYPE = new AuthorizationGrantType(GRANT_TYPE_VALUE);

    public OAuth2ResourceOwnerEmailAuthenticationToken(Authentication clientPrincipal, Set<String> scopes,
        Map<String, Object> additionalParameters) {
        super(GRANT_TYPE, clientPrincipal, scopes, additionalParameters);
    }
}
