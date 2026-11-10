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

import dev.macula.cloud.system.controller.OAuth2ClientConfigController.SecretResetForm;
import dev.macula.cloud.system.form.MessageTemplateForm;
import dev.macula.cloud.system.form.MessageTestForm;
import dev.macula.cloud.system.message.MailTestSender.MailOptions;
import dev.macula.cloud.system.message.TencentSmsTestSender.TencentOptions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
        var omitted = json.readValue("{}", dev.macula.cloud.system.form.IdentityProviderForm.class);
        assertFalse(omitted.isBindingSourceFieldPresent());
        var cleared = json.readValue("{\"bindingSourceField\":null,\"bindingTargetField\":null}", dev.macula.cloud.system.form.IdentityProviderForm.class);
        assertTrue(cleared.isBindingSourceFieldPresent());
        assertTrue(cleared.isBindingTargetFieldPresent());
        assertNull(cleared.getBindingSourceField());
        var configured = json.readValue("{\"bindingSourceField\":\"email\",\"bindingTargetField\":\"email\"}", dev.macula.cloud.system.form.IdentityProviderForm.class);
        assertEquals("email", configured.getBindingSourceField());
        assertFalse(json.writeValueAsString(configured).contains("FieldPresent"));
        assertThrows(RuntimeException.class, () -> json.readValue("{\"bindingSourceField\":\"email\",\"bindingSourceField\":\"mobile\"}",
            dev.macula.cloud.system.form.IdentityProviderForm.class));
        assertThrows(RuntimeException.class, () -> json.readValue("{\"arbitraryColumn\":\"password\"}",
            dev.macula.cloud.system.form.IdentityProviderForm.class));
    }

    /** 请求省略与显式清空不同；顶层及嵌套重复键和未知字段都应被拒绝。 */
    @Test
    void bindingArrayKeepsPresenceAndRejectsAmbiguousJson() {
        var omitted = json.readValue("{}", dev.macula.cloud.system.form.IdentityProviderForm.class);
        assertFalse(omitted.isBindingMappingsPresent());
        var empty = json.readValue("{\"bindingMappings\":[]}", dev.macula.cloud.system.form.IdentityProviderForm.class);
        assertTrue(empty.isBindingMappingsPresent());
        assertTrue(empty.getBindingMappings().isEmpty());
        for (String value : java.util.List.of("{\"bindingMappings\":[],\"bindingMappings\":[]}",
            "{\"bindingMappings\":[{\"sourceField\":\"mobile\",\"sourceField\":\"email\",\"targetField\":\"email\"}]}",
            "{\"bindingMappings\":[{\"sourceField\":\"mobile\",\"targetField\":\"mobile\",\"sql\":\"id\"}]}")) {
            assertThrows(RuntimeException.class, () -> json.readValue(value, dev.macula.cloud.system.form.IdentityProviderForm.class));
        }
    }

    /** 转换标记只接受明确Boolean，不接受字符串、null或重复键。 */
    @Test
    void conversionRequiresExplicitBoolean() {
        var type=dev.macula.cloud.system.form.OAuth2ClientForm.class;
        assertFalse(json.readValue("{}",type).isConvertToInApp());
        assertTrue(json.readValue("{\"convertToInApp\":true}",type).isConvertToInApp());
        assertFalse(json.writeValueAsString(json.readValue("{\"convertToInApp\":true}",type)).contains("convertToInApp"));
        for(String value:List.of("{\"convertToInApp\":null}","{\"convertToInApp\":\"true\"}","{\"convertToInApp\":true,\"convertToInApp\":false}")) {
            assertThrows(RuntimeException.class,()->json.readValue(value,type));
        }
    }

}
