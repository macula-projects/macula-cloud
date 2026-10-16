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

import dev.macula.boot.starter.auditlog.annotation.AuditLog;
import dev.macula.cloud.system.form.PasswordPolicyConfigForm;
import dev.macula.cloud.system.form.SecurityBasicConfigForm;
import dev.macula.cloud.system.form.SecurityDefensePolicyConfigForm;
import dev.macula.cloud.system.service.SecurityPolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 统一安全策略协议及旧接口删除测试。
 *
 * @author Rain
 * @since 2026-10-16
 */
class SecurityPolicyControllerTest {
    private final SecurityPolicyService service = mock(SecurityPolicyService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new SecurityPolicyController(service)).build();

    @Test
    void exposesUnifiedReadAndRemovesOldReads() throws Exception {
        mvc.perform(get("/api/v1/security-policy")).andExpect(status().isOk());
        verify(service).get();
        mvc.perform(get("/api/v1/security-settings")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/password-policy")).andExpect(status().isNotFound());
    }

    @Test
    void rejectsClientSuppliedTenantId() throws Exception {
        mvc.perform(put("/api/v1/security-policy/basic")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sessionValidTime":1800,"verifyCodeValidTime":300,
                     "rememberMeValidTime":0,"sessionMaximum":1,"tenantId":12}
                    """))
            .andExpect(status().isBadRequest());
        verify(service, never()).saveBasic(any());
    }

    @Test
    void auditsOnlyChangedKeysAndOutcome() throws Exception {
        assertSafeAudit("saveBasic", SecurityBasicConfigForm.class, "sessionValidTime");
        assertSafeAudit("saveDefense", SecurityDefensePolicyConfigForm.class, "contentSecurityPolicy");
        assertSafeAudit("savePassword", PasswordPolicyConfigForm.class, "password");
    }

    private static void assertSafeAudit(String method, Class<?> formType, String expectedKey) throws Exception {
        AuditLog audit = SecurityPolicyController.class.getDeclaredMethod(method, formType)
            .getAnnotation(AuditLog.class);
        assertNotNull(audit);
        assertTrue(audit.title().contains(expectedKey));
        assertFalse(audit.isSaveRequestData());
        assertFalse(audit.isSaveResponseData());
    }
}
