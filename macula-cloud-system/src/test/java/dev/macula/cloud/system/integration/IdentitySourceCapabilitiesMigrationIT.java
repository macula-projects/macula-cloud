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
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 在空的隔离MySQL验证V21升级只登记原能力，不合并注册或改写密文/绑定。
 * @author Rain
 * @since 6.1.0
 */
@EnabledIfEnvironmentVariable(named = "IDENTITY_CAPABILITIES_IT", matches = "true")
class IdentitySourceCapabilitiesMigrationIT {
    /** 迁移前后逐项快照保持，重新启动不重复执行迁移。 */
    @Test
    void upgradesLegacyRegistrationsWithoutChangingIdentityBindingsOrSecrets() {
        String url = System.getenv("IAM_SOURCE_IT_URL");
        assertTrue(url.startsWith("jdbc:mysql://127.0.0.1:"));
        assertTrue(URI.create(url.substring(5)).getPath().startsWith("/macula_identity_binding_it_"));
        var ds = new DriverManagerDataSource(url,System.getenv("IAM_SOURCE_IT_USER"),System.getenv("IAM_SOURCE_IT_PASSWORD"));
        var jdbc = new JdbcTemplate(ds);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",Integer.class));
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("21").load().migrate();
        String[] types={"WECOM_WEB","WECOM_SDK","WECHAT_WEBSITE","WECHAT_OFFICIAL_ACCOUNT","WECHAT_MINIAPP","FEISHU_WEBVIEW"};
        String[] vendors={"WECOM","WECOM","WECHAT","WECHAT","WECHAT","FEISHU"};
        String[] flows={"WECOM_WEB_V1","WECOM_SDK_V1","WECHAT_WEBSITE_V1","WECHAT_OFFICIAL_V1","WECHAT_MINIAPP_V1","FEISHU_WEBVIEW_V1"};
        for(int i=0;i<types.length;i++) {
            jdbc.update("INSERT INTO sys_identity_provider(id,name,provider_type,source_type,identity_flow,app_id,app_secret,config_json,enabled,visible,version) VALUES(?,?,?,?,?,'fixture-id','cipher-fixture','{}',0,0,9)",
                96000+i,"cap-fixture-"+i,vendors[i],types[i],flows[i]);
        }
        jdbc.update("INSERT INTO sys_registration_identity_binding(registration_id,identity_kind,identity_value,user_type,user_id) VALUES(96000,'MEMBER_ID','fixture-member','employee',1)");
        var rows = jdbc.queryForList("SELECT id,source_type,identity_flow,app_id,app_secret,config_json,version FROM sys_identity_provider WHERE id BETWEEN 96000 AND 96005 ORDER BY id");
        var bindings = jdbc.queryForList("SELECT * FROM sys_registration_identity_binding WHERE registration_id=96000");
        var clients = jdbc.queryForList("SELECT client_id,client_secret,browser_provider_bindings FROM sys_oauth2_client ORDER BY id");
        var upgrade = Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("22").load();
        assertEquals(1,upgrade.migrate().migrationsExecuted);
        assertEquals(rows,jdbc.queryForList("SELECT id,source_type,identity_flow,app_id,app_secret,config_json,version FROM sys_identity_provider WHERE id BETWEEN 96000 AND 96005 ORDER BY id"));
        assertEquals(bindings,jdbc.queryForList("SELECT * FROM sys_registration_identity_binding WHERE registration_id=96000"));
        assertEquals(clients,jdbc.queryForList("SELECT client_id,client_secret,browser_provider_bindings FROM sys_oauth2_client ORDER BY id"));
        assertEquals("WECOM_APPLICATION",jdbc.queryForObject("SELECT credential_type FROM sys_identity_provider WHERE id=96000",String.class));
        assertEquals(2,jdbc.queryForObject("SELECT JSON_LENGTH(capabilities_json) FROM sys_identity_provider WHERE id=96000",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT JSON_LENGTH(capabilities_json) FROM sys_identity_provider WHERE id=96001",Integer.class));
        assertEquals("WECHAT_WEBSITE",jdbc.queryForObject("SELECT credential_type FROM sys_identity_provider WHERE id=96002",String.class));
        assertEquals("WECHAT_OFFICIAL_ACCOUNT",jdbc.queryForObject("SELECT credential_type FROM sys_identity_provider WHERE id=96003",String.class));
        assertEquals(2,jdbc.queryForObject("SELECT JSON_LENGTH(capabilities_json) FROM sys_identity_provider WHERE id=96004",Integer.class));
        assertEquals("FEISHU_WEBVIEW_V1",jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(capabilities_json,'$.FEISHU_WEBVIEW')) FROM sys_identity_provider WHERE id=96005",String.class));
        assertEquals(0,upgrade.migrate().migrationsExecuted);
    }
}
