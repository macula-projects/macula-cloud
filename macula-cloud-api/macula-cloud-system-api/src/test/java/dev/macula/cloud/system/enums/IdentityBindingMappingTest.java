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
 * 验证六种受控组合、旧合同及手机号证明的能力边界。
 * @author Rain
 * @since 6.1.0
 */
class IdentityBindingMappingTest {
    /** 全局六种组合不代表任何平台都可使用；非法类型不得进入持久层。 */
    @Test
    void validatesCombinationsAndSourceCapabilities() {
        int count = 0;
        for (var source : IdentityBindingSourceField.values()) {
            for (var target : IdentityBindingMapping.targets(source)) {
                IdentityBindingMapping.of(source.value(), target.value()).validateFor(IdentitySourceType.FEISHU_WEB);
                count++;
            }
        }
        assertEquals(6, count);
        IdentityBindingMapping.of("mobile", "mobile").validateFor(IdentitySourceType.WECHAT_MINIAPP);
        assertThrows(IllegalArgumentException.class, () -> IdentityBindingMapping.of("mobile", "username").validateFor(IdentitySourceType.WECHAT_MINIAPP));
        assertThrows(IllegalArgumentException.class, () -> IdentityBindingMapping.of("email", "email").validateFor(IdentitySourceType.GITHUB_OAUTH));
        assertThrows(IllegalArgumentException.class, () -> IdentityBindingMapping.of("member_id", "email"));
        assertThrows(IllegalArgumentException.class, () -> IdentityBindingMapping.of("mobile", null));
        assertThrows(IllegalArgumentException.class, () -> IdentityBindingMapping.of("member_id", "id"));
        assertNull(IdentityBindingMapping.of(null, null));
    }
    /** 旧三种规则可双向表达；新组合不得伪造旧规则。 */
    @Test
    void translatesLegacyRulesWithoutLosingNewMappings() {
        for (var rule : IdentityBindingRule.values()) {
            assertEquals(rule.value(), IdentityBindingMapping.legacy(rule.value()).legacyValue());
        }
        assertNull(IdentityBindingMapping.of("email", "email").legacyValue());
        assertThrows(IllegalArgumentException.class, () -> IdentityBindingMapping.resolve("email", "email", "MEMBER_ID_TO_USERNAME"));
        assertThrows(IllegalArgumentException.class, () -> IdentityBindingMapping.legacy("unknown"));
    }
}
