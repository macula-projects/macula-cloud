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

import java.util.Map;
import java.util.Set;

import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.system.security.OAuth2ResourcePolicy;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 从可信客户端许可计算令牌资源，统一校验授权版本。
 *
 * @author Rain
 * @since 6.1.0
 */
public final class ResourceAuthorizationPolicy {
    public static final String PERMISSIONS = OAuth2ResourcePolicy.PERMISSIONS_SETTING;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * 只提供领域安全校验。
     */
    private ResourceAuthorizationPolicy() {
    }

    /**
     * 数据仅由System受信任目录写入；损坏数据默认拒绝。
     */
    public static Map<String, String> permissions(SysOAuth2Client client) {
        if (client == null || client.getResourcePermissions() == null) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
        }
        return JSON.readValue(client.getResourcePermissions(), new TypeReference<Map<String, String>>() {
        });
    }

    /**
     * 使用有效配置快照生成资源声明，用户请求不能自行指定aud。
     */
    public static Set<String> audiences(RegisteredClient client, Set<String> scopes) {
        Map<String, String> permissions = client.getClientSettings().getSetting(PERMISSIONS);
        try {
            return OAuth2ResourcePolicy.audiences(scopes, permissions);
        } catch (IllegalArgumentException error) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_SCOPE);
        }
    }

    /**
     * 所有令牌读取都按当前服务端配置版本判断，不接受缺失绑定的旧授权。
     */
    public static boolean current(OAuth2Authorization authorization, SysOAuth2Client client) {
        Number version = authorization.getAttribute(OAuth2ResourcePolicy.AUTHORIZATION_VERSION);
        Object accessVersion =
                authorization.getAccessToken() == null || authorization.getAccessToken().getClaims() == null ? null
                        : authorization.getAccessToken().getClaims().get(OAuth2ResourcePolicy.AUTHORIZATION_VERSION);
        if (accessVersion instanceof Number issued && (version == null || issued.longValue() != version.longValue())) {
            return false;
        }
        return client != null && "APPLICATION".equals(client.getPurpose()) &&
                "ENABLED".equals(client.getConfigurationStatus())
                && version != null && client.getAuthorizationVersion() != null
                && version.longValue() == client.getAuthorizationVersion();
    }
}
