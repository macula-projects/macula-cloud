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
package dev.macula.cloud.iam.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 固定 IAM 第三方登录类型及路径的对外兼容值。
 *
 * @author Rain
 * @since 6.1.0
 */
class ThirdPartyProviderTypeTest {
    @Test
    void keepsExistingProviderTypesAndLoginPaths() {
        assertArrayEquals(new ThirdPartyProviderType[] {
            ThirdPartyProviderType.GITHUB, ThirdPartyProviderType.WECOM
        }, ThirdPartyProviderType.values());
        assertEquals("GITHUB", ThirdPartyProviderType.GITHUB.providerType());
        assertEquals("/login/github", ThirdPartyProviderType.GITHUB.loginPath());
        assertEquals("WECOM", ThirdPartyProviderType.WECOM.providerType());
        assertEquals("/login/wecom", ThirdPartyProviderType.WECOM.loginPath());
    }
}
