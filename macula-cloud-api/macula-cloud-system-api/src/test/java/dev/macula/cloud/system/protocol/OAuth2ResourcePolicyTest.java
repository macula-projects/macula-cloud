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
package dev.macula.cloud.system.protocol;

import dev.macula.cloud.system.security.OAuth2ResourcePolicy;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 资源目录和Scope授权不能从同一签发者或Client推断。
 * @author Rain
 * @since 6.1.0
 */
class OAuth2ResourcePolicyTest {
    /** openid不是API许可，未知Scope不能获得audience。 */
    @Test
    void resourcesRequireExplicitScopeMappings() {
        assertEquals(Set.of(OAuth2ResourcePolicy.PROFILE_RESOURCE), OAuth2ResourcePolicy.audiences(
            Set.of("openid", "profile"), Map.of("profile", OAuth2ResourcePolicy.PROFILE_RESOURCE)));
        assertThrows(IllegalArgumentException.class, () -> OAuth2ResourcePolicy.audiences(Set.of("other"), Map.of()));
    }

    /** 配置解析只能接受已知字符串集合，不把损坏存储解释为允许全部。 */
    @Test
    void userTypesRejectMalformedStorage() {
        assertEquals(Set.of(), OAuth2ResourcePolicy.allowedUserTypes("[]"));
        assertEquals(Set.of("employee", "member"),
            OAuth2ResourcePolicy.allowedUserTypes("[\"employee\",\"member\"]"));
        for (String value : new String[] {null, "null", "{}", "[1]", "[\"unknown\"]",
            "[\"employee\",\"employee\"]", "[] []"}) {
            assertThrows(RuntimeException.class, () -> OAuth2ResourcePolicy.allowedUserTypes(value));
        }
    }

}
