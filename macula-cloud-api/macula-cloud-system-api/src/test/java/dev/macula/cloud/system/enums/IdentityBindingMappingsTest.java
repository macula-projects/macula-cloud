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

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证集合稳定线格式、旧单项兼容及损坏数据不得回退。
 * @author Rain
 * @since 6.1.0
 */
class IdentityBindingMappingsTest {
    /** 多条规则往返不使用Record字段名或枚举名，旧列NULL才允许兼容读取。 */
    @Test
    void roundTripsStableValuesAndPreservesLegacySingleton() {
        var expected = List.of(IdentityBindingMapping.of("mobile", "mobile"), IdentityBindingMapping.of("email", "email"));
        String encoded = IdentityBindingMappings.encode(expected);
        assertEquals(expected, IdentityBindingMappings.decode(encoded, null, null, null));
        assertEquals("mobile", IdentityBindingMappings.toWire(expected).get(0).get("sourceField"));
        assertEquals(List.of(IdentityBindingMapping.of("mobile", "mobile")),
            IdentityBindingMappings.decode(null, null, null, "VERIFIED_MOBILE_TO_MOBILE"));
        assertThrows(IllegalArgumentException.class, () -> IdentityBindingMappings.decode(encoded, "mobile", "mobile", null));
    }
    /** 重复、未知、半组、数值、嵌套、null和非数组不能成为绑定策略。 */
    @Test
    void rejectsMalformedOrAmbiguousWireData() {
        String[] invalid = {"null", "{}", "[null]", "[{\"sourceField\":\"mobile\"}]",
            "[{\"sourceField\":1,\"targetField\":\"mobile\"}]",
            "[{\"sourceField\":\"mobile\",\"sourceField\":\"email\",\"targetField\":\"email\"}]",
            "[{\"sourceField\":\"mobile\",\"targetField\":\"mobile\",\"sql\":\"id\"}]",
            "[{\"source\":\"MOBILE\",\"target\":\"MOBILE\"}]",
            "[{\"sourceField\":\"mobile\",\"targetField\":\"mobile\"},{\"sourceField\":\"mobile\",\"targetField\":\"mobile\"}]"};
        for (String value : invalid) {
            assertThrows(RuntimeException.class, () -> IdentityBindingMappings.decode(value, "member_id", "username", null));
        }
    }
}
