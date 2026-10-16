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
package dev.macula.cloud.system.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * IAM 接入应用创建和修改表单，密钥仅在创建及重置时使用。
 *
 * @author Rain
 * @since 2026-10-13
 */
@Data
public class OAuth2ClientForm {
    @NotBlank @Size(max = 100)
    private String clientId;
    @NotBlank @Size(max = 200)
    private String clientName;
    private String clientSecret;
    @NotBlank
    private String clientAuthenticationMethods;
    @NotBlank
    private String authorizationGrantTypes;
    private String redirectUris;
    @NotBlank
    private String scopes;
    @NotNull
    private Boolean requireProofKey;
    @NotNull
    private Boolean requireAuthorizationConsent;
    private String jwkSetUrl;
    private String signingAlgorithm;
    private Long authorizationCodeTimeToLive;
    private Long accessTokenTimeToLive;
    private String tokenFormat;
    @NotNull
    private Boolean reuseRefreshTokens;
    private Long refreshTokenTimeToLive;
    private String idTokenSignatureAlgorithm;
    @NotBlank
    private String userType;
    @NotNull
    private Long tenantId;
}
