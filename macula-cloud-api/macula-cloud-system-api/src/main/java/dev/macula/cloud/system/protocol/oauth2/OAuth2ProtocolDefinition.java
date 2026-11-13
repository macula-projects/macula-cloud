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
package dev.macula.cloud.system.protocol.oauth2;

import dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration;
import dev.macula.cloud.system.protocol.ApplicationProtocolDefinition;
import dev.macula.cloud.system.protocol.ApplicationProtocolRegistry;
import dev.macula.cloud.system.protocol.IamApplicationProtocolRegistry;
import dev.macula.cloud.system.protocol.OAuth2ApplicationProtocolSettings;
import dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.util.StringUtils;

import dev.macula.cloud.system.enums.ExternalCodeType;
import dev.macula.cloud.system.enums.IamApplicationPurpose;
import dev.macula.cloud.system.enums.IamApplicationType;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 已实现的OAuth2.1/OIDC策略；普通授权与资源服务器管理身份分别校验。
 * 内部资源登记不能因为使用OAuth协议代码而获得业务令牌授予。
 * @author Rain
 * @since 6.1.0
 */
public final class OAuth2ProtocolDefinition implements ApplicationProtocolDefinition<OAuth2ApplicationProtocolSettings> {
    public static final int SCHEMA_VERSION = 2;
    private static final Set<String> PROTOCOLS = Set.of(ApplicationProtocolRegistry.OIDC, ApplicationProtocolRegistry.OAUTH2_1);
    private static final Set<String> RESOURCE_AUTH = Set.of("client_secret_basic", "client_secret_post", "private_key_jwt");
    private static final JsonMapper JSON = JsonMapper.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final String protocol;

    /** 一个显式策略实例只表示一个已实现协议，不接受任意别名。 */
    public OAuth2ProtocolDefinition(String protocol) {
        if (!PROTOCOLS.contains(protocol)) {
            throw new IllegalArgumentException("OAuth协议类型无效");
        }
        this.protocol = protocol;
    }

    /** 当前产品仅登记实际实现的两种协议，未来实现单独加入。 */
    public static IamApplicationProtocolRegistry registry() {
        return new IamApplicationProtocolRegistry(List.of(new OAuth2ProtocolDefinition(ApplicationProtocolRegistry.OIDC),
            new OAuth2ProtocolDefinition(ApplicationProtocolRegistry.OAUTH2_1)));
    }

    /** 返回稳定协议代码。 */
    @Override
    public String protocol() {
        return protocol;
    }

    /** 返回协议目录名称。 */
    @Override
    public String label() {
        return ApplicationProtocolRegistry.OIDC.equals(protocol) ? "OIDC · 用户登录" : "OAuth 2.1 · API授权";
    }

    /** v2独立于历史v1合同。 */
    @Override
    public int schemaVersion() {
        return SCHEMA_VERSION;
    }

    /** 只允许明确的非秘密设置类型。 */
    @Override
    public Class<OAuth2ApplicationProtocolSettings> settingsType() {
        return OAuth2ApplicationProtocolSettings.class;
    }

    /** 严格且有界的类型解析，不允许重复键覆盖先前值。 */
    @Override
    public OAuth2ApplicationProtocolSettings parse(String configuration) {
        if (configuration == null || configuration.getBytes(StandardCharsets.UTF_8).length
            > IamApplicationProtocolRegistry.MAX_CONFIG_BYTES) {
            throw new IllegalArgumentException("协议配置载荷无效或超出限制");
        }
        return JSON.readValue(configuration, settingsType());
    }

    /** 序列化只包含策略，不加入客户端凭据。 */
    @Override
    public String serialize(OAuth2ApplicationProtocolSettings settings) {
        return JSON.writeValueAsString(settings);
    }

