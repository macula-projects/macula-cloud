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

package dev.macula.cloud.system.controller;

import dev.macula.boot.starter.auditlog.annotation.AuditLog;
import dev.macula.cloud.system.form.ApplicationApiKeyForm;
import dev.macula.cloud.system.service.SysApplicationApiKeyService;
import dev.macula.cloud.system.vo.app.ApplicationApiKeySecretVO;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 凭据响应不缓存、审计排除敏感响应以及写入长度约束。
 * @author Rain
 * @since 6.1.0
 */
class SysApplicationApiKeyControllerTest {
    @Test
    void createAndViewNeverAllowResponseCachingOrAuditSecret() throws Exception {
        var service = mock(SysApplicationApiKeyService.class);
        var controller = new SysApplicationApiKeyController(service);
        var form = new ApplicationApiKeyForm();
        var secret = new ApplicationApiKeySecretVO("9", "sk-synthetic-no-store");
        when(service.create(34L, form)).thenReturn(secret);
        when(service.secret(34L, 9L)).thenReturn(secret);
        var created = new MockHttpServletResponse();
        assertThat(controller.create(34L, form, created)).isSameAs(secret);
        var viewed = new MockHttpServletResponse();
        assertThat(controller.secret(34L, 9L, viewed)).isSameAs(secret);
        assertThat(created.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(viewed.getHeader("Cache-Control")).isEqualTo("no-store");
        for (var method : SysApplicationApiKeyController.class.getDeclaredMethods()) {
            AuditLog audit = method.getAnnotation(AuditLog.class);
            if (audit != null) {
                assertThat(audit.isSaveRequestData()).isFalse();
                assertThat(audit.isSaveResponseData()).isFalse();
            }
        }
        assertThat(secret.toString()).doesNotContain(secret.getApiKey());
    }

    @Test
    void httpRoutesValidateInputAndReturnSecretOnlyFromExplicitEndpoints() throws Exception {
        var service = mock(SysApplicationApiKeyService.class);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
            .standaloneSetup(new SysApplicationApiKeyController(service)).build();
        when(service.create(org.mockito.ArgumentMatchers.eq(34L), org.mockito.ArgumentMatchers.any()))
            .thenReturn(new ApplicationApiKeySecretVO("9", "sk-synthetic-http-fixture"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .post("/api/v1/app/34/api-keys").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("{\"remark\":\"integration\",\"accessPath\":\"GET:/key/**\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string("Cache-Control", "no-store"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.apiKey")
                .value("sk-synthetic-http-fixture"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .post("/api/v1/app/34/api-keys").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("{\"tenantId\":999,\"apiKey\":\"sk-caller-controlled\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .put("/api/v1/app/34/api-keys/9").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("{\"remark\":\"note\",\"accessPath\":\"\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .delete("/api/v1/app/34/api-keys/9"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        org.mockito.Mockito.verify(service).delete(34L, 9L);
    }

    @Test
    void editableFormHasLengthBoundsAndNoIdentityFields() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var form = new ApplicationApiKeyForm();
            form.setRemark("x".repeat(256));
            form.setAccessPath("x".repeat(2001));
            assertThat(factory.getValidator().validate(form)).hasSize(3);
            assertThat(ApplicationApiKeyForm.class.getDeclaredFields()).extracting("name")
                .containsExactlyInAnyOrder("remark", "accessPath", "allowIps", "allowIpsProvided");
        }
    }
    /** HTTP 校验拒绝错误 IP，合法的 IPv6/CIDR 配置才能进入服务层。 */
    @Test
    void httpRejectsMalformedIpAndAcceptsCidr() throws Exception {
        var service = mock(SysApplicationApiKeyService.class);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
            .standaloneSetup(new SysApplicationApiKeyController(service)).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .put("/api/v1/app/34/api-keys/9").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("{\"allowIps\":\"hostname.invalid\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(service);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .put("/api/v1/app/34/api-keys/9").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("{\"allowIps\":\"2001:db8::/32\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        org.mockito.Mockito.verify(service).update(org.mockito.ArgumentMatchers.eq(34L), org.mockito.ArgumentMatchers.eq(9L),
            org.mockito.ArgumentMatchers.argThat(form -> form.isAllowIpsProvided() && "2001:db8::/32".equals(form.getAllowIps())));
    }

}
