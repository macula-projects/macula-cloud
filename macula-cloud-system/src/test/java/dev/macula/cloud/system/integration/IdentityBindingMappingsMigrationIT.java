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
package dev.macula.cloud.system.integration;

import java.net.URI;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证一次性本地库的V22升级只新增集合列，不改写旧来源与绑定。
 * @author Rain
 * @since 6.1.0
 */
@EnabledIfEnvironmentVariable(named = "IDENTITY_BINDING_MAPPINGS_IT", matches = "true")
class IdentityBindingMappingsMigrationIT {
    /** 从空库执行历史迁移，再验证V23升级及重复启动不改变旧配置。 */
    @Test
    void upgradesFromV22WithoutRewritingOldMappingsOrSecrets() {
        String url = System.getenv("IAM_SOURCE_IT_URL");
        assertTrue(url != null && url.startsWith("jdbc:mysql://127.0.0.1:"));
        assertTrue(URI.create(url.substring(5)).getPath().startsWith("/macula_identity_binding_it_"));
        var ds = new DriverManagerDataSource(url, System.getenv("IAM_SOURCE_IT_USER"), System.getenv("IAM_SOURCE_IT_PASSWORD"));
        var jdbc = new JdbcTemplate(ds);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class));
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("22").load().migrate();
        jdbc.update("INSERT INTO sys_identity_provider(id,name,provider_type,source_type,app_id,app_secret,config_json,enabled,visible,version,binding_rule,binding_source_field,binding_target_field) "
            + "VALUES(95001,'multi-mapping-fixture','FEISHU','FEISHU_WEB','fixture-app','fixture-secret','{}',0,0,7,'EMPLOYEE_NO_TO_USERNAME','employee_no','username')");
        jdbc.update("INSERT INTO sys_registration_identity_binding(registration_id,identity_kind,identity_value,user_type,user_id) VALUES(95001,'OPEN_ID','fixture-identity','employee',95001)");
        jdbc.update("INSERT INTO sys_oauth2_client_external_provider(client_id,code_type,identity_provider_id) VALUES('multi-mapping-client','FEISHU_APP_CODE',95001)");
        String bindingBefore = jdbc.queryForObject("SELECT SHA2(CONCAT_WS('|',registration_id,identity_kind,identity_value,user_type,user_id),256) FROM sys_registration_identity_binding WHERE registration_id=95001", String.class);
        String clientBefore = jdbc.queryForObject("SELECT SHA2(CONCAT_WS('|',client_id,code_type,identity_provider_id),256) FROM sys_oauth2_client_external_provider WHERE client_id='multi-mapping-client'", String.class);
        String before = jdbc.queryForObject("SELECT SHA2(CONCAT_WS('|',id,source_type,app_secret,version,binding_rule,binding_source_field,binding_target_field),256) FROM sys_identity_provider WHERE id=95001", String.class);
        var flyway = Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("23").load();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        assertNull(jdbc.queryForObject("SELECT binding_mappings_json FROM sys_identity_provider WHERE id=95001", String.class));
        assertEquals(before, jdbc.queryForObject("SELECT SHA2(CONCAT_WS('|',id,source_type,app_secret,version,binding_rule,binding_source_field,binding_target_field),256) FROM sys_identity_provider WHERE id=95001", String.class));
        assertEquals(bindingBefore, jdbc.queryForObject("SELECT SHA2(CONCAT_WS('|',registration_id,identity_kind,identity_value,user_type,user_id),256) FROM sys_registration_identity_binding WHERE registration_id=95001", String.class));
        assertEquals(clientBefore, jdbc.queryForObject("SELECT SHA2(CONCAT_WS('|',client_id,code_type,identity_provider_id),256) FROM sys_oauth2_client_external_provider WHERE client_id='multi-mapping-client'", String.class));
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }
}
