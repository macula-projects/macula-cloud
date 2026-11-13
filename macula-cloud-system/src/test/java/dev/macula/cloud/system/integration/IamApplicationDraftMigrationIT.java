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

import dev.macula.cloud.system.protocol.oauth2.OAuth2ProtocolDefinition;
import dev.macula.cloud.system.service.impl.ProtocolConfigurationTestFixtures;
import dev.macula.boot.starter.mp.config.MyBatisPlusProperties;
import dev.macula.boot.starter.mp.handler.AuditMetaObjectHandler;
import dev.macula.cloud.system.mapper.SysIamApplicationProtocolConfigMapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import dev.macula.cloud.system.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.system.mapper.SysIamApplicationMapper;
import dev.macula.cloud.system.mapper.SysIamApplicationIdentityProviderMapper;
import dev.macula.cloud.system.mapper.SysTenantInfoMapper;
import dev.macula.cloud.system.service.impl.IamApplicationCatalogServiceImpl;
import dev.macula.cloud.system.config.OAuth2ScopeCatalogProperties;
import dev.macula.cloud.system.form.IamApplicationDraftForm;
import java.net.URI;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.cache.Cache;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * 独立MySQL验证V15升级和真实草稿Mapper持久化，不推断旧Client协议。
 * @author Rain
 * @since 6.1.0
 */
@EnabledIfEnvironmentVariable(named = "IAM_PROTOCOL_DRAFT_IT", matches = "true")
class IamApplicationDraftMigrationIT {
    /** 原配置不变、新字段保持草稿，真实服务创建不造假租户或授权能力。 */
    @Test
    void upgradesAndPersistsUnconfiguredDrafts() throws Exception {
        String url = System.getenv("IAM_SOURCE_IT_URL");
        assertTrue(url != null && url.startsWith("jdbc:mysql://127.0.0.1:"));
        assertTrue(URI.create(url.substring(5)).getPath().startsWith("/macula_identity_binding_it_"));
        var dataSource = new DriverManagerDataSource(url,System.getenv("IAM_SOURCE_IT_USER"),System.getenv("IAM_SOURCE_IT_PASSWORD"));
        var jdbc = new JdbcTemplate(dataSource);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",Integer.class));
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("15").load().migrate();
        String before = jdbc.queryForList("SELECT id,client_id,client_secret,protocol_mode,authorization_grant_types,scopes,tenant_id,user_type FROM sys_oauth2_client ORDER BY id").toString();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("16").load().migrate();
        assertEquals(before,jdbc.queryForList("SELECT id,client_id,client_secret,protocol_mode,authorization_grant_types,scopes,tenant_id,user_type FROM sys_oauth2_client ORDER BY id").toString());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_oauth2_client WHERE enabled_protocols IS NOT NULL OR default_protocol IS NOT NULL OR configuration_status<>'DRAFT'",Integer.class));
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("27").load().migrate();
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        var global = new com.baomidou.mybatisplus.core.config.GlobalConfig();
        global.setMetaObjectHandler(new AuditMetaObjectHandler(
            new MyBatisPlusProperties().getAudit()));
        factory.setGlobalConfig(global);
        var sqlFactory = factory.getObject();
        for (Class<?> mapper : new Class<?>[]{SysIamApplicationMapper.class,SysIamApplicationProtocolConfigMapper.class,SysIamApplicationIdentityProviderMapper.class,
            SysIdentityProviderMapper.class,SysTenantInfoMapper.class}) { sqlFactory.getConfiguration().addMapper(mapper); }
        var session = new SqlSessionTemplate(sqlFactory);
        var caches = mock(CacheManager.class);
        when(caches.getCache(anyString())).thenReturn(mock(Cache.class));
        var sources = session.getMapper(SysIdentityProviderMapper.class);
        var service = ProtocolConfigurationTestFixtures.service(session.getMapper(SysIamApplicationMapper.class), session.getMapper(SysIamApplicationIdentityProviderMapper.class), session.getMapper(SysIamApplicationProtocolConfigMapper.class), sources, session.getMapper(SysTenantInfoMapper.class), mock(PasswordEncoder.class), caches, new IamApplicationCatalogServiceImpl(new OAuth2ScopeCatalogProperties(), sources, OAuth2ProtocolDefinition.registry()));
        var form = new IamApplicationDraftForm();
        form.setClientName("isolated draft");
        form.setApplicationType("SPA");
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var result = transaction.execute(status -> service.createDraft(form));
        assertEquals("DRAFT",result.getConfigurationStatus());
        assertNull(result.getTenantId());
        assertNull(result.getUserType());
        assertNull(result.getProtocol());
        assertNull(result.getSettings());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_iam_application_protocol_config WHERE application_id=?", Integer.class, result.getId()));
    }
}
