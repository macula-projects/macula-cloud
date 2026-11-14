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

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import dev.macula.cloud.system.mapper.SysIamApplicationMapper;
import dev.macula.cloud.system.security.OAuth2ResourcePolicy;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 专属MySQL验证V29空策略、不重写既有凭据/绑定及真实Mapper集合字段。
 * @author Rain
 * @since 6.1.0
 */
@EnabledIfEnvironmentVariable(named = "RESOURCE_USER_TYPE_IT", matches = "true")
class ResourceUserTypePolicyIT {
    private static final String DATABASE_PREFIX = "macula_user_type_it_";
    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private DriverManagerDataSource dataSource;
    private String database;

    /** 创建测试专有随机库，拒绝非本机管理连接。 */
    @BeforeEach
    void prepare() {
        String url = System.getenv("RESOURCE_USER_TYPE_IT_URL");
        assertThat(url).matches("jdbc:mysql://127[.]0[.]0[.]1:[0-9]+/mysql[?].+");
        String user = System.getenv("RESOURCE_USER_TYPE_IT_USER");
        String password = System.getenv("RESOURCE_USER_TYPE_IT_PASSWORD");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        database = DATABASE_PREFIX + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE DATABASE " + database + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        dataSource = new DriverManagerDataSource(url.replace("/mysql?", "/" + database + "?"), user, password);
        jdbc = new JdbcTemplate(dataSource);
    }

    /** 只删除当前测试生成的随机数据库。 */
    @AfterEach
    void cleanup() {
        if (admin != null && database != null) {
            assertThat(database).matches(DATABASE_PREFIX + "[0-9a-f]{32}");
            admin.execute("DROP DATABASE " + database);
        }
    }

    /** 全新升级具有非空空集合默认值，不自动给任何资源服务器放行用户。 */
    @Test
    void freshDatabaseHasEmptyPolicies() {
        migrate("29");
        assertThat(jdbc.queryForList("SELECT allowed_user_types FROM sys_iam_application", String.class))
            .allSatisfy(value -> assertThat(value).isEqualTo("[]"));
        assertThat(jdbc.queryForObject("SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema=? "
            + "AND table_name='sys_iam_application' AND column_name='allowed_user_types'", String.class, database))
            .isEqualTo("NO");
    }

    /** V28升级只增加列，既有元数据、凭据和当前绑定逐值保持。 */
    @Test
    void upgradePreservesDataAndMapperReadsSelections() throws Exception {
        migrate("28");
        var before = jdbc.queryForList("SELECT * FROM sys_iam_application ORDER BY id");
        var credentials = jdbc.queryForList("SELECT * FROM sys_iam_application_protocol_config ORDER BY id");
        var bindings = jdbc.queryForList("SELECT * FROM sys_identity_provider_user_binding ORDER BY id");
        migrate("29");
        var after = jdbc.queryForList("SELECT * FROM sys_iam_application ORDER BY id");
        after.forEach(row -> assertThat(row.remove("allowed_user_types")).isEqualTo("[]"));
        assertThat(after).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM sys_iam_application_protocol_config ORDER BY id")).isEqualTo(credentials);
        assertThat(jdbc.queryForList("SELECT * FROM sys_identity_provider_user_binding ORDER BY id")).isEqualTo(bindings);
        assertThat(before).isNotEmpty();
        long id = ((Number) before.get(0).get("id")).longValue();
        jdbc.update("UPDATE sys_iam_application SET allowed_user_types=? WHERE id=?", "[\"employee\",\"member\"]", id);
        var factory = new MybatisSqlSessionFactoryBean();
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);
        factory.setDataSource(dataSource);
        var sql = factory.getObject();
        sql.getConfiguration().addMapper(SysIamApplicationMapper.class);
        var mapper = new SqlSessionTemplate(sql).getMapper(SysIamApplicationMapper.class);
        assertThat(OAuth2ResourcePolicy.allowedUserTypes(mapper.selectById(id).getAllowedUserTypes()))
            .containsExactlyInAnyOrder("employee", "member");
        jdbc.update("UPDATE sys_iam_application SET allowed_user_types='[]' WHERE id=?", id);
        assertThat(OAuth2ResourcePolicy.allowedUserTypes(mapper.selectById(id).getAllowedUserTypes())).isEmpty();
    }

    /** 只运行指定版本的仓库迁移，不清理或修复历史校验和。 */
    private void migrate(String target) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load().migrate();
    }
}
