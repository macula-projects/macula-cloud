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

import dev.macula.cloud.system.protocol.OAuth2ProtocolDefinition;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import dev.macula.boot.constants.CacheConstants;
import dev.macula.boot.starter.mp.handler.AuditMetaObjectHandler;
import dev.macula.boot.starter.mp.config.MyBatisPlusProperties;
import dev.macula.cloud.system.config.OAuth2ScopeCatalogProperties;
import dev.macula.cloud.system.converter.IdentityProviderConverter;
import dev.macula.cloud.system.form.IamApplicationDraftForm;
import dev.macula.cloud.system.form.IamApplicationForm;
import dev.macula.cloud.system.mapper.SysIamApplicationMapper;
import dev.macula.cloud.system.mapper.SysIamApplicationIdentityProviderMapper;
import dev.macula.cloud.system.mapper.SysIamApplicationProtocolConfigMapper;
import dev.macula.cloud.system.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.system.mapper.SysTenantInfoMapper;
import dev.macula.cloud.system.protocol.OAuth2ProtocolSettings;
import dev.macula.cloud.system.security.OAuth2ClientCacheNames;
import dev.macula.cloud.system.service.impl.IamApplicationCatalogServiceImpl;
import dev.macula.cloud.system.service.impl.IamApplicationServiceImpl;
import dev.macula.cloud.system.service.impl.IdentityProviderServiceImpl;
import dev.macula.cloud.system.service.impl.ProtocolConfigurationTestFixtures;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mapstruct.factory.Mappers;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * V27真实Mapper/事务验证聚合写入、回滚、密钥旋转与来源删除竞争。
 * @author Rain
 * @since 6.1.0
 */
@EnabledIfEnvironmentVariable(named = "IAM_APPLICATION_MODEL_IT", matches = "true")
class IamApplicationModelConcurrencyIT {
    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private String database;
    private SqlSessionTemplate session;
    private TransactionTemplate transaction;
    private ConcurrentMapCacheManager caches;

    /** 仅新建任务专属随机schema，完整运行V1到V27。 */
    @BeforeEach
    void setup() throws Exception {
        String url = System.getenv("IAM_APPLICATION_MODEL_IT_URL");
        assertThat(url).matches("jdbc:mysql://127[.]0[.]0[.]1:[0-9]+/mysql[?].+");
        String user = System.getenv("IAM_APPLICATION_MODEL_IT_USER");
        String password = System.getenv("IAM_APPLICATION_MODEL_IT_PASSWORD");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        database = "macula_iam_model_it_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE DATABASE " + database + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        var dataSource = new DriverManagerDataSource(url.replace("/mysql?", "/" + database + "?"), user, password);
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("27").load().migrate();
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        var global = new GlobalConfig();
        global.setMetaObjectHandler(new AuditMetaObjectHandler(new MyBatisPlusProperties().getAudit()));
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setGlobalConfig(global);
        var sql = factory.getObject();
        for (Class<?> type : List.of(SysIamApplicationMapper.class, SysIamApplicationProtocolConfigMapper.class,
            SysIamApplicationIdentityProviderMapper.class, SysIdentityProviderMapper.class, SysTenantInfoMapper.class)) {
            sql.getConfiguration().addMapper(type);
        }
        session = new SqlSessionTemplate(sql);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        caches = new ConcurrentMapCacheManager(CacheConstants.OAUTH2_CLIENT_CACHE_KEY, OAuth2ClientCacheNames.TOKEN_ORIGIN_INDEX);
    }

