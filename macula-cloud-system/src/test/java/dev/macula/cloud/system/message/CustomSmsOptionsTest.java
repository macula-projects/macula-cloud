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
package dev.macula.cloud.system.message;

import dev.macula.cloud.system.message.sms.support.CustomSmsOptions;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证认证 Header 和地址在进入出站调用前受到服务端约束。
 * @author Rain
 * @since 6.1.0
 */
class CustomSmsOptionsTest {
    /** 安全配置往返后保持 Header 原始名称和值。 */
    @Test
    void roundTripsCredentials() {
        String encoded = CustomSmsOptions.encodeHeaders(Map.of("Authorization", "Bearer fixture-only"));
        var parsed = CustomSmsOptions.parse("https://sms.example.test/send", encoded);
        assertThat(parsed.headers()).containsEntry("Authorization", "Bearer fixture-only");
        assertThat(parsed.endpoint().getHost()).isEqualTo("sms.example.test");
    }

    /** 拒绝重复、传输控制、注入字符和超出数据库字段长度的凭据。 */
    @Test
    void rejectsUnsafeHeaders() {
        Map<String, String> duplicate = new LinkedHashMap<>();
        duplicate.put("Authorization", "one");
        duplicate.put("authorization", "two");
        for (Map<String, String> value : java.util.List.of(duplicate, Map.of("Host", "elsewhere"),
                Map.of("X-Auth", "one\r\nInjected: two"), Map.of("bad name", "value"),
                Map.of("X-Auth", "v".repeat(2048)), Map.<String, String>of())) {
            assertThatThrownBy(() -> CustomSmsOptions.encodeHeaders(value))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /** 地址不允许凭据、片段或非 HTTP 协议，内网服务保持可用。 */
    @Test
    void rejectsUnsafeEndpointsAndMalformedStoredHeaders() {
        for (String value : java.util.List.of("file:///tmp/test", "http://u:p@example.test/", "https://example.test/#x",
                "/relative", "http://example.test:70000/")) {
            assertThatThrownBy(() -> CustomSmsOptions.endpoint(value)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(CustomSmsOptions.endpoint("http://10.0.0.5/send").getHost()).isEqualTo("10.0.0.5");
        assertThatThrownBy(() -> CustomSmsOptions.parse("https://example.test", "{\"X\":123}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("自定义短信配置无效");
    }
}
