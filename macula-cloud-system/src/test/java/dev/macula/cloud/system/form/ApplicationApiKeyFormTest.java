/*
 * Copyright (c) 2026 Macula
 * macula.dev, China
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

package dev.macula.cloud.system.form;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * APIKEY 多行配置、输入校验及省略/清空语义。
 * @author Rain
 * @since 6.1.0
 */
class ApplicationApiKeyFormTest {
    /** 验证 JSON 字段存在性且保留未知身份字段拒绝行为。 */
    @Test
    void distinguishesOmittedAndExplicitIpConfiguration() {
        var json = JsonMapper.builder().build();
        var omitted = json.readValue("{}", ApplicationApiKeyForm.class);
        assertThat(omitted.isAllowIpsProvided()).isFalse();
        var clear = json.readValue("{\"allowIps\":null}", ApplicationApiKeyForm.class);
        assertThat(clear.isAllowIpsProvided()).isTrue();
        clear.normalizeAccessRules();
        assertThat(clear.getAllowIps()).isEmpty();
        assertThat(json.writeValueAsString(clear)).doesNotContain("allowIpsProvided", "accessRulesValid");
        assertThatThrownBy(() -> json.readValue("{\"tenantId\":999}", ApplicationApiKeyForm.class))
            .isInstanceOf(RuntimeException.class);
    }

    /** 多行 IP 和旧 URL 共同校验，错误地址或超长规则不能写入。 */
    @Test
    void validatesAndNormalizesRules() {
        var form = new ApplicationApiKeyForm();
        form.setAccessPath(" GET:/a, POST:/b ");
        form.setAllowIps(" 192.0.2.7\r\n2001:db8::/32 ");
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(form)).isEmpty();
            form.normalizeAccessRules();
            assertThat(form.getAccessPath()).isEqualTo("GET:/a\nPOST:/b");
            assertThat(form.getAllowIps()).isEqualTo("192.0.2.7\n2001:db8::/32");
            for (String invalid : new String[] {"192.0.2.1,192.0.2.2", "::/129", "hostname.invalid", "x".repeat(2001)}) {
                form.setAllowIps(invalid);
                assertThat(validator.validate(form)).isNotEmpty();
                assertThatThrownBy(form::normalizeAccessRules).isInstanceOf(IllegalArgumentException.class);
            }
        }
    }
}
