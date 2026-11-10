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

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用运行时 JSON 栈验证认证租户的缺省、清空和大整数契约。
 *
 * @author Rain
 * @since 6.1.0
 */
class ApplicationFormTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void distinguishesOmittedFieldFromExplicitNull() {
        ApplicationForm omitted = json.readValue("{}", ApplicationForm.class);
        ApplicationForm cleared = json.readValue("{\"authTenantId\":null}", ApplicationForm.class);
        assertThat(omitted.isAuthTenantIdProvided()).isFalse();
        assertThat(cleared.isAuthTenantIdProvided()).isTrue();
        assertThat(cleared.getAuthTenantId()).isNull();
    }

    @Test
    void acceptsStringLongWithoutExposingPresenceFlag() {
        ApplicationForm form = json.readValue("{\"authTenantId\":\"9007199254740993\"}", ApplicationForm.class);
        assertThat(form.getAuthTenantId()).isEqualTo(9007199254740993L);
        assertThat(form.isAuthTenantIdProvided()).isTrue();
        assertThat(json.writeValueAsString(form)).doesNotContain("authTenantIdProvided");
    }

    @Test
    void cannotSetPresenceFlagFromRequest() {
        ApplicationForm form = json.readValue("{\"authTenantIdProvided\":true}", ApplicationForm.class);
        assertThat(form.isAuthTenantIdProvided()).isFalse();
        ApplicationForm explicit = json.readValue(
            "{\"authTenantId\":7,\"authTenantIdProvided\":false}", ApplicationForm.class);
        assertThat(explicit.isAuthTenantIdProvided()).isTrue();
    }

    @Test
    void responseSerializesAuthTenantAsString() {
        dev.macula.cloud.system.vo.app.ApplicationVO view = new dev.macula.cloud.system.vo.app.ApplicationVO();
        view.setAuthTenantId(9007199254740993L);
        assertThat(json.writeValueAsString(view)).contains("\"authTenantId\":\"9007199254740993\"");
    }
    /** 运行时 JSON 栈区分 IP 缺省与清空，内部标记不接受请求覆盖。 */
    @Test
    void distinguishesIpPresenceAndNormalizesLegacyUrls() {
        ApplicationForm omitted = json.readValue("{}", ApplicationForm.class);
        assertThat(omitted.isAllowIpsProvided()).isFalse();
        ApplicationForm cleared = json.readValue("{\"allowIps\":null,\"allowIpsProvided\":false}", ApplicationForm.class);
        assertThat(cleared.isAllowIpsProvided()).isTrue();
        cleared.normalizeAccessRules();
        assertThat(cleared.getAllowIps()).isEmpty();
        ApplicationForm configured = json.readValue(
            "{\"accessPath\":\"GET:/a, POST:/b\",\"allowIps\":\"192.0.2.0/24\\r\\n2001:db8::/32\"}", ApplicationForm.class);
        assertThat(configured.isAccessRulesValid()).isTrue();
        configured.normalizeAccessRules();
        assertThat(configured.getAccessPath()).isEqualTo("GET:/a\nPOST:/b");
        assertThat(configured.getAllowIps()).isEqualTo("192.0.2.0/24\n2001:db8::/32");
        assertThat(json.writeValueAsString(configured)).doesNotContain("allowIpsProvided", "accessRulesValid");
        ApplicationForm forged = json.readValue("{\"allowIpsProvided\":true}", ApplicationForm.class);
        assertThat(forged.isAllowIpsProvided()).isFalse();
        configured.setAllowIps("example.invalid");
        assertThat(configured.isAccessRulesValid()).isFalse();
        configured.setAllowIps(null);
        configured.setAccessPath("x".repeat(2001));
        assertThat(configured.isAccessRulesValid()).isFalse();
    }

}
