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
package dev.macula.cloud.iam.protocol.oauth2.grant.externalcode;

import dev.macula.cloud.iam.protocol.oauth2.grant.base.OAuth2ResourceOwnerBaseAuthenticationToken;
import dev.macula.cloud.system.enums.ExternalCodeType;
import lombok.Getter;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.util.Map;
import java.util.Set;

/**
 * 已完成协议参数解析、等待服务端核验的外部凭证 grant 请求。
 *
 * @author Rain
 * @since 6.1.0
 */
@Getter
public class OAuth2ExternalCodeAuthenticationToken extends OAuth2ResourceOwnerBaseAuthenticationToken {
    public static final AuthorizationGrantType GRANT_TYPE = new AuthorizationGrantType(ExternalCodeType.GRANT_TYPE);

    private final ExternalCodeType codeType;
    private final String code;

    public OAuth2ExternalCodeAuthenticationToken(Authentication clientPrincipal, Set<String> scopes,
        Map<String, Object> additionalParameters, ExternalCodeType codeType, String code) {
        super(GRANT_TYPE, clientPrincipal, scopes, additionalParameters);
        this.codeType = codeType;
        this.code = code;
    }
}
