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
package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * 增加用途和资源许可；歧义双协议需管理员重新配置，身份与Secret摘要原样保留。
 * @author Rain
 * @since 6.1.0
 */
public class V19__single_protocol_resource_boundaries extends BaseJavaMigration {
    /** JDBC迁移不依赖尚未初始化的Spring服务或本地凭据。 */
    @Override
    public void migrate(Context context) throws Exception {
        try (var statement = context.getConnection().createStatement()) {
            statement.execute("ALTER TABLE sys_oauth2_client ADD purpose VARCHAR(32) NOT NULL DEFAULT 'APPLICATION', "
                + "ADD managed_key VARCHAR(64) NULL, ADD authorization_version BIGINT NOT NULL DEFAULT 1, "
                + "ADD resource_permissions TEXT NULL, ADD introspection_resources TEXT NULL, "
                + "ADD UNIQUE KEY uk_oauth_client_managed_key (managed_key)");
            statement.execute("UPDATE sys_oauth2_client SET resource_permissions='{}', introspection_resources=''");
            statement.execute("CREATE TEMPORARY TABLE ambiguous_oauth_clients AS SELECT application_id "
                + "FROM sys_application_protocol_config GROUP BY application_id HAVING COUNT(*) > 1");
            statement.execute("UPDATE sys_oauth2_client SET configuration_status='DRAFT', default_protocol=NULL "
                + "WHERE id IN (SELECT application_id FROM ambiguous_oauth_clients)");
            statement.execute("DELETE FROM sys_application_protocol_config WHERE application_id "
                + "IN (SELECT application_id FROM ambiguous_oauth_clients)");
            statement.execute("DROP TEMPORARY TABLE ambiguous_oauth_clients");
            statement.execute("ALTER TABLE sys_application_protocol_config ADD UNIQUE KEY uk_application_single_protocol (application_id)");
        }
    }
}
