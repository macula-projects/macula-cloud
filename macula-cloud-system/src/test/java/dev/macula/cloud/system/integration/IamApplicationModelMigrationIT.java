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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import db.migration.V27__iam_application_model;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import dev.macula.cloud.system.mapper.SysIamApplicationMapper;
import dev.macula.cloud.system.mapper.SysIamApplicationProtocolConfigMapper;
import dev.macula.cloud.system.mapper.SysIamApplicationIdentityProviderMapper;
import org.apache.ibatis.logging.slf4j.Slf4jImpl;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import dev.macula.cloud.system.protocol.OAuth2ProtocolDefinition;
import dev.macula.cloud.system.protocol.OAuth2ProtocolSettings;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 真实隔离MySQL验证V27数据保真、唯一约束与DDL阶段失败；不连接业务库。
 * @author Rain
 * @since 6.1.0
 */
@EnabledIfEnvironmentVariable(named = "IAM_APPLICATION_MODEL_IT", matches = "true")
class IamApplicationModelMigrationIT {
    private static final String SYNTHETIC_HASH = "{noop}synthetic-preserved-hash";
    private final List<String> databases = new ArrayList<>();
    private final JsonMapper json = JsonMapper.builder().build();
    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private DriverManagerDataSource dataSource;

    /** 全新库运行全部历史迁移，基线旧凭据保留为不可执行槽。 */
    @Test
    void freshDatabaseMigratesAllTheWayWithoutGuessingProtocol() {
        isolated(false);
        assertThat(flyway("27").migrate().migrationsExecuted).isEqualTo(27);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_iam_application", Integer.class)).isPositive();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_iam_application_protocol_config WHERE protocol IS NOT NULL", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_iam_application_protocol_config WHERE protocol IS NULL AND schema_version=0 AND enabled=0", Integer.class)).isPositive();
        assertThat(flyway("27").migrate().migrationsExecuted).isZero();
        assertNoOldOrStagingTables();
    }

    /** 所有旧公开标识、签发时间、哈希和有效期逐值保持，不依赖重新编码。 */
    @Test
    void preservesAssignedAndUnassignedCredentialsAndAudit() {
        isolated(true);
        long active = application("OriginalCase", "APPLICATION", "ENABLED", SYNTHETIC_HASH);
        var settings = settings("client_secret_basic", "authorization_code,refresh_token", "openid,profile");
        long protocolId = addProtocol(active, "OIDC", settings);
        long legacy = application("LegacyUnassigned", "APPLICATION", "DRAFT", "{bcrypt}synthetic-original-encoding");
        jdbc.update("UPDATE sys_oauth2_client SET authorization_grant_types='password',introspection_resources='historical-only' WHERE id=?", legacy);
        jdbc.update("UPDATE sys_oauth2_client SET client_secret_expires_at='2035-02-03 04:05:06' WHERE id=?", active);
        jdbc.update("UPDATE sys_oauth2_client SET client_secret_expires_at='2036-03-04 05:06:07' WHERE id=?", legacy);
        jdbc.execute("ALTER TABLE sys_oauth2_client AUTO_INCREMENT=9000");
        var expected = jdbc.queryForList("SELECT id,client_id,client_id_issued_at,client_secret,client_secret_expires_at,create_time,last_update_time FROM sys_oauth2_client ORDER BY id");
        var protocolAudit = jdbc.queryForMap("SELECT id,create_by,create_time,last_update_by,last_update_time FROM sys_application_protocol_config WHERE id=?", protocolId);
        flyway("27").migrate();
        var actual = jdbc.queryForList("SELECT a.id,a.application_code AS client_id,a.application_code_issued_at AS client_id_issued_at,p.credential_hash AS client_secret,p.credential_expires_at AS client_secret_expires_at,a.create_time,a.last_update_time FROM sys_iam_application a LEFT JOIN sys_iam_application_protocol_config p ON p.application_id=a.id ORDER BY a.id");
        assertThat(actual).isEqualTo(expected);
        assertThat(jdbc.queryForObject("SELECT auto_increment FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='sys_iam_application'", Long.class)).isGreaterThanOrEqualTo(9000L);
        assertThat(jdbc.queryForMap("SELECT id,create_by,create_time,last_update_by,last_update_time FROM sys_iam_application_protocol_config WHERE id=?", protocolId)).isEqualTo(protocolAudit);
        var row = jdbc.queryForMap("SELECT * FROM sys_iam_application_protocol_config WHERE application_id=?", active);
        var registration = OAuth2ProtocolDefinition.registry().read("OIDC", 2, true, "OriginalCase", null,
            String.valueOf(row.get("configuration")), "WEB_SERVER", "APPLICATION");
        assertThat(registration.assigned()).isTrue();
        assertThat(OAuth2ProtocolDefinition.registry().requiresSecret(registration)).isTrue();
        String shadow = jdbc.queryForObject("SELECT configuration FROM sys_iam_application_protocol_config WHERE application_id=?", String.class, legacy);
        assertThat(json.readTree(shadow).get("authorization_grant_types").asString()).isEqualTo("password");
        assertThat(json.readTree(shadow).get("introspection_resources").asString()).isEqualTo("historical-only");
        assertThat(shadow).doesNotContain("synthetic-original-encoding");
        assertNoOldOrStagingTables();
    }