    /** 新建、保存、版本漂移、旋转与回滚只使用新表，凭据和普通视图严格分离。 */
    @Test
    void aggregateWritesAndSecretRotationAreAtomic() {
        var service = service(session.getMapper(SysIdentityProviderMapper.class));
        var draft = new IamApplicationDraftForm();
        draft.setApplicationType("WEB_SERVER");
        draft.setClientName("fixture");
        var created = transaction.execute(status -> service.createDraft(draft));
        assertThat(created.getProtocol()).isNull();
        var form = form(created.getClientId());
        form.setConfigurationVersion(created.getConfigurationVersion());
        form.setClientSecret("synthetic-initial-secret");
        var enabled = transaction.execute(status -> service.save(created.getId(), form));
        String originalHash = jdbc.queryForObject("SELECT credential_hash FROM sys_iam_application_protocol_config WHERE application_id=?", String.class, created.getId());
        assertThat(new BCryptPasswordEncoder().matches("synthetic-initial-secret", originalHash)).isTrue();
        assertThat(enabled.toString()).doesNotContain(originalHash);
        var cache = caches.getCache(CacheConstants.OAUTH2_CLIENT_CACHE_KEY);
        cache.put("fixture", "committed");
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            service.resetSecret(created.getId(), "synthetic-rolled-back-secret", enabled.getConfigurationVersion());
            throw new IllegalStateException("fixture rollback");
        }));
        assertThat(cache.get("fixture", String.class)).isEqualTo("committed");
        assertThat(originalHash.equals(jdbc.queryForObject("SELECT credential_hash FROM sys_iam_application_protocol_config WHERE application_id=?", String.class, created.getId()))).isTrue();
        transaction.executeWithoutResult(status -> service.resetSecret(created.getId(), "synthetic-replacement-secret", enabled.getConfigurationVersion()));
        assertThat(cache.get("fixture")).isNull();
        assertThat(new BCryptPasswordEncoder().matches("synthetic-replacement-secret", jdbc.queryForObject("SELECT credential_hash FROM sys_iam_application_protocol_config WHERE application_id=?", String.class, created.getId()))).isTrue();
        var stale = form(enabled.getClientId());
        stale.setConfigurationVersion(enabled.getConfigurationVersion());
        var error = assertThrows(ResponseStatusException.class, () -> transaction.execute(status -> service.save(created.getId(), stale)));
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    /** 两事务竞争来源锁：关联先提交则删除409；删除先提交则保存400且无孤儿。 */
    @Test
    void sourceDeletionCannotRaceSuccessfulAssociation() throws Exception {
        jdbc.update("INSERT INTO sys_identity_provider(id,name,provider_type,source_type,credential_type,capabilities_json,app_id,app_secret,enabled) VALUES(99111,'fixture-source','GITHUB','GITHUB_OAUTH','GITHUB_OAUTH','{\"GITHUB_OAUTH\":\"GITHUB_OAUTH_V1\"}','synthetic-id','synthetic-material',1)");
        var realSources = session.getMapper(SysIdentityProviderMapper.class);
        var locked = new CountDownLatch(1);
        var deleteAttempt = new CountDownLatch(1);
        var savingSources = mock(SysIdentityProviderMapper.class, delegatesTo(realSources));
        doAnswer(call -> {
            var row = realSources.selectByIdForUpdate(99111L);
            locked.countDown();
            assertThat(deleteAttempt.await(10, TimeUnit.SECONDS)).isTrue();
            return row;
        }).when(savingSources).selectByIdForUpdate(99111L);
        var saving = service(savingSources);
        var deleting = new IdentityProviderServiceImpl(realSources, Mappers.getMapper(IdentityProviderConverter.class), session.getMapper(SysIamApplicationIdentityProviderMapper.class));
        var form = form("source-race-app");
        form.setBrowserCapabilityBindings(Map.of("GITHUB_OAUTH", 99111L));
        form.setClientSecret("synthetic-source-race-secret");
        var executor = Executors.newFixedThreadPool(2);
        try {
            var save = executor.submit(() -> transaction.execute(status -> saving.save(null, form)));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var delete = executor.submit(() -> {
                deleteAttempt.countDown();
                return assertThrows(ResponseStatusException.class, () -> transaction.executeWithoutResult(status -> deleting.delete(99111L)));
            });
            assertThat(save.get(15, TimeUnit.SECONDS).getProtocol()).isEqualTo("OIDC");
            assertThat(delete.get(15, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_identity_provider WHERE id=99111", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_iam_application_identity_provider WHERE identity_provider_id=99111", Integer.class)).isEqualTo(1);
        jdbc.update("INSERT INTO sys_identity_provider(id,name,provider_type,source_type,app_id,app_secret,enabled) VALUES(99112,'deleted-fixture','GITHUB','GITHUB_OAUTH','synthetic-id','synthetic-material',1)");
        transaction.executeWithoutResult(status -> deleting.delete(99112L));
        var rejected = form("after-source-deletion");
        rejected.setBrowserCapabilityBindings(Map.of("GITHUB_OAUTH", 99112L));
        assertThrows(ResponseStatusException.class, () -> transaction.execute(status -> saving.save(null, rejected)));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_iam_application_identity_provider WHERE identity_provider_id=99112", Integer.class)).isZero();
    }

    /** 使用实际Mapper和生产组件装配，不运行Web、Nacos或真实第三方调用。 */
    private IamApplicationServiceImpl service(SysIdentityProviderMapper sources) {
        return ProtocolConfigurationTestFixtures.service(session.getMapper(SysIamApplicationMapper.class),
            session.getMapper(SysIamApplicationIdentityProviderMapper.class), session.getMapper(SysIamApplicationProtocolConfigMapper.class),
            sources, session.getMapper(SysTenantInfoMapper.class), new BCryptPasswordEncoder(), caches,
            new IamApplicationCatalogServiceImpl(new OAuth2ScopeCatalogProperties(), sources, OAuth2ProtocolDefinition.registry()));
    }

    /** 完整公开表单保留现有OAuth HTTP合同。 */
    private IamApplicationForm form(String code) {
        var form = new IamApplicationForm();
        form.setClientId(code);
        form.setClientName("fixture");
        form.setPurpose("APPLICATION");
        form.setApplicationType("WEB_SERVER");
        form.setConfigurationStatus("ENABLED");
        form.setWebLoginMode("BROWSER");
        form.setTenantId(1L);
        form.setUserType("employee");
        form.setProtocol("OIDC");
        var settings = new OAuth2ProtocolSettings();
        settings.setClientAuthenticationMethods("client_secret_basic");
        settings.setAuthorizationGrantTypes("authorization_code");
        settings.setRedirectUris("https://client.example/callback");
        settings.setScopes("openid,profile");
        settings.setRequireProofKey(true);
        settings.setRequireAuthorizationConsent(false);
        form.setSettings(settings);
        return form;
    }

    /** 仅删除本测试创建并通过前缀检查的随机库。 */
    @AfterEach
    void cleanup() {
        if (database != null) {
            assertThat(database).matches("macula_iam_model_it_[a-f0-9]{32}");
            admin.execute("DROP DATABASE " + database);
        }
    }
}
