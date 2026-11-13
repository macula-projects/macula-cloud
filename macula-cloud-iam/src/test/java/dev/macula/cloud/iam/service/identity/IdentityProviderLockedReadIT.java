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
package dev.macula.cloud.iam.service.identity;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import dev.macula.boot.starter.crypto.config.CryptoProperties;
import dev.macula.boot.starter.crypto.core.CryptoContext;
import dev.macula.boot.starter.crypto.core.CryptoManager;
import dev.macula.boot.starter.crypto.enums.AlgorithmType;
import dev.macula.boot.starter.crypto.enums.EncodeType;
import dev.macula.boot.starter.mp.interceptor.MybatisDecryptInterceptor;
import dev.macula.boot.starter.mp.interceptor.MybatisEncryptInterceptor;
import dev.macula.cloud.iam.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.iam.pojo.entity.SysIdentityProvider;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通过隔离MySQL和真实Crypto插件验证绑定锁定读；不使用业务凭据或真实第三方身份。
 *
 * @author Rain
 * @since 6.1.0
 */
@EnabledIfEnvironmentVariable(named = "IAM_LOCKED_SOURCE_IT", matches = "true")
class IdentityProviderLockedReadIT {
    private static final long SOURCE_ID = 93111L;
    private static final long INVALID_SOURCE_ID = 93112L;
    private static final String FIXTURE_SECRET = "synthetic-provider-secret";
    private JdbcTemplate jdbc;
    private SysIdentityProviderMapper sources;
    private IdentityProviderConfigService configs;
    private TransactionTemplate transaction;
    private CryptoManager crypto;
    private CryptoContext context;

    /** 只允许本机专用隔离数据库，安装与生产相同的加解密插件。 */
    @BeforeEach
    void prepare() throws Exception {
        String url = System.getenv("IAM_LOCKED_SOURCE_URL");
        assertTrue(url != null && url.startsWith("jdbc:mysql://127.0.0.1:"));
        URI target = URI.create(url.substring(5));
        assertEquals("127.0.0.1", target.getHost());
        assertTrue(target.getPath().startsWith("/macula_identity_binding_it_"));
        var dataSource = new DriverManagerDataSource(url, System.getenv("IAM_SOURCE_IT_USER"),
                System.getenv("IAM_SOURCE_IT_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sys_identity_provider (
                id BIGINT PRIMARY KEY, name TEXT, provider_type TEXT, source_type TEXT, credential_type TEXT,
                capabilities_json TEXT, identity_flow TEXT, directory_policy TEXT, organization_id TEXT,
                oauth_redirect_uri TEXT, auto_bind_enabled BOOLEAN, binding_rule TEXT,
                binding_source_field TEXT, binding_target_field TEXT, binding_mappings_json TEXT,
                version BIGINT, app_id TEXT, app_secret TEXT, config_json TEXT, secret_config_json TEXT,
                enabled BOOLEAN, visible BOOLEAN)
                """);
        jdbc.update("DELETE FROM sys_identity_provider WHERE id IN (?,?)", SOURCE_ID, INVALID_SOURCE_ID);
        var properties = new CryptoProperties();
        properties.setEnable(true);
        properties.setAlgorithm(AlgorithmType.AES);
        properties.setEncode(EncodeType.BASE64);
        properties.setPassword("fixture-key-1234");
        crypto = new CryptoManager();
        context = new CryptoContext(properties);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setPlugins(new MybatisDecryptInterceptor(crypto, properties),
                new MybatisEncryptInterceptor(crypto, properties));
        var sqlFactory = factory.getObject();
        sqlFactory.getConfiguration().addMapper(SysIdentityProviderMapper.class);
        sources = new SqlSessionTemplate(sqlFactory).getMapper(SysIdentityProviderMapper.class);
        configs = new IdentityProviderConfigService(sources);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        insertSource(SOURCE_ID, "{}");
    }

    /** 普通读和同事务重复锁定读必须得到同一有效配置，数据库仍保存原密文。 */
    @Test
    void lockedReadsPreserveDecryptionAndSourceValidation() {
        SysIdentityProvider ordinary = sources.selectById(SOURCE_ID);
        assertReadable(ordinary);
        String stored = jdbc.queryForObject("SELECT app_secret FROM sys_identity_provider WHERE id=?",
                String.class, SOURCE_ID);
        transaction.execute(status -> {
            assertReadable(sources.selectByIdForUpdate(SOURCE_ID));
            assertReadable(sources.selectByIdForUpdate(SOURCE_ID));
            return null;
        });
        assertTrue(stored.equals(jdbc.queryForObject("SELECT app_secret FROM sys_identity_provider WHERE id=?",
                String.class, SOURCE_ID)));
        assertTrue(!FIXTURE_SECRET.equals(stored));
    }

    /** 缺失来源或解密后的非法配置仍失败关闭，不把新查询当作跳过校验。 */
    @Test
    void missingAndInvalidConfigurationsRemainRejected() {
        insertSource(INVALID_SOURCE_ID, "not-json");
        transaction.execute(status -> {
            assertNull(sources.selectByIdForUpdate(-1L));
            assertThrows(AuthenticationServiceException.class,
                    () -> configs.requireLoaded(sources.selectByIdForUpdate(-1L), -1L, "GITHUB"));
            assertThrows(AuthenticationServiceException.class,
                    () -> configs.requireLoaded(sources.selectByIdForUpdate(INVALID_SOURCE_ID),
                            INVALID_SOURCE_ID, "GITHUB"));
            return null;
        });
    }

    /** 不在断言失败消息中输出加密字段；使用真实配置校验保护解析和来源合同。 */
    private void assertReadable(SysIdentityProvider source) {
        assertTrue(FIXTURE_SECRET.equals(source.getAppSecret()));
        assertTrue("{}".equals(source.getSecretConfigJson()));
        assertDoesNotThrow(() -> configs.requireLoaded(source, SOURCE_ID, "GITHUB"));
    }

    /** 仅通过JDBC写入合成密文，避免被测试入参加密器重复加密。 */
    private void insertSource(long id, String secretJson) {
        jdbc.update("""
                INSERT INTO sys_identity_provider(id,provider_type,source_type,identity_flow,directory_policy,
                auto_bind_enabled,version,app_id,app_secret,config_json,secret_config_json,enabled,visible)
                VALUES(?, 'GITHUB', 'GITHUB_OAUTH', 'GITHUB_OAUTH_V1', 'IDENTITY_ONLY', false, 0,
                'fixture-app', ?, '{"clientId":"fixture-app"}', ?, true, true)
                """, id, crypto.encrypt(FIXTURE_SECRET, context), crypto.encrypt(secretJson, context));
    }
}
