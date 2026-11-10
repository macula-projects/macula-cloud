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
import dev.macula.cloud.system.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.system.mapper.SysOAuth2ClientConfigMapper;
import dev.macula.cloud.system.mapper.SysOAuth2ClientExternalProviderMapper;
import dev.macula.cloud.system.mapper.SysTenantInfoMapper;
import dev.macula.cloud.system.service.impl.OAuth2ClientConfigServiceImpl;
import dev.macula.cloud.system.service.impl.OAuth2ClientConfigurationCatalogServiceImpl;
import dev.macula.cloud.system.config.OAuth2ScopeCatalogProperties;
import java.net.URI;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;


import dev.macula.cloud.system.config.AdminClientProvisioningProperties;
import dev.macula.cloud.system.service.impl.AdminClientProvisioningService;
import dev.macula.cloud.system.security.OAuth2ClientCacheNames;
import dev.macula.boot.constants.CacheConstants;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.data.redis.cache.RedisCacheManager;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;

/**
 * 隔离 MySQL/Redis 中验证两身份原子提交、幂等、拒绝漂移和提交后缓存失效。
 * @author Rain
 * @since 6.1.0
 */
@EnabledIfEnvironmentVariable(named = "ADMIN_CLIENT_PROVISIONING_IT", matches = "true")
class AdminClientProvisioningIT {
    /** 只允许全新隔离库和显式本地 Redis，不连接业务库。 */
    @Test
    void provisionsAtomicallyAndPreservesOtherIdentities() throws Exception {
        String url = System.getenv("ADMIN_CLIENT_PROVISIONING_IT_URL");
        assertTrue(url != null && url.startsWith("jdbc:mysql://127.0.0.1:"));
        assertTrue(URI.create(url.substring(5)).getPath().startsWith("/macula_admin_client_it_"));
        var dataSource = new DriverManagerDataSource(url, System.getenv("IAM_SOURCE_IT_USER"),
            System.getenv("IAM_SOURCE_IT_PASSWORD"));
        var jdbc = new JdbcTemplate(dataSource);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class));
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("16").load().migrate();
        jdbc.update("UPDATE sys_oauth2_client SET application_type='SERVICE',enabled_protocols='OAUTH2_1',default_protocol='OAUTH2_1',configuration_status='ENABLED',client_authentication_methods='client_secret_basic,client_secret_post',authorization_grant_types='client_credentials',scopes='',require_proof_key=0,require_authorization_consent=0,reuse_refresh_tokens=0,tenant_id=1,user_type='employee' WHERE client_id='e4da4a32-592b-46f0-ae1d-784310e88423'");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
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
        var redisConfig = new org.redisson.config.Config();
        redisConfig.useSingleServer().setAddress("redis://127.0.0.1:" + System.getenv("ADMIN_CLIENT_IT_REDIS_PORT"));
        var redisson = org.redisson.Redisson.create(redisConfig);
        var redis = new RedissonConnectionFactory(redisson);
        redis.afterPropertiesSet();
        try {
            // 唯一前缀确保即使端口误配也不会驱逐其他测试或业务缓存。
            var caches = RedisCacheManager.builder(redis).cacheDefaults(
                org.springframework.data.redis.cache.RedisCacheConfiguration.defaultCacheConfig()
                    .prefixCacheNameWith("admin-client-it-" + java.util.UUID.randomUUID() + "::"))
                .initialCacheNames(java.util.Set.of(
                CacheConstants.OAUTH2_CLIENT_CACHE_KEY, OAuth2ClientCacheNames.TOKEN_ORIGIN_INDEX)).build();
            caches.afterPropertiesSet();
            var sources = session.getMapper(SysIdentityProviderMapper.class);
            var mapper = session.getMapper(SysOAuth2ClientConfigMapper.class);
            var configService = new OAuth2ClientConfigServiceImpl(mapper,
                session.getMapper(SysOAuth2ClientExternalProviderMapper.class), sources,
                session.getMapper(SysTenantInfoMapper.class), mock(PasswordEncoder.class), caches,
                new OAuth2ClientConfigurationCatalogServiceImpl(new OAuth2ScopeCatalogProperties(), sources), new dev.macula.cloud.system.service.impl.ApplicationProtocolConfigurationService(session.getMapper(dev.macula.cloud.system.mapper.SysApplicationProtocolConfigMapper.class), session.getMapper(SysOAuth2ClientExternalProviderMapper.class)));
            var properties = new AdminClientProvisioningProperties();
            properties.setAdminClientId(java.util.UUID.randomUUID().toString());
            properties.setGatewayClientId("e4da4a32-592b-46f0-ae1d-784310e88423");
            properties.setAdminOrigin("http://127.0.0.1:5900");
            properties.setTenantId(1L);
            properties.setUserType("employee");
            String gatewayId = properties.getGatewayClientId();
            String originalSecret = jdbc.queryForObject("SELECT client_secret FROM sys_oauth2_client WHERE client_id=?", String.class, gatewayId);
            String others = jdbc.queryForList("SELECT * FROM sys_oauth2_client WHERE client_id<>? ORDER BY id", gatewayId).toString();
            String before = jdbc.queryForList("SELECT * FROM sys_oauth2_client ORDER BY id").toString();
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            // 第二个写入失败必须撤回第一个 Gateway 更新，提交后缓存回调也不能执行。
            var failing = spy(configService);
            doThrow(new IllegalStateException("synthetic second write failure")).when(failing).save(isNull(), any());
            var failureService = new AdminClientProvisioningService(mapper, failing);
            var cache = caches.getCache(CacheConstants.OAUTH2_CLIENT_CACHE_KEY);
            cache.put("clear-check", "synthetic");
            cache.clear();
            assertNull(cache.get("clear-check"), "isolated Redis cache clear must work before testing transactions");
            cache.put("provisioning-fixture", "before");
            assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
                failureService.provision(properties);
                return null;
            }));
            assertEquals(before, jdbc.queryForList("SELECT * FROM sys_oauth2_client ORDER BY id").toString());
            assertEquals("before", cache.get("provisioning-fixture", String.class));
            var service = new AdminClientProvisioningService(mapper, configService);
            transaction.execute(status -> {
                service.provision(properties);
                return null;
            });
            assertNull(cache.get("provisioning-fixture"));
            assertEquals(originalSecret, jdbc.queryForObject("SELECT client_secret FROM sys_oauth2_client WHERE client_id=?", String.class, gatewayId));
            assertEquals(others, jdbc.queryForList("SELECT * FROM sys_oauth2_client WHERE client_id NOT IN (?,?) ORDER BY id",
                gatewayId, properties.getAdminClientId()).toString());
            String enabled = jdbc.queryForList("SELECT * FROM sys_oauth2_client ORDER BY id").toString();
            transaction.execute(status -> {
                service.provision(properties);
                return null;
            });
            assertEquals(enabled, jdbc.queryForList("SELECT * FROM sys_oauth2_client ORDER BY id").toString());
            jdbc.update("UPDATE sys_oauth2_client SET client_name='operator change' WHERE client_id=?", properties.getAdminClientId());
            assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
                service.provision(properties);
                return null;
            }));
            assertEquals("operator change", jdbc.queryForObject("SELECT client_name FROM sys_oauth2_client WHERE client_id=?", String.class, properties.getAdminClientId()));
        } finally {
            redis.destroy();
            redisson.shutdown();
        }
    }
}
