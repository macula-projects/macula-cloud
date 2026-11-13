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
import dev.macula.cloud.system.service.impl.ProtocolConfigurationTestFixtures;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import dev.macula.boot.constants.CacheConstants;
import dev.macula.cloud.system.config.OAuth2ScopeCatalogProperties;
import dev.macula.cloud.system.form.IamApplicationForm;
import dev.macula.cloud.system.mapper.SysIamApplicationProtocolConfigMapper;
import dev.macula.cloud.system.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.system.mapper.SysIamApplicationMapper;
import dev.macula.cloud.system.mapper.SysIamApplicationIdentityProviderMapper;
import dev.macula.cloud.system.mapper.SysTenantInfoMapper;
import dev.macula.cloud.system.pojo.entity.SysIamApplication;
import dev.macula.cloud.system.protocol.OAuth2ProtocolSettings;
import dev.macula.cloud.system.security.OAuth2ClientCacheNames;
import dev.macula.cloud.system.service.impl.IamApplicationCatalogServiceImpl;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 真实MySQL事务验证资源服务器保存、密钥轮换及失败回滚共用快照边界。
 * @author Rain
 * @since 6.1.0
 */
@EnabledIfEnvironmentVariable(named = "IAM_RESOURCE_IT", matches = "true")
class ResourceClientConcurrencyIT {
    /** 旧页面不能覆盖已提交保存或密钥重置，回滚不遗留半份身份。 */
    @Test
    void resourceSavesSecretsAndRollbackShareTheSameBoundary() throws Exception {
        String rootUrl = System.getenv("IAM_RESOURCE_IT_URL");
        assertTrue(rootUrl.startsWith("jdbc:mysql://127.0.0.1:") && rootUrl.contains("/mysql?"));
        String database = "macula_identity_binding_it_resource_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        var admin = new JdbcTemplate(new DriverManagerDataSource(rootUrl, System.getenv("IAM_RESOURCE_IT_USER"), System.getenv("IAM_RESOURCE_IT_PASSWORD")));
        admin.execute("CREATE DATABASE " + database + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        try {
            var ds = new DriverManagerDataSource(rootUrl.replace("/mysql?", "/" + database + "?"), System.getenv("IAM_RESOURCE_IT_USER"), System.getenv("IAM_RESOURCE_IT_PASSWORD"));
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("26").load().migrate();
            var jdbc = new JdbcTemplate(ds);
            Long id = jdbc.queryForObject("SELECT MIN(id) FROM sys_oauth2_client", Long.class);
            jdbc.update("DELETE FROM sys_application_protocol_config WHERE application_id=?", id);
            jdbc.update("UPDATE sys_oauth2_client SET purpose='RESOURCE_SERVER',application_type='SERVICE',managed_key=NULL,configuration_status='ENABLED', "
                + "client_authentication_methods='client_secret_basic',authorization_grant_types='',scopes='',redirect_uris='', "
                + "client_secret='original-test-hash',tenant_id=NULL,user_type=NULL WHERE id=?", id);
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("27").load().migrate();
            var configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(ds);
            factory.setConfiguration(configuration);
            var sql = factory.getObject();
            for (Class<?> type : List.of(SysIamApplicationMapper.class, SysIamApplicationProtocolConfigMapper.class, SysIamApplicationIdentityProviderMapper.class)) {
                sql.getConfiguration().addMapper(type);
            }
            var session = new SqlSessionTemplate(sql);
            var mapper = session.getMapper(SysIamApplicationMapper.class);
            var sources = session.getMapper(SysIamApplicationIdentityProviderMapper.class);
            var protocols = ProtocolConfigurationTestFixtures.protocols(session.getMapper(SysIamApplicationProtocolConfigMapper.class), sources);
            var caches = new SimpleCacheManager();
            caches.setCaches(List.of(new ConcurrentMapCache(CacheConstants.OAUTH2_CLIENT_CACHE_KEY), new ConcurrentMapCache(OAuth2ClientCacheNames.TOKEN_ORIGIN_INDEX)));
            caches.initializeCaches();
            var providers = mock(SysIdentityProviderMapper.class);
            var service = ProtocolConfigurationTestFixtures.service(mapper, sources, session.getMapper(SysIamApplicationProtocolConfigMapper.class), providers, mock(SysTenantInfoMapper.class), new BCryptPasswordEncoder(), caches, new IamApplicationCatalogServiceImpl(new OAuth2ScopeCatalogProperties(), providers, OAuth2ProtocolDefinition.registry()));
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(ds));
            var initial = service.get(id);
            var firstForm = form(initial.getClientId(), initial.getConfigurationVersion(), "first");
            var secondForm = form(initial.getClientId(), initial.getConfigurationVersion(), "second");
            var locked = new CountDownLatch(1);
            var attempted = new CountDownLatch(1);
            var executor = Executors.newFixedThreadPool(2);
            try {
                var first = executor.submit(() -> transaction.executeWithoutResult(status -> {
                    mapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysIamApplication>()
                        .eq(SysIamApplication::getId, id).last("FOR UPDATE"));
                    locked.countDown();
                    try { assertTrue(attempted.await(5, TimeUnit.SECONDS)); }
                    catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(error);
                    }
                    service.save(id, firstForm);
                }));
                var second = executor.submit(() -> {
                    try { assertTrue(locked.await(5, TimeUnit.SECONDS)); }
                    catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(error);
                    }
                    attempted.countDown();
                    assertThrows(ResponseStatusException.class, () -> transaction.executeWithoutResult(status -> service.save(id, secondForm)));
                });
                first.get(15, TimeUnit.SECONDS);
                second.get(15, TimeUnit.SECONDS);
            } finally { executor.shutdownNow(); }
            assertEquals("first", service.get(id).getClientName());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_iam_application_protocol_config WHERE application_id=?", Integer.class, id));
            var beforeReset = service.get(id);
            transaction.executeWithoutResult(status -> service.resetSecret(id, "synthetic-test-secret-rotation", beforeReset.getConfigurationVersion()));
            assertThrows(ResponseStatusException.class, () -> transaction.executeWithoutResult(status -> service.save(id,
                form(beforeReset.getClientId(), beforeReset.getConfigurationVersion(), "stale"))));
            var beforeRollback = service.get(id);
            assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
                service.save(id, form(beforeRollback.getClientId(), beforeRollback.getConfigurationVersion(), "rolled-back"));
                throw new IllegalStateException("fixture rollback");
            }));
            assertEquals(beforeRollback.getConfigurationVersion(), service.get(id).getConfigurationVersion());
            jdbc.update("UPDATE sys_iam_application SET managed_key='platform-fixture' WHERE id=?", id);
            var managed = service.get(id);
            assertThrows(ResponseStatusException.class, () -> transaction.executeWithoutResult(status -> service.delete(id, managed.getConfigurationVersion())));
            var disabled = form(managed.getClientId(), managed.getConfigurationVersion(), "disabled");
            disabled.setConfigurationStatus("DRAFT");
            assertThrows(ResponseStatusException.class, () -> transaction.executeWithoutResult(status -> service.save(id, disabled)));
            var changedPurpose = form(managed.getClientId(), managed.getConfigurationVersion(), "changed-purpose");
            changedPurpose.setPurpose("APPLICATION");
            assertThrows(ResponseStatusException.class, () -> transaction.executeWithoutResult(status -> service.save(id, changedPurpose)));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_iam_application WHERE id=?", Integer.class, id));
        } finally {
            if (database.matches("macula_identity_binding_it_resource_[a-f0-9]{24}")) { admin.execute("DROP DATABASE IF EXISTS " + database); }
        }
    }

    /** 资源服务器表单没有协议、grant、租户或用户类型。 */
    private IamApplicationForm form(String clientId, String version, String name) {
        var form = new IamApplicationForm();
        form.setClientId(clientId);
        form.setClientName(name);
        form.setPurpose("RESOURCE_SERVER");
        form.setApplicationType("SERVICE");
        form.setConfigurationStatus("ENABLED");
        form.setConfigurationVersion(version);

        var settings = new OAuth2ProtocolSettings();
        settings.setClientAuthenticationMethods("client_secret_basic");
        form.setSettings(settings);
        return form;
    }
}