    /** 普通空草稿没有协议行，标识时间由中立主表承载，不猜填默认协议。 */
    @Test
    void emptyDraftHasNoProtocolAndRetainsApplicationCodeTime() {
        isolated(true);
        long id = application("empty-draft", "APPLICATION", "DRAFT", null);
        Object issued = jdbc.queryForObject("SELECT client_id_issued_at FROM sys_oauth2_client WHERE id=?", Object.class, id);
        flyway("27").migrate();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_iam_application_protocol_config WHERE application_id=?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT application_code_issued_at FROM sys_iam_application WHERE id=?", Object.class, id)).isEqualTo(issued);
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_iam_application'", String.class))
            .doesNotContain("client_secret", "scopes", "enabled_protocols", "default_protocol", "browser_capability_bindings");
    }

    /** 空资源身份草稿不猜造认证方式；启用后缺少凭据仍会被拒绝。 */
    @Test
    void emptyResourceDraftKeepsNoProtocolRegistration() {
        isolated(true);
        long id = application("empty-resource-draft", "RESOURCE_SERVER", "DRAFT", null);
        jdbc.update("UPDATE sys_oauth2_client SET application_type='SERVICE',web_login_mode=NULL WHERE id=?", id);
        flyway("27").migrate();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_iam_application_protocol_config WHERE application_id=?", Integer.class, id)).isZero();
    }

    /** 资源服务器内部登记保留Secret或JWKS，不产生业务授予及来源入口。 */
    @ParameterizedTest
    @ValueSource(strings = {"client_secret_basic", "private_key_jwt"})
    void resourceIdentitiesRetainManagementAuthenticationOnly(String method) {
        isolated(true);
        long id = application("management-" + method, "RESOURCE_SERVER", "ENABLED", method.startsWith("client_secret") ? SYNTHETIC_HASH : null);
        jdbc.update("UPDATE sys_oauth2_client SET application_type='SERVICE',web_login_mode=NULL,client_authentication_methods=?,jwk_set_url=?,signing_algorithm=? WHERE id=?", method,
            "private_key_jwt".equals(method) ? "https://synthetic.example/jwks" : null,
            "private_key_jwt".equals(method) ? "RS256" : null, id);
        flyway("27").migrate();
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM sys_iam_application_protocol_config WHERE application_id=?", id);
        assertThat(row.get("protocol")).isEqualTo("OAUTH2_1");
        var registration = OAuth2ProtocolDefinition.registry().read("OAUTH2_1", 2, true, "management-" + method, null,
            String.valueOf(row.get("configuration")), "SERVICE", "RESOURCE_SERVER");
        assertThat(OAuth2ProtocolDefinition.registry().requiresSecret(registration)).isEqualTo(method.startsWith("client_secret"));
        assertThat(json.readTree(String.valueOf(row.get("configuration"))).get("oauth2").get("authorizationGrantTypes").asString()).isEmpty();
    }

