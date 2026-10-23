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
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 在指定的全新隔离库上验证 Flyway 历史升级及旧身份源行保留。
 *
 * @author Rain
 * @since 2026-10-14
 */
@EnabledIfEnvironmentVariable(named = "SYSTEM_MIGRATION_IT", matches = "true")
class IdentityProviderMigrationIT {
    @Test
    void migratesEmptyAndExistingV2SchemaWithoutLosingLegacyIdentity() throws Exception {
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
                "测试库必须是空库，避免重复运行覆盖已有数据");
        }

        Flyway toV2 = Flyway.configure().dataSource(url, username, password)
            .locations("classpath:db/migration").target(MigrationVersion.fromVersion("2")).load();
        toV2.migrate();
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertEquals(2L, count(connection,
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
            try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO sys_identity_provider (name, provider_type, app_id, config_json, enabled, visible) " +
                    "VALUES ('migration-legacy-fixture', 'GITHUB', 'legacy-client', '{}', 1, 1)")) {
                assertEquals(1, insert.executeUpdate());
            }
        }

        Flyway toV3 = Flyway.configure().dataSource(url, username, password)
            .locations("classpath:db/migration").target(MigrationVersion.fromVersion("3")).load();
        toV3.migrate();
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertEquals(3L, count(connection,
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
            assertEquals(1L, count(connection,
                "SELECT COUNT(*) FROM sys_identity_provider WHERE name = 'migration-legacy-fixture' " +
                    "AND app_id = 'legacy-client' AND config_json = '{}'"));
            assertEquals("text", string(connection,
                "SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema = ? " +
                    "AND table_name = 'sys_identity_provider' AND column_name = 'app_secret'", database));
            assertEquals("varchar", string(connection,
                "SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema = ? " +
                    "AND table_name = 'sys_identity_provider' AND column_name = 'remark'", database));
        }
        Flyway toV4 = Flyway.configure().dataSource(url, username, password)
            .locations("classpath:db/migration").target(MigrationVersion.fromVersion("4")).load();
        toV4.migrate();
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertEquals(4L, count(connection,
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
            assertEquals(1L, count(connection,
                "SELECT COUNT(*) FROM sys_identity_provider WHERE name = 'migration-legacy-fixture' " +
                    "AND app_id = 'legacy-client' AND config_json = '{}'"));
        }
        Flyway toV6 = Flyway.configure().dataSource(url, username, password)
            .locations("classpath:db/migration").target(MigrationVersion.fromVersion("6"))
            .cleanDisabled(false).load();
        toV6.migrate();
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertEquals(6L, count(connection,
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
            assertEquals(1L, count(connection,
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? " +
                    "AND table_name = 'sys_user_identity_binding'", database));
            assertEquals(0L, count(connection,
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? " +
                    "AND table_name = 'sys_user_identity_binding' AND column_name IN " +
                    "('tenant_id', 'user_id', 'user_type', 'access_token')", database));
            assertEquals(2L, count(connection,
                "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema = ? " +
                    "AND table_name = 'sys_user_identity_binding' AND non_unique = 0 " +
                    "AND index_name LIKE 'uk_user_identity_%'", database));
            assertEquals(1L, count(connection,
                "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = ? " +
                    "AND table_name = 'sys_identity_provider' " +
                    "AND index_name = 'uk_identity_provider_enabled_type'", database));
            assertEquals("utf8mb4", string(connection,
                "SELECT CHARACTER_SET_NAME FROM information_schema.columns WHERE table_schema = ? " +
                    "AND table_name = 'sys_user_identity_binding' AND column_name = 'username'", database));
            assertEquals(64L, count(connection,
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns WHERE table_schema = ? " +
                    "AND table_name = 'sys_user_identity_binding' AND column_name = 'username'", database));
            execute(connection, "INSERT INTO sys_user_identity_binding " +
                "(provider_type, provider_subject, username) VALUES ('GITHUB', '123', 'alice')");
            assertThrows(SQLException.class, () -> execute(connection,
                "INSERT INTO sys_user_identity_binding (provider_type, provider_subject, username) " +
                    "VALUES ('GITHUB', '123', 'bob')"));
            assertThrows(SQLException.class, () -> execute(connection,
                "INSERT INTO sys_user_identity_binding (provider_type, provider_subject, username) " +
                    "VALUES ('GITHUB', '456', 'alice')"));
            assertThrows(SQLException.class, () -> execute(connection,
                "INSERT INTO sys_identity_provider (name, provider_type, app_id, config_json, enabled, visible) " +
                    "VALUES ('second-enabled-github', 'GITHUB', 'other', '{}', 1, 1)"));
        }

        Flyway toV8 = Flyway.configure().dataSource(url, username, password)
            .locations("classpath:db/migration").target(MigrationVersion.fromVersion("8"))
            .cleanDisabled(false).load();
        toV8.migrate();
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertEquals(8L, count(connection,
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
            assertEquals("text", string(connection,
                "SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema = ? " +
                    "AND table_name = 'sys_identity_provider' AND column_name = 'secret_config_json'", database));
            assertEquals(1L, count(connection,
                "SELECT COUNT(*) FROM sys_identity_provider WHERE name = 'migration-legacy-fixture' " +
                    "AND app_id = 'legacy-client' AND config_json = '{}' AND secret_config_json IS NULL"));
        }

        toV8.clean();
        Flyway toV5 = Flyway.configure().dataSource(url, username, password)
            .locations("classpath:db/migration").target(MigrationVersion.fromVersion("5")).load();
        toV5.migrate();
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            execute(connection, "INSERT INTO sys_identity_provider " +
                "(name, provider_type, app_id, config_json, enabled, visible) VALUES " +
                "('duplicate-a', 'GITHUB', 'a', '{}', 1, 1), " +
                "('duplicate-b', 'GITHUB', 'b', '{}', 1, 1)");
        }
        Flyway duplicateUpgrade = Flyway.configure().dataSource(url, username, password)
            .locations("classpath:db/migration").target(MigrationVersion.fromVersion("6")).load();
        assertThrows(FlywayException.class, duplicateUpgrade::migrate,
            "V6 必须在历史同类型多条启用配置时停止");
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertEquals(0L, count(connection,
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? " +
                    "AND table_name = 'sys_user_identity_binding'", database),
                "迁移预检失败时不得留下后续对象");
            execute(connection, "DELETE FROM sys_identity_provider WHERE name = 'duplicate-b'");
        }
        duplicateUpgrade.repair();
        duplicateUpgrade.migrate();
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertEquals(6L, count(connection,
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
            assertEquals(1L, count(connection,
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? " +
                    "AND table_name = 'sys_user_identity_binding'", database),
                "清理重复数据并 repair 后必须能安全重跑 V6");
        }
    }

    private static long count(Connection connection, String sql, Object... arguments) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < arguments.length; i++) {
                statement.setObject(i + 1, arguments[i]);
            }
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private static String string(Connection connection, String sql, Object... arguments) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < arguments.length; i++) {
                statement.setObject(i + 1, arguments[i]);
            }
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }
}