    /** 按用途隔离授权规则，草稿也不能引入已退役grant。 */
    @Override
    public void validate(OAuth2ApplicationProtocolSettings settings, String applicationType, String purpose, boolean enabled) {
        if (settings == null || settings.getOauth2() == null || settings.getExternalCodeTypes() == null) {
            throw new IllegalArgumentException("OAuth协议设置无效");
        }
        validateOptionalParameters(settings.getOauth2());
        validateOrigins(settings.getOauth2().getAllowedOrigins());
        IamApplicationPurpose applicationPurpose;
        try {
            applicationPurpose = IamApplicationPurpose.valueOf(purpose);
            IamApplicationType.valueOf(applicationType);
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new IllegalArgumentException("应用用途或架构类型无效");
        }
        if (applicationPurpose == IamApplicationPurpose.RESOURCE_SERVER) {
            validateResource(settings);
            return;
        }
        OAuth2ProtocolConfiguration.validateWritable(new ApplicationProtocolConfiguration(protocol,
            ApplicationProtocolRegistry.SCHEMA_VERSION, enabled, settings.getOauth2()), applicationType);
        Set<String> grants = OAuth2ProtocolConfiguration.csv(settings.getOauth2().getAuthorizationGrantTypes());
        Set<String> methods = OAuth2ProtocolConfiguration.csv(settings.getOauth2().getClientAuthenticationMethods());
        if (methods.contains("private_key_jwt")) {
            validateJwks(settings.getOauth2());
        }
        if (enabled && settings.getExternalCodeTypes().contains(ExternalCodeType.FEISHU_APP_CODE)
            && OAuth2ProtocolConfiguration.csv(settings.getOauth2().getRedirectUris()).stream().noneMatch(this::appLinkRedirect)) {
            throw new IllegalArgumentException("飞书AppLink必须登记HTTPS授权回调地址");
        }
        if (!grants.contains(ExternalCodeType.GRANT_TYPE) && !settings.getExternalCodeTypes().isEmpty()
            || enabled && grants.contains(ExternalCodeType.GRANT_TYPE) && settings.getExternalCodeTypes().isEmpty()) {
            throw new IllegalArgumentException("外部Code许可与授权方式不一致");
        }
    }

    /** 资源管理认证需要明确机密方式，禁止业务grant、Scope、来源及回调。 */
    private void validateResource(OAuth2ApplicationProtocolSettings settings) {
        OAuth2ProtocolSettings oauth = settings.getOauth2();
        Set<String> methods = OAuth2ProtocolConfiguration.csv(oauth.getClientAuthenticationMethods());
        if (!ApplicationProtocolRegistry.OAUTH2_1.equals(protocol) || methods.isEmpty()
            || !RESOURCE_AUTH.containsAll(methods)
            || !OAuth2ProtocolConfiguration.csv(oauth.getAuthorizationGrantTypes()).isEmpty()
            || !OAuth2ProtocolConfiguration.csv(oauth.getScopes()).isEmpty()
            || !OAuth2ProtocolConfiguration.csv(oauth.getRedirectUris()).isEmpty()
            || oauth.getAllowedOrigins() == null || !oauth.getAllowedOrigins().isEmpty()
            || !settings.getExternalCodeTypes().isEmpty()) {
            throw new IllegalArgumentException("资源身份不允许业务授权配置");
        }
        if (methods.contains("private_key_jwt")) {
            validateJwks(oauth);
        }
    }

