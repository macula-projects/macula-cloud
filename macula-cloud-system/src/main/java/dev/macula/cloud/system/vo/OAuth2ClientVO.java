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
package dev.macula.cloud.system.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * IAM 接入应用脱敏展示，不包含密钥摘要。
 *
 * @author Rain
 * @since 2026-10-13
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2ClientVO {
    private Long id;
    private String clientId;
    private String clientName;
    private Instant clientIdIssuedAt;
    private String clientAuthenticationMethods;
    private String authorizationGrantTypes;
    private String redirectUris;
    private String scopes;
    private Set<String> externalCodeTypes;
    private Set<String> allowedOrigins;
    private Map<String, Long> externalProviderBindings;
    private Set<String> browserLoginProviderTypes;
    private String directLoginProviderType;
    private boolean requireProofKey;
    private boolean requireAuthorizationConsent;
    private String jwkSetUrl;
    private String signingAlgorithm;
    private Long authorizationCodeTimeToLive;
    private Long accessTokenTimeToLive;
    private String tokenFormat;
    private boolean reuseRefreshTokens;
    private Long refreshTokenTimeToLive;
    private String idTokenSignatureAlgorithm;
    private String userType;
    private Long tenantId;
    private boolean secretConfigured;
}
