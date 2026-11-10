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

import dev.macula.cloud.system.protocol.OAuth2ProtocolSettings;
import java.net.URI;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 隔离MySQL验证V16到V18的精确单协议、能力拆分和非法配置草稿化。
 * @author Rain
 * @since 6.1.0
 */
@EnabledIfEnvironmentVariable(named = "APPLICATION_PROTOCOL_IT", matches = "true")
class ApplicationProtocolConfigurationMigrationIT {
    /** 仅接收新建的隔离本地库；旧列和凭据摘要不被改写，Flyway重跑幂等。 */
    @Test
    void migrationPreservesIdentityAndSplitsOnlyValidProtocols() {
        String url = System.getenv("IAM_SOURCE_IT_URL");
        assertTrue(url.startsWith("jdbc:mysql://127.0.0.1:"));
        assertTrue(URI.create(url.substring(5)).getPath().startsWith("/macula_identity_binding_it_"));
        var ds = new DriverManagerDataSource(url, System.getenv("IAM_SOURCE_IT_USER"), System.getenv("IAM_SOURCE_IT_PASSWORD"));
        var jdbc = new JdbcTemplate(ds);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class));
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("16").load().migrate();
        insert(jdbc, "fixture-admin", "SPA", "OIDC", "OIDC", "none", "authorization_code", "openid,profile", null);
        insert(jdbc, "fixture-gateway", "SERVICE", "OAUTH2_1", "OAUTH2_1", "client_secret_basic", "client_credentials", "", "synthetic-hash");
        insert(jdbc, "fixture-dual", "WEB_SERVER", "OIDC,OAUTH2_1", "OIDC", "client_secret_basic", "authorization_code,refresh_token,client_credentials", "openid,profile,api.read", "synthetic-hash");
        insert(jdbc, "fixture-invalid", "SPA", "OIDC,OAUTH2_1", "OIDC", "none", "authorization_code,refresh_token", "openid,api.read", null);
        insert(jdbc, "fixture-malformed", "SERVICE", "OAUTH2_1", "OAUTH2_1", "client_secret_basic", "client_credentials", "api.read,,other", "synthetic-hash");
        String before = jdbc.queryForList("SELECT client_id,client_secret,tenant_id,user_type,authorization_grant_types,scopes FROM sys_oauth2_client ORDER BY id").toString();
        // 此用例验证V17/V18的历史拆分行为；V19单协议收紧由独立迁移IT覆盖。
        var flyway = Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("18").load();
         flyway.migrate();
        assertEquals(before, jdbc.queryForList("SELECT client_id,client_secret,tenant_id,user_type,authorization_grant_types,scopes FROM sys_oauth2_client ORDER BY id").toString());
        assertEquals("ENABLED", status(jdbc, "fixture-admin"));
         assertEquals("ENABLED", status(jdbc, "fixture-gateway"));
        assertEquals("ENABLED", status(jdbc, "fixture-dual"));
         assertEquals("DRAFT", status(jdbc, "fixture-invalid"));
         assertEquals("DRAFT", status(jdbc, "fixture-malformed"));
        var json = JsonMapper.builder().build();
        String oauthJson = jdbc.queryForObject("SELECT p.configuration FROM sys_application_protocol_config p JOIN sys_oauth2_client c ON c.id=p.application_id WHERE c.client_id='fixture-dual' AND p.protocol='OAUTH2_1'", String.class);
        var oauth = json.readValue(oauthJson, OAuth2ProtocolSettings.class);
         assertEquals("api.read", oauth.getScopes());
        String oidcJson = jdbc.queryForObject("SELECT p.configuration FROM sys_application_protocol_config p JOIN sys_oauth2_client c ON c.id=p.application_id WHERE c.client_id='fixture-dual' AND p.protocol='OIDC'", String.class);
        var oidc = json.readValue(oidcJson, OAuth2ProtocolSettings.class);
         assertFalse(oidc.getAuthorizationGrantTypes().contains("client_credentials"));
        assertFalse(oauthJson.contains("synthetic-hash"));
        int count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_application_protocol_config", Integer.class);
         flyway.migrate();
        assertEquals(count, jdbc.queryForObject("SELECT COUNT(*) FROM sys_application_protocol_config", Integer.class));
    }

    /** 显式参数构造旧模式，不连接业务身份或真实密钥。 */
    private void insert(JdbcTemplate jdbc, String id, String type, String protocols, String defaultProtocol, String method, String grants, String scopes, String secret) {
        jdbc.update("INSERT INTO sys_oauth2_client(client_id,client_name,application_type,enabled_protocols,default_protocol,configuration_status,client_authentication_methods,authorization_grant_types,scopes,client_secret,tenant_id,user_type,redirect_uris,require_proof_key,require_authorization_consent,reuse_refresh_tokens,allowed_origins,token_format,create_time,last_update_time) VALUES(?,?,?,?,?,'ENABLED',?,?,?,?,1,'employee','https://client.example/callback',1,0,0,'https://client.example','reference',NOW(),NOW())",
            id, id, type, protocols, defaultProtocol, method, grants, scopes, secret);
    }

    /** 读取应用启用状态，不打印敏感配置。 */
    private String status(JdbcTemplate jdbc, String id) {
        return jdbc.queryForObject("SELECT configuration_status FROM sys_oauth2_client WHERE client_id=?", String.class, id);
    }
}
