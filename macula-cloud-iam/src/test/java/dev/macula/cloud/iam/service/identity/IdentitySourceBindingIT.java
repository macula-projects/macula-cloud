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
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import dev.macula.boot.mapstruct.DateTimeConverter;
import dev.macula.boot.starter.mp.config.MyBatisPlusProperties;
import dev.macula.boot.starter.mp.handler.AuditMetaObjectHandler;
import dev.macula.cloud.iam.authentication.thirdparty.BindingTestTransactions;
import dev.macula.cloud.iam.authentication.thirdparty.ExternalBindingTicketStore;
import dev.macula.cloud.iam.authentication.thirdparty.PendingThirdPartyBindingRepository;
import dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity;
import dev.macula.cloud.iam.authentication.thirdparty.binding.ThirdPartyBindingService;
import dev.macula.cloud.iam.authentication.verification.RedisVerificationCodeStore;
import dev.macula.cloud.iam.authentication.verification.SmsVerificationProperties;
import dev.macula.cloud.iam.authentication.verification.VerificationChannel;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeChannelService;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeContext;
import dev.macula.cloud.iam.authentication.verification.VerificationCodePurpose;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeScope;
import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.mapper.SysIamApplicationIdentityProviderMapper;
import dev.macula.cloud.iam.mapper.SysIamApplicationMapper;
import dev.macula.cloud.iam.mapper.SysIamApplicationProtocolConfigMapper;
import dev.macula.cloud.iam.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.iam.mapper.SysIdentityProviderUserBindingMapper;
import dev.macula.cloud.iam.mapper.SysUserMapper;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2ClientRegistrationServiceImpl;
import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2LoginApplicationResolver;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalBindingRequiredException;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeAccountResolver;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeClientSettings;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeSecurityProperties;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeVerification;
import dev.macula.cloud.iam.service.account.ClientAccountDetailsService;
import dev.macula.cloud.iam.service.account.SysUserService;
import dev.macula.cloud.iam.service.application.IamApplicationService;
import dev.macula.cloud.iam.service.application.IamApplicationServiceImpl;
import dev.macula.cloud.iam.service.application.IamApplicationSnapshot;
import dev.macula.cloud.iam.service.identity.directory.IdentityDirectoryService;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import dev.macula.cloud.system.enums.ExternalCodeType;
import dev.macula.cloud.system.enums.IdentityBindingMapping;
import dev.macula.cloud.system.protocol.OAuth2ApplicationProtocolSettings;
import dev.macula.cloud.system.protocol.OAuth2ProtocolDefinition;
import dev.macula.cloud.system.protocol.OAuth2ProtocolSettings;
import dev.macula.cloud.system.security.UserIdentifierNormalizer;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.mybatis.spring.SqlSessionTemplate;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 在V27隔离MySQL通过真实Mapper、Service和事务验证来源隔离及并发绑定。
 * 用户认证范围服务为明确Mock，实际厂商及完整权限集成不由本测试证明。
 *
 * @author Rain
 * @since 6.1.0
 */
@EnabledIfEnvironmentVariable(named = "IAM_SOURCE_BINDING_IT", matches = "true")
class IdentitySourceBindingIT {
    private static final String CLIENT_A = "binding-it-a";
    private static final String CLIENT_B = "binding-it-b";
    private static final String ALICE = "binding-it-alice";
    private JdbcTemplate jdbc;
    private SysUserMapper identityUsers;
    private TransactionTemplate transaction;
    private IdentityProviderConfigService configs;
    private IdentityProviderUserBindingService bindings;
    private IdentitySourceBindingPolicyService policy;

