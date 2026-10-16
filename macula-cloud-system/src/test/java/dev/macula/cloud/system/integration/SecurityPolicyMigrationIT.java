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
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 在全新隔离 MySQL 中验证安全策略字典迁移、重复项保护和菜单替换。
 *
 * @author Rain
 * @since 2026-10-16
 */
@EnabledIfEnvironmentVariable(named = "SYSTEM_SECURITY_POLICY_MIGRATION_IT", matches = "true")
class SecurityPolicyMigrationIT {
    @Test
    void rejectsDuplicatesThenMigratesNameValueStorage() throws Exception {
        String url = System.getenv("SPRING_DATASOURCE_URL");
        assertNotNull(url, "必须显式指定全新隔离数据库");
        assertTrue(url.startsWith("jdbc:mysql://"), "只允许 MySQL 隔离数据库");
        String database = URI.create(url.substring("jdbc:".length())).getPath().substring(1);
        assertTrue(database.startsWith("macula_settings_it_"), "只允许 macula_settings_it_ 隔离数据库");
        String username = System.getenv("SPRING_DATASOURCE_USERNAME");
        String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
        assertNotNull(username);
        assertNotNull(password);

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertEquals(0L, count(connection,
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ?", database),
                "测试库必须是空库");
        }

        Flyway toV1 = Flyway.configure().dataSource(url, username, password)
            .locations("classpath:db/migration").target(MigrationVersion.fromVersion("1")).load();
        toV1.migrate();
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            insertItem(connection, 1, "system_security_policy", "sessionMaximum", "1");
            insertItem(connection, 1, "system_security_policy", "sessionMaximum", "2");
            insertTenantMenu(connection, 12, 174, "/system/security-settings");
        }

        Flyway toV4 = Flyway.configure().dataSource(url, username, password)
            .locations("classpath:db/migration").load();
        assertThrows(FlywayException.class, toV4::migrate);

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            try (PreparedStatement cleanup = connection.prepareStatement(
                "DELETE FROM sys_dict_item_tenant WHERE tenant_id = 1 AND type_code = ? AND name = ? AND value = ?")) {
                cleanup.setString(1, "system_security_policy");
                cleanup.setString(2, "sessionMaximum");
                cleanup.setString(3, "2");
                assertEquals(1, cleanup.executeUpdate());
            }
        }
        toV4.repair();
        toV4.migrate();

        String longValue = "default-src 'self'; " + "https://example.test ".repeat(20);
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            insertItem(connection, 2, "system_security_policy", "sessionMaximum", "8");
            insertItem(connection, 1, "system_security_policy", "contentSecurityPolicy", longValue);
            assertEquals("text", string(connection,
                "SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema = ? " +
                    "AND table_name = 'sys_dict_item_tenant' AND column_name = 'value'", database));
            assertEquals(2L, count(connection,
                "SELECT COUNT(*) FROM sys_dict_item_tenant WHERE type_code = 'system_security_policy' " +
                    "AND name = 'sessionMaximum'"));
            assertEquals(longValue, string(connection,
                "SELECT value FROM sys_dict_item_tenant WHERE tenant_id = 1 " +
                    "AND type_code = 'system_security_policy' AND name = 'contentSecurityPolicy'"));
            assertEquals(0L, count(connection,
                "SELECT COUNT(*) FROM sys_menu_tenant WHERE tenant_id = 1 AND path IN " +
                    "('/system/security-settings', '/system/password-policy')"));
            assertEquals(1L, count(connection,
                "SELECT COUNT(*) FROM sys_menu_tenant WHERE tenant_id = 12 " +
                    "AND path = '/system/security-settings'"));
            assertEquals(1L, count(connection,
                "SELECT COUNT(*) FROM sys_menu_tenant WHERE tenant_id = 1 AND path = '/system/security-policy'"));
            assertEquals(1L, count(connection,
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '4' AND success = 1"));
        }
    }

    private static void insertTenantMenu(Connection connection, long tenantId, long parentId, String path)
        throws Exception {
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO sys_menu_tenant " +
                "(tenant_id, parent_id, type, name, path, component, perm, icon, sort, visible, redirect, " +
                "create_by, create_time, last_update_by, last_update_time, full_page) " +
                "VALUES (?, ?, 1, '租户旧安全设置', ?, 'system/securitySettings/index', '', '', 0, 1, '', " +
                "'*SYSADM', NOW(), '*SYSADM', NOW(), 0)")) {
            insert.setLong(1, tenantId);
            insert.setLong(2, parentId);
            insert.setString(3, path);
            assertEquals(1, insert.executeUpdate());
        }
    }

    private static void insertItem(Connection connection, long tenantId, String typeCode, String name, String value)
        throws Exception {
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO sys_dict_item_tenant " +
                "(tenant_id, type_code, name, value, sort, status, defaulted, create_time, last_update_time) " +
                "VALUES (?, ?, ?, ?, 0, 1, 0, NOW(), NOW())")) {
            insert.setLong(1, tenantId);
            insert.setString(2, typeCode);
            insert.setString(3, name);
            insert.setString(4, value);
            assertEquals(1, insert.executeUpdate());
        }
    }

    private static long count(Connection connection, String sql, Object... arguments) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < arguments.length; i++) statement.setObject(i + 1, arguments[i]);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private static String string(Connection connection, String sql, Object... arguments) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < arguments.length; i++) statement.setObject(i + 1, arguments[i]);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }
}
