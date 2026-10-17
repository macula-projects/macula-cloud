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

import dev.macula.boot.constants.CacheConstants;
import dev.macula.boot.starter.cache.CacheEvictMessage;
import dev.macula.boot.starter.cache.TwoLevelCache;
import dev.macula.boot.starter.cache.TwoLevelCacheManager;
import dev.macula.boot.starter.cache.TwoLevelCacheProperties;
import dev.macula.cloud.system.form.IdentityProviderForm;
import dev.macula.cloud.system.form.MessageProviderForm;
import dev.macula.cloud.system.form.OAuth2ClientForm;
import dev.macula.cloud.system.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.system.mapper.SysOAuth2ClientConfigMapper;
import dev.macula.cloud.system.pojo.entity.SysIdentityProvider;
import dev.macula.cloud.system.service.OAuth2ClientConfigService;
import dev.macula.cloud.system.service.IdentityProviderService;
import dev.macula.cloud.system.service.MessageSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.cache.autoconfigure.CacheProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Connection;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 仅在显式指定的隔离数据库上验证加密字段和提交后共享缓存失效。
 *
 * @author Rain
 * @since 2026-10-13
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {"spring.cloud.nacos.discovery.enabled=false", "spring.flyway.enabled=false"})
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "SYSTEM_SETTINGS_IT", matches = "true")
class SettingsPersistenceIT {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SysIdentityProviderMapper identityMapper;
    @Autowired private SysOAuth2ClientConfigMapper clientMapper;
    @Autowired private IdentityProviderService identityService;
    @Autowired private MessageSettingsService messageService;
    @Autowired private OAuth2ClientConfigService clientService;
    @Autowired private CacheManager cacheManager;
    @Autowired private ApplicationContext context;
    @Autowired private TwoLevelCacheProperties cacheProperties;
    @Autowired @Qualifier("twoLevelCacheRedisTemplate") private RedisTemplate<Object, Object> cacheRedis;

