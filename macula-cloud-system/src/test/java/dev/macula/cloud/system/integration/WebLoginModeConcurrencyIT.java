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
import java.net.URI;
import java.sql.DriverManager;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import dev.macula.boot.constants.CacheConstants;
import dev.macula.boot.starter.mp.config.MyBatisPlusProperties;
import dev.macula.boot.starter.mp.handler.AuditMetaObjectHandler;
import dev.macula.cloud.system.config.OAuth2ScopeCatalogProperties;
import dev.macula.cloud.system.form.IamApplicationForm;
import dev.macula.cloud.system.mapper.SysIamApplicationProtocolConfigMapper;
import dev.macula.cloud.system.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.system.mapper.SysIamApplicationMapper;
import dev.macula.cloud.system.mapper.SysIamApplicationIdentityProviderMapper;
import dev.macula.cloud.system.mapper.SysTenantInfoMapper;
import dev.macula.cloud.system.pojo.entity.SysIamApplicationIdentityProvider;
import dev.macula.cloud.system.security.OAuth2ClientCacheNames;
import dev.macula.cloud.system.service.impl.IamApplicationCatalogServiceImpl;
import dev.macula.cloud.system.service.impl.ProtocolConfigurationTestFixtures;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mybatis.spring.SqlSessionTemplate;
import org.redisson.Redisson;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

/**
 * 隔离MySQL中验证固定模式内配置更新、来源关联与凭据保留的原子性和并发CAS。
 *
 * @author Rain
 * @since 6.1.0
 */
