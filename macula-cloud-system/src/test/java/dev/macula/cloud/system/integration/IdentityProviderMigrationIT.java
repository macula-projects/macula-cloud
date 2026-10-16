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
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;

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
            .locations("classpath:db/migration").load();
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
