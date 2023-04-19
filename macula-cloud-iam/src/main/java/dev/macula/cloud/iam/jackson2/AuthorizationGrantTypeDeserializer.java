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

package dev.macula.cloud.iam.jackson2;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.io.IOException;

/**
 * {@code AuthorizationGrantTypeDeserializer} is
 *
 * @author rain
 * @since 2023/4/18 19:25
 */
public class AuthorizationGrantTypeDeserializer extends JsonDeserializer<AuthorizationGrantType> {

    public static SimpleModule generateModule() {
        SimpleModule authGrantModule = new SimpleModule();
        authGrantModule.addDeserializer(AuthorizationGrantType.class, new AuthorizationGrantTypeDeserializer());
        return authGrantModule;
    }

    @Override
    public AuthorizationGrantType deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException, JacksonException {
        Root root = p.readValueAs(Root.class);
        return root != null ? new AuthorizationGrantType(root.value) : new AuthorizationGrantType("");
    }

    private static class Root {
        public String value;
    }
}
