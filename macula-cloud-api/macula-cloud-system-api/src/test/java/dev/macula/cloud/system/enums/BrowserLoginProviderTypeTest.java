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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 浏览器登录提供商稳定协议值测试。
 *
 * @author Rain
 * @since 6.1.0
 */
class BrowserLoginProviderTypeTest {

    @Test
    void exposesStableProviderValues() {
        assertArrayEquals(new BrowserLoginProviderType[] {
            BrowserLoginProviderType.GITHUB,
            BrowserLoginProviderType.WECOM,
            BrowserLoginProviderType.FEISHU,
            BrowserLoginProviderType.DINGTALK,
            BrowserLoginProviderType.WECHAT
        }, BrowserLoginProviderType.values());
        assertEquals(BrowserLoginProviderType.WECOM, BrowserLoginProviderType.fromValue("WECOM"));
        assertThrows(IllegalArgumentException.class,
            () -> BrowserLoginProviderType.fromValue("UNKNOWN"));
    }
}
