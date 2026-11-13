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

import dev.macula.boot.starter.mp.config.MyBatisPlusProperties;
import dev.macula.boot.starter.mp.handler.AuditMetaObjectHandler;
import java.sql.Timestamp;

import dev.macula.cloud.system.service.impl.ProtocolConfigurationTestFixtures;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import dev.macula.cloud.system.mapper.SysIamApplicationMapper;
import dev.macula.cloud.system.mapper.SysIamApplicationProtocolConfigMapper;
import dev.macula.cloud.system.mapper.SysIamApplicationIdentityProviderMapper;
import dev.macula.cloud.system.pojo.entity.SysIamApplication;
import dev.macula.cloud.system.protocol.OAuth2ProtocolSettings;
import dev.macula.cloud.system.protocol.OAuth2ApplicationProtocolSettings;
import dev.macula.cloud.system.protocol.IamApplicationProtocolRegistration;
import dev.macula.boot.mapstruct.DateTimeConverter;
import dev.macula.cloud.system.service.impl.IamApplicationProtocolService;
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
        global.setMetaObjectHandler(new AuditMetaObjectHandler(
            new MyBatisPlusProperties().getAudit()));
        factory.setGlobalConfig(global);
        var sql = factory.getObject();
        for (Class<?> type : List.of(SysIamApplicationMapper.class, SysIamApplicationProtocolConfigMapper.class, SysIamApplicationIdentityProviderMapper.class)) { sql.getConfiguration().addMapper(type); }
        var session = new SqlSessionTemplate(sql);
        var mapper = session.getMapper(SysIamApplicationMapper.class);
        var service = ProtocolConfigurationTestFixtures.protocols(session.getMapper(SysIamApplicationProtocolConfigMapper.class), session.getMapper(SysIamApplicationIdentityProviderMapper.class));
        Long id = jdbc.queryForObject("SELECT MIN(id) FROM sys_iam_application", Long.class);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.update("UPDATE sys_iam_application SET application_type='WEB_SERVER' WHERE id=?", id);
        var initial = new OAuth2ProtocolSettings();
         initial.setAccessTokenTimeToLive(300L);
        transaction.executeWithoutResult(status -> replace(service, mapper.selectById(id), "OIDC", initial));
        String original = service.version(mapper.selectById(id));
        CountDownLatch locked = new CountDownLatch(1), attempted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> transaction.executeWithoutResult(status -> {
                var current = mapper.selectOne(new LambdaQueryWrapper<SysIamApplication>().eq(SysIamApplication::getId, id).last("FOR UPDATE"));
                service.checkVersion(current, service.state(id, true), original);
                 locked.countDown();
                try { assertTrue(attempted.await(5, TimeUnit.SECONDS));
                 } catch (InterruptedException ex) { throw new IllegalStateException(ex);
                 }
                var settings = new OAuth2ProtocolSettings();
                 settings.setAccessTokenTimeToLive(120L);
                replace(service, current, "OAUTH2_1", settings);
                jdbc.update("UPDATE sys_iam_application_protocol_config SET credential_hash='rotated-test-hash' WHERE application_id=?", id);
            }));
            Future<?> second = executor.submit(() -> {
                try { assertTrue(locked.await(5, TimeUnit.SECONDS));
                 } catch (InterruptedException ex) { throw new IllegalStateException(ex);
                 }
                attempted.countDown();
                assertThrows(ResponseStatusException.class, () -> transaction.executeWithoutResult(status -> {
                    var current = mapper.selectOne(new LambdaQueryWrapper<SysIamApplication>().eq(SysIamApplication::getId, id).last("FOR UPDATE"));
                    service.checkVersion(current, service.state(id, true), original);
                }));
            });
            first.get(15, TimeUnit.SECONDS);
             second.get(15, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        var savedRegistration = service.registration(mapper.selectById(id), service.state(id, false));
        assertEquals("OAUTH2_1", savedRegistration.protocol());
        assertEquals(120L, ((OAuth2ApplicationProtocolSettings) savedRegistration.settings()).getOauth2().getAccessTokenTimeToLive());
        String saved = service.version(mapper.selectById(id));
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            mapper.selectOne(new LambdaQueryWrapper<SysIamApplication>().eq(SysIamApplication::getId, id).last("FOR UPDATE"));
            service.delete(id);
             throw new IllegalStateException("fixture rollback");
        }));
        assertEquals(saved, service.version(mapper.selectById(id)));
        assertNotNull(jdbc.queryForObject("SELECT create_time FROM sys_iam_application_protocol_config WHERE application_id=? LIMIT 1", Timestamp.class, id));
    }
    /** 以真实v2载荷替换登记，凭据只从受保护存储列保留。 */
    private void replace(IamApplicationProtocolService service, SysIamApplication app, String protocol, OAuth2ProtocolSettings settings) {
        var state = service.state(app.getId(), true);
        var payload = new OAuth2ApplicationProtocolSettings();
        payload.setOauth2(settings);
        var registration = new IamApplicationProtocolRegistration<>(protocol, 2, false, app.getApplicationCode(),
            new DateTimeConverter("UTC").toInstant(app.getApplicationCodeIssuedAt()), payload);
        service.save(app, state, registration, state.protocol() == null ? null : state.protocol().getCredentialHash());
    }
}
