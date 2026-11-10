/*
 * Copyright (c) 2026 Macula
 * macula.dev, China
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

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 在显式指定的空隔离 MySQL 中验证 V23 到 V24 升级，绝不使用业务库。
 * @author Rain
 * @since 6.1.0
 */
@EnabledIfEnvironmentVariable(named = "APPLICATION_ALLOWLIST_IT", matches = "true")
class ApplicationUrlIpAllowlistMigrationIT {
    /** 真实升级保留历史逗号数据，并验证扩列及两张表多行 IP 往返。 */
    @Test
    void upgradesLegacyRowsWithoutChangingExistingPermissions() throws Exception {
        String url = System.getenv("ALLOWLIST_IT_JDBC_URL");
        assertThat(url).startsWith("jdbc:mysql://127.0.0.1:").contains("/macula_allowlist_it");
        String user = System.getenv("ALLOWLIST_IT_USERNAME");
        String password = System.getenv("ALLOWLIST_IT_PASSWORD");
        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).as("only an empty isolated database is allowed").isZero();
        }
        Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration")
            .target("23").load().migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        String legacy = "GET:/legacy/**,POST:/legacy/orders";
        jdbc.update("INSERT INTO sys_application_tenant (id,tenant_id,application_name,code,sk,manager,mobile,access_path,create_time,last_update_time) "
            + "VALUES (900201,1,'isolated allowlist','isolated-allowlist','synthetic','test','000',?,NOW(),NOW())", legacy);
        jdbc.update("INSERT INTO sys_application_apikey_tenant (id,tenant_id,application_id,api_key,access_path) "
            + "VALUES (900202,1,900201,'sk-synthetic-migration-only',?)", legacy);
        Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration").load().migrate();
        assertThat(jdbc.queryForObject("SELECT access_path FROM sys_application_tenant WHERE id=900201", String.class))
            .isEqualTo(legacy);
        assertThat(jdbc.queryForObject("SELECT allow_ips FROM sys_application_tenant WHERE id=900201", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT allow_ips FROM sys_application_apikey_tenant WHERE id=900202", String.class)).isNull();
        String longUrls = "GET:/" + "a".repeat(300) + "\nPOST:/orders";
        String ips = "192.0.2.0/24\n2001:db8::/32";
        jdbc.update("UPDATE sys_application_tenant SET access_path=?,allow_ips=? WHERE id=900201", longUrls, ips);
        jdbc.update("UPDATE sys_application_apikey_tenant SET access_path=?,allow_ips=? WHERE id=900202", longUrls, ips);
        assertThat(jdbc.queryForObject("SELECT access_path FROM sys_application_tenant WHERE id=900201", String.class))
            .isEqualTo(longUrls);
        assertThat(jdbc.queryForObject("SELECT allow_ips FROM sys_application_apikey_tenant WHERE id=900202", String.class))
            .isEqualTo(ips);
    }
}
