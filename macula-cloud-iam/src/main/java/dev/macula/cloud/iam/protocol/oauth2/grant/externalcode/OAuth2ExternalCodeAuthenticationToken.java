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

import java.util.Map;
import java.util.Set;

import dev.macula.cloud.system.enums.ExternalCodeType;
import lombok.Getter;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;

/**
 * 已完成协议参数解析、等待服务端核验的外部凭证 grant 请求。
 *
 * @author Rain
 * @since 6.1.0
 */
@Getter
public class OAuth2ExternalCodeAuthenticationToken extends OAuth2AuthorizationGrantAuthenticationToken {
    public static final AuthorizationGrantType GRANT_TYPE = new AuthorizationGrantType(ExternalCodeType.GRANT_TYPE);

    private final ExternalCodeType codeType;
    private final String code;
    private final Set<String> scopes;

    /**
     * 保存不可变的请求快照；平台 Code 仍须由固定来源服务端核验。
     */
    public OAuth2ExternalCodeAuthenticationToken(Authentication clientPrincipal, Set<String> scopes,
            Map<String, Object> additionalParameters, ExternalCodeType codeType, String code) {
        super(GRANT_TYPE, clientPrincipal, additionalParameters);
        this.scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
        this.codeType = codeType;
        this.code = code;
    }
}
