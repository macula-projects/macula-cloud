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
package dev.macula.cloud.system.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 只对正常Web/SPA缺省模式，非法和已移除架构必须拒绝。
 * @author Rain
 * @since 6.1.0
 */
class IamWebLoginModeTest {
    /** 精确模式及缺省覆盖所有架构，不静默修正错误配置。 */
    @Test
    void resolvesOnlyValidArchitectureAndModePairs() {
        for (String type : new String[]{"WEB_SERVER", "SPA"}) {
            assertEquals(IamWebLoginMode.BROWSER, IamWebLoginMode.resolve(type, null));
            assertEquals(IamWebLoginMode.IN_APP, IamWebLoginMode.resolve(type, "IN_APP"));
            for (String value : new String[]{"", "browser", " BROWSER", "OTHER"}) {
                assertThrows(IllegalArgumentException.class, () -> IamWebLoginMode.resolve(type, value));
            }
        }
        for (String type : new String[]{"NATIVE", "MINIAPP", "SERVICE"}) {
            assertNull(IamWebLoginMode.resolve(type, null));
            assertThrows(IllegalArgumentException.class, () -> IamWebLoginMode.resolve(type, "BROWSER"));
            assertThrows(IllegalArgumentException.class, () -> IamWebLoginMode.resolve(type, "IN_APP"));
        }
        assertThrows(IllegalArgumentException.class, () -> IamWebLoginMode.resolve("IN_APP", null));
        assertThrows(IllegalArgumentException.class, () -> IamWebLoginMode.resolve("UNKNOWN", null));
    }
}
