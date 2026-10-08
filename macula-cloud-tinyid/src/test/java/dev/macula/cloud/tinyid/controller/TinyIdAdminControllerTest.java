/*
 * Copyright (c) 2023 Macula
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
package dev.macula.cloud.tinyid.controller;

import dev.macula.boot.starter.auditlog.annotation.AuditLog;
import dev.macula.cloud.tinyid.service.TinyIdManagementService;
import dev.macula.cloud.tinyid.pojo.vo.TinyIdBusinessAggregateVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import org.springframework.http.MediaType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the controller's defense-in-depth authorization and audit metadata.
 *
 * @author Rain
 * @since 6.1.0
 */
@WebMvcTest(value = TinyIdAdminController.class, properties = {
    "spring.cloud.nacos.config.enabled=false", "spring.cloud.nacos.discovery.enabled=false"
})
@Import({TinyIdAdminControllerTest.TestSecurityConfiguration.class, SecurityAutoConfiguration.class,
    ServletWebSecurityAutoConfiguration.class})
class TinyIdAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TinyIdManagementService managementService;

    @Test
    void everyManagementEndpointIsRootOnlyAndBodyAuditIsDisabled() {
        PreAuthorize authorization = TinyIdAdminController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(authorization);
        assertEquals("hasRole('ROOT')", authorization.value());

        for (Method method : TinyIdAdminController.class.getDeclaredMethods()) {
            AuditLog audit = method.getAnnotation(AuditLog.class);
            assertNotNull(audit, method.getName());
            assertFalse(audit.isSaveRequestData(), method.getName());
            assertFalse(audit.isSaveResponseData(), method.getName());
        }
    }

    @Test
    void rejectsAnonymousAndNonRootButAllowsRoot() throws Exception {
        mockMvc.perform(get("/api/v1/admin/data-sources"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/data-sources").with(user("operator").roles("USER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/data-sources").with(user("root").roles("ROOT")))
            .andExpect(status().isOk());
    }

    @Test
    void rejectsUnboundedOrMissingBusinessQueryParameters() throws Exception {
        mockMvc.perform(get("/api/v1/admin/businesses").param("page", "0").param("pageSize", "101")
                .with(user("root").roles("ROOT")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsLegacyDatasourceAndRemainderCreationFields() throws Exception {
        mockMvc.perform(post("/api/v1/admin/businesses")
                .with(user("root").roles("ROOT"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"bizType":"order","step":100,"delta":10,
                     "dataSourceKey":"master","remainder":0}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rootCreatesAggregateBusinessWithoutDatasourceInput() throws Exception {
        when(managementService.createBusiness(any())).thenReturn(
            new TinyIdBusinessAggregateVO("order", 100, 10, "COMPLETE", List.of()));

        mockMvc.perform(post("/api/v1/admin/businesses")
                .with(user("root").roles("ROOT"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"bizType":"order","step":100}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bizType").value("order"))
            .andExpect(jsonPath("$.delta").value(10))
            .andExpect(jsonPath("$.consistencyStatus").value("COMPLETE"));
    }

    @Test
    void rootCanDeleteApplicationsAndBusinesses() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/apps/8")
                .with(user("root").roles("ROOT"))
                .with(csrf()))
            .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/admin/businesses/order")
                .with(user("root").roles("ROOT"))
                .with(csrf()))
            .andExpect(status().isOk());

        verify(managementService).deleteApplication(8L);
        verify(managementService).deleteBusiness("order");
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfiguration {
    }
}
