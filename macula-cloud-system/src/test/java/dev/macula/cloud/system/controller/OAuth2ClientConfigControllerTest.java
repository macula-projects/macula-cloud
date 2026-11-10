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
package dev.macula.cloud.system.controller;

import dev.macula.cloud.system.form.OAuth2ClientForm;
import dev.macula.cloud.system.service.OAuth2ClientConfigService;
import dev.macula.cloud.system.service.OAuth2IssuerMetadataService;
import dev.macula.cloud.system.vo.OAuth2IssuerMetadataVO;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 新增字段JSON三态与只读元数据路由契约。
 *
 * @author Rain
 * @since 6.1.0
 */
class OAuth2ClientConfigControllerTest {
    /** JSON缺失/null/空集合必须保持不同语义，内部presence不暴露。 */
    @Test
    void preservesJsonPresence() {
        JsonMapper json = JsonMapper.builder().build();
        OAuth2ClientForm omitted = json.readValue("{}", OAuth2ClientForm.class);
        assertFalse(omitted.isApplicationTypePresent());
        assertFalse(omitted.isHostedLoginMethodsPresent());
        OAuth2ClientForm inherited = json.readValue("{\"hostedLoginMethods\":null}", OAuth2ClientForm.class);
        assertTrue(inherited.isHostedLoginMethodsPresent());
        assertNull(inherited.getHostedLoginMethods());
        OAuth2ClientForm disabled = json.readValue("{\"hostedLoginMethods\":[]}", OAuth2ClientForm.class);
        assertTrue(disabled.isHostedLoginMethodsPresent());
        assertTrue(disabled.getHostedLoginMethods().isEmpty());
        assertFalse(json.writeValueAsString(disabled).contains("hostedLoginMethodsPresent"));
    }

    /** URL参数不成为获取源；接口只调用无参数服务。 */
    @Test
    void metadataCannotAcceptAnArbitrarySource() throws Exception {
        OAuth2IssuerMetadataService metadata = mock(OAuth2IssuerMetadataService.class);
        when(metadata.get("OIDC")).thenReturn(new OAuth2IssuerMetadataVO(false, "未配置", Map.of()));
        MockMvcBuilders.standaloneSetup(new OAuth2ClientConfigController(mock(OAuth2ClientConfigService.class), metadata, mock(dev.macula.cloud.system.service.OAuth2ClientConfigurationCatalogService.class)))
            .build().perform(get("/api/v1/oauth2-clients/metadata").param("url", "http://attacker.invalid"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
    }
}
