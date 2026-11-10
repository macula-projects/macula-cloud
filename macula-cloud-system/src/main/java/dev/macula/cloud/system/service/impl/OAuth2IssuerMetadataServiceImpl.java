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
package dev.macula.cloud.system.service.impl;

import dev.macula.cloud.system.config.OAuth2IssuerMetadataProperties;
import dev.macula.cloud.system.service.OAuth2IssuerMetadataService;
import dev.macula.cloud.system.vo.OAuth2IssuerMetadataVO;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 懒加载可信Discovery，校验Issuer、限制大小并仅缓存成功结果。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
public class OAuth2IssuerMetadataServiceImpl implements OAuth2IssuerMetadataService {
    private static final int MAX_BYTES = 256 * 1024;
    private static final Set<String> ENDPOINTS = Set.of("authorization_endpoint", "token_endpoint",
        "userinfo_endpoint", "jwks_uri", "introspection_endpoint", "revocation_endpoint", "end_session_endpoint");
    private final OAuth2IssuerMetadataProperties properties;
    private final RestClient client;
    private final JsonMapper json = JsonMapper.builder().build();
    private final Map<String, CachedMetadata> cache = new java.util.HashMap<>();

    /** 单协议缓存，另一协议不可用不能污染已有成功结果。 @since 6.1.0 */
    private record CachedMetadata(OAuth2IssuerMetadataVO value, long expiresAt) { }

    /** 只装配依赖，不在启动阶段访问IAM。 */
    public OAuth2IssuerMetadataServiceImpl(OAuth2IssuerMetadataProperties properties,
        @Qualifier("issuerMetadataClient") RestClient client) {
        this.properties = properties;
        this.client = client;
    }

    /** 元数据不可用时返回受控状态；不用过期缓存伪装成功。 */
    @Override
    public synchronized OAuth2IssuerMetadataVO get(String protocol) {
        if (!dev.macula.cloud.system.protocol.ApplicationProtocolRegistry.PROTOCOLS.contains(protocol)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "不支持的协议");
        }
        CachedMetadata cached = cache.get(protocol);
        if (cached != null && System.currentTimeMillis() < cached.expiresAt()) {
            return cached.value();
        }
        try {
            URI configuredSource = validUri(properties.getDiscoveryUrl());
            URI source = metadataSource(configuredSource, protocol);
            validUri(properties.getExpectedIssuer());
            OAuth2IssuerMetadataVO result = client.get().uri(source).exchange((request, response) -> {
                if (!response.getStatusCode().is2xxSuccessful()) {
                    throw new IllegalStateException("Discovery unavailable");
                }
                byte[] bytes = response.getBody().readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) {
                    throw new IllegalStateException("Discovery too large");
                }
                JsonNode root = json.readTree(bytes);
                if (!properties.getExpectedIssuer().equals(root.path("issuer").asText())) {
                    throw new IllegalStateException("Issuer mismatch");
                }
                Map<String, String> endpoints = new LinkedHashMap<>();
                endpoints.put("issuer", properties.getExpectedIssuer());
                // Discovery显示对外Issuer对应地址，抓取地址可能是内网地址，不能暴露为平台入口。
                endpoints.put("discovery", metadataAddress(validUri(properties.getExpectedIssuer()), protocol));
                for (String key : ENDPOINTS) {
                    if ("OAUTH2_1".equals(protocol) && Set.of("userinfo_endpoint", "end_session_endpoint").contains(key)) {
                        continue;
                    }
                    if (root.hasNonNull(key)) {
                        String value = root.get(key).asText();
                        URI endpoint = validUri(value);
                        URI issuer = validUri(properties.getExpectedIssuer());
                        if (!sameOrigin(endpoint, issuer)) { throw new IllegalStateException("Untrusted endpoint"); }
                        endpoints.put(key, value);
                    }
                }
                return new OAuth2IssuerMetadataVO(protocol, true, "", endpoints);
            });
            cache.put(protocol, new CachedMetadata(result, System.currentTimeMillis() + properties.getCacheSeconds() * 1000L));
            return result;
        } catch (Exception ex) {
            return new OAuth2IssuerMetadataVO(protocol, false, "平台接入地址暂不可用，请检查IAM元数据配置后重试", Map.of());
        }
    }

    /** 从服务器固定OIDC抓取地址推导内网OAuth地址，不接受请求提供任意URL。 */
    private URI metadataSource(URI configured, String protocol) {
        if ("OIDC".equals(protocol)) { return configured; }
        String suffix = "/.well-known/openid-configuration";
        if (!configured.getPath().endsWith(suffix)) { throw new IllegalArgumentException("Invalid discovery source"); }
        String issuerPath = configured.getPath().substring(0, configured.getPath().length() - suffix.length());
        return URI.create(configured.getScheme() + "://" + configured.getRawAuthority()
            + "/.well-known/oauth-authorization-server" + issuerPath);
    }

    /** OIDC追加在issuer路径后；RFC8414插入在主机与issuer路径之间。 */
    private String metadataAddress(URI issuer, String protocol) {
        String path = issuer.getRawPath().replaceAll("/$", "");
        String origin = issuer.getScheme() + "://" + issuer.getRawAuthority();
        return "OIDC".equals(protocol) ? origin + path + "/.well-known/openid-configuration"
            : origin + "/.well-known/oauth-authorization-server" + path;
    }

    /** 元数据字段不能把浏览器引向另一来源，默认端口按规范等价处理。 */
    private boolean sameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme()) && left.getHost().equalsIgnoreCase(right.getHost())
            && port(left) == port(right);
    }

    /** 默认HTTP/HTTPS端口仅用于同源比较。 */
    private int port(URI uri) { return uri.getPort() < 0 ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80) : uri.getPort(); }

    /** HTTPS为默认边界；本地HTTP须由服务器显式开启。 */
    private URI validUri(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Missing URI");
        }
        URI uri = URI.create(value);
        if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null
            || !("https".equalsIgnoreCase(uri.getScheme())
            || (properties.isAllowHttp() && "http".equalsIgnoreCase(uri.getScheme())))) {
            throw new IllegalArgumentException("Invalid URI");
        }
        return uri;
    }
}