    @BeforeEach
    void requireIsolatedDatabase() throws Exception {
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            assertTrue(connection.getCatalog().startsWith("macula_settings_it_"),
                "集成测试只能连接 macula_settings_it_ 隔离数据库");
        }
    }

    @Test
    void cryptoFieldWritesCiphertextAndReadsPlaintext() {
        SysIdentityProvider entity = new SysIdentityProvider();
        entity.setName("settings-it-" + UUID.randomUUID());
        entity.setProviderType("GITHUB");
        entity.setAppId("fixture-client");
        entity.setAppSecret("fixture-secret-not-real");
        entity.setEnabled(false);
        entity.setVisible(false);
        try {
            identityMapper.insert(entity);
            String stored = jdbc.queryForObject("SELECT app_secret FROM sys_identity_provider WHERE id = ?",
                String.class, entity.getId());
            assertNotNull(stored);
            assertNotEquals("fixture-secret-not-real", stored);
            assertEquals("fixture-secret-not-real", identityMapper.selectById(entity.getId()).getAppSecret());
        } finally {
            if (entity.getId() != null) identityMapper.deleteById(entity.getId());
        }
    }

    @Test
    void blankUpdateRetainsEncryptedIdentityAndMailCredentials() {
        IdentityProviderForm identity = new IdentityProviderForm();
        identity.setName("settings-it-" + UUID.randomUUID());
        identity.setProviderType("GITHUB");
        identity.setCategory("social");
        identity.setConfig(Map.of("clientId", "fixture-client", "clientSecret", "fixture-idp-secret"));
        Long identityId = null;
        Long mailId = null;
        try {
            identityId = identityService.save(null, identity).getId();
            jdbc.update("UPDATE sys_identity_provider SET callback_url = ? WHERE id = ?",
                "https://legacy.example.test/callback", identityId);
            identity.setConfig(Map.of("clientId", "fixture-client", "clientSecret", ""));
            identityService.save(identityId, identity);
            assertEquals("https://legacy.example.test/callback", jdbc.queryForObject(
                "SELECT callback_url FROM sys_identity_provider WHERE id = ?", String.class, identityId));
            assertEquals("fixture-idp-secret", identityMapper.selectById(identityId).getAppSecret());
            assertFalse(jdbc.queryForObject("SELECT config_json FROM sys_identity_provider WHERE id = ?",
                String.class, identityId).contains("fixture-idp-secret"));
            assertNotEquals("fixture-idp-secret", jdbc.queryForObject(
                "SELECT app_secret FROM sys_identity_provider WHERE id = ?", String.class, identityId));

            MessageProviderForm mail = new MessageProviderForm();
            mail.setProviderType("CUSTOM");
            mail.setEndpoint("smtp.example.test");
            mail.setPort(465);
            mail.setSecurityMode("SSL");
            mail.setAccount("fixture-user");
            mail.setCredential("fixture-smtp-secret");
            mail.setSender("sender@example.test");
            messageService.saveProvider("MAIL", mail);
            mailId = jdbc.queryForObject("SELECT id FROM sys_message_provider WHERE channel = 'MAIL'", Long.class);
            mail.setCredential("");
            messageService.saveProvider("MAIL", mail);
            assertTrue(messageService.provider("MAIL").isCredentialConfigured());
            assertNotEquals("fixture-smtp-secret", jdbc.queryForObject(
                "SELECT credential FROM sys_message_provider WHERE id = ?", String.class, mailId));
        } finally {
            if (mailId != null) jdbc.update("DELETE FROM sys_message_provider WHERE id = ?", mailId);
            if (identityId != null) identityService.delete(identityId);
        }
    }

    @Test
    void alipayPrivateKeyUsesExpandedEncryptedColumnNotJson() {
        String privateKey = "fixture-private-key-" + "x".repeat(4000);
        IdentityProviderForm form = new IdentityProviderForm();
        form.setName("settings-it-" + UUID.randomUUID());
        form.setProviderType("ALIPAY");
        form.setCategory("social");
        form.setConfig(Map.of("appId", "fixture-app", "appPrivateKey", privateKey,
            "alipayPublicKey", "fixture-public-key"));
        Long id = null;
        try {
            id = identityService.save(null, form).getId();
            String ciphertext = jdbc.queryForObject("SELECT app_secret FROM sys_identity_provider WHERE id = ?",
                String.class, id);
            String publicJson = jdbc.queryForObject("SELECT config_json FROM sys_identity_provider WHERE id = ?",
                String.class, id);
            assertNotNull(ciphertext);
            assertTrue(ciphertext.length() > 2048);
            assertFalse(ciphertext.contains(privateKey));
            assertFalse(publicJson.contains(privateKey));
            assertEquals(privateKey, identityMapper.selectById(id).getAppSecret());
        } finally {
            if (id != null) identityService.delete(id);
        }
    }

    @Test
    void legacyIdentityRowRemainsReadableAfterV3() {
        String name = "settings-it-legacy-" + UUID.randomUUID();
        jdbc.update("INSERT INTO sys_identity_provider (name, provider_type, app_id, config_json, " +
            "enabled, visible) VALUES (?, 'GITHUB', ?, '{}', 1, 1)", name, "fixture-legacy-client");
        Long id = jdbc.queryForObject("SELECT id FROM sys_identity_provider WHERE name = ?", Long.class, name);
        try {
            assertEquals("fixture-legacy-client", identityService.get(id).getConfig().get("clientId"));
            assertFalse(identityService.get(id).getConfiguredSecrets().get("clientSecret"));
        } finally {
            jdbc.update("DELETE FROM sys_identity_provider WHERE id = ?", id);
        }
    }

    @Test
    void publicClientPersistsNullSecretAndEvictsRedisCacheAfterCommit() throws Exception {
        String clientId = "settings-it-" + UUID.randomUUID();
        Cache cache = cacheManager.getCache(CacheConstants.OAUTH2_CLIENT_CACHE_KEY);
        assertNotNull(cache);
        TwoLevelCacheManager remoteManager = new TwoLevelCacheManager(
            context.getBeanProvider(CacheProperties.class), cacheProperties, cacheRedis);
        TwoLevelCache remoteCache = (TwoLevelCache)remoteManager.getCache(CacheConstants.OAUTH2_CLIENT_CACHE_KEY);
        assertNotNull(remoteCache);
        RedisMessageListenerContainer remoteListener = new RedisMessageListenerContainer();
        remoteListener.setConnectionFactory(cacheRedis.getConnectionFactory());
        remoteListener.addMessageListener((message, pattern) -> {
            Object value = cacheRedis.getValueSerializer().deserialize(message.getBody());
            if (value instanceof CacheEvictMessage event &&
                CacheConstants.OAUTH2_CLIENT_CACHE_KEY.equals(event.getCacheName())) {
                remoteCache.clearLocal(event.getEntryKey());
            }
        }, new ChannelTopic(cacheProperties.getTopic()));
        remoteListener.afterPropertiesSet();
        remoteListener.start();
        remoteCache.put(clientId, "stale-client");
        assertEquals("stale-client", remoteCache.get(clientId).get());
        OAuth2ClientForm form = new OAuth2ClientForm();
        form.setClientId(clientId);
        form.setClientName("Public integration fixture");
        form.setClientAuthenticationMethods("none");
        form.setAuthorizationGrantTypes("authorization_code,refresh_token");
        form.setRedirectUris("https://example.test/callback");
        form.setScopes("openid,profile");
        form.setRequireProofKey(true);
        form.setRequireAuthorizationConsent(false);
        form.setReuseRefreshTokens(true);
        form.setUserType("employee");
        form.setTenantId(1L);
        Long id = null;
        try {
            id = clientService.save(null, form).getId();
            assertNull(cache.get(clientId));
            long deadline = System.nanoTime() + java.time.Duration.ofSeconds(3).toNanos();
            while (remoteCache.get(clientId) != null && System.nanoTime() < deadline) {
                Thread.sleep(25);
            }
            assertNull(remoteCache.get(clientId), "另一个 Boot 缓存实例的本地缓存应收到 Redis 驱逐消息");
            assertNull(jdbc.queryForObject("SELECT client_secret FROM sys_oauth2_client WHERE id = ?",
                String.class, id));
            assertEquals("none", clientMapper.selectById(id).getClientAuthenticationMethods());
            assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_oauth2_client WHERE id IN (1, 2)",
                Long.class));
        } finally {
            if (id != null) clientService.delete(id);
            cache.evict(clientId);
            remoteListener.stop();
        }
    }
}
