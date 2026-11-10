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

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 在任务专属MySQL中新建随机库，验证完整历史迁移及V18升级；不连接业务库。
 * @author Rain
 * @since 6.1.0
 */
@EnabledIfEnvironmentVariable(named = "IAM_RESOURCE_IT", matches = "true")
class SingleProtocolResourceMigrationIT {
    private JdbcTemplate admin;
    private String database;

    /** 空库执行全部历史迁移，防止共享校验器变化破坏V18。 */
    @Test
    void emptyDatabaseMigratesThroughAllHistoricalVersions() {
        var dataSource = isolated();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        assertEquals("19", jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1", String.class));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
            + "AND table_name='sys_oauth2_client' AND column_name IN ('purpose','managed_key','authorization_version','resource_permissions','introspection_resources')", Integer.class));
    }

    /** 双配置草稿化，身份/摘要原样保留，唯一约束阻止第二协议或重复平台归属。 */
    @Test
    void existingV18PreservesIdentityAndRejectsAmbiguousConfiguration() {
        var dataSource = isolated();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("18").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO sys_oauth2_client(client_id,client_name,configuration_status,application_type,client_secret,tenant_id,user_type,client_authentication_methods,authorization_grant_types,redirect_uris,scopes,create_time,last_update_time) "
            + "VALUES('fixture-dual','fixture','ENABLED','WEB_SERVER','synthetic-hash',1,'employee','client_secret_basic','authorization_code','https://fixture.example/cb','openid',NOW(),NOW())");
        Long id = jdbc.queryForObject("SELECT id FROM sys_oauth2_client WHERE client_id='fixture-dual'", Long.class);
        for (String protocol : java.util.List.of("OIDC", "OAUTH2_1")) {
            jdbc.update("INSERT INTO sys_application_protocol_config(application_id,protocol,schema_version,enabled,configuration) VALUES(?,?,1,1,'{}')", id, protocol);
        }
        var flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
        flyway.migrate();
        assertEquals(id, jdbc.queryForObject("SELECT id FROM sys_oauth2_client WHERE client_id='fixture-dual'", Long.class));
        assertEquals("synthetic-hash", jdbc.queryForObject("SELECT client_secret FROM sys_oauth2_client WHERE id=?", String.class, id));
        assertEquals("DRAFT", jdbc.queryForObject("SELECT configuration_status FROM sys_oauth2_client WHERE id=?", String.class, id));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_application_protocol_config WHERE application_id=?", Integer.class, id));
        jdbc.update("INSERT INTO sys_application_protocol_config(application_id,protocol,schema_version,enabled,configuration) VALUES(?,'OIDC',1,0,'{}')", id);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
            "INSERT INTO sys_application_protocol_config(application_id,protocol,schema_version,enabled,configuration) VALUES(?,'OAUTH2_1',1,0,'{}')", id));
        jdbc.update("UPDATE sys_oauth2_client SET managed_key='platform-fixture' WHERE id=?", id);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
            "INSERT INTO sys_oauth2_client(client_id,client_name,managed_key,client_authentication_methods,authorization_grant_types,redirect_uris,scopes,create_time,last_update_time) VALUES('duplicate','fixture','platform-fixture','','','','',NOW(),NOW())"));
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    /** 仅允许任务启动的回环MySQL端口，所有测试库使用专属前缀。 */
    private DriverManagerDataSource isolated() {
        String url = System.getenv("IAM_RESOURCE_IT_URL");
        assertNotNull(url);
        assertTrue(url.startsWith("jdbc:mysql://127.0.0.1:"));
        assertTrue(url.contains("/mysql?"));
        var root = new DriverManagerDataSource(url, System.getenv("IAM_RESOURCE_IT_USER"), System.getenv("IAM_RESOURCE_IT_PASSWORD"));
        admin = new JdbcTemplate(root);
        database = "macula_identity_binding_it_resource_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        admin.execute("CREATE DATABASE " + database + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        return new DriverManagerDataSource(url.replace("/mysql?", "/" + database + "?"),
            System.getenv("IAM_RESOURCE_IT_USER"), System.getenv("IAM_RESOURCE_IT_PASSWORD"));
    }

    /** 只删除本例创建并校验过前缀的随机库。 */
    @AfterEach
    void cleanup() {
        if (admin != null && database != null && database.matches("macula_identity_binding_it_resource_[a-f0-9]{24}")) {
            admin.execute("DROP DATABASE IF EXISTS " + database);
        }
    }
}
