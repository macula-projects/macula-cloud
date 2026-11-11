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

package dev.macula.cloud.iam.jackson;

import java.util.List;

import dev.macula.cloud.iam.authentication.email.EmailCodeAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import tools.jackson.core.JsonParser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.node.MissingNode;

/**
 * IAM 认证对象的 Jackson 3 序列化适配。
 *
 * @author felord.cn
 * @since 1.0.0
 */
class EmailCodeAuthenticationTokenDeserializer extends ValueDeserializer<EmailCodeAuthenticationToken> {

    private static final TypeReference<List<GrantedAuthority>> GRANTED_AUTHORITY_LIST =
            new TypeReference<List<GrantedAuthority>>() {
            };

    /**
     * 只恢复固定载体，主体和权限继续走原受控Mapper。
     */
    @Override
    public EmailCodeAuthenticationToken deserialize(JsonParser jp, DeserializationContext ctxt) {
        JsonNode jsonNode = ctxt.readTree(jp);
        boolean authenticated = readJsonNode(jsonNode, "authenticated").asBoolean();
        JsonNode principalNode = readJsonNode(jsonNode, "principal");
        Object principal = getPrincipal(ctxt, principalNode);
        JsonNode credentialsNode = readJsonNode(jsonNode, "code");
        String credentials = getCode(credentialsNode);
        if (authenticated && (credentials != null || !readJsonNode(jsonNode, "clientId").isNull()
                && !readJsonNode(jsonNode, "clientId").isMissingNode())) {
            throw new IllegalArgumentException("邮箱认证主体无效");
        }
        List<GrantedAuthority> authorities = ctxt.readTreeAsValue(readJsonNode(jsonNode, "authorities"),
                ctxt.getTypeFactory().constructType(GRANTED_AUTHORITY_LIST));
        EmailCodeAuthenticationToken token = (!authenticated) ? new EmailCodeAuthenticationToken(principal, credentials)
                : new EmailCodeAuthenticationToken(principal, credentials, authorities);
        JsonNode detailsNode = readJsonNode(jsonNode, "details");
        if (detailsNode.isNull() || detailsNode.isMissingNode()) {
            token.setDetails(null);
        } else {
            Object details = ctxt.readTreeAsValue(detailsNode, Object.class);
            token.setDetails(details);
        }
        return token;
    }

    /**
     * 读取可空凭据，不从其他字段回退。
     */
    private String getCode(JsonNode credentialsNode) {
        if (credentialsNode.isNull() || credentialsNode.isMissingNode()) {
            return null;
        }
        return credentialsNode.asString();
    }

    /**
     * 嵌套主体仍由安全Mapper校验。
     */
    private Object getPrincipal(DeserializationContext ctxt, JsonNode principalNode) {
        if (principalNode.isObject()) {
            return ctxt.readTreeAsValue(principalNode, Object.class);
        }
        return principalNode.asString();
    }

    /**
     * 缺失字段保持明确的MissingNode语义。
     */
    private JsonNode readJsonNode(JsonNode jsonNode, String field) {
        return jsonNode.has(field) ? jsonNode.get(field) : MissingNode.getInstance();
    }

}
