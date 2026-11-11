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

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 专用空MySQL验证一次性EMAIL开启边界及取消后的持久性，不读取业务配置。
 * @author Macula
 * @since 6.1.0
 */
@EnabledIfEnvironmentVariable(named = "HOSTED_EMAIL_LOGIN_IT", matches = "true")
class HostedEmailLoginMigrationIT {
    /** 历史合法列表追加EMAIL，全关及不支持的应用保持；其他Client和身份字段保持。 */
    @Test
    void migratesOnlyEligibleExplicitListsOnce() {
        String url = System.getenv("HOSTED_EMAIL_IT_URL");
        assertThat(url).startsWith("jdbc:mysql://127.0.0.1:");
        assertThat(URI.create(url.substring(5)).getPath()).startsWith("/macula_email_login_it_");
        var ds = new DriverManagerDataSource(url, System.getenv("HOSTED_EMAIL_IT_USER"), System.getenv("HOSTED_EMAIL_IT_PASSWORD"));
        var jdbc = new JdbcTemplate(ds);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class)).isZero();
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("24").load().migrate();
        Map<String, String> methods = new LinkedHashMap<>();
        methods.put("inherit", null);
        methods.put("disabled", "");
        methods.put("password", "PASSWORD");
        methods.put("sms", "SMS");
        methods.put("both", "PASSWORD,SMS");
        methods.put("already", "PASSWORD,EMAIL");
        methods.put("invalid", "PASSWORD,UNKNOWN");
        methods.put("lowercase", "password");
        methods.put("service", "PASSWORD");
        methods.put("in-app", "SMS");
        methods.put("resource", "PASSWORD");
        methods.forEach((name, value) -> jdbc.update("INSERT INTO sys_oauth2_client(client_id,client_name,client_secret,purpose,application_type,hosted_login_methods,authorization_version,tenant_id,user_type,client_authentication_methods,authorization_grant_types,redirect_uris,scopes,create_time,last_update_time) "
            + "VALUES(?,?, 'fixture-hash',?,?,?,7,1,'employee','none','authorization_code','https://client.example/callback','openid',NOW(),NOW())",
            "email-it-" + name, name, name.equals("resource") ? "RESOURCE_SERVER" : "APPLICATION",
            name.equals("service") ? "SERVICE" : name.equals("in-app") ? "IN_APP" : "SPA", value));
        var protectedBefore = jdbc.queryForList("SELECT client_id,client_secret,purpose,application_type,authorization_version,default_protocol,redirect_uris,scopes FROM sys_oauth2_client ORDER BY id");
        var usersBefore = jdbc.queryForList("SELECT id,username,password,email,mobile FROM sys_user ORDER BY id");
        var bindingsBefore = jdbc.queryForList("SELECT * FROM sys_registration_identity_binding ORDER BY id");
        var flyway = Flyway.configure().dataSource(ds).locations("classpath:db/migration").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        methods.forEach((name, value) -> {
            String expected = switch (name) {
                case "password", "sms", "both" -> value + ",EMAIL";
                default -> value;
            };
            assertThat(jdbc.queryForObject("SELECT hosted_login_methods FROM sys_oauth2_client WHERE client_id=?", String.class, "email-it-" + name)).isEqualTo(expected);
        });
        assertThat(jdbc.queryForList("SELECT client_id,client_secret,purpose,application_type,authorization_version,default_protocol,redirect_uris,scopes FROM sys_oauth2_client ORDER BY id")).isEqualTo(protectedBefore);
        assertThat(jdbc.queryForList("SELECT id,username,password,email,mobile FROM sys_user ORDER BY id")).isEqualTo(usersBefore);
        assertThat(jdbc.queryForList("SELECT * FROM sys_registration_identity_binding ORDER BY id")).isEqualTo(bindingsBefore);
        jdbc.update("UPDATE sys_oauth2_client SET hosted_login_methods='PASSWORD' WHERE client_id='email-it-password'");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT hosted_login_methods FROM sys_oauth2_client WHERE client_id='email-it-password'", String.class)).isEqualTo("PASSWORD");
    }
}