    /** 浏览器和Code路由合并为明确能力，身份键大小写、绑定主键及旧表均保持。 */
    @Test
    void preservesIdentityBindingsAndNormalizesBothSourceRoutes() {
        isolated(true);
        long browser = application("browser", "APPLICATION", "DRAFT", null);
        long source = source("GITHUB", "GITHUB_OAUTH", "GITHUB_OAUTH", "GITHUB_OAUTH_V1");
        jdbc.update("UPDATE sys_oauth2_client SET browser_login_provider_types='GITHUB',browser_provider_bindings=?,direct_login_provider_type='GITHUB' WHERE id=?", "{\"GITHUB\":" + source + "}", browser);
        long explicit = application("explicit-capability", "APPLICATION", "DRAFT", null);
        jdbc.update("UPDATE sys_oauth2_client SET browser_login_provider_types='DINGTALK',browser_provider_bindings='{\"DINGTALK\":999999}',browser_capability_bindings=? WHERE id=?", "{\"GITHUB_OAUTH\":" + source + "}", explicit);
        long nativeApp = application("native", "APPLICATION", "DRAFT", null);
        jdbc.update("UPDATE sys_oauth2_client SET application_type='NATIVE',web_login_mode=NULL,external_code_types='FEISHU_APP_CODE' WHERE id=?", nativeApp);
        long feishu = source("FEISHU", "FEISHU_APPLINK", "FEISHU_APPLICATION", "FEISHU_APPLINK_V3");
        jdbc.update("INSERT INTO sys_oauth2_client_external_provider(id,client_id,code_type,identity_provider_id) VALUES(451,'NATIVE','FEISHU_APP_CODE',?)", feishu);
        for (String identity : List.of("CaseIdentity", "caseidentity")) {
            jdbc.update("INSERT INTO sys_registration_identity_binding(registration_id,identity_kind,identity_value,user_type,user_id) VALUES(?,'OPEN_ID',?,'employee',?)", source, identity, identity.startsWith("C") ? 3001 : 3002);
        }
        var before = jdbc.queryForList("SELECT id,registration_id AS identity_provider_id,identity_kind,identity_value,user_type,user_id,create_by,create_time,last_update_by,last_update_time FROM sys_registration_identity_binding ORDER BY id");
        jdbc.update("INSERT INTO sys_user_identity_binding(provider_type,provider_subject,username) VALUES('GITHUB','synthetic-legacy-subject','synthetic-legacy-user')");
        var oldBindings = jdbc.queryForList("SELECT * FROM sys_user_identity_binding ORDER BY id");
        var sources = jdbc.queryForList("SELECT * FROM sys_identity_provider ORDER BY id");
        flyway("27").migrate();
        assertThat(jdbc.queryForList("SELECT id,identity_provider_id,identity_kind,identity_value,user_type,user_id,create_by,create_time,last_update_by,last_update_time FROM sys_identity_provider_user_binding ORDER BY id")).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM sys_user_identity_binding ORDER BY id")).isEqualTo(oldBindings);
        assertThat(jdbc.queryForList("SELECT * FROM sys_identity_provider ORDER BY id")).isEqualTo(sources);
        assertThat(jdbc.queryForObject("SELECT direct_login_capability FROM sys_iam_application WHERE id=?", String.class, browser)).isEqualTo("GITHUB_OAUTH");
        assertThat(jdbc.queryForObject("SELECT identity_provider_id FROM sys_iam_application_identity_provider WHERE application_id=? AND login_capability='GITHUB_OAUTH'", Long.class, explicit)).isEqualTo(source);
        assertThat(jdbc.queryForMap("SELECT application_id,login_capability,identity_provider_id FROM sys_iam_application_identity_provider WHERE id=451"))
            .containsEntry("application_id", nativeApp).containsEntry("login_capability", "FEISHU_APPLINK").containsEntry("identity_provider_id", feishu);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO sys_identity_provider_user_binding(identity_provider_id,identity_kind,identity_value,user_type,user_id) VALUES(?,'OPEN_ID','CaseIdentity','employee',5000)", source));
    }

