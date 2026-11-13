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
package dev.macula.cloud.iam.protocol.oauth2;

import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2ClientRegistration;
import dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration;
import dev.macula.cloud.system.protocol.ApplicationProtocolRegistry;
import dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

/**
 * 按原始授权或持久化绑定选择协议，不读取默认协议或未验证JWT声明。
 *
 * @author Rain
 * @since 6.1.0
 */
public final class ApplicationProtocolResolver {
    public static final String PROTOCOL_ATTRIBUTE = "macula.authorization.protocol";
    public static final String SCHEMA_ATTRIBUTE = "macula.authorization.protocol-schema";
    public static final String EFFECTIVE_PROTOCOL = "macula.effective-protocol";
    private final OAuth2AuthorizationService authorizations;

    /**
     * 授权服务只依赖基础仓库，不能回调此解析器。
     */
    public ApplicationProtocolResolver(OAuth2AuthorizationService authorizations) {
        this.authorizations = authorizations;
    }

    /**
     * 绑定缺失或Client不匹配时不能按当前默认协议猜测。
     */
    public static String bound(OAuth2Authorization authorization, String clientId) {
        if (authorization == null || !clientId.equals(authorization.getRegisteredClientId())
                || !Integer.valueOf(ApplicationProtocolRegistry.SCHEMA_VERSION)
                .equals(authorization.getAttribute(SCHEMA_ATTRIBUTE))) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }
        String protocol = authorization.getAttribute(PROTOCOL_ATTRIBUTE);
        if (!ApplicationProtocolRegistry.PROTOCOLS.contains(protocol)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }
        return protocol;
    }

    /**
     * Scope的openid决定标准请求含义，绝不由默认协议补Scope。
     */
    public static String requested(Set<String> scopes) {
        return scopes.contains("openid") ? ApplicationProtocolRegistry.OIDC : ApplicationProtocolRegistry.OAUTH2_1;
    }

    /**
     * 管理端点选择调用方配置，目标令牌不参与认证协议选择。
     */
    public ApplicationProtocolConfiguration resolve(OAuth2ClientRegistration client, HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String protocol;
        if (Set.of("/oauth2/introspect", "/oauth2/revoke").contains(path)) {
            return ApplicationProtocolRegistry.management(client.getProtocolConfigurations());
        }
        if ("/oauth2/consent".equals(path)) {
            protocol = bound(find(single(request, "state"), new OAuth2TokenType("state")), client.getClientId());
        } else if ("/oauth2/authorize".equals(path)) {
            if ("POST".equals(request.getMethod())) {
                protocol = bound(find(single(request, "state"), new OAuth2TokenType("state")), client.getClientId());
            } else {
                protocol = requested(OAuth2ProtocolConfiguration.csv(single(request, "scope").replace(' ', ',')));
            }
        } else if ("/userinfo".equals(path)) {
            String bearer = request.getHeader("Authorization");
            if (bearer == null || !bearer.startsWith("Bearer ")) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_TOKEN);
            }
            OAuth2Authorization authorization = find(bearer.substring(7), OAuth2TokenType.ACCESS_TOKEN);
            if (authorization == null || !client.getClientId().equals(authorization.getRegisteredClientId())
                    || !authorization.getAuthorizedScopes().contains("openid")) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_TOKEN);
            }
            protocol = ApplicationProtocolRegistry.OIDC;
        } else if ("/connect/logout".equals(path)) {
            // 仅选择已配置OIDC策略；签名、audience与会话仍由原生logout provider校验。
            protocol = ApplicationProtocolRegistry.OIDC;
        } else if ("/oauth2/token".equals(path)) {
            // 单协议后客户端认证不应先消费/解释别人的code；原生grant与授权版本校验负责所有权。
            single(request, "grant_type");
            protocol = client.getProtocolConfigurations().get(0).protocol();
        } else {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }
        return OAuth2ClientProtocolPolicy.configuration(client, protocol);
    }

    /**
     * 只读取授权记录，不消费code或绕过原生provider的密码学验证。
     */
    private OAuth2Authorization find(String value, OAuth2TokenType type) {
        return authorizations.findByToken(value, type);
    }

    /**
     * 拒绝重复、缺失或空参数，避免不同阶段读到不同协议。
     */
    private String single(HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        if (values == null || values.length != 1 || values[0].isBlank()) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }
        return values[0];
    }
}
