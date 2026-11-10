/*
 * Copyright (c) 2026 Macula
 * macula.dev, China
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

package dev.macula.cloud.system.migration;

import dev.macula.boot.mapstruct.DateTimeConverter;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import dev.macula.boot.constants.CacheConstants;
import dev.macula.boot.context.TenantContextHolder;
import dev.macula.cloud.system.converter.ApplicationConverter;
import dev.macula.cloud.system.form.ApplicationApiKeyForm;
import dev.macula.cloud.system.mapper.SysApplicationApiKeyMapper;
import dev.macula.cloud.system.mapper.SysApplicationMapper;
import dev.macula.cloud.system.pojo.entity.SysTenantInfo;
import dev.macula.cloud.system.service.ApplicationCredentialCacheService;
import dev.macula.cloud.system.service.SysTenantService;
import dev.macula.cloud.system.service.impl.SysApplicationApiKeyServiceImpl;
import dev.macula.cloud.system.service.impl.SysApplicationServiceImpl;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 在显式指定的空隔离 MySQL 和 Redis 中验证迁移、真实 Mapper/缓存及行锁竞争。
 * @author Rain
 * @since 6.1.0
 */
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.MethodName.class)
@EnabledIfEnvironmentVariable(named = "SYSTEM_APIKEY_IT", matches = "true")
class ApplicationApiKeyIntegrationIT {
    private static JdbcTemplate jdbc;
    private static TransactionTemplate transaction;
    private static SysApplicationApiKeyMapper keys;
    private static SysApplicationMapper apps;
    private static SysApplicationApiKeyServiceImpl service;
    private static SysApplicationServiceImpl applications;
    private static ApplicationCredentialCacheService cache;
    private static RedisTemplate<String, Object> redis;
    private static dev.macula.cloud.system.service.MenuRouteTenantContext tenantContext;
    private static RedissonConnectionFactory connections;
    private static RedissonClient redisson;

