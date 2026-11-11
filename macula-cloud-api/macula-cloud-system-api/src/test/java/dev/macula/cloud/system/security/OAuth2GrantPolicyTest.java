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

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 当前执行白名单与退役识别必须分开，不能以历史可读为新授权许可。
 * @author Macula
 * @since 6.1.0
 */
class OAuth2GrantPolicyTest {
    /** 当前只有三个标准授权和唯一第三方扩展。 */
    @Test void supportsOnlyAcceptedGrants() {
        assertEquals(Set.of("authorization_code", "refresh_token", "client_credentials",
            "urn:macula:params:oauth:grant-type:external_code"), OAuth2GrantPolicy.supported());
    }

    /** 新写入同时拒绝已退役和未知值，保留固定退役标识用于旧Refresh拒绝。 */
    @Test void rejectsRetiredAndUnknownWrites() {
        for (String grant : Set.of("password", "sms", "email")) {
            assertTrue(OAuth2GrantPolicy.retired(grant));
            assertThrows(IllegalArgumentException.class, () -> OAuth2GrantPolicy.requireSupported(Set.of(grant)));
        }
        assertThrows(IllegalArgumentException.class, () -> OAuth2GrantPolicy.requireSupported(Set.of("unknown")));
    }
}
