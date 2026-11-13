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

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 将V26冻结格式迁为单协议应用模型；不调用运行时策略或重新编码凭据。
 * MySQL DDL不回滚：发布前保留原表，发布后失败必须人工核对恢复状态。
 * @author Rain
 * @since 6.1.0
 */
public class V27__iam_application_model extends BaseJavaMigration {
    private static final int MAX_BYTES = 32 * 1024;
    private static final String PREFIX = "v27_stage_";
    private static final String BACKUP = "v27_old_";
    private static final List<String> OLD = List.of("sys_oauth2_client", "sys_application_protocol_config",
        "sys_oauth2_client_external_provider", "sys_registration_identity_binding");
    private static final List<String> TARGET = List.of("sys_iam_application", "sys_iam_application_protocol_config",
        "sys_iam_application_identity_provider", "sys_identity_provider_user_binding");
    private static final List<String> AUDIT = List.of("create_by", "create_time", "last_update_by", "last_update_time");
    private static final List<String> COMMON = List.of("id", "purpose", "managed_key", "authorization_version",
        "resource_permissions", "application_type", "web_login_mode", "miniapp_platform", "hosted_login_methods",
        "user_type", "tenant_id", "configuration_status");
    private static final Map<String, String> OAUTH_FIELDS = oauthFields();
    private static final Set<String> REMOVED = Set.of("client_secret", "client_secret_expires_at", "protocol_mode",
        "enabled_protocols", "default_protocol", "browser_provider_bindings", "browser_capability_bindings",
        "external_code_types", "browser_login_provider_types", "direct_login_provider_type", "introspection_resources");
    private static final Set<String> AUTH = Set.of("none", "client_secret_basic", "client_secret_post", "private_key_jwt");
    private static final String EXTERNAL_GRANT = "urn:macula:params:oauth:grant-type:external_code";
    private static final Map<String, String> CODE_CAPABILITIES = Map.ofEntries(
        Map.entry("FEISHU_APP_CODE", "FEISHU_APPLINK"), Map.entry("WECOM_APP_CODE", "WECOM_SDK"),
        Map.entry("DINGTALK_APP_CODE", "DINGTALK_SDK"), Map.entry("WECHAT_OPEN_APP_CODE", "WECHAT_MOBILE_APP"),
        Map.entry("ALIYUN_MOBILE_TOKEN", "ALIYUN_MOBILE"), Map.entry("WECHAT_MINIAPP_LOGIN_CODE", "WECHAT_MINIAPP_LOGIN"),
        Map.entry("WECHAT_MINIAPP_PHONE_CODE", "WECHAT_MINIAPP_PHONE"), Map.entry("WECOM_MINIAPP_CODE", "WECOM_MINIAPP"),
        Map.entry("FEISHU_WEBVIEW_CODE", "FEISHU_WEBVIEW"), Map.entry("DINGTALK_WEBVIEW_CODE", "DINGTALK_WEBVIEW"));
    private static final Map<String, String> CAPABILITY_SOURCE = capabilitySources();
    private static final Map<String, Set<String>> FLOWS = flows();
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    /** DDL会自动提交，不能给调用者事务可回滚的错误印象。 */
    @Override
    public boolean canExecuteInTransaction() {
        return false;
    }

    /** 完成预检、暂存、逐值核对和原子发布；错误不携带原始SQL或行值。 */
    @Override
    public void migrate(Context context) {
        Stage stage = Stage.PRECHECK;
        try {
            Connection connection = context.getConnection();
            requireUnusedNames(connection);
            List<List<Map<String, Object>>> original = originals(connection);
            List<Map<String, Object>> sources = read(connection, "sys_identity_provider");
            List<List<Map<String, Object>>> projected = project(connection, original, sources);
            checkpoint(stage);
            stage = Stage.STAGING;
            createStaging(connection);
            checkpoint(stage);
            stage = Stage.COPY;
            for (int i = 0; i < TARGET.size(); i++) {
                insert(connection, PREFIX + TARGET.get(i), projected.get(i));
            }
            preserveSequences(connection);
            checkpoint(stage);
            stage = Stage.PREPUBLICATION;
            verify(connection, PREFIX, projected);
            require(originals(connection).equals(original), "SOURCE_CHANGED");
            require(read(connection, "sys_identity_provider").equals(sources), "SOURCE_CHANGED");
            checkpoint(stage);
            stage = Stage.PUBLICATION;
            List<String> renames = new ArrayList<>();
            for (int i = 0; i < OLD.size(); i++) {
                renames.add(OLD.get(i) + " TO " + BACKUP + OLD.get(i));
                renames.add(PREFIX + TARGET.get(i) + " TO " + TARGET.get(i));
            }
            execute(connection, "RENAME TABLE " + String.join(",", renames));
            checkpoint(stage);
            stage = Stage.VERIFICATION;
            verify(connection, "", projected);
            checkpoint(stage);
            stage = Stage.CLEANUP;
            checkpoint(stage);
            execute(connection, "DROP TABLE " + String.join(",", OLD.stream().map(name -> BACKUP + name).toList()));
        } catch (SQLException error) {
            String state = error.getSQLState();
            String safeState = state != null && state.matches("[A-Z0-9]{5}") ? state : "UNKNOWN";
            throw new FlywayException("V27 stage=" + stage + " recovery=" + recovery(stage)
                + " SQLState=" + safeState + " code=" + error.getErrorCode());
        } catch (RuntimeException error) {
            String code = error instanceof Problem problem ? problem.code : "INVALID_DATA";
            throw new FlywayException("V27 stage=" + stage + " recovery=" + recovery(stage) + " issue=" + code);
        }
    }

    /** 测试可在固定阶段注入失败；没有运行配置可跳过预检或自动repair。 */
    protected void checkpoint(Stage stage) throws SQLException { }

    /**
     * 明确区分发布前原表保留与发布后需要恢复的阶段。
     * @since 6.1.0
     */
    protected enum Stage {
        PRECHECK, STAGING, COPY, PREPUBLICATION, PUBLICATION, VERIFICATION, CLEANUP
    }

