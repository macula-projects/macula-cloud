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
package dev.macula.cloud.system.protocol;

import lombok.Data;
import java.util.Set;

/**
 * OAuth家族独立策略，时间单位秒；不存储客户端密钥或身份源凭据。
 * @author Rain
 * @since 6.1.0
 */
@Data
public class OAuth2ProtocolSettings implements ApplicationProtocolSettings {
    /** 即使平台ObjectMapper忽略未知字段，协议载荷仍严格拒绝未知项。 */
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("协议配置包含未知字段");
    }

    private String clientAuthenticationMethods;
    private String authorizationGrantTypes;
    private String redirectUris = "";
    private String scopes = "";
    private Set<String> allowedOrigins = Set.of();
    private Boolean requireProofKey;
    private Boolean requireAuthorizationConsent;
    private String jwkSetUrl;
    private String signingAlgorithm;
    private Long authorizationCodeTimeToLive = 300L;
    private Long accessTokenTimeToLive = 3600L;
    private String tokenFormat = "reference";
    private Boolean reuseRefreshTokens = false;
    private Long refreshTokenTimeToLive = 3600L;
    private String idTokenSignatureAlgorithm;
}
