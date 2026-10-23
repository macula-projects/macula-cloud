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
 * 在显式隔离 MySQL 中验证 V8 到 V10 的标识预检、Client 默认拒绝与过时配置清理。
 *
 * @author Rain
 * @since 6.1.0
 */
@EnabledIfEnvironmentVariable(named = "SYSTEM_EXTERNAL_LOGIN_MIGRATION_IT", matches = "true")
class ExternalCredentialLoginMigrationIT {
    @Test
    void validatesHistoricalIdentifiersBeforeCreatingExternalLoginSchema() throws Exception {
        String url = System.getenv("SPRING_DATASOURCE_URL");
        assertNotNull(url, "必须显式指定全新隔离数据库");
        assertTrue(url.startsWith("jdbc:mysql://"), "只允许 MySQL 隔离数据库");
        String database = URI.create(url.substring("jdbc:".length())).getPath().substring(1);
        assertTrue(database.startsWith("macula_external_login_it_"),
            "只允许 macula_external_login_it_ 隔离数据库");
        String username = System.getenv("SPRING_DATASOURCE_USERNAME");
        String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
        assertNotNull(username);
        assertNotNull(password);
        requireEmptyDatabase(url, username, password, database);

        Flyway flyway = flyway(url, username, password, "8");
        flyway.migrate();
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            insertUser(connection, "v9-null-a", null, null);
            insertUser(connection, "v9-null-b", "   ", "  ");
            insertUser(connection, "v9-canonical", "13800000009", "canonical@example.test");
            createExistingMobileUniqueIndex(connection);
        }

