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
package dev.macula.cloud.system.migration;

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

import static org.junit.jupiter.api.Assertions.*;

/**
 * 在显式指定的全新隔离 MySQL 库中验证全局登录名唯一迁移和重复数据失败关闭。
 *
 * @author Rain
 * @since 6.1.0
 */
@EnabledIfEnvironmentVariable(named = "SYSTEM_MIGRATION_IT", matches = "true")
class SysUserUsernameUniqueMigrationTest {
    @Test
    void duplicateUsernameStopsV7UntilDataIsFixed() throws Exception {
        String url = System.getenv("SPRING_DATASOURCE_URL");
        assertNotNull(url, "必须显式指定全新隔离数据库");
        assertTrue(url.startsWith("jdbc:mysql://"));
        String database = URI.create(url.substring("jdbc:".length())).getPath().substring(1);
        assertTrue(database.startsWith("macula_settings_it_"));
        String username = System.getenv("SPRING_DATASOURCE_USERNAME");
        String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
        assertNotNull(username);
        assertNotNull(password);
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertEquals(0, count(connection,
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ?", database),
                "只允许在空的隔离数据库运行");
        }

        Flyway.configure().dataSource(url, username, password).locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("6")).load().migrate();
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            execute(connection, "INSERT INTO sys_user (username, create_time, last_update_time) " +
                "VALUES ('root', NOW(), NOW())");
            assertEquals(2, count(connection, "SELECT COUNT(*) FROM sys_user WHERE username = 'root'"));
        }

        Flyway toV7 = Flyway.configure().dataSource(url, username, password)
            .locations("classpath:db/migration").load();
        assertThrows(FlywayException.class, toV7::migrate);
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertEquals(2, count(connection, "SELECT COUNT(*) FROM sys_user WHERE username = 'root'"),
                "迁移不得静默清理重复用户");
            assertEquals(0, count(connection,
                "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = ? " +
                    "AND table_name = 'sys_user' AND index_name = 'uk_sys_user_username'", database));
            execute(connection, "DELETE FROM sys_user WHERE username = 'root' AND id > 40");
        }
        toV7.repair();
        toV7.migrate();
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertEquals(1, count(connection,
                "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = ? " +
                    "AND table_name = 'sys_user' AND index_name = 'uk_sys_user_username' " +
                    "AND non_unique = 0", database));
            assertThrows(SQLException.class, () -> execute(connection,
                "INSERT INTO sys_user (username, create_time, last_update_time) " +
                    "VALUES ('root', NOW(), NOW())"));
        }
    }

    private static long count(Connection connection, String sql, Object... arguments) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < arguments.length; i++) statement.setObject(i + 1, arguments[i]);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }
}
