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

package dev.macula.cloud.iam.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import dev.macula.boot.starter.mp.entity.BaseEntity;
import dev.macula.boot.starter.mp.handler.DurationTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.convert.Jsr310Converters;

import java.time.Duration;
import java.time.Instant;

/**
 * {@code SysOAuth2Client} Oauth2 Client DTO
 *
 * @author rain
 * @since 2023/4/10 17:15
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName(value = "sys_oauth2_client")
public class SysOAuth2Client extends BaseEntity {
    /** 客户端ID */
    private String clientId;
    /** 创建时间 */
    private Instant clientIdIssuedAt;
    /** 客户端密钥 */
    private String clientSecret;
    /** 客户端密钥过期时间 */
    private Instant clientSecretExpiresAt;
    /** 客户端名称 */
    private String clientName;

    /** 客户端认证方式，逗号分隔 */
    private String clientAuthenticationMethods;
    /** 授权方式，逗号分隔 */
    private String authorizationGrantTypes;
    /** 允许的重定向URL，逗号分隔 */
    private String redirectUris;
    /** 授权范围，逗号分隔 */
    private String scopes;

    /** 是否需要proofKe */
    private boolean requireProofKey;
    /** 是否授权确认 */
    private boolean requireAuthorizationConsent;
    /** JWK端点(授权方式为PRIVATE_KEY_JWT时请输入客户端提供的jwkSetUrl) */
    private String jwkSetUrl;
    /** 签名算法(认证方式为CLIENT_SECRET_JWT或PRIVATE_KEY_JWT时需要指定签名算法) */
    private String signingAlgorithm;

    /** CODE的生存时间，单位秒 */
    @TableField(typeHandler = DurationTypeHandler.class)
    private Duration authorizationCodeTimeToLive;
    /** 访问令牌生存时间，单位秒 */
    @TableField(typeHandler = DurationTypeHandler.class)
    private Duration accessTokenTimeToLive;
    /** 令牌格式 */
    private String tokenFormat;
    /** 复用刷新令牌 */
    private boolean reuseRefreshTokens = true;
    /** 刷新令牌生存时间，单位秒 */
    @TableField(typeHandler = DurationTypeHandler.class)
    private Duration refreshTokenTimeToLive;
    /** IDToken 签名算法 */
    private String idTokenSignatureAlgorithm;
}