        flyway(url, username, password, "9").migrate();
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertEquals(0L, count(connection,
                "SELECT COUNT(*) FROM sys_user WHERE username = 'v9-null-b' "
                    + "AND (mobile IS NOT NULL OR email IS NOT NULL)"));
            assertEquals(2L, count(connection,
                "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics "
                    + "WHERE table_schema = ? AND table_name = 'sys_user' "
                    + "AND index_name IN ('uk_sys_user_mobile', 'uk_sys_user_email')", database));
            assertEquals(2L, count(connection,
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? "
                    + "AND table_name = 'sys_oauth2_client' "
                    + "AND column_name IN ('external_code_types', 'allowed_origins')", database));
            assertEquals(1L, count(connection,
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? "
                    + "AND table_name = 'sys_oauth2_client_external_provider'", database));
            assertEquals(0L, count(connection,
                "SELECT COUNT(*) FROM sys_oauth2_client WHERE external_code_types <> '' OR allowed_origins <> ''"));
            assertThrows(SQLException.class,
                () -> insertUser(connection, "v9-duplicate-mobile", "13800000009", null));
            assertThrows(SQLException.class,
                () -> insertUser(connection, "v9-duplicate-email", null, "canonical@example.test"));
            insertUser(connection, "v9-null-c", null, null);
            prepareV10Fixture(connection);
        }

        Flyway v10 = flyway(url, username, password, "10");
        v10.migrate();
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertEquals(2L, count(connection,
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? "
                    + "AND table_name = 'sys_oauth2_client' "
                    + "AND column_name IN ('browser_login_provider_types', 'direct_login_provider_type')",
                database));
            assertEquals(0L, count(connection,
                "SELECT COUNT(*) FROM sys_oauth2_client WHERE browser_login_provider_types <> '' "
                    + "OR direct_login_provider_type IS NOT NULL"));
            assertEquals(1L, count(connection,
                "SELECT COUNT(*) FROM sys_oauth2_client WHERE client_id = "
                    + "'e2fa7e64-249b-46f0-ae1d-797610e88615' "
                    + "AND external_code_types = 'WECOM_MINIAPP_CODE'"));
            assertEquals(0L, count(connection,
                "SELECT COUNT(*) FROM sys_oauth2_client_external_provider "
                    + "WHERE code_type = 'WECOM_WEBVIEW_CODE'"));
            assertEquals(1L, count(connection,
                "SELECT COUNT(*) FROM sys_oauth2_client_external_provider "
                    + "WHERE code_type = 'WECOM_MINIAPP_CODE'"));
        }

        v10.clean();
        Flyway nonCanonical = flyway(url, username, password, "8");
        nonCanonical.migrate();
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            insertUser(connection, "v9-invalid-email", null, "UPPER@example.test");
        }
        Flyway rejectedUpgrade = flyway(url, username, password, "9");
        assertThrows(FlywayException.class, rejectedUpgrade::migrate);
        assertExternalSchemaAbsent(url, username, password, database);

        rejectedUpgrade.clean();
        Flyway invalidFormatHistory = flyway(url, username, password, "8");
        invalidFormatHistory.migrate();
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            insertUser(connection, "v9-invalid-mobile-format", "not-a-phone", null);
            insertUser(connection, "v9-invalid-email-format", null, "missing-at-sign.test");
        }
        Flyway invalidFormatUpgrade = flyway(url, username, password, "9");
        assertThrows(FlywayException.class, invalidFormatUpgrade::migrate);
        assertExternalSchemaAbsent(url, username, password, database);

        invalidFormatUpgrade.clean();
        Flyway duplicateHistory = flyway(url, username, password, "8");
        duplicateHistory.migrate();
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            insertUser(connection, "v9-duplicate-a", "13800000008", null);
            insertUser(connection, "v9-duplicate-b", "13800000008", null);
        }
        Flyway duplicateUpgrade = flyway(url, username, password, "9");
        assertThrows(FlywayException.class, duplicateUpgrade::migrate);
        assertExternalSchemaAbsent(url, username, password, database);
    }

    private static Flyway flyway(String url, String username, String password, String target) {
        return Flyway.configure().dataSource(url, username, password)
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion(target))
            .cleanDisabled(false)
            .load();
    }

    private static void requireEmptyDatabase(String url, String username, String password, String database)
        throws Exception {
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertEquals(0L, count(connection,
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ?", database),
                "测试库必须是空库，避免覆盖已有数据");
        }
    }

    private static void assertExternalSchemaAbsent(String url, String username, String password, String database)
        throws Exception {
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertEquals(0L, count(connection,
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? "
                    + "AND table_name = 'sys_oauth2_client' "
                    + "AND column_name IN ('external_code_types', 'allowed_origins')", database));
            assertEquals(0L, count(connection,
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? "
                    + "AND table_name = 'sys_oauth2_client_external_provider'", database));
            assertEquals(0L, count(connection,
                "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = ? "
                    + "AND table_name = 'sys_user' "
                    + "AND index_name IN ('uk_sys_user_mobile', 'uk_sys_user_email')", database));
        }
    }

    private static void insertUser(Connection connection, String username, String mobile, String email)
        throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO sys_user (username, nickname, mobile, email, create_time, last_update_time) "
                + "VALUES (?, 'Migration fixture', ?, ?, NOW(), NOW())")) {
            statement.setString(1, username);
            statement.setString(2, mobile);
            statement.setString(3, email);
            statement.executeUpdate();
        }
    }

    private static void createExistingMobileUniqueIndex(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "ALTER TABLE sys_user ADD UNIQUE KEY uk_sys_user_mobile (mobile)")) {
            statement.executeUpdate();
        }
    }

    private static void prepareV10Fixture(Connection connection) throws SQLException {
        String clientId = "e2fa7e64-249b-46f0-ae1d-797610e88615";
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE sys_oauth2_client SET external_code_types = ? WHERE client_id = ?")) {
            statement.setString(1, "WECOM_WEBVIEW_CODE,WECOM_MINIAPP_CODE");
            statement.setString(2, clientId);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO sys_oauth2_client_external_provider "
                + "(client_id, code_type, identity_provider_id) VALUES (?, ?, ?)")) {
            statement.setString(1, clientId);
            statement.setString(2, "WECOM_WEBVIEW_CODE");
            statement.setLong(3, 100L);
            statement.executeUpdate();
            statement.setString(2, "WECOM_MINIAPP_CODE");
            statement.setLong(3, 100L);
            statement.executeUpdate();
        }
    }

    private static long count(Connection connection, String sql, Object... arguments) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < arguments.length; index++) {
                statement.setObject(index + 1, arguments[index]);
            }
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }
}
