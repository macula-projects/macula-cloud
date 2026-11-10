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

import dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration;
import dev.macula.cloud.system.protocol.ApplicationProtocolRegistry;
import dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration;
import dev.macula.cloud.system.protocol.OAuth2ProtocolSettings;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import tools.jackson.databind.json.JsonMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 将V16明确配置投影为独立协议；无法合法拆分则保留旧列并关闭应用。
 * @author Rain
 * @since 6.1.0
 */
public class V18__project_legacy_application_protocols extends BaseJavaMigration {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(V18__project_legacy_application_protocols.class);
    private static final Set<String> OIDC_GRANTS = Set.of("authorization_code", "refresh_token");
    private final JsonMapper json = JsonMapper.builder().build();

    /** Flyway启动时只依赖JDBC和纯协议规则，不依赖尚未创建的Spring Service。 */
    @Override
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        try (var select = connection.prepareStatement("SELECT * FROM sys_oauth2_client ORDER BY id");
             var applications = select.executeQuery()) {
            while (applications.next()) {
                long id = applications.getLong("id");
                boolean valid = true;
                Set<String> protocols;
                try {
                    protocols = OAuth2ProtocolConfiguration.csv(applications.getString("enabled_protocols"));
                } catch (IllegalArgumentException ex) {
                    protocols = Set.of();
                    valid = false;
                }
                for (String column : Set.of("authorization_grant_types", "scopes", "allowed_origins", "redirect_uris", "client_authentication_methods")) {
                    try { OAuth2ProtocolConfiguration.csv(applications.getString(column)); }
                    catch (IllegalArgumentException ex) { valid = false; }
                }
                if ("ENABLED".equals(applications.getString("configuration_status"))
                    && (applications.getObject("tenant_id") == null || applications.getString("user_type") == null)) { valid = false; }
                for (String protocol : protocols) {
                    if (!ApplicationProtocolRegistry.PROTOCOLS.contains(protocol)) {
                        valid = false;
                        continue;
                    }
                    OAuth2ProtocolSettings settings = project(applications, protocol, protocols.size() > 1);
                    boolean enabled = valid && "ENABLED".equals(applications.getString("configuration_status"));
                    try {
                        OAuth2ProtocolConfiguration.validate(new ApplicationProtocolConfiguration(protocol,
                            ApplicationProtocolRegistry.SCHEMA_VERSION, enabled, settings), applications.getString("application_type"));
                        if (enabled && settings.getClientAuthenticationMethods() != null
                            && settings.getClientAuthenticationMethods().contains("client_secret_")
                            && (applications.getString("client_secret") == null || applications.getString("client_secret").isBlank())) {
                            throw new IllegalArgumentException("Missing application credential");
                        }
                    } catch (RuntimeException ex) {
                        enabled = false;
                        valid = false;
                    }
                    try (var insert = connection.prepareStatement("INSERT INTO sys_application_protocol_config "
                        + "(application_id,protocol,schema_version,enabled,configuration) VALUES (?,?,?,?,?)")) {
                        insert.setLong(1, id);
                        insert.setString(2, protocol);
                        insert.setInt(3, ApplicationProtocolRegistry.SCHEMA_VERSION);
                        insert.setBoolean(4, enabled);
                        insert.setString(5, json.writeValueAsString(settings));
                        insert.executeUpdate();
                    }
                }
                String defaultProtocol = applications.getString("default_protocol");
                if ("ENABLED".equals(applications.getString("configuration_status"))
                    && (protocols.isEmpty() || defaultProtocol == null || !protocols.contains(defaultProtocol))) {
                    valid = false;
                }
                if (!valid) {
                    LOG.warn("Application {} moved to DRAFT: legacy protocol configuration failed validation; original columns retained", id);
                    try (var update = connection.prepareStatement("UPDATE sys_oauth2_client SET configuration_status='DRAFT' WHERE id=?")) {
                        update.setLong(1, id);
                        update.executeUpdate();
                    }
                }
            }
        }
    }

    /** 按协议能力拆分，不复制OIDC身份Scope或直接Token扩展到错误协议。 */
    private OAuth2ProtocolSettings project(ResultSet application, String protocol, boolean split) throws SQLException {
        boolean oidc = ApplicationProtocolRegistry.OIDC.equals(protocol);
        OAuth2ProtocolSettings settings = new OAuth2ProtocolSettings();
        settings.setClientAuthenticationMethods(application.getString("client_authentication_methods"));
        Set<String> grants = safeCsv(application.getString("authorization_grant_types"));
        settings.setAuthorizationGrantTypes(split ? join(grants.stream().filter(grant -> !oidc || OIDC_GRANTS.contains(grant)).collect(Collectors.toSet())) : application.getString("authorization_grant_types"));
        settings.setRedirectUris(application.getString("redirect_uris"));
        Set<String> scopes = safeCsv(application.getString("scopes"));
        settings.setScopes(split ? join(scopes.stream().filter(scope -> oidc || !Set.of("openid", "profile").contains(scope)).collect(Collectors.toSet())) : application.getString("scopes"));
        settings.setAllowedOrigins(safeCsv(application.getString("allowed_origins")));
        settings.setRequireProofKey(application.getBoolean("require_proof_key"));
        settings.setRequireAuthorizationConsent(application.getBoolean("require_authorization_consent"));
        settings.setJwkSetUrl(application.getString("jwk_set_url"));
        settings.setSigningAlgorithm(application.getString("signing_algorithm"));
        settings.setAuthorizationCodeTimeToLive(seconds(application, "authorization_code_time_to_live", 300));
        settings.setAccessTokenTimeToLive(seconds(application, "access_token_time_to_live", 3600));
        settings.setTokenFormat(application.getString("token_format") == null ? "reference" : application.getString("token_format"));
        settings.setReuseRefreshTokens(application.getBoolean("reuse_refresh_tokens"));
        settings.setRefreshTokenTimeToLive(seconds(application, "refresh_token_time_to_live", 3600));
        settings.setIdTokenSignatureAlgorithm(oidc ? application.getString("id_token_signature_algorithm") : null);
        return settings;
    }

    /** 原列为空保留既有平台默认，不推断Scope或增加grant。 */
    private long seconds(ResultSet application, String column, long fallback) throws SQLException {
        long value = application.getLong(column);
        return application.wasNull() ? fallback : value;
    }

    /** 非法CSV不作为有效集合，后续完整启用校验会拒绝。 */
    private Set<String> safeCsv(String value) {
        try {
            return OAuth2ProtocolConfiguration.csv(value);
        } catch (IllegalArgumentException ex) {
            return Set.of();
        }
    }

    /** 稳定排序保证迁移结果可比较，旧共享列保持原样作为恢复依据。 */
    private String join(Set<String> values) {
        return String.join(",", new TreeSet<>(values));
    }
}
