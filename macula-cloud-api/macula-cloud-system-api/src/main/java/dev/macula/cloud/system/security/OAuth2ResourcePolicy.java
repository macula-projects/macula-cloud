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
package dev.macula.cloud.system.security;

import java.util.List;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * OAuth 资源能力的稳定标识与无状态校验，不承担用户/角色授权。
 * @author Rain
 * @since 6.1.0
 */
public final class OAuth2ResourcePolicy {
    public static final String USER_TYPE_CLAIM = "userType";
    public static final Set<String> USER_TYPES = Set.of("member", "employee", "odc", "supplier");

    /** RegisteredClient中受信任的Scope到资源映射设置键。 */
    public static final String PERMISSIONS_SETTING = "macula.resource-permissions";
    public static final String SYSTEM_RESOURCE = "urn:macula:system";
    public static final String PROFILE_RESOURCE = "urn:macula:iam:profile";
    public static final String SYSTEM_SCOPE = "system:access";
    public static final String PROFILE_SCOPE = "profile";
    public static final String OPENID_SCOPE = "openid";
    public static final String AUTHORIZATION_VERSION = "macula_authorization_version";
    public static final String INTROSPECTION_CAPABILITY = "urn:macula:internal:introspection-only";
    public static final String ADMIN_MANAGED_KEY = "platform-admin";
    public static final String GATEWAY_MANAGED_KEY = "platform-gateway";

    /** 仅提供静态领域规则。 */
    private OAuth2ResourcePolicy() { }

    /** Scope 必须已获明确许可；openid 不构成资源授权。 */
    public static Set<String> audiences(Set<String> scopes, Map<String, String> permissions) {
        if (permissions == null || scopes.stream().anyMatch(scope ->
            !OPENID_SCOPE.equals(scope) && !permissions.containsKey(scope))) {
            throw new IllegalArgumentException("Scope缺少明确的资源许可");
        }
        return scopes.stream().filter(scope -> !OPENID_SCOPE.equals(scope))
            .map(permissions::get).filter(value -> value != null && !value.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 严格读取资源服务器用户类型集合；未知、重复或非字符串值拒绝，不能默认允许。
     * @param value 非空的 JSON 数组
     * @return 不可变的稳定用户类型代码集合
     * @throws IllegalArgumentException 配置损坏时抛出
     */
    public static Set<String> allowedUserTypes(String value) {
        if (value == null) {
            throw new IllegalArgumentException("允许的用户类型不能为空");
        }
        List<Object> raw = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build().readValue(value, new TypeReference<List<Object>>() { });
        if (raw == null || raw.stream().anyMatch(item -> !(item instanceof String type) || !USER_TYPES.contains(type))
            || Set.copyOf(raw).size() != raw.size()) {
            throw new IllegalArgumentException("允许的用户类型无效");
        }
        return raw.stream().map(String.class::cast).collect(Collectors.toUnmodifiableSet());
    }

}
