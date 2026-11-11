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
package dev.macula.cloud.iam.jackson;

import java.security.Principal;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 只读恢复退役邮箱授权的主体载体，不接受HTTP凭据或扩大反序列化类型白名单。
 *
 * @author Macula
 * @since 6.1.0
 */
public final class LegacyEmailPrincipalReader {
    private static final JsonMapper TREE = JsonMapper.builder().build();
    private static final String TYPE = "@class";
    private static final String LEGACY = "dev.macula.cloud.iam.authentication.email.EmailAuthenticationToken";
    private static final Set<String> FIELDS = Set.of(TYPE, "authenticated", "authorities", "clientId",
            "credentials", "details", "name", "principal");

    /**
     * 不创建读取器实例。
     */
    private LegacyEmailPrincipalReader() {
    }

    /**
     * 仅转换已认证且不含凭据的精确旧主体，嵌套类型仍交给原安全Mapper核验。
     *
     * @param attributes 受信授权存储中的attributes JSON
     * @return 无需改写时返回原JSON；否则返回等价的标准主体载体
     * @throws IllegalArgumentException 旧主体形状不符合已捕获的序列化合同
     */
    public static String normalize(String attributes) {
        if (attributes == null || attributes.isBlank()) {
            return attributes;
        }
        JsonNode root = TREE.readTree(attributes);
        JsonNode value = root.get(Principal.class.getName());
        if (value == null || !LEGACY.equals(value.path(TYPE).asString())) {
            return attributes;
        }
        if (!(value instanceof ObjectNode principal) || !principal.path("authenticated").isBoolean()
                || !principal.path("authenticated").asBoolean() || !principal.path("credentials").isNull()
                || !principal.path("clientId").isNull() || !principal.path("principal").isObject()
                || !principal.path("authorities").isArray()) {
            throw new IllegalArgumentException("历史邮箱主体格式无效");
        }
        for (String field : principal.propertyNames()) {
            if (!FIELDS.contains(field)) {
                throw new IllegalArgumentException("历史邮箱主体字段不受支持");
            }
        }
        principal.put(TYPE, "org.springframework.security.authentication.UsernamePasswordAuthenticationToken");
        principal.remove("clientId");
        return TREE.writeValueAsString(root);
    }
}
