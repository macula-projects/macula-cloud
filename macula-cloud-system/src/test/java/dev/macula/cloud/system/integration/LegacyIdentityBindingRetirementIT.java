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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 在专属隔离MySQL验证旧绑定表退役；非空升级不能改写当前绑定、应用或凭据。
 * @author Rain
 * @since 6.1.0
 */
@EnabledIfEnvironmentVariable(named = "IAM_LEGACY_BINDING_RETIREMENT_IT", matches = "true")
class LegacyIdentityBindingRetirementIT {
    private static final String DATABASE_PREFIX = "macula_legacy_binding_it_";
    private static final String LEGACY_TABLE = "sys_user_identity_binding";
    private static final List<String> CURRENT_TABLES = List.of("sys_iam_application",
        "sys_iam_application_protocol_config", "sys_iam_application_identity_provider",
        "sys_identity_provider_user_binding", "sys_identity_provider", "sys_user");
    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private DriverManagerDataSource dataSource;
    private String database;

    /** 每个用例创建全新随机库，不允许沿用运行库或默认连接。 */
    @BeforeEach
    void prepareDatabase() {
        String url = System.getenv("IAM_LEGACY_BINDING_RETIREMENT_IT_URL");
        assertThat(url).matches("jdbc:mysql://127[.]0[.]0[.]1:[0-9]+/mysql[?].+");
        String user = System.getenv("IAM_LEGACY_BINDING_RETIREMENT_IT_USER");
        String password = System.getenv("IAM_LEGACY_BINDING_RETIREMENT_IT_PASSWORD");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        database = DATABASE_PREFIX + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE DATABASE " + database + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        dataSource = new DriverManagerDataSource(url.replace("/mysql?", "/" + database + "?"), user, password);
        jdbc = new JdbcTemplate(dataSource);
    }

    /** 只删除本例生成的专用库，不能对外部schema执行清理。 */
    @AfterEach
    void dropOwnDatabase() {
        if (admin != null && database != null) {
            assertThat(database).matches(DATABASE_PREFIX + "[0-9a-f]{32}");
            admin.execute("DROP DATABASE " + database);
        }
    }

    /** 新空库执行全部版本，最终只保留当前绑定模型。 */
    @Test
    void freshInstallRetiresLegacyTable() {
        assertThat(flyway("28").migrate().migrationsExecuted).isEqualTo(28);
        assertThat(tableExists(LEGACY_TABLE)).isFalse();
        assertThat(tableExists("sys_identity_provider_user_binding")).isTrue();
        assertThat(version()).isEqualTo("28");
    }

    /** 旧表非空也只退役历史表，已归档记录不得被猜测合并到当前关系。 */
    @Test
    void upgradeFromV27PreservesAllCurrentData() {
        flyway("27").migrate();
        jdbc.update("INSERT INTO sys_user_identity_binding(provider_type,provider_subject,username) "
            + "VALUES('GITHUB','synthetic-legacy-subject','synthetic-legacy-user')");
        jdbc.update("INSERT INTO sys_identity_provider_user_binding(identity_provider_id,identity_kind,"
            + "identity_value,user_type,user_id) VALUES(99101,'OPEN_ID','synthetic-current-subject','employee',99102)");
        var archived = jdbc.queryForList("SELECT * FROM sys_user_identity_binding ORDER BY id");
        var before = currentSnapshot();

        assertThat(flyway("28").migrate().migrationsExecuted).isEqualTo(1);

        assertThat(tableExists(LEGACY_TABLE)).isFalse();
        assertThat(currentSnapshot()).isEqualTo(before);
        assertThat(archived).hasSize(1);
        assertThat(archived.get(0)).containsEntry("provider_subject", "synthetic-legacy-subject");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_identity_provider_user_binding", Integer.class))
            .isEqualTo(1);
    }

    /** 已在归档后移除旧表的库也能记录V28，不要求重建旧表。 */
    @Test
    void alreadyAbsentLegacyTableIsSafe() {
        flyway("27").migrate();
        jdbc.execute("DROP TABLE sys_user_identity_binding");
        var before = currentSnapshot();
        assertThat(flyway("28").migrate().migrationsExecuted).isEqualTo(1);
        assertThat(currentSnapshot()).isEqualTo(before);
        assertThat(tableExists(LEGACY_TABLE)).isFalse();
    }

    /** Flyway重复启动不再执行删除，也不修改当前聚合或绑定。 */
    @Test
    void repeatedMigrationLeavesCurrentDataUnchanged() {
        flyway("28").migrate();
        var before = currentSnapshot();
        assertThat(flyway("28").migrate().migrationsExecuted).isZero();
        assertThat(currentSnapshot()).isEqualTo(before);
        assertThat(tableExists(LEGACY_TABLE)).isFalse();
    }

    /** 固定目标验证历史边界，不能让后续版本改变此退役测试的含义。 */
    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load();
    }

    /** 只检查当前专用schema中的精确表名。 */
    private boolean tableExists(String name) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
            + "WHERE table_schema=DATABASE() AND table_name=?", Integer.class, name) == 1;
    }

    /** 逐值比较所有当前数据，不用行数代替凭据及绑定保持证明。 */
    private Map<String, List<Map<String, Object>>> currentSnapshot() {
        Map<String, List<Map<String, Object>>> snapshot = new LinkedHashMap<>();
        for (String table : CURRENT_TABLES) {
            snapshot.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
        }
        return snapshot;
    }

    /** 读取已成功执行的最后版本。 */
    private String version() {
        return jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success=1 "
            + "ORDER BY installed_rank DESC LIMIT 1", String.class);
    }
}