@EnabledIfEnvironmentVariable(named = "WEB_LOGIN_MODE_IT", matches = "true")
class WebLoginModeConcurrencyIT {
    /** 两个连接同时读取相同配置快照，再按指定顺序提交，仅获胜者产生绑定/缓存副作用。 */
    @Test
    void racesConfigurationWithinFixedModeAndPreservesIdentity() throws Exception {
        String url = System.getenv("WEB_LOGIN_MODE_URL");
        assertNotNull(url);
        assertTrue(url.startsWith("jdbc:mysql://127.0.0.1:"));
        assertTrue(URI.create(url.substring(5)).getPath().startsWith("/macula_identity_binding_it_"));
        String user = System.getenv("SPRING_DATASOURCE_USERNAME");
        String password = System.getenv("SPRING_DATASOURCE_PASSWORD");
        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()")) {
            assertTrue(result.next());
            assertEquals(0, result.getInt(1), "必须使用全新隔离数据库");
        }
        Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration").target("26").load().migrate();
        var dataSource = new DriverManagerDataSource(url, user, password);
        var jdbc = new JdbcTemplate(dataSource);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        var global = new GlobalConfig();
        global.setMetaObjectHandler(new AuditMetaObjectHandler(
            new MyBatisPlusProperties().getAudit()));
        factory.setGlobalConfig(global);
        var sqlFactory = factory.getObject();
        for (Class<?> mapper : new Class<?>[] {SysIamApplicationMapper.class,SysIamApplicationProtocolConfigMapper.class, SysIamApplicationIdentityProviderMapper.class,
            SysIdentityProviderMapper.class, SysTenantInfoMapper.class}) {
            sqlFactory.getConfiguration().addMapper(mapper);
        }
        var session = new SqlSessionTemplate(sqlFactory);
        var realMapper = session.getMapper(SysIamApplicationMapper.class);
        var bindings = mock(SysIamApplicationIdentityProviderMapper.class,
            delegatesTo(session.getMapper(SysIamApplicationIdentityProviderMapper.class)));
        jdbc.update("INSERT INTO sys_identity_provider(id,name,provider_type,source_type,app_id,app_secret,enabled) VALUES(90000,'fixed-mode-fixture','WECOM','WECOM_WEB','corp','fixture-secret',1)");
        jdbc.update("INSERT INTO sys_registration_identity_binding(registration_id,identity_kind,identity_value,user_type,user_id) VALUES(90000,'MEMBER_ID','fixture-member','employee',1)");
        jdbc.update("UPDATE sys_oauth2_client SET application_type='WEB_SERVER', web_login_mode='IN_APP', purpose='APPLICATION', managed_key=NULL, authorization_version=3, protocol_mode='OIDC', miniapp_platform=NULL, hosted_login_methods=NULL, "
            + "client_name='before', client_authentication_methods='client_secret_basic', client_secret='retained-test-hash', "
            + "authorization_grant_types='authorization_code', require_proof_key=1, "
            + "redirect_uris='https://client.example/callback' WHERE id=1");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("27").load().migrate();
        var mapper = mock(SysIamApplicationMapper.class, delegatesTo(realMapper));
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
            return realMapper.selectByIdForUpdate(invocation.getArgument(0));
        }).when(mapper).selectByIdForUpdate(ArgumentMatchers.anyLong());
        int redisPort = Integer.parseInt(System.getenv("WEB_LOGIN_MODE_REDIS_PORT"));
        assertTrue(redisPort >= 30000);
        var redisConfiguration = new Config();
        redisConfiguration.useSingleServer().setAddress("redis://127.0.0.1:" + redisPort);
        var firstRedis = Redisson.create(redisConfiguration);
        var secondRedis = Redisson.create(redisConfiguration);
        try {
        // 两个独立连接/CacheManager观察同一Redis；验证提交驱逐实际跨实例生效。
        var firstCaches = RedisCacheManager.builder(
            new RedissonConnectionFactory(firstRedis)).build();
        var secondCaches = RedisCacheManager.builder(
            new RedissonConnectionFactory(secondRedis)).build();
        firstCaches.afterPropertiesSet();
        secondCaches.afterPropertiesSet();
        var cache = Mockito.spy(firstCaches.getCache(CacheConstants.OAUTH2_CLIENT_CACHE_KEY));
        var otherCache = secondCaches.getCache(CacheConstants.OAUTH2_CLIENT_CACHE_KEY);
        var otherOrigins = secondCaches.getCache(OAuth2ClientCacheNames.TOKEN_ORIGIN_INDEX);
        otherCache.put("mode-fixture", "before");
        otherOrigins.put("mode-fixture", "before");
        var cacheManager = mock(CacheManager.class);
        when(cacheManager.getCache(CacheConstants.OAUTH2_CLIENT_CACHE_KEY)).thenReturn(cache);
        when(cacheManager.getCache(OAuth2ClientCacheNames.TOKEN_ORIGIN_INDEX))
            .thenReturn(firstCaches.getCache(OAuth2ClientCacheNames.TOKEN_ORIGIN_INDEX));
        var service = ProtocolConfigurationTestFixtures.service(mapper, bindings, session.getMapper(SysIamApplicationProtocolConfigMapper.class), session.getMapper(SysIdentityProviderMapper.class), session.getMapper(SysTenantInfoMapper.class), mock(PasswordEncoder.class), cacheManager, new IamApplicationCatalogServiceImpl(new OAuth2ScopeCatalogProperties(), session.getMapper(SysIdentityProviderMapper.class), OAuth2ProtocolDefinition.registry()));
        IamApplicationForm first = form("WEB_SERVER", "winner");
        IamApplicationForm second = form("WEB_SERVER", "loser");
        String version = ProtocolConfigurationTestFixtures.protocols(session.getMapper(SysIamApplicationProtocolConfigMapper.class), session.getMapper(SysIamApplicationIdentityProviderMapper.class)).version(realMapper.selectById(1L));
        first.setConfigurationVersion(version);
        second.setConfigurationVersion(version);
        ProtocolConfigurationTestFixtures.configure(first);
        ProtocolConfigurationTestFixtures.configure(second);
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
            assertEquals(first.getApplicationType(), jdbc.queryForObject("SELECT application_type FROM sys_iam_application WHERE id=1", String.class));
            assertEquals("IN_APP", jdbc.queryForObject("SELECT web_login_mode FROM sys_iam_application WHERE id=1", String.class));
            assertEquals("winner", jdbc.queryForObject("SELECT application_name FROM sys_iam_application WHERE id=1", String.class));
            verify(cache, times(1)).invalidate();
            Assertions.assertNull(otherCache.get("mode-fixture"));
            Assertions.assertNull(otherOrigins.get("mode-fixture"));
            assertEquals("retained-test-hash",jdbc.queryForObject("SELECT credential_hash FROM sys_iam_application_protocol_config WHERE application_id=1",String.class));
            assertEquals(4L,jdbc.queryForObject("SELECT authorization_version FROM sys_iam_application WHERE id=1",Long.class));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sys_identity_provider_user_binding WHERE identity_provider_id=90000",Integer.class));
            String committed = service.get(1L).getConfigurationVersion();
            var forbidden = form("WEB_SERVER", "forbidden-mode-change");
            forbidden.setWebLoginMode("BROWSER");
            forbidden.setConfigurationVersion(committed);
            ProtocolConfigurationTestFixtures.configure(forbidden);
            var rejected = Assertions.assertThrows(ResponseStatusException.class,
                () -> transaction.executeWithoutResult(status -> service.save(1L, forbidden)));
            assertEquals(400, rejected.getStatusCode().value());
            assertEquals(committed, service.get(1L).getConfigurationVersion());
            assertEquals("IN_APP", jdbc.queryForObject("SELECT web_login_mode FROM sys_iam_application WHERE id=1", String.class));
            verify(cache, times(1)).invalidate();
            second.setWebLoginMode("IN_APP");
            second.setConfigurationVersion(committed);
            otherCache.put("mode-fixture", "rollback-preserved");
            Assertions.assertThrows(IllegalStateException.class,()->transaction.executeWithoutResult(status->{service.save(1L,second);
            throw new IllegalStateException("fixture rollback");
            }));
            assertEquals(committed,service.get(1L).getConfigurationVersion());
            verify(cache,times(1)).invalidate();
            assertEquals("rollback-preserved", otherCache.get("mode-fixture", String.class));
        } finally {
            executor.shutdownNow();
        }
        verify(bindings, times(2)).delete(ArgumentMatchers.<Wrapper<SysIamApplicationIdentityProvider>>any());
        } finally {
            firstRedis.shutdown();
            secondRedis.shutdown();
        }
    }

    /** 完整新模型不依赖缺失字段合并；Web端内网页只启用授权码并保留机密认证。 */
    private IamApplicationForm form(String type, String name) {
        IamApplicationForm form = new IamApplicationForm();
        form.setClientId("e2fa7e64-249b-46f0-ae1d-797610e88615");
        form.setClientName(name);
        form.setProtocolMode("OIDC");
        form.setEnabledProtocols(Set.of("OIDC"));
        form.setDefaultProtocol("OIDC");
        form.setConfigurationStatus("ENABLED");
        if (type != null) {
            form.setApplicationType(type);
            form.setWebLoginMode("IN_APP");
            form.setHostedLoginMethods(Set.of());
            form.setBrowserCapabilityBindings(Map.of("WECOM_WEBVIEW",90000L));
        }
        form.setClientAuthenticationMethods("client_secret_basic");
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
