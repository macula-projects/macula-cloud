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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import dev.macula.cloud.system.mapper.SysOAuth2ClientConfigMapper;
import dev.macula.cloud.system.mapper.SysApplicationProtocolConfigMapper;
import dev.macula.cloud.system.mapper.SysOAuth2ClientExternalProviderMapper;
import dev.macula.cloud.system.pojo.entity.SysOAuth2ClientConfig;
import dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration;
import dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration;
import dev.macula.cloud.system.protocol.OAuth2ProtocolSettings;
import dev.macula.cloud.system.service.impl.ApplicationProtocolConfigurationService;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 隔离MySQL证明不同协议与凭据共用主行锁、旧快照冲突及子配置事务回滚。
 * @author Rain
 * @since 6.1.0
 */
@EnabledIfEnvironmentVariable(named = "APPLICATION_PROTOCOL_IT", matches = "true")
class ApplicationProtocolConfigurationConcurrencyIT {
    /** 第二个页面不能覆盖已提交的另一个协议或共享密钥，失败事务无部分写入。 */
    @Test
    void serializesProtocolSnapshotsAndRollsBackTogether() throws Exception {
        String url = System.getenv("APPLICATION_PROTOCOL_CONCURRENCY_URL");
        assertTrue(url.startsWith("jdbc:mysql://127.0.0.1:"));
        assertTrue(URI.create(url.substring(5)).getPath().startsWith("/macula_identity_binding_it_"));
        var ds = new DriverManagerDataSource(url, System.getenv("IAM_SOURCE_IT_USER"), System.getenv("IAM_SOURCE_IT_PASSWORD"));
        var jdbc = new JdbcTemplate(ds);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class));
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        var config = new MybatisConfiguration();
         config.setMapUnderscoreToCamelCase(true);
        var factory = new MybatisSqlSessionFactoryBean();
         factory.setDataSource(ds);
         factory.setConfiguration(config);
        var global = new com.baomidou.mybatisplus.core.config.GlobalConfig();
        global.setMetaObjectHandler(new dev.macula.boot.starter.mp.handler.AuditMetaObjectHandler(
            new dev.macula.boot.starter.mp.config.MyBatisPlusProperties().getAudit()));
        factory.setGlobalConfig(global);
        var sql = factory.getObject();
        for (Class<?> type : List.of(SysOAuth2ClientConfigMapper.class, SysApplicationProtocolConfigMapper.class, SysOAuth2ClientExternalProviderMapper.class)) { sql.getConfiguration().addMapper(type); }
        var session = new SqlSessionTemplate(sql);
        var mapper = session.getMapper(SysOAuth2ClientConfigMapper.class);
        var service = new ApplicationProtocolConfigurationService(session.getMapper(SysApplicationProtocolConfigMapper.class), session.getMapper(SysOAuth2ClientExternalProviderMapper.class));
        Long id = jdbc.queryForObject("SELECT MIN(id) FROM sys_oauth2_client", Long.class);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(ds));
        var initial = new OAuth2ProtocolSettings();
         initial.setAccessTokenTimeToLive(300L);
        transaction.executeWithoutResult(status -> service.replace(id, List.of(new ApplicationProtocolConfiguration("OIDC", 1, false, initial))));
        String original = service.version(mapper.selectById(id));
        CountDownLatch locked = new CountDownLatch(1), attempted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> transaction.executeWithoutResult(status -> {
                var current = mapper.selectOne(new LambdaQueryWrapper<SysOAuth2ClientConfig>().eq(SysOAuth2ClientConfig::getId, id).last("FOR UPDATE"));
                service.checkVersion(current, original);
                 locked.countDown();
                try { assertTrue(attempted.await(5, TimeUnit.SECONDS));
                 } catch (InterruptedException ex) { throw new IllegalStateException(ex);
                 }
                var settings = new OAuth2ProtocolSettings();
                 settings.setAccessTokenTimeToLive(120L);
                service.replace(id, List.of(new ApplicationProtocolConfiguration("OAUTH2_1", 1, false, settings)));
                jdbc.update("UPDATE sys_oauth2_client SET client_secret='rotated-test-hash' WHERE id=?", id);
            }));
            Future<?> second = executor.submit(() -> {
                try { assertTrue(locked.await(5, TimeUnit.SECONDS));
                 } catch (InterruptedException ex) { throw new IllegalStateException(ex);
                 }
                attempted.countDown();
                assertThrows(ResponseStatusException.class, () -> transaction.executeWithoutResult(status -> {
                    var current = mapper.selectOne(new LambdaQueryWrapper<SysOAuth2ClientConfig>().eq(SysOAuth2ClientConfig::getId, id).last("FOR UPDATE"));
                    service.checkVersion(current, original);
                }));
            });
            first.get(15, TimeUnit.SECONDS);
             second.get(15, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        assertEquals(1, service.list(id).size());
        assertEquals(120L, OAuth2ProtocolConfiguration.settings(service.list(id).stream().filter(value -> "OAUTH2_1".equals(value.protocol())).findFirst().orElseThrow()).getAccessTokenTimeToLive());
        String saved = service.version(mapper.selectById(id));
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            mapper.selectOne(new LambdaQueryWrapper<SysOAuth2ClientConfig>().eq(SysOAuth2ClientConfig::getId, id).last("FOR UPDATE"));
            service.replace(id, List.of());
             throw new IllegalStateException("fixture rollback");
        }));
        assertEquals(saved, service.version(mapper.selectById(id)));
        assertNotNull(jdbc.queryForObject("SELECT create_time FROM sys_application_protocol_config WHERE application_id=? LIMIT 1", java.sql.Timestamp.class, id));
    }
}
