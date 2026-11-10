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
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
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
    private String purpose;
    private String protocol;
    private dev.macula.cloud.system.protocol.OAuth2ProtocolSettings settings;

    private String managedKey;
    private Long authorizationVersion;
    @JsonIgnore
    private java.util.List<dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration> protocolConfigurations = java.util.List.of();
    private String configurationVersion;
    @JsonIgnore
    private String managementAuthenticationProtocol;
    private Long id;
    private String clientId;
    private String clientName;
    @JsonIgnore
    private String protocolMode;
    @JsonIgnore
    private Set<String> enabledProtocols;
    @JsonIgnore
    private String defaultProtocol;
    private String configurationStatus;
    private Map<String, Long> browserProviderBindings = Map.of();
    private Map<String, Long> browserCapabilityBindings;
    private String applicationType;
    private String miniappPlatform;
    private Set<String> hostedLoginMethods;
    private Date clientIdIssuedAt;
    @JsonIgnore
    private String clientAuthenticationMethods;
    @JsonIgnore
    private String authorizationGrantTypes;
    @JsonIgnore
    private String redirectUris;
    @JsonIgnore
    private String scopes;
    private Set<String> externalCodeTypes;
    @JsonIgnore
    private Set<String> allowedOrigins;
    private Map<String, Long> externalProviderBindings;
    private Set<String> browserLoginProviderTypes;
    private String directLoginProviderType;
    @JsonIgnore
    private boolean requireProofKey;
    @JsonIgnore
    private boolean requireAuthorizationConsent;
    @JsonIgnore
    private String jwkSetUrl;
    @JsonIgnore
    private String signingAlgorithm;
    @JsonIgnore
    private Long authorizationCodeTimeToLive;
    @JsonIgnore
    private Long accessTokenTimeToLive;
    @JsonIgnore
    private String tokenFormat;
    @JsonIgnore
    private boolean reuseRefreshTokens;
    @JsonIgnore
    private Long refreshTokenTimeToLive;
    @JsonIgnore
    private String idTokenSignatureAlgorithm;
    private String userType;
    private Long tenantId;
    private boolean secretConfigured;
}
