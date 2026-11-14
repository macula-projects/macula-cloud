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

package dev.macula.cloud.iam.protocol.oauth2.client;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.ToString;

/**
 * OAuth发布层的客户端投影；不映射数据库表，不承担公共登录策略持久化。
 *
 * @author rain
 * @since 2023/4/10 17:15
 */
@Data
public class OAuth2ClientRegistration {
    /** 仅用于读取公共聚合，不作为OAuth RegisteredClient.id。 */
    private Long id;
    private String purpose;
    private String managedKey;
    private Long authorizationVersion;
    private String resourcePermissions;

    /** 唯一已分配协议的运行视图；资源管理身份没有业务协议。 */
    private List<ApplicationProtocolConfiguration> protocolConfigurations
            = List.of();

    private Map<String, Long> externalProviderBindings = Map.of();

    /** 原公开OAuth标识，不使用应用内部主键替代。 */
    private String clientId;
    /**
     * 创建时间
     */
    private Instant clientIdIssuedAt;
    /**
     * 客户端密钥
     */
    @JsonIgnore
    @ToString.Exclude
    private String clientSecret;
    /**
     * 客户端密钥过期时间
     */
    private Instant clientSecretExpiresAt;
    /**
     * 客户端名称
     */
    private String clientName;
    private String configurationStatus;
    private String browserProviderBindings;
    private String browserCapabilityBindings;

    /**
     * 应用架构类型，历史记录不推断。
     */
    private String applicationType;
    /** Web/SPA 登录模式；历史NULL按BROWSER解释，其他架构不适用。 */
    private String webLoginMode;
    /**
     * 小程序平台。
     */
    private String miniappPlatform;
    /**
     * null继承全局，空字符串关闭全部本地方式。
     */
    private String hostedLoginMethods;

    /**
     * 客户端认证方式，逗号分隔
     */
    private String clientAuthenticationMethods;
    /**
     * 授权方式，逗号分隔
     */
    private String authorizationGrantTypes;
    /**
     * 允许的重定向URL，逗号分隔
     */
    private String redirectUris;
    /**
     * 授权范围，逗号分隔
     */
    private String scopes;
    /**
     * 允许的外部凭证类型，逗号分隔
     */
    private String externalCodeTypes;
    /**
     * 协议验证码端点允许的精确 Origin，逗号分隔
     */
    private String allowedOrigins;
    /**
     * 允许的浏览器第三方登录提供商，逗号分隔
     */
    private String browserLoginProviderTypes;
    /**
     * 进入登录页时直接发起的第三方登录提供商
     */
    private String directLoginProviderType;

    /**
     * 是否需要proofKe
     */
    private boolean requireProofKey;
    /**
     * 是否授权确认
     */
    private boolean requireAuthorizationConsent;
    /**
     * JWK端点(授权方式为PRIVATE_KEY_JWT时请输入客户端提供的jwkSetUrl)
     */
    private String jwkSetUrl;
    /**
     * 签名算法(认证方式为CLIENT_SECRET_JWT或PRIVATE_KEY_JWT时需要指定签名算法)
     */
    private String signingAlgorithm;

    /**
     * CODE的生存时间，单位秒
     */

    private Duration authorizationCodeTimeToLive;
    /**
     * 访问令牌生存时间，单位秒
     */

    private Duration accessTokenTimeToLive;
    /**
     * 令牌格式
     */
    private String tokenFormat;
    /**
     * 复用刷新令牌
     */
    private boolean reuseRefreshTokens = true;
    /**
     * 刷新令牌生存时间，单位秒
     */

    private Duration refreshTokenTimeToLive;
    /**
     * IDToken 签名算法
     */
    private String idTokenSignatureAlgorithm;
    /**
     * 用户类型
     */
    private UserTypeEnum userType;
    private Set<String> allowedUserTypes = Set.of();
    /**
     * 租户ID
     */
    private Long tenantId;
}