    /** 不将低层异常原因或业务值带入Flyway的异常链。 */
    private static String recovery(Stage stage) {
        return stage.ordinal() >= Stage.PUBLICATION.ordinal() ? "INSPECT_PUBLICATION" : "ORIGINALS_RETAINED";
    }

    /** 禁止覆盖上次失败或其他人建立的对象，恢复必须显式处理。 */
    private void requireUnusedNames(Connection connection) throws SQLException {
        for (int i = 0; i < OLD.size(); i++) {
            for (String name : List.of(TARGET.get(i), PREFIX + TARGET.get(i), BACKUP + OLD.get(i))) {
                try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?")) {
                    statement.setString(1, name);
                    try (var rows = statement.executeQuery()) {
                        rows.next();
                        require(rows.getInt(1) == 0, "EXISTING_RECOVERY_OBJECT");
                    }
                }
            }
        }
    }

    /** 固定原表顺序，读取只用于受控内存转换，不输出任何行。 */
    private List<List<Map<String, Object>>> originals(Connection connection) throws SQLException {
        List<List<Map<String, Object>>> result = new ArrayList<>();
        for (String name : OLD) {
            result.add(read(connection, name));
        }
        return result;
    }

    /** SQL标识仅取本类常量，不接受用户输入；使用参数化语句写入业务值。 */
    private List<Map<String, Object>> read(Connection connection, String table) throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();
        try (var statement = connection.prepareStatement("SELECT * FROM " + table + " ORDER BY id");
             var rows = statement.executeQuery()) {
            while (rows.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
                    Object value = rows.getObject(i);
                    int sqlType = rows.getMetaData().getColumnType(i);
                    // getObject可能把非法tinyint=2转换成true；预检必须看到原始0/1值。
                    if (value != null && Set.of(Types.BIT, Types.BOOLEAN, Types.TINYINT).contains(sqlType)) {
                        value = rows.getInt(i);
                    }
                    row.put(rows.getMetaData().getColumnLabel(i), value);
                }
                result.add(row);
            }
        }
        return result;
    }

    /** 预检并投影四组目标数据；任何歧义都发生在第一个DDL之前。 */
    private List<List<Map<String, Object>>> project(Connection connection, List<List<Map<String, Object>>> original,
        List<Map<String, Object>> sourceRows) throws SQLException {
        Set<String> expected = new LinkedHashSet<>(COMMON);
        expected.addAll(AUDIT);
        expected.addAll(OAUTH_FIELDS.keySet());
        expected.addAll(REMOVED);
        expected.addAll(Set.of("client_id", "client_id_issued_at", "client_name"));
        requireColumns(connection, OLD.get(0), expected);
        Map<Long, Map<String, Object>> sources = index(sourceRows);
        Map<Long, Map<String, Object>> apps = index(original.get(0));
        Map<Long, Map<String, Object>> protocols = new LinkedHashMap<>();
        for (Map<String, Object> row : original.get(1)) {
            Long applicationId = number(row.get("application_id"));
            require(apps.containsKey(applicationId) && protocols.putIfAbsent(applicationId, row) == null, "PROTOCOL_ASSOCIATION");
        }
        List<Map<String, Object>> applicationRows = new ArrayList<>();
        List<Map<String, Object>> protocolRows = new ArrayList<>();
        List<Map<String, Object>> associations = new ArrayList<>();
        Map<Long, Map<String, Long>> routes = new LinkedHashMap<>();
        long nextProtocol = nextId(connection, OLD.get(1));
        long nextAssociation = nextId(connection, OLD.get(2));
        for (Map<String, Object> app : original.get(0)) {
            Long id = number(app.get("id"));
            String purpose = text(app, "purpose");
            require(Set.of("APPLICATION", "RESOURCE_SERVER").contains(purpose), "APPLICATION_PURPOSE");
            require(Set.of("DRAFT", "ENABLED").contains(text(app, "configuration_status")), "APPLICATION_STATUS");
            String architecture = text(app, "application_type");
            require(architecture.isEmpty() && !active(app)
                || Set.of("WEB_SERVER", "SPA", "NATIVE", "SERVICE", "MINIAPP").contains(architecture), "APPLICATION_TYPE");
            if (active(app) && "APPLICATION".equals(purpose)) {
                require(app.get("tenant_id") != null, "APPLICATION_DIRECTORY");
                require("SERVICE".equals(architecture)
                    || Set.of("member", "employee", "odc", "supplier").contains(text(app, "user_type")), "APPLICATION_DIRECTORY");
            }
            for (String flag : List.of("require_proof_key", "require_authorization_consent", "reuse_refresh_tokens")) {
                enabled(app.get(flag));
            }
            Set<String> hosted = csv(text(app, "hosted_login_methods"));
            require(Set.of("PASSWORD", "SMS", "EMAIL").containsAll(hosted), "HOSTED_METHOD");
            require(!"MINIAPP".equals(architecture) || Set.of("WECHAT", "WECOM").contains(text(app, "miniapp_platform")), "MINIAPP_PLATFORM");
            require("MINIAPP".equals(architecture) || text(app, "miniapp_platform").isEmpty(), "MINIAPP_PLATFORM");
            String mode = text(app, "web_login_mode");
            require(Set.of("WEB_SERVER", "SPA").contains(architecture)
                ? mode.isEmpty() || Set.of("BROWSER", "IN_APP").contains(mode) : mode.isEmpty(), "APPLICATION_MODE");
            Map<String, Object> application = subset(app, COMMON);
            application.putAll(subset(app, AUDIT));
            application.put("application_code", app.get("client_id"));
            application.put("application_code_issued_at", app.get("client_id_issued_at"));
            application.put("application_name", app.get("client_name"));
            Map<String, Long> browser = browserRoutes(app, sources);
            routes.put(id, new LinkedHashMap<>(browser));
            String direct = text(app, "direct_login_provider_type");
            List<String> directCapabilities = browser.keySet().stream().filter(capability -> provider(capability).equals(direct)).toList();
            require(direct.isBlank() || directCapabilities.size() == 1, "DIRECT_SOURCE_AMBIGUITY");
            application.put("direct_login_capability", direct.isBlank() ? null : directCapabilities.get(0));
            applicationRows.add(application);
            for (var route : browser.entrySet()) {
                associations.add(association(nextAssociation++, id, route.getKey(), route.getValue(), app));
            }
            Map<String, Object> existing = protocols.get(id);
            if (!text(app, "introspection_resources").isBlank()
                && (existing != null || "RESOURCE_SERVER".equals(purpose))) {
                Set<String> introspection = csv(text(app, "introspection_resources"));
                Map<String, Object> permissions = object(text(app, "resource_permissions"));
                require(new TreeSet<>(permissions.values().stream().map(String::valueOf).toList()).equals(introspection), "LEGACY_RESOURCE_POLICY");
            }
            if ("RESOURCE_SERVER".equals(purpose)) {
                require(existing == null && browser.isEmpty() && csv(text(app, "external_code_types")).isEmpty(), "RESOURCE_BUSINESS_POLICY");
                if (!active(app) && app.get("client_secret") == null && app.get("client_secret_expires_at") == null && !hasLegacyPolicy(app)) {
                    continue;
                }
                Map<String, Object> settings = legacyOauth(app);
                require(csv(String.valueOf(settings.get("authorizationGrantTypes"))).isEmpty()
                    && csv(String.valueOf(settings.get("scopes"))).isEmpty()
                    && csv(String.valueOf(settings.get("redirectUris"))).isEmpty(), "RESOURCE_BUSINESS_POLICY");
                validateOauth(settings, "OAUTH2_1", app, true);
                protocolRows.add(protocol(app, null, nextProtocol++, "OAUTH2_1", 2, active(app), settings, Set.of()));
            } else if (existing != null) {
                String protocol = text(existing, "protocol");
                require(Set.of("OIDC", "OAUTH2_1").contains(protocol) && number(existing.get("schema_version")) == 1, "PROTOCOL_VERSION");
                Map<String, Object> settings = object(text(existing, "configuration"));
                Set<String> keys = new LinkedHashSet<>(OAUTH_FIELDS.values());
                keys.add("kind");
                require(keys.containsAll(settings.keySet()) && (!settings.containsKey("kind") || "oauth2".equals(settings.get("kind"))), "PROTOCOL_FIELDS");
                require(!enabled(existing.get("enabled")) || active(app), "PROTOCOL_STATUS");
                require(!active(app) || enabled(existing.get("enabled")), "PROTOCOL_STATUS");
                Set<String> allowed = csv(text(app, "external_code_types"));
                require(CODE_CAPABILITIES.keySet().containsAll(allowed), "CODE_TYPE");
                Set<String> grants = csv(value(settings, "authorizationGrantTypes"));
                require(allowed.isEmpty() || grants.contains(EXTERNAL_GRANT), "CODE_GRANT_CONFLICT");
                require(!active(app) || !grants.contains(EXTERNAL_GRANT) || !allowed.isEmpty(), "CODE_GRANT_CONFLICT");
                validateOauth(settings, protocol, app, false);
                protocolRows.add(protocol(app, existing, number(existing.get("id")), protocol, 2,
                    enabled(existing.get("enabled")), settings, allowed));
            } else {
                require(!active(app), "MISSING_ACTIVE_PROTOCOL");
                Map<String, Object> shadow = subset(app, new ArrayList<>(OAUTH_FIELDS.keySet()));
                for (String column : List.of("protocol_mode", "enabled_protocols", "default_protocol", "external_code_types", "introspection_resources")) {
                    shadow.put(column, app.get(column));
                }
                // 已知旧槽保留所有V26协议影子，不将其解释成可执行默认协议。
                if (app.get("client_secret") != null || app.get("client_secret_expires_at") != null
                    || hasLegacyPolicy(app)) {
                    Map<String, Object> slot = protocol(app, null, nextProtocol++, null, 0, false, Map.of(), Set.of());
                    slot.put("configuration", bounded(JSON.writeValueAsString(shadow)));
                    protocolRows.add(slot);
                }
            }
        }
        Map<Long, Long> externalApplications = externalApplications(connection);
        for (Map<String, Object> old : original.get(2)) {
            Long applicationId = externalApplications.get(number(old.get("id")));
            require(applicationId != null && apps.containsKey(applicationId), "EXTERNAL_ORPHAN");
            String code = text(old, "code_type");
            String capability = CODE_CAPABILITIES.get(code);
            require(capability != null, "CODE_TYPE");
            Map<String, Object> app = apps.get(applicationId);
            validateCodeScene(code, app);
            require(csv(text(app, "external_code_types")).contains(code), "EXTERNAL_PERMISSION_CONFLICT");
            Long sourceId = number(old.get("identity_provider_id"));
            validateSource(capability, sourceId, sources, active(app));
            require(routes.get(applicationId).putIfAbsent(capability, sourceId) == null, "ROUTE_CONFLICT");
            associations.add(association(number(old.get("id")), applicationId, capability, sourceId, old));
        }
        for (Map<String, Object> app : original.get(0)) {
            Set<String> expectedCodes = csv(text(app, "external_code_types"));
            require(CODE_CAPABILITIES.keySet().containsAll(expectedCodes), "CODE_TYPE");
            for (String code : expectedCodes) {
                require(routes.get(number(app.get("id"))).containsKey(CODE_CAPABILITIES.get(code)), "EXTERNAL_ORPHAN");
            }
            Map<String, Long> fixed = routes.get(number(app.get("id")));
            String type = text(app, "application_type");
            boolean inApp = "IN_APP".equals(text(app, "web_login_mode"));
            if (Set.of("NATIVE", "MINIAPP").contains(type) || inApp) {
                require(new LinkedHashSet<>(fixed.values()).size() <= 1, "MULTIPLE_IDENTITY_SOURCES");
            }
            require(!"NATIVE".equals(type) || fixed.size() <= 1, "MULTIPLE_NATIVE_CAPABILITIES");
            require(!inApp || fixed.size() <= 1 && (!active(app) || fixed.size() == 1), "INAPP_CAPABILITY_COUNT");
        }
        List<Map<String, Object>> bindings = new ArrayList<>();
        for (Map<String, Object> old : original.get(3)) {
            require(sources.containsKey(number(old.get("registration_id"))), "BINDING_ORPHAN");
            Map<String, Object> binding = new LinkedHashMap<>(old);
            binding.put("identity_provider_id", binding.remove("registration_id"));
            bindings.add(binding);
        }
        return List.of(applicationRows, protocolRows, associations, bindings);
    }

    /** 原数据库排序规则解析client_id，不能用Java小写替代MySQL的唯一语义。 */
    private Map<Long, Long> externalApplications(Connection connection) throws SQLException {
        Map<Long, Long> result = new LinkedHashMap<>();
        try (var statement = connection.prepareStatement("SELECT e.id,c.id AS application_id FROM sys_oauth2_client_external_provider e JOIN sys_oauth2_client c ON c.client_id=e.client_id");
             var rows = statement.executeQuery()) {
            while (rows.next()) {
                require(result.putIfAbsent(rows.getLong("id"), rows.getLong("application_id")) == null, "EXTERNAL_AMBIGUITY");
            }
        }
        return result;
    }

    /** 非空能力JSON是权威；旧厂商JSON只能按来源和当前模式唯一映射。 */
    private Map<String, Long> browserRoutes(Map<String, Object> app, Map<Long, Map<String, Object>> sources) {
        boolean capabilities = app.get("browser_capability_bindings") != null;
        Map<String, Object> declared = object(capabilities ? text(app, "browser_capability_bindings") : text(app, "browser_provider_bindings"));
        Map<String, Long> result = new LinkedHashMap<>();
        for (var entry : declared.entrySet()) {
            Long sourceId = number(entry.getValue());
            Map<String, Object> source = sources.get(sourceId);
            require(source != null, "BROWSER_ORPHAN");
            String capability = entry.getKey();
            if (!capabilities) {
                String provider = entry.getKey();
                List<String> candidates = CAPABILITY_SOURCE.keySet().stream().filter(key -> provider(key).equals(provider))
                    .filter(key -> browserCapability(key, app))
                    .filter(key -> credential(CAPABILITY_SOURCE.get(key)).equals(credential(text(source, "source_type")))).toList();
                require(candidates.size() == 1, "BROWSER_AMBIGUITY");
                capability = candidates.get(0);
            }
            require(browserCapability(capability, app), "BROWSER_MODE");
            validateSource(capability, sourceId, sources, active(app));
            require(result.putIfAbsent(capability, sourceId) == null, "ROUTE_CONFLICT");
        }
        Set<String> providers = csv(text(app, "browser_login_provider_types"));
        require(capabilities || providers.equals(new TreeSet<>(result.keySet().stream().map(this::provider).toList())), "BROWSER_PERMISSION_CONFLICT");
        return result;
    }

    /** 冻结外部Code的终端场景，不把公共客户端认证当成跨终端许可。 */
    private void validateCodeScene(String code, Map<String, Object> app) {
        boolean compatible = switch (text(app, "application_type")) {
            case "WEB_SERVER", "SPA" -> "IN_APP".equals(text(app, "web_login_mode"))
                && Set.of("FEISHU_WEBVIEW_CODE", "DINGTALK_WEBVIEW_CODE").contains(code);
            case "NATIVE" -> Set.of("FEISHU_APP_CODE", "WECOM_APP_CODE", "DINGTALK_APP_CODE",
                "WECHAT_OPEN_APP_CODE", "ALIYUN_MOBILE_TOKEN").contains(code);
            case "MINIAPP" -> "WECHAT".equals(text(app, "miniapp_platform"))
                ? Set.of("WECHAT_MINIAPP_LOGIN_CODE", "WECHAT_MINIAPP_PHONE_CODE").contains(code)
                : "WECOM".equals(text(app, "miniapp_platform")) && "WECOM_MINIAPP_CODE".equals(code);
            default -> false;
        };
        require(compatible, "CODE_SCENE");
    }

    /** 冻结V26的浏览器/端内能力选择；不会创建新登录入口。 */
    private boolean browserCapability(String capability, Map<String, Object> app) {
        if (!Set.of("WEB_SERVER", "SPA").contains(text(app, "application_type"))) {
            return false;
        }
        return "IN_APP".equals(text(app, "web_login_mode"))
            ? Set.of("WECHAT_OFFICIAL_ACCOUNT", "WECOM_WEBVIEW").contains(capability)
            : Set.of("WECHAT_WEBSITE", "WECOM_BROWSER", "FEISHU_WEB", "DINGTALK_WEB", "GITHUB_OAUTH").contains(capability);
    }

    /** 核对具体凭据域、能力与固定兑换flow，不在同厂商中任意择源。 */
    private void validateSource(String capability, Long id, Map<Long, Map<String, Object>> sources, boolean active) {
        Map<String, Object> source = sources.get(id);
        String expectedSource = CAPABILITY_SOURCE.get(capability);
        require(source != null && expectedSource != null, "SOURCE_REFERENCE");
        String expectedCredential = credential(expectedSource);
        String actualCredential = text(source, "credential_type");
        if (actualCredential.isBlank()) {
            actualCredential = credential(text(source, "source_type"));
        }
        require(expectedCredential.equals(actualCredential), "SOURCE_CREDENTIAL");
        String expectedProvider = "ALIYUN_MOBILE".equals(expectedSource) ? expectedSource : provider(capability);
        require(expectedProvider.equals(text(source, "provider_type")), "SOURCE_PROVIDER");
        Map<String, Object> capabilities = object(text(source, "capabilities_json"));
        String flow;
        if (source.get("capabilities_json") == null) {
            require(credential(text(source, "source_type")).equals(expectedCredential), "SOURCE_CAPABILITY");
            flow = text(source, "identity_flow");
            require(legacyCapabilities(text(source, "source_type")).contains(capability), "SOURCE_CAPABILITY");
        } else {
            require(capabilities.containsKey(capability) && capabilities.get(capability) instanceof String, "SOURCE_CAPABILITY");
            flow = String.valueOf(capabilities.get(capability));
        }
        Set<String> knownFlows = FLOWS.get(expectedSource);
        require(knownFlows != null && (!flow.isBlank() ? knownFlows.contains(flow) : !expectedSource.startsWith("FEISHU")), "SOURCE_FLOW");
        require(!active || !"FEISHU_WEBVIEW_V1".equals(flow), "SOURCE_FLOW_UNIMPLEMENTED");
    }

    /** 普通空草稿没有协议登记；仅保留确有内容且不能无损丢弃的旧影子。 */
    private boolean hasLegacyPolicy(Map<String, Object> app) {
        for (String column : List.of("client_authentication_methods", "authorization_grant_types", "redirect_uris",
            "scopes", "allowed_origins", "jwk_set_url", "signing_algorithm", "token_format", "id_token_signature_algorithm",
            "protocol_mode", "enabled_protocols", "default_protocol", "external_code_types", "introspection_resources")) {
            if (!text(app, column).isBlank()) {
                return true;
            }
        }
        return List.of("authorization_code_time_to_live", "access_token_time_to_live", "refresh_token_time_to_live").stream()
            .anyMatch(column -> app.get(column) != null) || enabled(app.get("require_proof_key"))
            || enabled(app.get("require_authorization_consent"))
            || app.get("reuse_refresh_tokens") != null && !enabled(app.get("reuse_refresh_tokens"));
    }

    /** 固定旧格式字段名，只保留明确可解释的影子值。 */
    private Map<String, Object> legacyOauth(Map<String, Object> app) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kind", "oauth2");
        OAUTH_FIELDS.forEach((column, field) -> result.put(field, app.get(column)));
        result.put("allowedOrigins", csv(text(app, "allowed_origins")));
        for (var flag : Map.of("require_proof_key", "requireProofKey", "require_authorization_consent", "requireAuthorizationConsent",
            "reuse_refresh_tokens", "reuseRefreshTokens").entrySet()) {
            result.put(flag.getValue(), app.get(flag.getKey()) == null ? null : enabled(app.get(flag.getKey())));
        }
        return result;
    }

    /** 冻结的基本协议约束；历史迁移不能依赖会变化的运行注册表。 */
    private void validateOauth(Map<String, Object> settings, String protocol, Map<String, Object> app, boolean resource) {
        validateFieldTypes(settings);
        Set<String> auth = csv(value(settings, "clientAuthenticationMethods"));
        Set<String> grants = csv(value(settings, "authorizationGrantTypes"));
        Set<String> scopes = csv(value(settings, "scopes"));
        require(AUTH.containsAll(auth) && (!auth.contains("none") || auth.size() == 1), "OAUTH_AUTH");
        require(Set.of("authorization_code", "refresh_token", "client_credentials", EXTERNAL_GRANT).containsAll(grants), "OAUTH_GRANT");
        require(!active(app) || !auth.isEmpty(), "OAUTH_AUTH");
        require(!active(app) || auth.stream().noneMatch(method -> method.startsWith("client_secret_"))
            || !text(app, "client_secret").isBlank(), "MISSING_CREDENTIAL");
        if (resource) {
            require(!auth.isEmpty() && !auth.contains("none") && grants.isEmpty() && scopes.isEmpty()
                && csv(value(settings, "redirectUris")).isEmpty()
                && settings.get("allowedOrigins") instanceof Set<?> origins && origins.isEmpty(), "RESOURCE_AUTH");
        } else if (active(app)) {
            String applicationType = text(app, "application_type");
            require(!grants.isEmpty() && Set.of("WEB_SERVER", "SPA", "NATIVE", "SERVICE", "MINIAPP").contains(applicationType), "OAUTH_POLICY");
            require(!Set.of("SPA", "NATIVE", "MINIAPP").contains(applicationType) || auth.equals(Set.of("none")), "OAUTH_POLICY");
            require(!"WEB_SERVER".equals(applicationType) || !auth.contains("none"), "OAUTH_POLICY");
            require(!"SERVICE".equals(applicationType) || "OAUTH2_1".equals(protocol) && grants.equals(Set.of("client_credentials")), "OAUTH_POLICY");
            require(settings.get("requireProofKey") instanceof Boolean
                && settings.get("requireAuthorizationConsent") instanceof Boolean
                && settings.get("reuseRefreshTokens") instanceof Boolean
                && settings.get("allowedOrigins") instanceof List<?>, "OAUTH_REQUIRED_FIELDS");
            require(settings.get("authorizationCodeTimeToLive") != null && settings.get("accessTokenTimeToLive") != null
                && settings.get("refreshTokenTimeToLive") != null && settings.get("tokenFormat") != null, "OAUTH_REQUIRED_FIELDS");
            require(!auth.contains("none") || !grants.contains("refresh_token") && !grants.contains("client_credentials"), "OAUTH_POLICY");
            require(!"OIDC".equals(protocol) || scopes.contains("openid") && !grants.contains("client_credentials"), "OAUTH_POLICY");
            require(!"OAUTH2_1".equals(protocol) || !scopes.contains("openid"), "OAUTH_POLICY");
            require(!grants.contains("authorization_code") || Boolean.TRUE.equals(settings.get("requireProofKey"))
                && !csv(value(settings, "redirectUris")).isEmpty(), "OAUTH_POLICY");
        }
        require(!"OIDC".equals(protocol) || !grants.contains("client_credentials"), "OAUTH_POLICY");
        require(!"OAUTH2_1".equals(protocol) || !scopes.contains("openid"), "OAUTH_POLICY");
        if (auth.contains("private_key_jwt")) {
            String uriValue = value(settings, "jwkSetUrl");
            URI uri = URI.create(uriValue);
            require("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                && uri.getRawUserInfo() == null && uri.getRawFragment() == null
                && Set.of("RS256", "RS384", "RS512").contains(value(settings, "signingAlgorithm")), "OAUTH_JWKS");
        }
    }

    /** 冻结设置字段的类型和可选值边界，关闭草稿也不能吞掉错误类型。 */
    private void validateFieldTypes(Map<String, Object> settings) {
        for (String field : List.of("clientAuthenticationMethods", "authorizationGrantTypes", "redirectUris", "scopes",
            "jwkSetUrl", "signingAlgorithm", "tokenFormat", "idTokenSignatureAlgorithm")) {
            value(settings, field);
        }
        for (String field : List.of("requireProofKey", "requireAuthorizationConsent", "reuseRefreshTokens")) {
            require(settings.get(field) == null || settings.get(field) instanceof Boolean, "JSON_FIELD_TYPE");
        }
        for (var interval : Map.of("authorizationCodeTimeToLive", List.of(30L, 3600L),
            "accessTokenTimeToLive", List.of(60L, 86400L), "refreshTokenTimeToLive", List.of(300L, 31536000L)).entrySet()) {
            Object duration = settings.get(interval.getKey());
            if (duration != null) {
                require(duration instanceof Number, "JSON_FIELD_TYPE");
                long seconds = number(duration);
                require(seconds >= interval.getValue().get(0) && seconds <= interval.getValue().get(1), "OAUTH_TTL");
            }
        }
        Object origins = settings.get("allowedOrigins");
        require(origins == null || origins instanceof List<?> || origins instanceof Set<?>, "JSON_FIELD_TYPE");
        if (origins instanceof Iterable<?> items) {
            for (Object origin : items) {
                require(origin instanceof String, "JSON_FIELD_TYPE");
            }
        }
        String format = value(settings, "tokenFormat");
        require(format.isEmpty() || Set.of("reference", "self-contained").contains(format), "OAUTH_TOKEN_FORMAT");
    }

    /** 保存凭据原字符串和有效期，JSON中只存非秘密设置。 */
    private Map<String, Object> protocol(Map<String, Object> app, Map<String, Object> existing, long id,
        String protocol, int version, boolean enabled, Map<String, Object> settings, Set<String> codes) {
        Map<String, Object> result = subset(existing == null ? app : existing, AUDIT);
        result.put("id", id);
        result.put("application_id", app.get("id"));
        result.put("protocol", protocol);
        result.put("schema_version", version);
        result.put("enabled", enabled);
        result.put("protocol_identifier", app.get("client_id"));
        result.put("identifier_issued_at", app.get("client_id_issued_at"));
        result.put("credential_hash", app.get("client_secret"));
        result.put("credential_expires_at", app.get("client_secret_expires_at"));
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("oauth2", settings);
        configuration.put("externalCodeTypes", codes);
        result.put("configuration", bounded(JSON.writeValueAsString(configuration)));
        return result;
    }

    /** 浏览器新增关联沿用应用审计，原external关联沿用其自身主键与审计。 */
    private Map<String, Object> association(long id, Long application, String capability, Long provider, Map<String, Object> audit) {
        Map<String, Object> result = subset(audit, AUDIT);
        result.put("id", id);
        result.put("application_id", application);
        result.put("login_capability", capability);
        result.put("identity_provider_id", provider);
        return result;
    }

    /** 从旧DDL复制审计/索引精度后在空暂存表重分层，原表保持不变。 */
    private void createStaging(Connection connection) throws SQLException {
        for (int i = 0; i < OLD.size(); i++) {
            execute(connection, "CREATE TABLE " + PREFIX + TARGET.get(i) + " LIKE " + OLD.get(i));
        }
        Set<String> dropped = new LinkedHashSet<>(REMOVED);
        dropped.addAll(OAUTH_FIELDS.keySet());
        execute(connection, "ALTER TABLE " + PREFIX + TARGET.get(0) + " "
            + String.join(",", dropped.stream().map(column -> "DROP COLUMN " + column).toList())
            + ", CHANGE client_id application_code VARCHAR(100) COLLATE utf8mb4_unicode_ci NOT NULL"
            + ", CHANGE client_id_issued_at application_code_issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
            + ", CHANGE client_name application_name VARCHAR(200) COLLATE utf8mb4_unicode_ci NOT NULL"
            + ", ADD direct_login_capability VARCHAR(64) COLLATE utf8mb4_bin NULL, COMMENT='IAM接入应用公共配置'");
        execute(connection, "ALTER TABLE " + PREFIX + TARGET.get(1)
            + " MODIFY protocol VARCHAR(32) COLLATE utf8mb4_bin NULL, ADD protocol_identifier VARCHAR(512) COLLATE utf8mb4_bin NULL,"
            + " ADD identifier_issued_at TIMESTAMP NULL, ADD credential_hash VARCHAR(200) COLLATE utf8mb4_bin NULL,"
            + " ADD credential_expires_at TIMESTAMP NULL, ADD oauth2_identifier VARCHAR(100) COLLATE utf8mb4_unicode_ci"
            + " GENERATED ALWAYS AS (CASE WHEN protocol IN ('OIDC','OAUTH2_1') OR (protocol IS NULL AND schema_version=0) THEN protocol_identifier ELSE NULL END) STORED,"
            + " ADD UNIQUE KEY uk_iam_oauth2_identifier (oauth2_identifier),"
            + " ADD UNIQUE KEY uk_iam_protocol_identifier (protocol,protocol_identifier),"
            + " ADD CONSTRAINT ck_iam_protocol_slot CHECK ((protocol IS NULL AND schema_version=0 AND enabled=0) OR (protocol IS NOT NULL AND schema_version>0)), COMMENT='IAM应用唯一协议登记'");
        execute(connection, "ALTER TABLE " + PREFIX + TARGET.get(2)
            + " DROP INDEX uk_oauth2_client_external_code_type, CHANGE client_id application_id BIGINT NOT NULL,"
            + " CHANGE code_type login_capability VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,"
            + " ADD UNIQUE KEY uk_iam_application_capability (application_id,login_capability), COMMENT='IAM应用登录能力与身份源关联'");
        execute(connection, "ALTER TABLE " + PREFIX + TARGET.get(3) + " CHANGE registration_id identity_provider_id BIGINT NOT NULL");
    }

    /** 用预检白名单生成列清单，所有行值都通过JDBC绑定；不输出SQL异常原因。 */
    private void insert(Connection connection, String table, List<Map<String, Object>> rows) throws SQLException {
        for (Map<String, Object> row : rows) {
            String columns = String.join(",", row.keySet());
            String placeholders = String.join(",", row.keySet().stream().map(key -> "?").toList());
            try (var statement = connection.prepareStatement("INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")")) {
                int index = 1;
                for (Object value : row.values()) {
                    statement.setObject(index++, value);
                }
                statement.executeUpdate();
            }
        }
    }

    /** 保留已删除行消耗过的自增边界，避免未来重新分配旧标识。 */
    private void preserveSequences(Connection connection) throws SQLException {
        for (int i = 0; i < OLD.size(); i++) {
            long next = Math.max(nextId(connection, OLD.get(i)), nextId(connection, PREFIX + TARGET.get(i)));
            execute(connection, "ALTER TABLE " + PREFIX + TARGET.get(i) + " AUTO_INCREMENT=" + next);
        }
    }

    /** 只从系统元数据取得已知表的自增值，不接受外部表名或数字。 */
    private long nextId(Connection connection, String table) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT auto_increment FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?")) {
            statement.setString(1, table);
            try (var rows = statement.executeQuery()) {
                require(rows.next(), "MISSING_SEQUENCE");
                return Math.max(1, rows.getLong(1));
            }
        }
    }

    /** 核对所有语义字段；生成列是派生值，不作为第二写入权威。 */
    private void verify(Connection connection, String prefix, List<List<Map<String, Object>>> expected) throws SQLException {
        for (int i = 0; i < TARGET.size(); i++) {
            Map<Long, Map<String, Object>> actual = index(read(connection, prefix + TARGET.get(i)));
            require(actual.size() == expected.get(i).size(), "COPY_COUNT");
            for (Map<String, Object> row : expected.get(i)) {
                Map<String, Object> found = actual.get(number(row.get("id")));
                require(found != null, "COPY_ID");
                for (var field : row.entrySet()) {
                    Object left = field.getValue();
                    Object right = found.get(field.getKey());
                    boolean equal = "configuration".equals(field.getKey()) || "resource_permissions".equals(field.getKey())
                        ? Objects.equals(left == null ? null : JSON.readTree(String.valueOf(left)), right == null ? null : JSON.readTree(String.valueOf(right)))
                        : Objects.equals(comparable(left), comparable(right));
                    require(equal, "COPY_VALUE");
                }
            }
        }
    }

    /** 防止未来增列被V27悄悄丢弃，空库也检查完整旧schema。 */
    private void requireColumns(Connection connection, String table, Set<String> expected) throws SQLException {
        Set<String> actual = new LinkedHashSet<>();
        try (var statement = connection.prepareStatement("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=?")) {
            statement.setString(1, table);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    actual.add(rows.getString(1));
                }
            }
        }
        require(actual.equals(expected), "UNKNOWN_V26_COLUMNS");
    }

    /** JDBC布尔/数字表示可不同，但凭据、标识与时间使用原值比较。 */
    private Object comparable(Object value) {
        if (value instanceof Boolean flag) {
            return new BigDecimal(flag ? 1 : 0);
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString()).stripTrailingZeros();
        }
        return value;
    }

    /** 执行固定DDL，异常由最外层统一脱敏。 */
    private void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    /** 建立内部主键索引并拒绝重复。 */
    private Map<Long, Map<String, Object>> index(List<Map<String, Object>> rows) {
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            require(result.putIfAbsent(number(row.get("id")), row) == null, "DUPLICATE_ID");
        }
        return result;
    }

    /** 复制已列明的列，禁止把原实体整体当作新持久化行。 */
    private Map<String, Object> subset(Map<String, Object> row, List<String> fields) {
        Map<String, Object> result = new LinkedHashMap<>();
        fields.forEach(field -> result.put(field, row.get(field)));
        return result;
    }

    /** JSON只接受有界对象；空值代表没有选择来源。 */
    private Map<String, Object> object(String value) {
        if (value == null || value.isBlank()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = JSON.readValue(bounded(value), new TypeReference<Map<String, Object>>() { });
        require(result != null, "JSON_OBJECT");
        return result;
    }

    /** UTF-8字节边界与运行协议一致，不根据字符数估算。 */
    private String bounded(String value) {
        require(value.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES, "CONFIG_SIZE");
        return value;
    }

    /** CSV原值不改写；空/重复片段拒绝而非静默去重。 */
    private Set<String> csv(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        List<String> entries = Arrays.stream(value.split(",", -1)).map(String::trim).toList();
        require(entries.stream().noneMatch(String::isBlank) && Set.copyOf(entries).size() == entries.size(), "CSV_STRUCTURE");
        return new TreeSet<>(entries);
    }

    /** 数据列的null只用于判断缺省，不替换实际复制值。 */
    private String text(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value == null ? "" : String.valueOf(value);
    }

    /** JSON可选字符串不接受数组、对象或隐式类型转换。 */
    private String value(Map<String, Object> row, String field) {
        Object value = row.get(field);
        require(value == null || value instanceof String, "JSON_FIELD_TYPE");
        return value == null ? "" : String.valueOf(value);
    }

    /** 标识必须是精确整数，不能截断小数或溢出。 */
    private Long number(Object value) {
        require(value != null, "MISSING_ID");
        return new BigDecimal(String.valueOf(value)).longValueExact();
    }

    /** 保留布尔列的JDBC 0/1语义。 */
    private boolean enabled(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        require(value instanceof Number, "BOOLEAN_VALUE");
        long flag = number(value);
        require(flag == 0 || flag == 1, "BOOLEAN_VALUE");
        return flag == 1;
    }

    /** 应用启用是现有显式状态，不推断或回填。 */
    private boolean active(Map<String, Object> app) {
        return "ENABLED".equals(text(app, "configuration_status"));
    }

    /** 浏览器厂商仅作兼容投影，持久化使用具体能力。 */
    private String provider(String capability) {
        return capability.startsWith("GITHUB") ? "GITHUB" : capability.substring(0, capability.indexOf('_'));
    }

    /** 企业应用凭据可提供多个明确能力，不能跨真实应用种类复用。 */
    private String credential(String source) {
        for (String enterprise : List.of("FEISHU", "WECOM", "DINGTALK")) {
            if (source.startsWith(enterprise + "_")) {
                return enterprise + "_APPLICATION";
            }
        }
        return source;
    }

    /** V22以前来源的原能力集合，不为同厂商其他来源增加能力。 */
    private Set<String> legacyCapabilities(String source) {
        return new TreeSet<>(CAPABILITY_SOURCE.entrySet().stream().filter(entry -> entry.getValue().equals(source)).map(Map.Entry::getKey).toList());
    }

    /** 冻结V26列与v1字段映射，不能导入运行模型替代此合同。 */
    private static Map<String, String> oauthFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("client_authentication_methods", "clientAuthenticationMethods");
        fields.put("authorization_grant_types", "authorizationGrantTypes");
        fields.put("redirect_uris", "redirectUris");
        fields.put("scopes", "scopes");
        fields.put("allowed_origins", "allowedOrigins");
        fields.put("require_proof_key", "requireProofKey");
        fields.put("require_authorization_consent", "requireAuthorizationConsent");
        fields.put("jwk_set_url", "jwkSetUrl");
        fields.put("signing_algorithm", "signingAlgorithm");
        fields.put("authorization_code_time_to_live", "authorizationCodeTimeToLive");
        fields.put("access_token_time_to_live", "accessTokenTimeToLive");
        fields.put("token_format", "tokenFormat");
        fields.put("reuse_refresh_tokens", "reuseRefreshTokens");
        fields.put("refresh_token_time_to_live", "refreshTokenTimeToLive");
        fields.put("id_token_signature_algorithm", "idTokenSignatureAlgorithm");
        return Map.copyOf(fields);
    }

    /** 冻结已存在的能力到具体来源合同。 */
    private static Map<String, String> capabilitySources() {
        return Map.ofEntries(Map.entry("WECHAT_WEBSITE", "WECHAT_WEBSITE"), Map.entry("WECHAT_OFFICIAL_ACCOUNT", "WECHAT_OFFICIAL_ACCOUNT"),
            Map.entry("WECHAT_MOBILE_APP", "WECHAT_MOBILE_APP"), Map.entry("WECHAT_MINIAPP_LOGIN", "WECHAT_MINIAPP"),
            Map.entry("WECHAT_MINIAPP_PHONE", "WECHAT_MINIAPP"), Map.entry("WECOM_BROWSER", "WECOM_WEB"),
            Map.entry("WECOM_WEBVIEW", "WECOM_WEB"), Map.entry("WECOM_SDK", "WECOM_SDK"), Map.entry("WECOM_MINIAPP", "WECOM_MINIAPP"),
            Map.entry("FEISHU_WEB", "FEISHU_WEB"), Map.entry("FEISHU_APPLINK", "FEISHU_APPLINK"), Map.entry("FEISHU_WEBVIEW", "FEISHU_WEBVIEW"),
            Map.entry("DINGTALK_WEB", "DINGTALK_WEB"), Map.entry("DINGTALK_SDK", "DINGTALK_SDK"), Map.entry("DINGTALK_WEBVIEW", "DINGTALK_WEBVIEW"),
            Map.entry("GITHUB_OAUTH", "GITHUB_OAUTH"), Map.entry("ALIYUN_MOBILE", "ALIYUN_MOBILE"));
    }

    /** 冻结V26兑换flow，不因未来策略扩展改变此历史迁移。 */
    private static Map<String, Set<String>> flows() {
        return Map.ofEntries(Map.entry("WECHAT_WEBSITE", Set.of("WECHAT_WEBSITE_V1")),
            Map.entry("WECHAT_OFFICIAL_ACCOUNT", Set.of("WECHAT_OFFICIAL_V1")), Map.entry("WECHAT_MOBILE_APP", Set.of("WECHAT_OPEN_APP_V1")),
            Map.entry("WECHAT_MINIAPP", Set.of("WECHAT_MINIAPP_V1")), Map.entry("WECOM_WEB", Set.of("WECOM_WEB_V1")),
            Map.entry("WECOM_SDK", Set.of("WECOM_SDK_V1")), Map.entry("WECOM_MINIAPP", Set.of("WECOM_MINIAPP_V1")),
            Map.entry("FEISHU_WEB", Set.of("FEISHU_OAUTH_V3")), Map.entry("FEISHU_APPLINK", Set.of("FEISHU_APPLINK_V3")),
            Map.entry("FEISHU_WEBVIEW", Set.of("FEISHU_WEBVIEW_V1", "FEISHU_WEBVIEW_V3")),
            Map.entry("DINGTALK_WEB", Set.of("DINGTALK_OAUTH_V1")), Map.entry("DINGTALK_SDK", Set.of("DINGTALK_OAUTH_V1")),
            Map.entry("DINGTALK_WEBVIEW", Set.of("DINGTALK_CORP_H5_V1")), Map.entry("GITHUB_OAUTH", Set.of("GITHUB_OAUTH_V1")),
            Map.entry("ALIYUN_MOBILE", Set.of("ALIYUN_MOBILE_V1")));
    }

    /** 只能传本类固定问题代码，业务值不会出现在异常消息或cause中。 */
    private void require(boolean condition, String code) {
        if (!condition) {
            throw new Problem(code);
        }
    }

    /**
     * 有界迁移诊断；不保存原始异常或数据对象。
     * @since 6.1.0
     */
    private static final class Problem extends RuntimeException {
        private final String code;
        /** 创建固定诊断代码，外层负责添加阶段。 */
        private Problem(String code) {
            this.code = code;
        }
    }
}
