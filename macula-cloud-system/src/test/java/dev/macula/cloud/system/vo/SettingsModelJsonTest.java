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
import dev.macula.cloud.system.message.CustomSmsTestSender.Options;
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
        MessageTemplateForm template = json.readValue("{\"templateKey\":\"MAIL_TEST\",\"content\":\"hello\",\"enabled\":true}",
            MessageTemplateForm.class);
        assertEquals("MAIL_TEST", template.getTemplateKey());
        assertTrue(template.isEnabled());
        MessageTestForm test = json.readValue("{\"providerId\":7,\"target\":\"temporary@example.test\",\"variables\":{\"platform\":\"Macula\"}}",
            MessageTestForm.class);
        assertEquals("Macula", test.getVariables().get("platform"));
        assertEquals("fixture-secret", json.readValue("{\"secret\":\"fixture-secret\"}", SecretResetForm.class).getSecret());
    }

    @Test
    void catalogAndTransportOptionsKeepTheirJsonFields() {
        IdentityProviderCatalogVO catalog = new IdentityProviderCatalogVO(
            List.of(new IdentityProviderCatalogVO.Category("social", "社交")),
            List.of(new IdentityProviderCatalogVO.Provider("GITHUB", "GitHub", "social", "/login/github",
                "https://github.com/settings/developers", "创建应用",
                List.of(new IdentityProviderCatalogVO.Field("clientId", "Client ID", true, false, false, "填写位置")))));
        String catalogJson = json.writeValueAsString(catalog);
        assertTrue(catalogJson.contains("\"callbackUrl\":\"/login/github\""));
        assertTrue(catalogJson.contains("\"required\":true"));
        assertEquals("GITHUB", json.readValue(catalogJson, IdentityProviderCatalogVO.class)
            .getProviders().get(0).getCode());

        assertEquals(587, json.readValue("{\"port\":587,\"startTls\":true,\"ssl\":false}",
            MailOptions.class).getPort());
        assertEquals("123", json.readValue("{\"smsSdkAppId\":\"123\"}", TencentOptions.class).getSmsSdkAppId());
        assertEquals("Authorization", json.readValue("{\"authHeader\":\"Authorization\"}",
            Options.class).getAuthHeader());
    }
}
