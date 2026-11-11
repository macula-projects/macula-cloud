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

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import dev.macula.boot.constants.CacheConstants;
import dev.macula.cloud.system.form.OAuth2ClientForm;
import dev.macula.cloud.system.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.system.mapper.SysOAuth2ClientConfigMapper;
import dev.macula.cloud.system.mapper.SysOAuth2ClientExternalProviderMapper;
import dev.macula.cloud.system.mapper.SysTenantInfoMapper;
import dev.macula.cloud.system.security.OAuth2ClientCacheNames;
import dev.macula.cloud.system.service.impl.OAuth2ClientConfigServiceImpl;
import java.net.URI;
import java.sql.DriverManager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

/**
 * 隔离MySQL中通过真实Mapper/Service验证完整配置更新的事务竞争。
 *
 * @author Rain
 * @since 6.1.0
 */
@EnabledIfEnvironmentVariable(named = "SYSTEM_CLIENT_CONCURRENCY_IT", matches = "true")
class OAuth2ClientApplicationConcurrencyIT {
    /** 两个连接竞争EMAIL/PASSWORD配置快照，只有获胜者提交，失败方409且无重复缓存副作用。 */
    @Test
    void racesCompleteConfigurationSnapshots() throws Exception {
        String url = System.getenv("SYSTEM_CLIENT_CONCURRENCY_URL");
        assertNotNull(url);
        assertTrue(URI.create(url.substring(5)).getPath().startsWith("/macula_client_concurrency_it_"));
        String user = System.getenv("SPRING_DATASOURCE_USERNAME");
        String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()")) {
            assertTrue(result.next());
            assertEquals(0, result.getInt(1), "必须使用全新隔离数据库");
        }
        Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration").load().migrate();
        var dataSource = new DriverManagerDataSource(url, user, password);
        var jdbc = new JdbcTemplate(dataSource);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        var global = new com.baomidou.mybatisplus.core.config.GlobalConfig();
        global.setMetaObjectHandler(new dev.macula.boot.starter.mp.handler.AuditMetaObjectHandler(
            new dev.macula.boot.starter.mp.config.MyBatisPlusProperties().getAudit()));
        factory.setGlobalConfig(global);
        var sqlFactory = factory.getObject();
        for (Class<?> mapper : new Class<?>[] {SysOAuth2ClientConfigMapper.class,dev.macula.cloud.system.mapper.SysApplicationProtocolConfigMapper.class, SysOAuth2ClientExternalProviderMapper.class,
            SysIdentityProviderMapper.class, SysTenantInfoMapper.class}) {
            sqlFactory.getConfiguration().addMapper(mapper);
        }
        var session = new SqlSessionTemplate(sqlFactory);
        var realMapper = session.getMapper(SysOAuth2ClientConfigMapper.class);
        var bindings = mock(SysOAuth2ClientExternalProviderMapper.class,
            delegatesTo(session.getMapper(SysOAuth2ClientExternalProviderMapper.class)));
        for (int scenario = 0; scenario < 3; scenario++) {
            jdbc.update("UPDATE sys_oauth2_client SET application_type='SPA', protocol_mode='OIDC', miniapp_platform=NULL, hosted_login_methods=NULL, "
                + "client_name='before', client_authentication_methods='none', client_secret=NULL, "
                + "authorization_grant_types='authorization_code', require_proof_key=1, "
                + "redirect_uris='https://client.example/callback' WHERE id=1");
            var mapper = mock(SysOAuth2ClientConfigMapper.class, delegatesTo(realMapper));
            CountDownLatch bothRead = new CountDownLatch(2), winnerCommitted = new CountDownLatch(1), firstRead = new CountDownLatch(1);
            AtomicInteger reads = new AtomicInteger();
            doAnswer(invocation -> {
                int index = reads.getAndIncrement();
                firstRead.countDown();
                bothRead.countDown();
                assertTrue(bothRead.await(10, TimeUnit.SECONDS));
                if (index == 1) {
                    assertTrue(winnerCommitted.await(10, TimeUnit.SECONDS));
                }
                return realMapper.selectOne(invocation.getArgument(0));
            }).when(mapper).selectOne(any());
            var cache = mock(Cache.class);
            var cacheManager = mock(CacheManager.class);
            when(cacheManager.getCache(CacheConstants.OAUTH2_CLIENT_CACHE_KEY)).thenReturn(cache);
            when(cacheManager.getCache(OAuth2ClientCacheNames.TOKEN_ORIGIN_INDEX)).thenReturn(mock(Cache.class));
            var service = new OAuth2ClientConfigServiceImpl(mapper, bindings,
                session.getMapper(SysIdentityProviderMapper.class), session.getMapper(SysTenantInfoMapper.class),
                mock(PasswordEncoder.class), cacheManager,
                new dev.macula.cloud.system.service.impl.OAuth2ClientConfigurationCatalogServiceImpl(
                    new dev.macula.cloud.system.config.OAuth2ScopeCatalogProperties(),
                    session.getMapper(SysIdentityProviderMapper.class)), new dev.macula.cloud.system.service.impl.ApplicationProtocolConfigurationService(session.getMapper(dev.macula.cloud.system.mapper.SysApplicationProtocolConfigMapper.class), session.getMapper(SysOAuth2ClientExternalProviderMapper.class)));
            OAuth2ClientForm first = form("SPA", "winner");
            OAuth2ClientForm second = form("SPA", "loser");
            first.setHostedLoginMethods(java.util.Set.of("EMAIL"));
            second.setHostedLoginMethods(java.util.Set.of("PASSWORD"));
            String version = new dev.macula.cloud.system.service.impl.ApplicationProtocolConfigurationService(
                session.getMapper(dev.macula.cloud.system.mapper.SysApplicationProtocolConfigMapper.class),
                session.getMapper(SysOAuth2ClientExternalProviderMapper.class)).version(realMapper.selectById(1L));
            first.setConfigurationVersion(version);
            second.setConfigurationVersion(version);
            dev.macula.cloud.system.service.impl.ProtocolConfigurationTestFixtures.configure(first);
            dev.macula.cloud.system.service.impl.ProtocolConfigurationTestFixtures.configure(second);
            var executor = Executors.newFixedThreadPool(2);
            try {
                var winner = executor.submit(() -> {
                    try {
                        return transaction.execute(status -> service.save(1L, first));
                    } finally {
                        winnerCommitted.countDown();
                    }
                });
                // Ensure first thread owns the first read, without serializing its transaction with the second.
                assertTrue(firstRead.await(10, TimeUnit.SECONDS));
                var loser = executor.submit(() -> {
                    try {
                        transaction.execute(status -> service.save(1L, second));
                        return 200;
                    } catch (ResponseStatusException ex) {
                        return ex.getStatusCode().value();
                    }
                });
                assertEquals("winner", winner.get(15, TimeUnit.SECONDS).getClientName());
                assertEquals(409, loser.get(15, TimeUnit.SECONDS));
                assertEquals(first.getApplicationType(), jdbc.queryForObject("SELECT application_type FROM sys_oauth2_client WHERE id=1", String.class));
                assertEquals("winner", jdbc.queryForObject("SELECT client_name FROM sys_oauth2_client WHERE id=1", String.class));
                assertEquals("EMAIL", jdbc.queryForObject("SELECT hosted_login_methods FROM sys_oauth2_client WHERE id=1", String.class));
                verify(cache, times(1)).invalidate();
            } finally {
                executor.shutdownNow();
            }
        }
        verify(bindings, times(3)).delete(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<dev.macula.cloud.system.pojo.entity.SysOAuth2ClientExternalProvider>>any());
    }

    /** 完整新模型不依赖缺失字段合并；public客户端只启用授权码。 */
    private OAuth2ClientForm form(String type, String name) {
        OAuth2ClientForm form = new OAuth2ClientForm();
        form.setClientId("e2fa7e64-249b-46f0-ae1d-797610e88615");
        form.setClientName(name);
        form.setProtocolMode("OIDC");
        form.setEnabledProtocols(java.util.Set.of("OIDC"));
        form.setDefaultProtocol("OIDC");
        form.setConfigurationStatus("ENABLED");
        if (type != null) {
            form.setApplicationType(type);
        }
        form.setClientAuthenticationMethods("none");
        form.setAuthorizationGrantTypes("authorization_code");
        form.setRedirectUris("https://client.example/callback");
        form.setScopes("openid");
        form.setRequireProofKey(true);
        form.setRequireAuthorizationConsent(false);
        form.setReuseRefreshTokens(true);
        form.setTenantId(1L);
        form.setUserType("employee");
        return form;
    }
}