    /** 管理认证沿用HTTPS JWKS和RSA算法约束，不接受带凭据或片段的地址。 */
    private void validateJwks(OAuth2ProtocolSettings oauth) {
        try {
            URI uri = URI.create(oauth.getJwkSetUrl());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                || oauth.getSigningAlgorithm() == null
                || !Set.of("RS256", "RS384", "RS512").contains(oauth.getSigningAlgorithm())) {
                throw new IllegalArgumentException("资源身份JWKS认证设置无效");
            }
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new IllegalArgumentException("资源身份JWKS认证设置无效");
        }
    }

    /** 资源身份和普通应用共享相同的Secret需求判定，不生成或读取Secret。 */
    @Override
    public boolean requiresSecret(OAuth2ApplicationProtocolSettings settings) {
        if (settings == null || settings.getOauth2() == null) {
            throw new IllegalArgumentException("OAuth协议设置无效");
        }
        return OAuth2ProtocolConfiguration.csv(settings.getOauth2().getClientAuthenticationMethods()).stream()
            .anyMatch(method -> method.startsWith("client_secret_"));
    }
    /** 草稿与启用应用共享可选URL、有效期和格式校验，不借草稿隐藏非法参数。 */
    private void validateOptionalParameters(OAuth2ProtocolSettings form) {
        for (String uriValue : OAuth2ProtocolConfiguration.csv(form.getRedirectUris())) {
            URI uri;
            try {
                uri = URI.create(uriValue);
            } catch (IllegalArgumentException ex) {
                invalid("回调 URI 无效");
                return;
            }
            if (!uri.isAbsolute() || uri.getFragment() != null ||
                !("https".equalsIgnoreCase(uri.getScheme()) ||
                    ("http".equalsIgnoreCase(uri.getScheme()) &&
                        Set.of("localhost", "127.0.0.1").contains(uri.getHost())))) {
                invalid("回调 URI 必须使用 HTTPS");
            }
        }
        if (form.getAuthorizationCodeTimeToLive() != null
            && (form.getAuthorizationCodeTimeToLive() < 30 || form.getAuthorizationCodeTimeToLive() > 3600)) {
            invalid("授权码有效期无效");
        }
        if (form.getAccessTokenTimeToLive() != null
            && (form.getAccessTokenTimeToLive() < 60 || form.getAccessTokenTimeToLive() > 86400)) {
            invalid("访问令牌有效期无效");
        }
        if (form.getRefreshTokenTimeToLive() != null
            && (form.getRefreshTokenTimeToLive() < 300 || form.getRefreshTokenTimeToLive() > 31536000)) {
            invalid("刷新令牌有效期无效");
        }
        if (StringUtils.hasText(form.getTokenFormat())
            && !Set.of("self-contained", "reference").contains(form.getTokenFormat())) {
            invalid("令牌格式无效");
        }
        if (StringUtils.hasText(form.getIdTokenSignatureAlgorithm())
            && !Set.of("RS256", "RS384", "RS512").contains(form.getIdTokenSignatureAlgorithm())) {
            invalid("ID Token 签名算法无效");
        }
        if (StringUtils.hasText(form.getJwkSetUrl())) {
            try {
                URI uri = URI.create(form.getJwkSetUrl());
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) { invalid("JWK URL必须使用HTTPS且不能含凭据或片段"); }
            } catch (IllegalArgumentException ex) { invalid("JWK URL无效"); }
        }
    }

    /** AppLink是平台固定回调，不接受用户信息或片段。 */
    private boolean appLinkRedirect(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                && uri.getRawUserInfo() == null && uri.getRawFragment() == null;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    /** 精确Origin沿用原长度和URL约束，不接受路径、查询或片段。 */
    private void validateOrigins(Set<String> origins) {
        if (origins == null) {
            return;
        }
        if (String.join(",", new TreeSet<>(origins)).length() > 2048) {
            throw new IllegalArgumentException("允许Origin超出长度限制");
        }
        for (String origin : origins) {
            URI uri = URI.create(origin);
            boolean localHttp = "http".equalsIgnoreCase(uri.getScheme()) && Set.of("localhost", "127.0.0.1").contains(uri.getHost() == null ? "" : uri.getHost());
            if ((!"https".equalsIgnoreCase(uri.getScheme()) && !localHttp) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !uri.getPath().isEmpty() && !"/".equals(uri.getPath())) {
                throw new IllegalArgumentException("允许Origin必须是精确HTTPS Origin");
            }
        }
    }

    /** 可公开的固定校验消息不包含URI或凭据。 */
    private void invalid(String message) {
        throw new IllegalArgumentException(message);
    }
}