    /** 实际MP Mapper验证新字段映射、受保护凭据读写和事务锁定；日志不得出现合成秘密。 */
    @Test
    void actualMappersReadAndWriteProtectedColumnsWithoutSqlParameterLogs() throws Exception {
        isolated(true);
        long id = application("mapper-fixture", "APPLICATION", "ENABLED", SYNTHETIC_HASH);
        addProtocol(id, "OIDC", settings("client_secret_basic", "authorization_code", "openid"));
        flyway("27").migrate();
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(Slf4jImpl.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        var sqlFactory = factory.getObject();
        sqlFactory.getConfiguration().addMapper(SysIamApplicationMapper.class);
        sqlFactory.getConfiguration().addMapper(SysIamApplicationProtocolConfigMapper.class);
        sqlFactory.getConfiguration().addMapper(SysIamApplicationIdentityProviderMapper.class);
        var template = new SqlSessionTemplate(sqlFactory);
        var apps = template.getMapper(SysIamApplicationMapper.class);
        var protocols = template.getMapper(SysIamApplicationProtocolConfigMapper.class);
        var routes = template.getMapper(SysIamApplicationIdentityProviderMapper.class);
        Logger logger = (Logger) LoggerFactory.getLogger("dev.macula.cloud.system.mapper");
        Level previousLevel = logger.getLevel();
        var captured = new ListAppender<ILoggingEvent>();
        captured.start();
        logger.setLevel(Level.INFO);
        logger.addAppender(captured);
        try {
            new TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(status -> {
                var app = apps.selectByIdForUpdate(id);
                assertThat(app.getApplicationCode()).isEqualTo("mapper-fixture");
                assertThat(app.getApplicationCodeIssuedAt().toString()).isEqualTo("2024-01-02T03:04:05");
                var protocol = protocols.selectByApplicationIdForUpdate(id);
                assertThat(SYNTHETIC_HASH.equals(protocol.getCredentialHash())).isTrue();
                assertThat(protocol.toString()).doesNotContain(SYNTHETIC_HASH);
                assertThat(json.writeValueAsString(protocol)).doesNotContain(SYNTHETIC_HASH, "credentialHash");
                protocol.setCredentialHash("synthetic-updated-hash");
                assertThat(protocols.updateById(protocol)).isEqualTo(1);
                assertThat("synthetic-updated-hash".equals(protocols.selectByApplicationIdForUpdate(id).getCredentialHash())).isTrue();
                assertThat(routes.selectByApplicationIdForUpdate(id)).isEmpty();
                return null;
            });
            assertThat(captured.list.stream().map(ILoggingEvent::getFormattedMessage).toList())
                .noneMatch(message -> message.contains(SYNTHETIC_HASH) || message.contains("synthetic-updated-hash"));
        } finally {
            logger.detachAppender(captured);
            logger.setLevel(previousLevel);
            captured.stop();
        }
    }

    /** MySQL大小写语义覆盖OIDC/OAuth2与未分配槽，不将生成列作为写入入口。 */
    @Test
    void enforcesSingleProtocolAndSharedOAuthIdentifierNamespace() {
        isolated(true);
        long id = application("ExistingID", "APPLICATION", "DRAFT", SYNTHETIC_HASH);
        flyway("27").migrate();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO sys_iam_application_protocol_config(application_id,protocol,schema_version,enabled,configuration,protocol_identifier) VALUES(9901,'OIDC',2,0,'{}','existingid')"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO sys_iam_application_protocol_config(application_id,protocol,schema_version,enabled,configuration,protocol_identifier) VALUES(?,'OAUTH2_1',2,0,'{}','another')", id));
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE sys_iam_application_protocol_config SET enabled=1 WHERE application_id=?", id));
        assertThat(jdbc.queryForObject("SELECT enabled FROM sys_iam_application_protocol_config WHERE application_id=?", Boolean.class, id)).isFalse();
        assertThat(jdbc.queryForObject("SELECT character_maximum_length FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_iam_application_protocol_config' AND column_name='protocol_identifier'", Integer.class)).isEqualTo(512);
    }

    /** 所有脏数据必须在首个DDL前拒绝，原数据及业务表结构保持。 */
    @ParameterizedTest
    @ValueSource(strings = {"unknown-column", "unknown-field", "version", "oversized", "orphan", "multiple", "resource-grant", "source-flow", "credential-type", "application-type", "application-mode", "missing-credential", "code-grant", "hosted-method", "miniapp-platform", "boolean-value"})
    void dirtyDataStopsBeforeAnyStagingDdl(String problem) {
        isolated(true);
        long id = application("dirty-fixture", "APPLICATION", "DRAFT", SYNTHETIC_HASH);
        long protocol = addProtocol(id, "OIDC", settings("client_secret_basic", "authorization_code", "openid"));
        switch (problem) {
            case "unknown-column" -> jdbc.execute("ALTER TABLE sys_oauth2_client ADD future_policy TEXT NULL");
            case "unknown-field" -> jdbc.update("UPDATE sys_application_protocol_config SET configuration=JSON_SET(configuration,'$.unexpectedSecret','synthetic-only') WHERE id=?", protocol);
            case "version" -> jdbc.update("UPDATE sys_application_protocol_config SET schema_version=9 WHERE id=?", protocol);
            case "oversized" -> jdbc.update("UPDATE sys_application_protocol_config SET configuration=JSON_SET(configuration,'$.redirectUris',?) WHERE id=?", "汉".repeat(12000), protocol);
            case "orphan" -> jdbc.update("UPDATE sys_application_protocol_config SET application_id=999999 WHERE id=?", protocol);
            case "multiple" -> {
                jdbc.execute("ALTER TABLE sys_application_protocol_config DROP INDEX uk_application_single_protocol");
                addProtocol(id, "OAUTH2_1", settings("client_secret_basic", "authorization_code", "profile"));
            }
            case "resource-grant" -> {
                jdbc.update("DELETE FROM sys_application_protocol_config WHERE id=?", protocol);
                jdbc.update("UPDATE sys_oauth2_client SET purpose='RESOURCE_SERVER',authorization_grant_types='client_credentials' WHERE id=?", id);
            }
            case "boolean-value" -> jdbc.update("UPDATE sys_application_protocol_config SET enabled=2 WHERE id=?", protocol);
            case "hosted-method" -> jdbc.update("UPDATE sys_oauth2_client SET hosted_login_methods='EMAIL,UNRECOGNIZED' WHERE id=?", id);
            case "miniapp-platform" -> jdbc.update("UPDATE sys_oauth2_client SET miniapp_platform='WECHAT' WHERE id=?", id);
            case "application-type" -> jdbc.update("UPDATE sys_oauth2_client SET application_type='IN_APP' WHERE id=?", id);
            case "application-mode" -> jdbc.update("UPDATE sys_oauth2_client SET web_login_mode='BOTH' WHERE id=?", id);
            case "missing-credential" -> {
                jdbc.update("UPDATE sys_oauth2_client SET configuration_status='ENABLED',client_secret=NULL WHERE id=?", id);
                jdbc.update("UPDATE sys_application_protocol_config SET enabled=1 WHERE id=?", protocol);
            }
            case "code-grant" -> jdbc.update("UPDATE sys_oauth2_client SET external_code_types='FEISHU_APP_CODE' WHERE id=?", id);
            case "source-flow", "credential-type" -> {
                long source = source("GITHUB", "GITHUB_OAUTH", "GITHUB_OAUTH", "GITHUB_OAUTH_V1");
                jdbc.update("UPDATE sys_oauth2_client SET browser_login_provider_types='GITHUB',browser_capability_bindings=? WHERE id=?", "{\"GITHUB_OAUTH\":" + source + "}", id);
                if ("source-flow".equals(problem)) {
                    jdbc.update("UPDATE sys_identity_provider SET capabilities_json='{\"GITHUB_OAUTH\":\"UNKNOWN_FLOW\"}' WHERE id=?", source);
                } else {
                    jdbc.update("UPDATE sys_identity_provider SET credential_type='WECHAT_WEBSITE' WHERE id=?", source);
                }
            }
            default -> throw new IllegalArgumentException("未知测试场景");
        }
        var original = jdbc.queryForList("SELECT * FROM sys_oauth2_client ORDER BY id");
        FlywayException error = assertThrows(FlywayException.class, () -> flyway("27").migrate());
        Throwable diagnostic = error;
        while (diagnostic.getCause() != null) {
            diagnostic = diagnostic.getCause();
        }
        assertThat(diagnostic.getMessage()).contains("stage=PRECHECK").doesNotContain(SYNTHETIC_HASH, "CaseIdentity", "synthetic-only");
        assertThat(jdbc.queryForList("SELECT * FROM sys_oauth2_client ORDER BY id")).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND (table_name LIKE 'v27_stage_%' OR table_name='sys_iam_application')", Integer.class)).isZero();
    }

    /** 即使复制语句成功，暂存凭据被改写也必须在RENAME之前被内容核对发现。 */
    @Test
    void corruptedStagingCredentialNeverPublishes() throws Exception {
        isolated(true);
        long id = application("corrupt-copy", "APPLICATION", "DRAFT", SYNTHETIC_HASH);
        try (Connection connection = dataSource.getConnection()) {
            var migration = new V27__corrupt_copy(connection, id);
            var error = assertThrows(FlywayException.class, () -> migration.migrate(context(connection)));
            assertThat(error.getMessage()).contains("stage=PREPUBLICATION", "issue=COPY_VALUE").doesNotContain(SYNTHETIC_HASH);
        }
        assertThat(SYNTHETIC_HASH.equals(jdbc.queryForObject("SELECT client_secret FROM sys_oauth2_client WHERE id=?", String.class, id))).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='sys_iam_application'", Integer.class)).isZero();
    }

    /** 各DDL边界模拟失败：发布前原表可用，发布后新旧数据都保留供人工恢复。 */
    @ParameterizedTest
    @ValueSource(strings = {"PRECHECK", "STAGING", "COPY", "PREPUBLICATION", "PUBLICATION", "VERIFICATION", "CLEANUP"})
    void stageFailureRetainsRecoverableRowsAndRedactsSqlExceptions(String stage) throws Exception {
        isolated(true);
        application("failure-fixture", "APPLICATION", "DRAFT", SYNTHETIC_HASH);
        var original = jdbc.queryForList("SELECT * FROM sys_oauth2_client ORDER BY id");
        try (Connection connection = dataSource.getConnection()) {
            Context context = new Context() {
                /** 本例直接调用固定迁移，不读取Spring或运行配置。 */
                @Override
                public Configuration getConfiguration() { return null; }
                /** 返回任务专属数据库连接。 */
                @Override
                public Connection getConnection() { return connection; }
            };
            FlywayException error = assertThrows(FlywayException.class, () -> new V27__failing_migration(stage).migrate(context));
            assertThat(error.getMessage()).contains("stage=" + stage).doesNotContain(SYNTHETIC_HASH);
            assertThat(error.getCause()).isNull();
        }
        boolean published = List.of("PUBLICATION", "VERIFICATION", "CLEANUP").contains(stage);
        String oldTable = published ? "v27_old_sys_oauth2_client" : "sys_oauth2_client";
        assertThat(jdbc.queryForList("SELECT * FROM " + oldTable + " ORDER BY id")).isEqualTo(original);
        if (published) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_iam_application", Integer.class)).isEqualTo(original.size());
        }
        try (Connection connection = dataSource.getConnection()) {
            if (!"PRECHECK".equals(stage)) {
                FlywayException retry = assertThrows(FlywayException.class, () -> new V27__iam_application_model().migrate(context(connection)));
                assertThat(retry.getMessage()).contains("EXISTING_RECOVERY_OBJECT");
            }
        }
    }

    /** 返回直接调用迁移所需的最小Flyway上下文。 */
    private Context context(Connection connection) {
        return new Context() {
            /** 不提供运行配置，迁移只能使用冻结规则。 */
            @Override
            public Configuration getConfiguration() { return null; }
            /** 返回测试专属连接。 */
            @Override
            public Connection getConnection() { return connection; }
        };
    }

    /** 建立专属随机库，绝不沿用用户正在运行的业务schema。 */
    private void isolated(boolean version26) {
        String url = System.getenv("IAM_APPLICATION_MODEL_IT_URL");
        assertThat(url).matches("jdbc:mysql://127[.]0[.]0[.]1:[0-9]+/mysql[?].+");
        var root = new DriverManagerDataSource(url, System.getenv("IAM_APPLICATION_MODEL_IT_USER"), System.getenv("IAM_APPLICATION_MODEL_IT_PASSWORD"));
        admin = new JdbcTemplate(root);
        String database = "macula_iam_model_it_" + UUID.randomUUID().toString().replace("-", "");
        databases.add(database);
        admin.execute("CREATE DATABASE " + database + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        dataSource = new DriverManagerDataSource(url.replace("/mysql?", "/" + database + "?"), System.getenv("IAM_APPLICATION_MODEL_IT_USER"), System.getenv("IAM_APPLICATION_MODEL_IT_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        if (version26) {
            flyway("26").migrate();
        }
    }

    /** 固定测试目标版本，不把历史SQL运行在改名后的表上。 */
    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load();
    }

    /** 创建合成应用，签发时间和审计时间使用固定历史值便于发现意外重写。 */
    private long application(String code, String purpose, String status, String hash) {
        jdbc.update("INSERT INTO sys_oauth2_client(client_id,client_name,client_id_issued_at,client_secret,client_secret_expires_at,purpose,configuration_status,application_type,web_login_mode,tenant_id,user_type,client_authentication_methods,authorization_grant_types,redirect_uris,scopes,create_time,last_update_time) VALUES(?,?,'2024-01-02 03:04:05',?,NULL,?,?,'WEB_SERVER','BROWSER',1,'employee','','','','','2024-01-02 03:04:05','2024-03-04 05:06:07')", code, code, hash, purpose, status);
        return jdbc.queryForObject("SELECT id FROM sys_oauth2_client WHERE client_id=?", Long.class, code);
    }

    /** 构造真实v1类型用于验证历史输出能被独立v2策略读取。 */
    private OAuth2ProtocolSettings settings(String auth, String grants, String scopes) {
        var result = new OAuth2ProtocolSettings();
        result.setClientAuthenticationMethods(auth);
        result.setAuthorizationGrantTypes(grants);
        result.setScopes(scopes);
        result.setRedirectUris("https://synthetic.example/callback");
        result.setRequireProofKey(true);
        result.setRequireAuthorizationConsent(false);
        return result;
    }

    /** 插入原协议记录，保留其独立主键和历史审计。 */
    private long addProtocol(long application, String protocol, OAuth2ProtocolSettings settings) {
        jdbc.update("INSERT INTO sys_application_protocol_config(application_id,protocol,schema_version,enabled,configuration,create_by,create_time,last_update_by,last_update_time) VALUES(?,?,1,(SELECT configuration_status='ENABLED' FROM sys_oauth2_client WHERE id=?),?,'fixture','2024-04-05 06:07:08','fixture','2024-05-06 07:08:09')", application, protocol, application, json.writeValueAsString(settings));
        return jdbc.queryForObject("SELECT id FROM sys_application_protocol_config WHERE application_id=? AND protocol=?", Long.class, application, protocol);
    }

    /** 来源包含固定能力和flow，不需要或输出真实平台凭据。 */
    private long source(String provider, String source, String credential, String flow) {
        String capability = "FEISHU_APPLINK".equals(source) ? "FEISHU_APPLINK" : "GITHUB_OAUTH";
        jdbc.update("INSERT INTO sys_identity_provider(name,provider_type,source_type,credential_type,identity_flow,capabilities_json,enabled,app_id,app_secret,secret_config_json) VALUES(?,?,?,?,?,?,1,'synthetic-application-id','synthetic-opaque-secret','synthetic-opaque-config')", source, provider, source, credential, flow, json.writeValueAsString(Map.of(capability, flow)));
        return jdbc.queryForObject("SELECT id FROM sys_identity_provider WHERE name=?", Long.class, source);
    }

    /** 正常完成后没有旧运行表或迁移暂存/备份对象。 */
    private void assertNoOldOrStagingTables() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND (table_name IN ('sys_oauth2_client','sys_application_protocol_config','sys_oauth2_client_external_provider','sys_registration_identity_binding') OR table_name LIKE 'v27_stage_%' OR table_name LIKE 'v27_old_%')", Integer.class)).isZero();
    }

    /** 仅删除本测试实际创建且前缀通过校验的随机schema。 */
    @AfterEach
    void cleanup() {
        for (String database : databases) {
            assertThat(database).matches("macula_iam_model_it_[a-f0-9]{32}");
            admin.execute("DROP DATABASE " + database);
        }
    }

    /**
     * 只改写任务专属暂存行的测试钩子，验证逐值核对不会仅依赖行数。
     * @since 6.1.0
     */
    private static final class V27__corrupt_copy extends V27__iam_application_model {
        private final Connection connection;
        private final long applicationId;
        /** 记录任务创建的连接和合成应用标识。 */
        private V27__corrupt_copy(Connection connection, long applicationId) {
            this.connection = connection;
            this.applicationId = applicationId;
        }
        /** 在复制完成但尚未核对前损坏合成凭据。 */
        @Override
        protected void checkpoint(Stage stage) throws SQLException {
            if (stage == Stage.COPY) {
                try (var statement = connection.prepareStatement("UPDATE v27_stage_sys_iam_application_protocol_config SET credential_hash='synthetic-corruption' WHERE application_id=?")) {
                    statement.setLong(1, applicationId);
                    statement.executeUpdate();
                }
            }
        }
    }

    /**
     * 在固定阶段注入含合成秘密的SQL异常，验证迁移不会传递原始message/cause。
     * @since 6.1.0
     */
    private static final class V27__failing_migration extends V27__iam_application_model {
        private final String failureStage;
        /** 指定测试需要验证的阶段。 */
        private V27__failing_migration(String stage) { failureStage = stage; }
        /** 注入测试异常，不存在生产配置或外部开关可触发。 */
        @Override
        protected void checkpoint(Stage stage) throws SQLException {
            if (failureStage.equals(stage.name())) {
                throw new SQLException(SYNTHETIC_HASH, "45000", 1644);
            }
        }
    }
}
