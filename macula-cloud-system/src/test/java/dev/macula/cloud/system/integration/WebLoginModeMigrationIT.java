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

import dev.macula.cloud.system.enums.IamWebLoginMode;
import java.net.URI;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全新隔离MySQL验证V26只加列，不改历史架构、身份、凭据和绑定。
 * @author Rain
 * @since 6.1.0
 */
@EnabledIfEnvironmentVariable(named = "WEB_LOGIN_MODE_MIGRATION_IT", matches = "true")
class WebLoginModeMigrationIT {
    /** 历史NULL只在领域读取时缺省；重复迁移不覆盖主动选择。 */
    @Test
    void addsModeWithoutRewritingExistingConfiguration() {
        String url = System.getenv("WEB_LOGIN_MODE_MIGRATION_URL");
        assertThat(url).startsWith("jdbc:mysql://127.0.0.1:");
        assertThat(URI.create(url.substring(5)).getPath()).startsWith("/macula_web_login_mode_it_");
        var ds = new DriverManagerDataSource(url, System.getenv("SPRING_DATASOURCE_USERNAME"), System.getenv("SPRING_DATASOURCE_PASSWORD"));
        var jdbc = new JdbcTemplate(ds);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class)).isZero();
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("25").load().migrate();
        for (String type : new String[]{"WEB_SERVER", "SPA", "IN_APP", "NATIVE"}) {
            jdbc.update("INSERT INTO sys_oauth2_client(client_id,client_name,client_secret,purpose,application_type,authorization_version,tenant_id,user_type,client_authentication_methods,authorization_grant_types,redirect_uris,scopes,create_time,last_update_time) "
                + "VALUES(?,?,'fixture-hash','APPLICATION',?,7,1,'employee','none','authorization_code','https://client.example/callback','openid',NOW(),NOW())", "mode-it-"+type, type, type);
        }
        var clients = jdbc.queryForList("SELECT * FROM sys_oauth2_client ORDER BY id");
        var protocols = jdbc.queryForList("SELECT * FROM sys_application_protocol_config ORDER BY id");
        var bindings = jdbc.queryForList("SELECT * FROM sys_registration_identity_binding ORDER BY id");
        var sources = jdbc.queryForList("SELECT * FROM sys_oauth2_client_external_provider ORDER BY id");
        var flyway = Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("26").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        var after = jdbc.queryForList("SELECT * FROM sys_oauth2_client ORDER BY id");
        after.forEach(row -> assertThat(row.remove("web_login_mode")).isNull());
        assertThat(after).isEqualTo(clients);
        assertThat(jdbc.queryForList("SELECT * FROM sys_application_protocol_config ORDER BY id")).isEqualTo(protocols);
        assertThat(jdbc.queryForList("SELECT * FROM sys_registration_identity_binding ORDER BY id")).isEqualTo(bindings);
        assertThat(jdbc.queryForList("SELECT * FROM sys_oauth2_client_external_provider ORDER BY id")).isEqualTo(sources);
        assertThat(IamWebLoginMode.resolve("WEB_SERVER", null)).isEqualTo(IamWebLoginMode.BROWSER);
        assertThat(IamWebLoginMode.resolve("SPA", null)).isEqualTo(IamWebLoginMode.BROWSER);
        jdbc.update("UPDATE sys_oauth2_client SET web_login_mode='IN_APP' WHERE client_id='mode-it-SPA'");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT web_login_mode FROM sys_oauth2_client WHERE client_id='mode-it-SPA'", String.class)).isEqualTo("IN_APP");
    }
}
