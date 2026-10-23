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
package dev.macula.cloud.system.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import dev.macula.boot.starter.mp.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * System 侧映射 IAM 客户端表，不依赖 IAM 实现模块。
 *
 * @author Rain
 * @since 2026-10-13
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_oauth2_client")
public class SysOAuth2ClientConfig extends BaseEntity {
    private String clientId;
    private Instant clientIdIssuedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String clientSecret;
    private Instant clientSecretExpiresAt;
    private String clientName;
    private String clientAuthenticationMethods;
    private String authorizationGrantTypes;
    private String redirectUris;
    private String scopes;
    private String externalCodeTypes;
    private String allowedOrigins;
    private String browserLoginProviderTypes;
    private String directLoginProviderType;
    private Boolean requireProofKey;
    private Boolean requireAuthorizationConsent;
    private String jwkSetUrl;
    private String signingAlgorithm;
    private Long authorizationCodeTimeToLive;
    private Long accessTokenTimeToLive;
    private String tokenFormat;
    private Boolean reuseRefreshTokens;
    private Long refreshTokenTimeToLive;
    private String idTokenSignatureAlgorithm;
    private String userType;
    private Long tenantId;
}