    @BeforeAll
    static void setupIsolatedServices() throws Exception {
        String url = System.getenv("APIKEY_IT_JDBC_URL");
        assertThat(url).startsWith("jdbc:mysql://127.0.0.1:").contains("/macula_apikey_it");
        String username = System.getenv("APIKEY_IT_USERNAME");
        String password = System.getenv("APIKEY_IT_PASSWORD");
        try (Connection connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).as("only an empty isolated database is allowed").isZero();
        }
        Flyway.configure().dataSource(url, username, password).locations("classpath:db/migration").load().migrate();
        var dataSource = new DriverManagerDataSource(url, username, password);
        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        var global = new com.baomidou.mybatisplus.core.config.GlobalConfig();
        global.setMetaObjectHandler(new dev.macula.boot.starter.mp.handler.AuditMetaObjectHandler(
            new dev.macula.boot.starter.mp.config.MyBatisPlusProperties.Audit()));
        factory.setGlobalConfig(global);
        factory.setConfiguration(configuration);
        factory.setPlugins(interceptor);
        var sqlFactory = factory.getObject();
        // GlobalConfig 必须先绑定，再注册 Mapper，否则审计填充器尚未进入元数据。
        sqlFactory.getConfiguration().addMapper(SysApplicationApiKeyMapper.class);
        sqlFactory.getConfiguration().addMapper(SysApplicationMapper.class);
        sqlFactory.getConfiguration().addMapper(dev.macula.cloud.system.mapper.SysTenantInfoMapper.class);
        var session = new SqlSessionTemplate(sqlFactory);
        keys = session.getMapper(SysApplicationApiKeyMapper.class);
        apps = session.getMapper(SysApplicationMapper.class);
        int port = Integer.parseInt(System.getenv("APIKEY_IT_REDIS_PORT"));
        assertThat(port).isNotEqualTo(6379);
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:" + port);
        redisson = Redisson.create(config);
        connections = new RedissonConnectionFactory(redisson);
        connections.afterPropertiesSet();
        redis = new RedisTemplate<>();
        redis.setConnectionFactory(connections);
        var serializer = new StringRedisSerializer();
        redis.setKeySerializer(serializer);
        redis.setValueSerializer(serializer);
        redis.setHashKeySerializer(serializer);
        redis.setHashValueSerializer(serializer);
        redis.afterPropertiesSet();
        SysTenantService tenants = mock(SysTenantService.class);
        var tenant = new SysTenantInfo();
        tenant.setId(1L);
        when(tenants.list()).thenReturn(List.of(tenant));
        cache = new ApplicationCredentialCacheService(apps, keys, tenants, redis, redisson,
            new DataSourceTransactionManager(dataSource));
        tenantContext = new dev.macula.cloud.system.service.MenuRouteTenantContext(
            mock(dev.macula.cloud.system.mapper.SysUserMapper.class),
            mock(dev.macula.cloud.system.mapper.SysTenantUserMapper.class),
            session.getMapper(dev.macula.cloud.system.mapper.SysTenantInfoMapper.class));
        service = new SysApplicationApiKeyServiceImpl(keys, apps, cache, tenantContext, new DateTimeConverter(null));
        applications = new SysApplicationServiceImpl(mock(ApplicationConverter.class), tenants, cache, keys, tenantContext);
        ReflectionTestUtils.setField(applications, "baseMapper", apps);
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clearCurrentTenantId();
        SecurityContextHolder.clearContext();
    }

    @AfterAll
    static void closeClients() throws Exception {
        if (redisson != null) {
            redisson.shutdown();
        }
        if (connections != null) {
            connections.destroy();
        }
    }

    /** 所有业务验证使用隔离库中的专属应用，不覆盖基线应用。 */
    private static void application(long id) {
        jdbc.update("INSERT INTO sys_application_tenant "
            + "(id,tenant_id,application_name,code,sk,manager,mobile,access_path,create_time,last_update_time) "
            + "VALUES (?,1,'integration application',?,'synthetic-hmac','test','000','GET:/app/**',NOW(),NOW())",
            id, "integration-" + id);
        root();
    }

    /** 为各测试线程显式初始化受信任的上下文。 */
    private static void root() {
        TenantContextHolder.setCurrentTenantId(1L);
        authenticate("root", "ROLE_ROOT");
    }

    /** 使用真实 token tenant claim，不能依赖测试身份的空属性绕过租户校验。 */
    private static void authenticate(String username, String role) {
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("synthetic-it-token")
            .header("alg", "none").subject(username).claim("tenantId", 1L).build();
        SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(role))));
    }

    @Test
    void migrationAndRealCacheLifecycleRespectIndependentUrlsAndRevocation() {
        application(900101L);
        var form = new ApplicationApiKeyForm();
        form.setAccessPath("POST:/key/**");
        var first = transaction.execute(status -> service.create(900101L, form));
        var second = transaction.execute(status -> service.create(900101L, new ApplicationApiKeyForm()));
        assertThat(first.getApiKey()).isNotEqualTo(second.getApiKey());
        String firstCache = CacheConstants.APIKEY_CACHE_PREFIX + first.getApiKey();
        String secondCache = CacheConstants.APIKEY_CACHE_PREFIX + second.getApiKey();
        assertThat(redis.opsForHash().get(firstCache, "permitUrls")).isEqualTo("POST:/key/**");
        assertThat(redis.opsForHash().get(secondCache, "permitUrls")).isEqualTo("GET:/app/**");
        assertThat(redis.opsForHash().get(firstCache, "applicationId")).isEqualTo("900101");
        assertThat(redis.opsForHash().get(firstCache, "tenantId")).isEqualTo("1");
        form.setAccessPath("");
        transaction.executeWithoutResult(status -> service.update(900101L, Long.valueOf(first.getId()), form));
        assertThat(redis.opsForHash().get(firstCache, "permitUrls")).isEqualTo("GET:/app/**");
        assertThat(jdbc.queryForObject("SELECT access_path FROM sys_application_apikey_tenant WHERE id=?",
            String.class, Long.valueOf(first.getId()))).isNull();
        transaction.executeWithoutResult(status -> service.delete(900101L, Long.valueOf(first.getId())));
        assertThat(redis.hasKey(firstCache)).isFalse();
        assertThat(redis.hasKey(secondCache)).isTrue();
        // 模拟Redis恢复了旧数据，启动重建必须利用持久化撤销状态清理它。
        redis.opsForHash().put(firstCache, "permitUrls", "GET:/**");
        cache.rebuild();
        assertThat(redis.hasKey(firstCache)).isFalse();
        assertThat(service.page(900101L, 1, 20).getTotal()).isEqualTo(1);
    }

    @Test
    void ordinaryManagerCanOperateAndCrossAppOrMissingRoleIsRejected() {
        application(900102L);
        application(900103L);
        authenticate("manager", "ROLE_USER");
        var created = transaction.execute(status -> service.create(900102L, new ApplicationApiKeyForm()));
        assertThat(service.page(900102L, 1, 20).getTotal()).isEqualTo(1);
        assertThatThrownBy(() -> service.secret(900103L, Long.valueOf(created.getId())))
            .isInstanceOf(ResponseStatusException.class);
        authenticate("reader", "ROLE_READER");
        assertThatThrownBy(() -> service.page(900102L, 1, 20)).isInstanceOf(ResponseStatusException.class);
        root();
        TenantContextHolder.setCurrentTenantId(12L);
        assertThatThrownBy(() -> service.secret(900102L, Long.valueOf(created.getId())))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rollbackDoesNotPublishAndApplicationDeletionCascadesToAllKeys() {
        application(900104L);
        var rolledBack = transaction.execute(status -> {
            var created = service.create(900104L, new ApplicationApiKeyForm());
            status.setRollbackOnly();
            return created;
        });
        assertThat(redis.hasKey(CacheConstants.APIKEY_CACHE_PREFIX + rolledBack.getApiKey())).isFalse();
        assertThat(service.page(900104L, 1, 20).getTotal()).isZero();
        var created = transaction.execute(status -> service.create(900104L, new ApplicationApiKeyForm()));
        transaction.executeWithoutResult(status -> applications.deleteApplications("900104"));
        assertThat(redis.hasKey(CacheConstants.APIKEY_CACHE_PREFIX + created.getApiKey())).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_application_apikey_tenant "
            + "WHERE application_id=900104 AND deleted=0", Long.class)).isZero();
    }

    @Test
    void createDuringApplicationDeletionWaitsForRowLockThenFailsClosed() throws Exception {
        application(900105L);
        var existing = transaction.execute(status -> service.create(900105L, new ApplicationApiKeyForm()));
        var executor = Executors.newSingleThreadExecutor();
        var started = new CountDownLatch(1);
        try {
            java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>> creator =
                new java.util.concurrent.atomic.AtomicReference<>();
            transaction.executeWithoutResult(status -> {
                keys.lockApplication(900105L, 1L);
                creator.set(executor.submit(() -> {
                    root();
                    try {
                        started.countDown();
                        return transaction.execute(nested -> service.create(900105L, new ApplicationApiKeyForm()));
                    } finally {
                        TenantContextHolder.clearCurrentTenantId();
                        SecurityContextHolder.clearContext();
                    }
                }));
                try {
                    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThrows(TimeoutException.class, () -> creator.get().get(200, TimeUnit.MILLISECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                applications.deleteApplications("900105");
            });
            var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                () -> creator.get().get(10, TimeUnit.SECONDS));
            assertThat(failure.getCause()).isInstanceOf(ResponseStatusException.class);
            assertThat(redis.hasKey(CacheConstants.APIKEY_CACHE_PREFIX + existing.getApiKey())).isFalse();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_application_apikey_tenant "
                + "WHERE application_id=900105 AND deleted=0", Long.class)).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void uniqueIndexDistinguishesCaseAndRetainsRevokedKeyUniqueness() {
        application(900106L);
        String sql = "INSERT INTO sys_application_apikey_tenant (tenant_id,application_id,api_key) VALUES (1,900106,?)";
        jdbc.update(sql, "sk-synthetic-case-A");
        jdbc.update(sql, "sk-synthetic-case-a");
        jdbc.update("UPDATE sys_application_apikey_tenant SET deleted=1 WHERE api_key=?", "sk-synthetic-case-A");
        assertThatThrownBy(() -> jdbc.update(sql, "sk-synthetic-case-A"))
            .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_application_apikey_tenant WHERE application_id=900106",
            Long.class)).isEqualTo(2);
    }

    @Test
    void zzBoundedPoolRefreshesWithoutBorrowingSecondConnection() throws Exception {
        application(900107L);
        var config = new com.zaxxer.hikari.HikariConfig();
        config.setJdbcUrl(System.getenv("APIKEY_IT_JDBC_URL"));
        config.setUsername(System.getenv("APIKEY_IT_USERNAME"));
        config.setPassword(System.getenv("APIKEY_IT_PASSWORD"));
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(500);
        try (var pool = new com.zaxxer.hikari.HikariDataSource(config)) {
            var factory = new MybatisSqlSessionFactoryBean();
            var mapping = new MybatisConfiguration();
            mapping.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(mapping);
            factory.setDataSource(pool);
            var global = new com.baomidou.mybatisplus.core.config.GlobalConfig();
            global.setMetaObjectHandler(new dev.macula.boot.starter.mp.handler.AuditMetaObjectHandler(
                new dev.macula.boot.starter.mp.config.MyBatisPlusProperties.Audit()));
            factory.setGlobalConfig(global);
            var sql = factory.getObject();
            sql.getConfiguration().addMapper(SysApplicationMapper.class);
            sql.getConfiguration().addMapper(SysApplicationApiKeyMapper.class);
            var session = new SqlSessionTemplate(sql);
            var manager = new DataSourceTransactionManager(pool);
            var bounded = new ApplicationCredentialCacheService(session.getMapper(SysApplicationMapper.class),
                session.getMapper(SysApplicationApiKeyMapper.class), mock(SysTenantService.class), redis, redisson, manager);
            var api = new SysApplicationApiKeyServiceImpl(session.getMapper(SysApplicationApiKeyMapper.class),
                session.getMapper(SysApplicationMapper.class), bounded, tenantContext, new DateTimeConverter(null));
            var tx = new TransactionTemplate(manager);
            var created = tx.execute(status -> api.create(900107L, new ApplicationApiKeyForm()));
            String key = CacheConstants.APIKEY_CACHE_PREFIX + created.getApiKey();
            assertThat(redis.hasKey(key)).isTrue();
            tx.executeWithoutResult(status -> api.delete(900107L, Long.valueOf(created.getId())));
            assertThat(redis.hasKey(key)).isFalse();
            assertThat(pool.getHikariPoolMXBean().getTotalConnections()).isEqualTo(1);
        }
    }
    /** 真实 Mapper 与 Redis 验证 IP 独立继承、清空及应用变更联动。 */
    @Test
    void peerIpConfigurationIsInheritedOverriddenAndClearedInRealCache() {
        application(900109L);
        jdbc.update("UPDATE sys_application_tenant SET allow_ips=? WHERE id=900109", "192.0.2.0/24");
        var form = new ApplicationApiKeyForm();
        form.setAccessPath("GET:/one, POST:/two");
        var created = transaction.execute(status -> service.create(900109L, form));
        String keyCache = CacheConstants.APIKEY_CACHE_PREFIX + created.getApiKey();
        assertThat(redis.opsForHash().get(keyCache, "permitUrls")).isEqualTo("GET:/one\nPOST:/two");
        assertThat(redis.opsForHash().get(keyCache, "permitIps")).isEqualTo("192.0.2.0/24");
        form.setAllowIps("2001:db8::/32");
        transaction.executeWithoutResult(status -> service.update(900109L, Long.valueOf(created.getId()), form));
        assertThat(redis.opsForHash().get(keyCache, "permitIps")).isEqualTo("2001:db8::/32");
        form.setAllowIps(null);
        transaction.executeWithoutResult(status -> service.update(900109L, Long.valueOf(created.getId()), form));
        assertThat(redis.opsForHash().get(keyCache, "permitIps")).isEqualTo("192.0.2.0/24");
        jdbc.update("UPDATE sys_application_tenant SET allow_ips=NULL WHERE id=900109");
        cache.refresh(1L, 900109L, null);
        assertThat(redis.opsForHash().get(keyCache, "permitIps")).isEqualTo("");
        assertThat(redis.opsForHash().get(CacheConstants.SECURITY_SYSTEM_APPS + "integration-900109", "permitIps"))
            .isEqualTo("");
    }

}