    /**
     * 只操作经过迁移测试建立的专用数据库和本类命名空间。
     */
    @BeforeEach
    void prepare() throws Exception {
        String url = System.getenv("IAM_SOURCE_IT_URL");
        assertTrue(url != null && url.startsWith("jdbc:mysql://127.0.0.1:"));
        assertTrue(URI.create(url.substring(5)).getPath().startsWith("/macula_identity_binding_it_"));
        var dataSource = new DriverManagerDataSource(url, System.getenv("IAM_SOURCE_IT_USER"),
                System.getenv("IAM_SOURCE_IT_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        assertEquals("27", jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1",
                String.class));
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        jdbc.update("DELETE FROM sys_identity_provider_user_binding WHERE identity_provider_id BETWEEN 91000 AND 91999");
        jdbc.update("DELETE r FROM sys_iam_application_identity_provider r JOIN sys_iam_application c ON c.id=r.application_id WHERE c.application_code IN (?,?)", CLIENT_A, CLIENT_B);
        jdbc.update(
                "DELETE p FROM sys_iam_application_protocol_config p JOIN sys_iam_application c ON c.id=p.application_id " +
                        "WHERE c.application_code IN (?,?)",
                CLIENT_A, CLIENT_B);
        jdbc.update("DELETE FROM sys_iam_application WHERE application_code IN (?,?)", CLIENT_A, CLIENT_B);
        jdbc.update("DELETE FROM sys_identity_provider WHERE id BETWEEN 91000 AND 91999");
        jdbc.update("DELETE FROM sys_user WHERE id IN (91001,91002)");
        jdbc.update("INSERT INTO sys_user(id,username,nickname,mobile,status,deleted,create_time,last_update_time) "
                        +
                        "VALUES(91001,?,'Fixture Alice','13800000001',1,0,NOW(),NOW()),(91002,'binding-it-bob'," +
                        "'Fixture Bob','13800000002',1,0,NOW(),NOW())",
                ALICE);
        source(91011L, "source-a", "corp-a");
        source(91012L, "source-b", "corp-b");
        client(CLIENT_A, 91011L);
        client(CLIENT_B, 91012L);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new ClassPathResource("mapper/SysUserMapper.xml"));
        var global = new GlobalConfig();
        global.setMetaObjectHandler(new AuditMetaObjectHandler(
                new MyBatisPlusProperties().getAudit()));
        factory.setGlobalConfig(global);
        var sqlFactory = factory.getObject();
        for (Class<?> mapper : new Class<?>[]{SysIdentityProviderMapper.class, SysIamApplicationMapper.class,
                SysIamApplicationProtocolConfigMapper.class,
                SysIamApplicationIdentityProviderMapper.class, SysIdentityProviderUserBindingMapper.class,
                SysUserMapper.class}) {
            if (!sqlFactory.getConfiguration().hasMapper(mapper)) {
                sqlFactory.getConfiguration().addMapper(mapper);
            }
        }
        var session = new SqlSessionTemplate(sqlFactory);
        identityUsers = session.getMapper(SysUserMapper.class);
        configs = new IdentityProviderConfigService(session.getMapper(SysIdentityProviderMapper.class));
        ClientAccountDetailsService details = mock(ClientAccountDetailsService.class);
        when(details.loadUserByApplication(any(), any(), any(), any())).thenAnswer(call -> {
            Long id = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=? AND deleted=0", Long.class,
                    (String) call.getArgument(3));
            var result = new SysUserDetails();
            result.setUserId(id);
            result.setUsername(call.getArgument(3));
            result.setTenantId(call.getArgument(1));
            result.setEnabled(true);
            return result;
        });
        var rawReader = new IamApplicationServiceImpl(session.getMapper(SysIamApplicationMapper.class),
            session.getMapper(SysIamApplicationProtocolConfigMapper.class),
            session.getMapper(SysIamApplicationIdentityProviderMapper.class), new DateTimeConverter("Asia/Shanghai"),
            OAuth2ProtocolDefinition.registry());
        var reads = new TransactionTemplate(transaction.getTransactionManager());
        reads.setReadOnly(true);
        reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        IamApplicationService appReader = new IamApplicationService() {
            /** 保持生产读取端口的RR事务边界。 */
            @Override public IamApplicationSnapshot findById(Long id) {
                return reads.execute(status -> rawReader.findById(id));
            }
            /** 锁始终加入调用者事务，不创建独立提交边界。 */
            @Override public IamApplicationSnapshot lock(Long id) { return rawReader.lock(id); }
        };
        var oauth = new OAuth2ClientRegistrationServiceImpl(session.getMapper(SysIamApplicationProtocolConfigMapper.class),
            appReader, new DateTimeConverter("Asia/Shanghai"));
        var loginApplications = new OAuth2LoginApplicationResolver(oauth, appReader,
            session.getMapper(SysIamApplicationProtocolConfigMapper.class), AuthorizationServerSettings.builder().build());
        bindings = new IdentityProviderUserBindingService(loginApplications, session.getMapper(SysIdentityProviderMapper.class),
            session.getMapper(SysUserMapper.class), session.getMapper(SysIdentityProviderUserBindingMapper.class),
            configs, details, new UserIdentifierNormalizer());
        policy = new IdentitySourceBindingPolicyService(bindings, configs, session.getMapper(SysUserMapper.class),
                BindingTestTransactions.directory());
    }

    /**
     * OIDC使用真实来源策略、账号resolver、Mapper与Redis票据，不Mock绑定链。
     */
    @Test
    void oidcSdkResolvesAutomaticAndManualBindingsThroughRealServices() {
        jdbc.update("UPDATE sys_iam_application_protocol_config p JOIN sys_iam_application c ON c.id=p.application_id "
                        +
                        "SET p.protocol='OIDC',p.configuration=JSON_SET(p.configuration,'$.oauth2.scopes','openid,profile') " +
                        "WHERE c.application_code=?",
                CLIENT_A);
        var redisson = isolatedRedis();
        try {
            var redis = new StringRedisTemplate(
                    new RedissonConnectionFactory(redisson));
            var security = new ExternalCodeSecurityProperties();
            security.setDigestKey("fixture-oidc-binding-digest-key");
            var tickets = new ExternalBindingTicketStore(redis, security);
            var resolver = new ExternalCodeAccountResolver(
                    policy, tickets);
            var type = ExternalCodeType.WECOM_APP_CODE;
            var client = RegisteredClient.withId(
                            CLIENT_A)
                    .clientId(CLIENT_A).clientAuthenticationMethod(
                            ClientAuthenticationMethod.NONE)
                    .authorizationGrantType(new AuthorizationGrantType(
                            ExternalCodeType.GRANT_TYPE))
                    .scope("openid").scope("profile").clientSettings(
                            ClientSettings.builder()
                                    .setting(
                                            ExternalCodeClientSettings.TENANT_ID,
                                            1L)
                                    .setting(
                                            ExternalCodeClientSettings.USER_TYPE,
                                            "employee")
                                    .setting(
                                            ExternalCodeClientSettings.PROVIDER_BINDINGS,
                                            Map.of(type.name(), 91011L)).build()).build();
            var manual = new ExternalCodeVerification(
                    identity(91011L, "manual-member"));
            var error = assertThrows(
                    ExternalBindingRequiredException.class,
                    () -> resolver.resolve(client, type, manual));
            assertEquals(0, count());
            var claim = tickets.claim(error.getBindingTicket());
            transaction.execute(
                    status -> bindings.bind(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE, claim.value().identity(), 91001L,
                            false));
            tickets.consume(claim);
            assertEquals(ALICE, resolver.resolve(client, type, manual).getName());
            jdbc.update(
                    "UPDATE sys_identity_provider SET auto_bind_enabled=1,binding_rule='MEMBER_ID_TO_USERNAME'," +
                            "version=version+1 WHERE id=91011");
            var automatic = new ExternalCodeVerification(
                    identity(91011L, "binding-it-bob"));
            assertEquals("binding-it-bob",
                    transaction.execute(status -> resolver.resolve(client, type, automatic)).getName());
            assertEquals(2, count());
            jdbc.update("DELETE r FROM sys_iam_application_identity_provider r JOIN sys_iam_application c ON c.id=r.application_id WHERE c.application_code=?", CLIENT_A);
            assertThrows(BadCredentialsException.class, () -> resolver.resolve(client, type, manual));
        } finally {
            redisson.shutdown();
        }
    }

    /**
     * 企业同名账号在关闭开关时必须待绑定；显式开启后才自动建立关系。
     */
    @Test
    void sourceToggleControlsFirstBindingAndExistingBindingSurvivesDisable() {
        var identity = identity(91011L, ALICE);
        assertTrue(
                transaction.execute(status -> policy.resolve(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE, identity)).isEmpty());
        assertEquals(0, count());
        jdbc.update(
                "UPDATE sys_identity_provider SET auto_bind_enabled=1,binding_rule='MEMBER_ID_TO_USERNAME'," +
                        "version=version+1 WHERE id=91011");
        assertThrows(BadCredentialsException.class, () -> transaction.execute(status ->
                policy.resolve(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE, identity)));
        var enabledIdentity = identity(91011L, ALICE);
        var user = transaction.execute(status -> policy.resolve(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE, enabledIdentity))
                .orElseThrow();
        assertEquals(ALICE, user.getUsername());
        assertEquals(1, count());
        jdbc.update(
                "UPDATE sys_identity_provider SET auto_bind_enabled=0,binding_rule=NULL,version=version+1 WHERE " +
                        "id=91011");
        jdbc.update("UPDATE sys_user SET username='binding-it-renamed' WHERE id=91001");
        var freshIdentity = identity(91011L, ALICE);
        assertEquals("binding-it-renamed",
                transaction.execute(status -> policy.resolve(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE, freshIdentity))
                        .orElseThrow().getUsername());
    }

    /**
     * 两个企业返回相同成员标识时，绑定和账号不会跨registration共享。
     */
    @Test
    void sameSubjectInTwoSourcesHasIndependentOwners() {
        var first = identity(91011L, "same-member");
        var second = identity(91012L, "same-member");
        transaction.execute(status -> bindings.bind(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE, first, 91001L, false));
        transaction.execute(status -> bindings.bind(CLIENT_B, 1L, UserTypeEnum.EMPLOYEE, second, 91002L, false));
        assertEquals(ALICE,
                transaction.execute(status -> bindings.findBound(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE, first))
                        .orElseThrow().getUsername());
        assertEquals("binding-it-bob",
                transaction.execute(status -> bindings.findBound(CLIENT_B, 1L, UserTypeEnum.EMPLOYEE, second))
                        .orElseThrow().getUsername());
        assertThrows(BadCredentialsException.class, () -> transaction.execute(status ->
                bindings.findBound(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE, second)));
    }

    /**
     * 同一外部身份竞争不同账号，实际数据库事务仅允许一个拥有者。
     */
    @Test
    void concurrentManualBindingsCannotOverwriteOwner() throws Exception {
        var identity = identity(91011L, "racing-member");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> raceBind(identity, 91001L, ready, start));
            var second = executor.submit(() -> raceBind(identity, 91002L, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS));
            assertEquals(1, count());
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 自动和手动同时争用一身份时，已有拥有者优先，不能被属性匹配覆盖。
     */
    @Test
    void automaticAndManualBindingShareAtomicBoundary() throws Exception {
        jdbc.update(
                "UPDATE sys_identity_provider SET auto_bind_enabled=1,binding_rule='MEMBER_ID_TO_USERNAME' WHERE " +
                        "id=91011");
        var identity = identity(91011L, ALICE);
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var automatic = executor.submit(() -> {
                start.await();
                try {
                    transaction.execute(status -> policy.resolve(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE, identity))
                            .orElseThrow();
                    return true;
                } catch (BadCredentialsException ex) {
                    return false;
                }
            });
            var manual = executor.submit(() -> {
                start.await();
                try {
                    transaction.execute(
                            status -> bindings.bind(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE, identity, 91002L, false));
                    return true;
                } catch (BadCredentialsException ex) {
                    return false;
                }
            });
            start.countDown();
            boolean automaticSucceeded = automatic.get(10, TimeUnit.SECONDS);
            boolean manualWon = manual.get(10, TimeUnit.SECONDS);
            assertTrue(automaticSucceeded || manualWon);
            assertEquals(manualWon ? 91002L : 91001L, jdbc.queryForObject(
                    "SELECT user_id FROM sys_identity_provider_user_binding WHERE identity_provider_id=91011", Long.class));
            assertEquals(1, count());
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 来源锁使并发停用先提交后，待绑定事务必须重读并拒绝。
     */
    @Test
    void disabledSourceCannotBeBoundAfterWaitingForConfigurationLock() throws Exception {
        var identity = identity(91011L, "disabled-race");
        var executor = Executors.newSingleThreadExecutor();
        var operation =
                new AtomicReference<Future<UserDetails>>();
        try {
            transaction.executeWithoutResult(status -> {
                jdbc.queryForObject("SELECT id FROM sys_identity_provider WHERE id=91011 FOR UPDATE", Long.class);
                var future = executor.submit(() -> transaction.execute(inner ->
                        bindings.bind(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE, identity, 91001L, false)));
                operation.set(future);
                assertThrows(TimeoutException.class, () -> future.get(150, TimeUnit.MILLISECONDS));
                jdbc.update("UPDATE sys_identity_provider SET enabled=0,version=version+1 WHERE id=91011");
            });
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            var failure = assertThrows(ExecutionException.class, () -> operation.get().get());
            assertTrue(
                    failure.getCause() instanceof AuthenticationServiceException);
            assertEquals(0, count());
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 手机号证明不冒充永久主体，本地号码变化后旧关联立即失效。
     */
    @Test
    void verifiedMobileRelationChecksCurrentLocalPhone() {
        jdbc.update("UPDATE sys_identity_provider SET provider_type='ALIYUN_MOBILE',source_type='ALIYUN_MOBILE',"
                + "app_id='key',config_json='{\"accessKeyId\":\"key\"}' WHERE id=91011");
        jdbc.update("UPDATE sys_iam_application_protocol_config p JOIN sys_iam_application c ON c.id=p.application_id SET p.configuration=JSON_SET(p.configuration,'$.externalCodeTypes',JSON_ARRAY('ALIYUN_MOBILE_TOKEN')) WHERE c.application_code=?",
                CLIENT_A);
        jdbc.update("UPDATE sys_iam_application_identity_provider r JOIN sys_iam_application c ON c.id=r.application_id SET r.login_capability='ALIYUN_MOBILE' WHERE c.application_code=?",
                CLIENT_A);
        var source = configs.requireEnabledById(91011L, "ALIYUN_MOBILE");
        var identity = VerifiedExternalIdentity.of(source, "ALIYUN_MOBILE_TOKEN", VerifiedExternalIdentity.MOBILE_PROOF,
                "13800000001", Map.of("verified_mobile", "13800000001"));
        assertTrue(
                transaction.execute(status -> policy.resolve(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE, identity)).isEmpty());
        transaction.execute(status -> bindings.bind(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE, identity, 91001L, false));
        jdbc.update("UPDATE sys_user SET mobile='13800000003' WHERE id=91001");
        assertThrows(BadCredentialsException.class, () -> transaction.execute(status ->
                bindings.findBound(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE, identity)));
    }

    /**
     * 状态、用户目录和租户路由不能通过手动绑定绕过。
     */
    @Test
    void disabledAccountAndChangedClientRouteAreDenied() {
        var identity = identity(91011L, "disabled-user");
        jdbc.update("UPDATE sys_user SET status=0 WHERE id=91001");
        assertThrows(BadCredentialsException.class, () -> transaction.execute(status ->
                bindings.bind(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE, identity, 91001L, false)));
        assertThrows(BadCredentialsException.class, () -> transaction.execute(status ->
                bindings.bind(CLIENT_A, 2L, UserTypeEnum.EMPLOYEE, identity, 91002L, false)));
        assertThrows(BadCredentialsException.class, () -> transaction.execute(status ->
                bindings.bind(CLIENT_A, 1L, UserTypeEnum.MEMBER, identity, 91002L, false)));
        assertEquals(0, count());
    }

    /**
     * 数据库的零/多候选和缺属性都转手动，唯一但禁用的账号拒绝。
     */
    @Test
    void missingAttributesAndNonUniqueCandidatesCannotAutoBind() {
        jdbc.update(
                "UPDATE sys_identity_provider SET auto_bind_enabled=1,binding_rule='MEMBER_ID_TO_USERNAME' WHERE " +
                        "id=91011");
        var source = configs.requireEnabledById(91011L, "WECOM");
        var missing = VerifiedExternalIdentity.of(source, "WECOM_APP_CODE", VerifiedExternalIdentity.MEMBER_ID, ALICE,
                Map.of());
        assertTrue(
                transaction.execute(status -> policy.resolve(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE, missing)).isEmpty());
        var absent = identity(91011L, "no-local-candidate");
        assertTrue(
                transaction.execute(status -> policy.resolve(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE, absent)).isEmpty());
        // 真实V9唯一索引从存储层阻止多候选；防御性多候选分支由单元测试覆盖。
        assertThrows(DuplicateKeyException.class,
                () -> jdbc.update("UPDATE sys_user SET username=? WHERE id=91002", ALICE));
        var ambiguous = identity(91011L, ALICE);
        assertEquals(0, count());
        jdbc.update("UPDATE sys_user SET username='binding-it-bob' WHERE id=91002");
        jdbc.update("UPDATE sys_user SET status=0 WHERE id=91001");
        assertThrows(BadCredentialsException.class,
                () -> transaction.execute(status -> policy.resolve(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE, ambiguous)));
    }

    /**
     * Redis真实claim不能被第二浏览器抢占、重放或通过重开页面重置失败次数。
     */
    @Test
    void sdkTicketHasOneBrowserOwnerAndFixedAttempts() {
        var redisson = isolatedRedis();
        try {
            var redis = new StringRedisTemplate(
                    new RedissonConnectionFactory(redisson));
            var properties =
                    new ExternalCodeSecurityProperties();
            properties.setDigestKey("test-only-binding-digest");
            properties.setBindingTicketMaxAttempts(3);
            var store = new ExternalBindingTicketStore(redis, properties);
            var verification = new ExternalCodeVerification(
                    identity(91011L, "redis-member"));
            String ticket = store.issue(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE, verification);
            var claimed = store.claimForBrowser(ticket);
            long ttl = redis.getExpire(claimed.key(), TimeUnit.MILLISECONDS);
            assertEquals(91011L, claimed.value().identity().registrationId());
            assertThrows(BadCredentialsException.class, () -> store.claimForBrowser(ticket));
            store.failedBrowserAttempt(claimed);
            store.failedBrowserAttempt(claimed);
            store.requireBrowserClaim(claimed);
            store.failedBrowserAttempt(claimed);
            assertThrows(BadCredentialsException.class, () -> store.requireBrowserClaim(claimed));
            assertThrows(BadCredentialsException.class, () -> store.claim(ticket));
            assertTrue(redis.getExpire(claimed.key(), TimeUnit.MILLISECONDS) <= ttl);
            String second = store.issue(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE, verification);
            var secondClaim = store.claimForBrowser(second);
            var pending = new PendingThirdPartyBindingRepository();
            pending.setTickets(store);
            var request = new MockHttpServletRequest();
            pending.saveSdk(request, secondClaim);
            assertEquals(CLIENT_A, pending.current(request).context().clientId());
            pending.clear(request);
            assertThrows(BadCredentialsException.class, () -> store.claim(second));
        } finally {
            redisson.shutdown();
        }
    }

    /**
     * 真实Redis验证用途/Client隔离与跨用途共享的号码/IP发送限流。
     */
    @Test
    void bindingSmsCannotConsumeLoginCodesOrBypassSendLimits() {
        var redisson = isolatedRedis();
        try {
            var redis = new StringRedisTemplate(
                    new RedissonConnectionFactory(redisson));
            var properties = new SmsVerificationProperties();
            properties.setDigestKey("test-only-sms-digest");
            properties.setMaxIpSendsPerInterval(1);
            var store = new RedisVerificationCodeStore(redis);
            var binding = VerificationCodePurpose.IDENTITY_BINDING;
            var login = VerificationCodePurpose.LOGIN;
            store.activate(new VerificationCodeScope(
                            VerificationChannel.SMS, binding,
                            CLIENT_A),
                    "13800000111", "123456", properties);
            assertFalse(store.consume(new VerificationCodeScope(
                            VerificationChannel.SMS, login, CLIENT_A),
                    "13800000111", "123456", properties));
            assertFalse(store.consume(new VerificationCodeScope(
                            VerificationChannel.SMS, binding,
                            CLIENT_B),
                    "13800000111", "123456", properties));
            assertTrue(store.consume(new VerificationCodeScope(
                            VerificationChannel.SMS, binding,
                            CLIENT_A),
                    "13800000111", "123456", properties));
            assertFalse(store.consume(new VerificationCodeScope(
                            VerificationChannel.SMS, binding,
                            CLIENT_A),
                    "13800000111", "123456", properties));
            store.issue(new VerificationCodeScope(
                            VerificationChannel.SMS, login, CLIENT_A),
                    "13800000112", "192.0.2.1", properties);
            assertThrows(IllegalStateException.class, () -> store.issue(
                    new VerificationCodeScope(
                            VerificationChannel.SMS, binding,
                            CLIENT_B), "13800000112", "192.0.2.2", properties));
            assertThrows(IllegalStateException.class, () -> store.issue(
                    new VerificationCodeScope(
                            VerificationChannel.SMS, binding,
                            CLIENT_B), "13800000113", "192.0.2.1", properties));
        } finally {
            redisson.shutdown();
        }
    }

    /**
     * 无SavedRequest且托管密码/短信均关闭，SDK仍可证明账号并在提交后消费票据。
     */
    @Test
    void sdkSmsProofUsesOriginalClientWithoutSavedRequest() {
        var redisson = isolatedRedis();
        try {
            var redis = new StringRedisTemplate(
                    new RedissonConnectionFactory(redisson));
            var properties =
                    new ExternalCodeSecurityProperties();
            properties.setDigestKey("sdk-proof-isolated-test");
            var tickets = new ExternalBindingTicketStore(redis, properties);
            var pending = new PendingThirdPartyBindingRepository();
            pending.setTickets(tickets);
            var request = new MockHttpServletRequest();
            var identity = identity(91011L, "sdk-proof-member");
            String ticket = tickets.issue(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE,
                    new ExternalCodeVerification(identity));
            pending.saveSdk(request, tickets.claimForBrowser(ticket));
            jdbc.update("UPDATE sys_iam_application SET hosted_login_methods='' WHERE application_code=?", CLIENT_A);
            var users = mock(SysUserService.class);
            var account = new SysUser();
            account.setId(91001L);
            account.setUsername(ALICE);
            account.setStatus(1);
            account.setMobile("13800000001");
            when(users.findUniqueByUsername(ALICE)).thenReturn(account);
            when(users.findUniqueByMobile("13800000001")).thenReturn(account);
            var sms = mock(VerificationCodeChannelService.class);
            var proof = VerificationCodeContext.binding(CLIENT_A);
            when(sms.verifyCode("13800000001", "654321", proof)).thenReturn(true);
            var service = new ThirdPartyBindingService(pending, bindings, users,
                    mock(PasswordEncoder.class), sms,
                    new UserIdentifierNormalizer(),
                    BindingTestTransactions.directory(),
                    transaction.getTransactionManager());
            service.sendSms(request, ALICE, "13800000001");
            Mockito.verify(sms).sendCode("13800000001", request.getRemoteAddr(), proof);
            assertTrue(transaction.execute(
                    status -> service.bind(request, ALICE, "sms", null, "13800000001", "654321", null)).sdk());
            assertEquals(1, count());
            assertThrows(BadCredentialsException.class, () -> pending.current(request));
            assertThrows(BadCredentialsException.class, () -> tickets.claim(ticket));
            assertEquals(null, request.getSession().getAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
        } finally {
            redisson.shutdown();
        }
    }

    /**
     * 端口和运行标记必须同时确认，拒绝连接默认或业务Redis。
     */
    private RedissonClient isolatedRedis() {
        int port = Integer.parseInt(System.getenv("IAM_SOURCE_IT_REDIS_PORT"));
        assertTrue(port >= 30000);
        var config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:" + port);
        var client = Redisson.create(config);
        String marker = System.getenv("IAM_SOURCE_IT_RUN_ID");
        assertTrue(marker != null && !marker.isBlank());
        assertEquals(marker,
                client.<String>getBucket("iam-source-it-only", StringCodec.INSTANCE).get());
        return client;
    }

    /**
     * 新准入策略不能被旧票据/无快照入口绕过，锁内比较原始来源及有效期。
     */
    @Test
    void requiredDirectoryAdmissionIsCheckedInsideBindingTransaction() {
        jdbc.update(
                "UPDATE sys_identity_provider SET directory_policy='REQUIRED',organization_id='corp-a' WHERE id=91011");
        var verified = identity(91011L, "admission-member");
        assertThrows(BadCredentialsException.class,
                () -> transaction.execute(status ->
                        bindings.bind(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE, verified, 91001L, false)));
        var expired = new IdentityDirectoryService.Admission(verified, "corp-a",
                Instant.now().minusSeconds(1));
        assertThrows(BadCredentialsException.class,
                () -> transaction.execute(status ->
                        bindings.bind(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE, verified, 91001L, false, expired)));
        var current = new IdentityDirectoryService.Admission(verified, "corp-a",
                Instant.now().plusSeconds(60));
        transaction.execute(
                status -> bindings.bind(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE, verified, 91001L, false, current));
        jdbc.update("UPDATE sys_identity_provider SET version=version+1 WHERE id=91011");
        assertThrows(BadCredentialsException.class,
                () -> transaction.execute(status ->
                        bindings.bind(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE, verified, 91001L, false, current)));
    }

    /**
     * 从真实数据库配置构造服务端核验材料，subject与匹配属性一致。
     */
    private VerifiedExternalIdentity identity(Long source, String subject) {
        return VerifiedExternalIdentity.of(configs.requireEnabledById(source, "WECOM"), "WECOM_APP_CODE",
                VerifiedExternalIdentity.MEMBER_ID, subject, Map.of("member_id", subject));
    }

    /**
     * 并发测试只把预期账号冲突记为失败，基础设施故障向测试传播。
     */
    private int raceBind(VerifiedExternalIdentity identity, Long userId, CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try {
            transaction.execute(status -> bindings.bind(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE, identity, userId, false));
            return 1;
        } catch (BadCredentialsException ex) {
            return 0;
        }
    }

    /**
     * 插入本用例持有的具体来源；凭据为合成测试值，不安装Crypto拦截器。
     */
    private void source(Long id, String name, String corp) {
        jdbc.update(
                "INSERT INTO sys_identity_provider(id,name,provider_type,source_type,app_id,app_secret,config_json," +
                        "enabled,visible) "
                        + "VALUES(?,?,'WECOM','WECOM_SDK',?,'test-secret',?,1,1)", id, name, corp,
                "{\"corpId\":\"" + corp + "\",\"agentId\":\"agent\"}");
    }

    /**
     * Client明确关联具体SDK来源，缺少该映射就不能认证。
     */
    private void client(String client, Long source) {
        jdbc.update("INSERT INTO sys_iam_application(application_code,application_name,user_type,tenant_id,"
            + "configuration_status,application_type,create_time,last_update_time) "
            + "VALUES(?,?,'employee',1,'ENABLED','NATIVE',NOW(),NOW())", client, client);
        var settings = new OAuth2ProtocolSettings();
        settings.setClientAuthenticationMethods("none");
        settings.setAuthorizationGrantTypes("urn:macula:params:oauth:grant-type:external_code");
        settings.setScopes("message.read");
        settings.setRequireProofKey(true);
        settings.setRequireAuthorizationConsent(false);
        var payload = new OAuth2ApplicationProtocolSettings();
        payload.setOauth2(settings);
        payload.setExternalCodeTypes(Set.of(ExternalCodeType.WECOM_APP_CODE));
        jdbc.update("INSERT INTO sys_iam_application_protocol_config(application_id,protocol,schema_version,enabled,"
            + "protocol_identifier,identifier_issued_at,configuration) "
            + "SELECT id,'OAUTH2_1',2,1,application_code,application_code_issued_at,? "
            + "FROM sys_iam_application WHERE application_code=?",
            JsonMapper.builder().build().writeValueAsString(payload), client);
        jdbc.update("INSERT INTO sys_iam_application_identity_provider(application_id,login_capability,identity_provider_id) "
            + "SELECT id,'WECOM_SDK',? FROM sys_iam_application WHERE application_code=?", source, client);

    }

    /**
     * 只统计本类拥有的来源命名空间。
     */
    private int count() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_identity_provider_user_binding WHERE identity_provider_id BETWEEN 91000 AND 91999",
                Integer.class);
    }

    /**
     * SQL按Java相同规范化识别多候选，并在事务内重查；不修改历史字段。
     */
    @Test
    void normalizedCandidatesRejectAmbiguousMobileAndEmailAndUseExactUsername() {
        var identity = new VerifiedExternalIdentity(91011L, "DINGTALK", "DINGTALK_WEB", 0L, "DINGTALK_OAUTH_V1",
                "BROWSER", "MEMBER_ID", "fixture-member",
                Map.of("mobile", "13800000001", "email", "same@example.test", "member_id", ALICE));
        jdbc.update("UPDATE sys_user SET mobile='138-0000-0001', email=' Same@Example.Test ' WHERE id=91001");
        jdbc.update("UPDATE sys_user SET mobile='138 0000 0001', email='same@example.test' WHERE id=91002");
        var matcher = new IdentityBindingMatcher(identityUsers);
        var mobile = IdentityBindingMapping.of("mobile", "mobile");
        var email = IdentityBindingMapping.of("email", "email");
        assertEquals(2, matcher.candidates(mobile, identity, false).size());
        assertEquals(2, matcher.candidates(email, identity, false).size());
        transaction.executeWithoutResult(status -> assertEquals(2, matcher.candidates(mobile, identity, true).size()));
        assertEquals(1,
                matcher.candidates(IdentityBindingMapping.of("member_id", "username"),
                        identity, false).size());
        var differentCase = new VerifiedExternalIdentity(91011L, "DINGTALK", "DINGTALK_WEB", 0L, "DINGTALK_OAUTH_V1",
                "BROWSER", "MEMBER_ID", "fixture-member",
                Map.of("member_id", ALICE.toUpperCase(Locale.ROOT)));
        assertTrue(matcher.candidates(IdentityBindingMapping.of("member_id", "username"),
                differentCase, false).isEmpty());
    }

    /**
     * READ COMMITTED不具备此查询所需范围锁，不能声称已经证明并发唯一性。
     */
    @Test
    void refusesAutomaticMatchingInsideReadCommittedTransaction() {
        var weak = new TransactionTemplate(transaction.getTransactionManager());
        weak.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        var identity = new VerifiedExternalIdentity(91011L, "DINGTALK", "DINGTALK_WEB", 0L, "DINGTALK_OAUTH_V1",
                "BROWSER", "MEMBER_ID", "fixture-member", Map.of("member_id", ALICE));
        assertThrows(BadCredentialsException.class,
                () -> weak.execute(status -> new IdentityBindingMatcher(identityUsers)
                        .candidates(IdentityBindingMapping.of("member_id", "username"),
                                identity, true)));
    }

    /**
     * 真实Mapper验证同源双字段冲突不绑定，一致后只绑定同一用户。
     */
    @Test
    void multipleMappingsRequireConsistentDatabaseCandidates() {
        var verified = multipleMappingIdentity();
        jdbc.update("UPDATE sys_user SET email='person@example.test' WHERE id=91002");
        assertTrue(
                transaction.execute(status -> policy.resolve(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE, verified)).isEmpty());
        assertEquals(0, count());
        jdbc.update("UPDATE sys_user SET email=NULL WHERE id=91002");
        jdbc.update("UPDATE sys_user SET email='person@example.test' WHERE id=91001");
        var resolved = transaction.execute(status -> policy.resolve(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE, verified));
        assertEquals(ALICE, resolved.orElseThrow().getUsername());
        assertEquals(1, count());
    }

    /**
     * 首次匹配后另一规则命中变化，实际绑定事务必须拒绝旧候选。
     */
    @Test
    void automaticBindingRechecksEveryRuleAfterCandidateChanges() {
        var verified = multipleMappingIdentity();
        jdbc.update("UPDATE sys_user SET email='person@example.test' WHERE id=91001");
        var source = configs.requireEnabledById(91011L, "DINGTALK");
        assertEquals(91001L,
                new IdentityBindingMatcher(identityUsers).candidates(source.bindingMappings(), verified, false).get(0)
                        .getId());
        jdbc.update("UPDATE sys_user SET email=NULL WHERE id=91001");
        jdbc.update("UPDATE sys_user SET email='person@example.test' WHERE id=91002");
        assertThrows(BadCredentialsException.class, () -> transaction.execute(status ->
                bindings.bind(CLIENT_A, 1L, UserTypeEnum.EMPLOYEE, verified, 91001L, true)));
        assertEquals(0, count());
    }

    /**
     * 将本测试持有的来源与Client切为统一钉钉SDK，提供真实多字段匹配材料。
     */
    private VerifiedExternalIdentity multipleMappingIdentity() {
        jdbc.update(
                "UPDATE sys_identity_provider SET provider_type='DINGTALK',source_type='DINGTALK_SDK'," +
                        "app_id='fixture-app',"
                        +
                        "config_json='{\"appKey\":\"fixture-app\",\"corpId\":\"corp-a\"}',auto_bind_enabled=1," +
                        "binding_mappings_json=? WHERE id=91011",
                "[{\"sourceField\":\"mobile\",\"targetField\":\"mobile\"},{\"sourceField\":\"email\"," +
                        "\"targetField\":\"email\"}]");
        jdbc.update("UPDATE sys_iam_application_protocol_config p JOIN sys_iam_application c ON c.id=p.application_id SET p.configuration=JSON_SET(p.configuration,'$.externalCodeTypes',JSON_ARRAY('DINGTALK_APP_CODE')) WHERE c.application_code=?", CLIENT_A);
        jdbc.update("UPDATE sys_iam_application_identity_provider r JOIN sys_iam_application c ON c.id=r.application_id SET r.login_capability='DINGTALK_SDK' WHERE c.application_code=?",
                CLIENT_A);
        var source = configs.requireEnabledById(91011L, "DINGTALK");
        return VerifiedExternalIdentity.of(source, "DINGTALK_APP_CODE", VerifiedExternalIdentity.MEMBER_ID,
                "fixture-member",
                Map.of("mobile", "13800000001", "email", "person@example.test"));
    }

}
