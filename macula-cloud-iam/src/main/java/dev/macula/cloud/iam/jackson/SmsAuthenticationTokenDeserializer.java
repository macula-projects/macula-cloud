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

import dev.macula.cloud.iam.authentication.sms.SmsAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import tools.jackson.core.JsonParser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.node.MissingNode;

import java.util.List;

/**
 * IAM 认证对象的 Jackson 3 序列化适配。
 *
 * @author felord.cn
 * @since 1.0.0
 */
class SmsAuthenticationTokenDeserializer extends ValueDeserializer<SmsAuthenticationToken> {

    private static final TypeReference<List<GrantedAuthority>> GRANTED_AUTHORITY_LIST =
        new TypeReference<List<GrantedAuthority>>() {
        };

    @Override
    public SmsAuthenticationToken deserialize(JsonParser jp, DeserializationContext ctxt) {
        JsonNode jsonNode = ctxt.readTree(jp);
        boolean authenticated = readJsonNode(jsonNode, "authenticated").asBoolean();
        JsonNode principalNode = readJsonNode(jsonNode, "principal");
        Object principal = getPrincipal(ctxt, principalNode);
        JsonNode credentialsNode = readJsonNode(jsonNode, "code");
        String credentials = getCode(credentialsNode);
        List<GrantedAuthority> authorities = ctxt.readTreeAsValue(readJsonNode(jsonNode, "authorities"),
            ctxt.getTypeFactory().constructType(GRANTED_AUTHORITY_LIST));
        SmsAuthenticationToken token = (!authenticated) ? new SmsAuthenticationToken(principal, credentials)
            : new SmsAuthenticationToken(principal, credentials, authorities);
        JsonNode detailsNode = readJsonNode(jsonNode, "details");
        if (detailsNode.isNull() || detailsNode.isMissingNode()) {
            token.setDetails(null);
        } else {
            Object details = ctxt.readTreeAsValue(detailsNode, Object.class);
            token.setDetails(details);
        }
        return token;
    }

    private String getCode(JsonNode credentialsNode) {
        if (credentialsNode.isNull() || credentialsNode.isMissingNode()) {
            return null;
        }
        return credentialsNode.asString();
    }

    private Object getPrincipal(DeserializationContext ctxt, JsonNode principalNode) {
        if (principalNode.isObject()) {
            return ctxt.readTreeAsValue(principalNode, Object.class);
        }
        return principalNode.asString();
    }

    private JsonNode readJsonNode(JsonNode jsonNode, String field) {
        return jsonNode.has(field) ? jsonNode.get(field) : MissingNode.getInstance();
    }

}
