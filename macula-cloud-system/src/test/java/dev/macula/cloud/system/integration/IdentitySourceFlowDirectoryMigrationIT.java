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
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 在新建的隔离MySQL库验证V20仅追加来源合同字段，不修改身份、凭据及旧迁移。
 * @author Rain
 * @since 6.1.0
 */
@EnabledIfEnvironmentVariable(named = "IDENTITY_SOURCE_FLOW_DIRECTORY_IT", matches = "true")
class IdentitySourceFlowDirectoryMigrationIT {
    /** 真实Flyway升级、原数据摘要及重跑幂等；拒绝任何非专用本地库。 */
    @Test
    void addsFieldsWithoutGuessingIdentityDomainsOrChangingSecrets() {
        String url = System.getenv("IAM_SOURCE_IT_URL");
        assertTrue(url != null && url.startsWith("jdbc:mysql://127.0.0.1:"));
        assertTrue(URI.create(url.substring(5)).getPath().startsWith("/macula_identity_binding_it_"));
        var ds = new DriverManagerDataSource(url, System.getenv("IAM_SOURCE_IT_USER"), System.getenv("IAM_SOURCE_IT_PASSWORD"));
        var jdbc = new JdbcTemplate(ds);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class));
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("19").load().migrate();
        jdbc.update("INSERT INTO sys_identity_provider(id,name,provider_type,source_type,app_id,app_secret,config_json,enabled,visible,version) "
            + "VALUES(90999,'migration-fixture','WECOM','WECOM_SDK','fixture-corp','fixture-secret','{\"corpId\":\"fixture-corp\",\"agentId\":\"1001\"}',1,1,7)");
        String before = digest(jdbc);
        var flyway = Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("20").load();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        assertEquals(before, digest(jdbc));
        Map<String,Object> row = jdbc.queryForMap("SELECT identity_flow,directory_policy,organization_id,oauth_redirect_uri,version FROM sys_identity_provider WHERE id=90999");
        assertNull(row.get("identity_flow"));
        assertNull(row.get("organization_id"));
        assertNull(row.get("oauth_redirect_uri"));
        assertEquals("IDENTITY_ONLY", row.get("directory_policy"));
        assertEquals(7L, ((Number) row.get("version")).longValue());
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
            + "AND table_name='sys_identity_provider' AND column_name IN ('identity_flow','directory_policy','organization_id','oauth_redirect_uri')", Integer.class));
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertFalse(jdbc.queryForObject("SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema=DATABASE() "
            + "AND table_name='sys_identity_provider' AND column_name='directory_policy'", String.class).equals("YES"));
    }
    /** 只比较合成凭据和来源字段的摘要，不打印原值。 */
    private String digest(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT SHA2(CONCAT_WS('|',name,provider_type,source_type,app_id,app_secret,config_json,version),256) "
            + "FROM sys_identity_provider WHERE id=90999", String.class);
    }
}
