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
 * 在一次性本地MySQL中验证映射迁移，不修改身份、凭据或未知旧规则。
 * @author Rain
 * @since 6.1.0
 */
@EnabledIfEnvironmentVariable(named = "IDENTITY_BINDING_MAPPING_IT", matches = "true")
class IdentityBindingMappingMigrationIT {
    /** 从空库建立V20再逐条升级，验证未知规则保留和重复启动幂等。 */
    @Test
    void migratesOnlyKnownRulesWithoutChangingIdentityOrSecrets() {
        String url = System.getenv("IAM_SOURCE_IT_URL");
        assertTrue(url != null && url.startsWith("jdbc:mysql://127.0.0.1:"));
        assertTrue(URI.create(url.substring(5)).getPath().startsWith("/macula_identity_binding_it_"));
        var ds = new DriverManagerDataSource(url, System.getenv("IAM_SOURCE_IT_USER"), System.getenv("IAM_SOURCE_IT_PASSWORD"));
        var jdbc = new JdbcTemplate(ds);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class));
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("20").load().migrate();
        String[] rules = {"MEMBER_ID_TO_USERNAME", "EMPLOYEE_NO_TO_USERNAME", "VERIFIED_MOBILE_TO_MOBILE", "unknown-rule"};
        for (int i = 0; i < rules.length; i++) {
            jdbc.update("INSERT INTO sys_identity_provider(id,name,provider_type,source_type,app_id,app_secret,config_json,enabled,visible,version,binding_rule,directory_policy) "
                + "VALUES(?,?,'GITHUB','GITHUB_OAUTH','fixture-id','fixture-secret','{}',0,0,7,?,'REQUIRED')", 95000 + i, "mapping-fixture-" + i, rules[i]);
        }
        String before = jdbc.queryForObject("SELECT GROUP_CONCAT(SHA2(CONCAT_WS('|',id,source_type,app_secret,version,binding_rule,directory_policy),256) ORDER BY id) "
            + "FROM sys_identity_provider WHERE id BETWEEN 95000 AND 95003", String.class);
        var flyway = Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("21").load();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        assertEquals("member_id", jdbc.queryForObject("SELECT binding_source_field FROM sys_identity_provider WHERE id=95000", String.class));
        assertEquals("employee_no", jdbc.queryForObject("SELECT binding_source_field FROM sys_identity_provider WHERE id=95001", String.class));
        assertEquals("mobile", jdbc.queryForObject("SELECT binding_target_field FROM sys_identity_provider WHERE id=95002", String.class));
        assertNull(jdbc.queryForObject("SELECT binding_source_field FROM sys_identity_provider WHERE id=95003", String.class));
        assertEquals(before, jdbc.queryForObject("SELECT GROUP_CONCAT(SHA2(CONCAT_WS('|',id,source_type,app_secret,version,binding_rule,directory_policy),256) ORDER BY id) "
            + "FROM sys_identity_provider WHERE id BETWEEN 95000 AND 95003", String.class));
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }
}
