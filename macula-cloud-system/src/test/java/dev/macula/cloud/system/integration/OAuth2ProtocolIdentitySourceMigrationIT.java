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

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 仅在全新隔离MySQL验证V14到V15真实升级，不访问业务数据库。
 * @author Rain
 * @since 6.1.0
 */
@EnabledIfEnvironmentVariable(named = "IAM_SOURCE_MIGRATION_IT", matches = "true")
class OAuth2ProtocolIdentitySourceMigrationIT {
    /** 保留旧配置/密钥/绑定，取消厂商单例索引并验证新稳定用户ID唯一性。 */
    @Test
    void upgradesWithoutGuessingOrMigratingBindings() {
        String url = System.getenv("IAM_SOURCE_IT_URL");
        assertTrue(url != null && url.startsWith("jdbc:mysql://127.0.0.1:"));
        assertTrue(URI.create(url.substring(5)).getPath().startsWith("/macula_identity_binding_it_"));
        String user = System.getenv("IAM_SOURCE_IT_USER");
        String password = System.getenv("IAM_SOURCE_IT_PASSWORD");
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class));
        Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration").target("14").load().migrate();
        jdbc.update("INSERT INTO sys_identity_provider(id,name,provider_type,app_id,app_secret,config_json,enabled,visible) "
            + "VALUES(90001,'migration-source','WECOM','old-corp','opaque-ciphertext-marker','{}',1,1)");
        jdbc.update("INSERT INTO sys_user_identity_binding(provider_type,provider_subject,username) VALUES('WECOM','old-subject','old-user')");
        String clientBefore = jdbc.queryForList("SELECT id,client_id,client_secret,authorization_grant_types,scopes,application_type "
            + "FROM sys_oauth2_client ORDER BY id").toString();
        String sourceBefore = jdbc.queryForList("SELECT id,name,provider_type,app_id,app_secret,config_json FROM sys_identity_provider ORDER BY id").toString();
        String bindingBefore = jdbc.queryForList("SELECT id,provider_type,provider_subject,username FROM sys_user_identity_binding ORDER BY id").toString();

        Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration").target("15").load().migrate();

        assertEquals(clientBefore, jdbc.queryForList("SELECT id,client_id,client_secret,authorization_grant_types,scopes,application_type "
            + "FROM sys_oauth2_client ORDER BY id").toString());
        assertEquals(sourceBefore, jdbc.queryForList("SELECT id,name,provider_type,app_id,app_secret,config_json FROM sys_identity_provider ORDER BY id").toString());
        assertEquals(bindingBefore, jdbc.queryForList("SELECT id,provider_type,provider_subject,username FROM sys_user_identity_binding ORDER BY id").toString());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_oauth2_client WHERE protocol_mode IS NOT NULL", Integer.class));
        assertNull(jdbc.queryForObject("SELECT source_type FROM sys_identity_provider WHERE id=90001", String.class));
        assertFalse(jdbc.queryForObject("SELECT auto_bind_enabled FROM sys_identity_provider WHERE id=90001", Boolean.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_registration_identity_binding", Integer.class));
        jdbc.update("INSERT INTO sys_identity_provider(id,name,provider_type,source_type,app_id,app_secret,config_json,enabled,visible) "
            + "VALUES(90002,'second-source','WECOM','WECOM_SDK','another-corp','test-secret','{}',1,1)");
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sys_identity_provider WHERE provider_type='WECOM' AND enabled=1", Integer.class));
        jdbc.update("INSERT INTO sys_registration_identity_binding(registration_id,identity_kind,identity_value,user_type,user_id) "
            + "VALUES(90002,'MEMBER_ID','Case-ID','employee',91),(90002,'MEMBER_ID','case-id','employee',92)");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO sys_registration_identity_binding"
            + "(registration_id,identity_kind,identity_value,user_type,user_id) VALUES(90002,'MEMBER_ID','Case-ID','employee',93)"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO sys_registration_identity_binding"
            + "(registration_id,identity_kind,identity_value,user_type,user_id) VALUES(90002,'MEMBER_ID','other-id','employee',91)"));
        jdbc.update("DELETE FROM sys_registration_identity_binding WHERE registration_id=90002");
    }
}
