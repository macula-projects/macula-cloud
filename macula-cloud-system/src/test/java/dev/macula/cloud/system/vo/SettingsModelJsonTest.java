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
package dev.macula.cloud.system.vo;

import dev.macula.cloud.system.form.IamApplicationDraftForm;
import java.util.List;

import dev.macula.cloud.system.controller.IamApplicationController.SecretResetForm;
import dev.macula.cloud.system.form.IdentityProviderForm;
import dev.macula.cloud.system.form.MessageTemplateForm;
import dev.macula.cloud.system.form.MessageTestForm;
import dev.macula.cloud.system.form.IamApplicationForm;
import dev.macula.cloud.system.message.MailTestSender.MailOptions;
import dev.macula.cloud.system.message.TencentSmsTestSender.TencentOptions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 普通 Java Bean 与既有管理端 JSON 字段名保持兼容。
 *
 * @author Rain
 * @since 2026-10-15
 */
class SettingsModelJsonTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void messageAndSecretFormsKeepTheirJsonFields() {
        MessageTemplateForm template = json.readValue("{\"content\":\"hello\",\"enabled\":true}",
            MessageTemplateForm.class);
        assertEquals("hello", template.getContent());
        assertTrue(template.isEnabled());
        MessageTestForm test = json.readValue("{\"scene\":\"MAIL_WELCOME\",\"target\":\"temporary@example.test\",\"variables\":{\"platform\":\"Macula\"}}",
            MessageTestForm.class);
        assertEquals("MAIL_WELCOME", test.getScene());
        assertEquals("Macula", test.getVariables().get("platform"));
        assertEquals("fixture-secret", json.readValue("{\"secret\":\"fixture-secret\"}", SecretResetForm.class).getSecret());
    }

    @Test
    void catalogAndTransportOptionsKeepTheirJsonFields() {
        IdentityProviderCatalogVO catalog = new IdentityProviderCatalogVO(
            List.of(new IdentityProviderCatalogVO.Category("social", "社交")),
            List.of(new IdentityProviderCatalogVO.Provider("GITHUB", "GitHub", "social", "/login/github",
                "https://github.com/settings/developers", "创建应用",
                List.of(new IdentityProviderCatalogVO.Field("clientId", "Client ID", true, false, false, "填写位置")))), List.of());
        String catalogJson = json.writeValueAsString(catalog);
        assertTrue(catalogJson.contains("\"callbackUrl\":\"/login/github\""));
        assertTrue(catalogJson.contains("\"required\":true"));
        assertEquals("GITHUB", json.readValue(catalogJson, IdentityProviderCatalogVO.class)
            .getProviders().get(0).getCode());

        assertEquals(587, json.readValue("{\"port\":587,\"securityMode\":\"SSL\"}",
            MailOptions.class).getPort());
        assertEquals("123", json.readValue("{\"smsSdkAppId\":\"123\"}", TencentOptions.class).getSmsSdkAppId());
    }
    /** 真实Jackson3必须保留省略、显式null与实际值的区别，内部标记不进入响应。 */
    @Test
    void mappingFieldsPreserveJsonPresenceWithoutExposingFlags() {
        var omitted = json.readValue("{}", IdentityProviderForm.class);
        assertFalse(omitted.isBindingSourceFieldPresent());
        var cleared = json.readValue("{\"bindingSourceField\":null,\"bindingTargetField\":null}", IdentityProviderForm.class);
        assertTrue(cleared.isBindingSourceFieldPresent());
        assertTrue(cleared.isBindingTargetFieldPresent());
        assertNull(cleared.getBindingSourceField());
        var configured = json.readValue("{\"bindingSourceField\":\"email\",\"bindingTargetField\":\"email\"}", IdentityProviderForm.class);
        assertEquals("email", configured.getBindingSourceField());
        assertFalse(json.writeValueAsString(configured).contains("FieldPresent"));
        assertThrows(RuntimeException.class, () -> json.readValue("{\"bindingSourceField\":\"email\",\"bindingSourceField\":\"mobile\"}",
            IdentityProviderForm.class));
        assertThrows(RuntimeException.class, () -> json.readValue("{\"arbitraryColumn\":\"password\"}",
            IdentityProviderForm.class));
    }

    /** 请求省略与显式清空不同；顶层及嵌套重复键和未知字段都应被拒绝。 */
    @Test
    void bindingArrayKeepsPresenceAndRejectsAmbiguousJson() {
        var omitted = json.readValue("{}", IdentityProviderForm.class);
        assertFalse(omitted.isBindingMappingsPresent());
        var empty = json.readValue("{\"bindingMappings\":[]}", IdentityProviderForm.class);
        assertTrue(empty.isBindingMappingsPresent());
        assertTrue(empty.getBindingMappings().isEmpty());
        for (String value : List.of("{\"bindingMappings\":[],\"bindingMappings\":[]}",
            "{\"bindingMappings\":[{\"sourceField\":\"mobile\",\"sourceField\":\"email\",\"targetField\":\"email\"}]}",
            "{\"bindingMappings\":[{\"sourceField\":\"mobile\",\"targetField\":\"mobile\",\"sql\":\"id\"}]}")) {
            assertThrows(RuntimeException.class, () -> json.readValue(value, IdentityProviderForm.class));
        }
    }

    /** 模式必须是单值精确字符串；内部缺省与HTTP显式null保持区别。 */
    @Test
    void webModeRequiresSingleExactString() {
        var type=IamApplicationForm.class;
        assertFalse(json.readValue("{}",type).isWebLoginModePresent());
        assertTrue(json.readValue("{\"webLoginMode\":\"IN_APP\"}",type).isWebLoginModePresent());
        for(String value:List.of("null", "true", "1", "[]", "{}", "\"\"", "\"browser\"", "\"OTHER\"")) {
            assertThrows(RuntimeException.class,()->json.readValue("{\"webLoginMode\":"+value+"}",type));
        }
        assertThrows(RuntimeException.class,()->json.readValue("{\"webLoginMode\":\"BROWSER\",\"webLoginMode\":\"IN_APP\"}",type));
    }

    /** 创建白名单显式接收模式；输入要求与完整Form一致，不能用null或重复键绕过。 */
    @Test
    void draftWebModeRequiresSingleExactString() {
        var type = IamApplicationDraftForm.class;
        assertFalse(json.readValue("{}", type).isWebLoginModePresent());
        for (String mode : List.of("BROWSER", "IN_APP")) {
            assertEquals(mode, json.readValue("{\"webLoginMode\":\"" + mode + "\"}", type).getWebLoginMode());
        }
        for (String value : List.of("null", "true", "1", "[]", "{}", "\"\"", "\"browser\"", "\"OTHER\"")) {
            assertThrows(RuntimeException.class, () -> json.readValue("{\"webLoginMode\":" + value + "}", type));
        }
        assertThrows(RuntimeException.class, () -> json.readValue("{\"webLoginMode\":\"BROWSER\",\"webLoginMode\":\"IN_APP\"}", type));
    }

}
