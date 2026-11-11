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
package dev.macula.cloud.iam.authentication.thirdparty.cache;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import dev.macula.boot.starter.crypto.config.CryptoProperties;
import dev.macula.boot.starter.crypto.core.CryptoManager;
import dev.macula.boot.starter.crypto.enums.AlgorithmType;
import dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures;
import dev.macula.cloud.iam.authentication.thirdparty.provider.ProviderIntegrationProperties;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeSecurityProperties;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService;
import dev.macula.cloud.system.enums.IdentitySourceType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 专用Redis证明应用Token刷新合并及失租后不得发布或返回旧结果。
 *
 * @author Rain
 * @since 6.1.0
 */
class ProviderApplicationTokenCacheIT {
    private static Process server;
    private static RedissonClient redisson;
    private static StringRedisTemplate redis;
    private final IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
    private final IdentityProviderConfigService.ProviderConfig source = IdentitySourceTestFixtures.source(
            IdentitySourceType.WECOM_SDK, "WECOM", "fixture-corp", "fixture-secret", Map.of());
    private ProviderCacheCipher cipher;
    private ProviderApplicationTokenCache cache;

    /**
     * 每类测试创建自己的进程和临时目录，不连接默认Redis或业务实例。
     */
    @BeforeAll
    static void start() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        var directory = Files.createTempDirectory("iam-provider-cache-it-");
        server = new ProcessBuilder(System.getenv().getOrDefault("TEST_REDIS_SERVER", "/opt/homebrew/bin/redis-server"),
                "--bind", "127.0.0.1", "--port", Integer.toString(port), "--save", "", "--appendonly", "no")
                .redirectErrorStream(true).redirectOutput(directory.resolve("redis.log").toFile()).start();
        var config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:" + port)
                .setConnectionMinimumIdleSize(1).setConnectionPoolSize(2).setRetryAttempts(5).setTimeout(1000);
        redisson = Redisson.create(config);
        redis = new StringRedisTemplate(new RedissonConnectionFactory(redisson));
        assertTrue(redisson.getKeys().count() == 0);
    }

    /**
     * 只停止本测试创建的进程。
     */
    @AfterAll
    static void stop() throws Exception {
        if (redisson != null) {
            redisson.shutdown();
        }
        if (server != null) {
            server.destroy();
            server.waitFor(5, TimeUnit.SECONDS);
        }
    }

    /**
     * 清理专用测试实例的本功能键，不能对外部实例执行flush。
     */
    @BeforeEach
    void prepare() {
        var keys = redis.keys("iam:provider:*");
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
        var crypto = new CryptoProperties();
        crypto.setAlgorithm(AlgorithmType.AES);
        crypto.setPassword("0123456789abcdef");
        var security = new ExternalCodeSecurityProperties();
        security.setDigestKey("fixture-only-provider-cache-hmac");
        cipher = new ProviderCacheCipher(new CryptoManager(), crypto, cacheSecurity(security));
        when(configs.requireEnabledById(1L, "WECOM")).thenReturn(source);
        cache = new ProviderApplicationTokenCache(redis, cipher, new ProviderIntegrationProperties(), configs);
    }

    /**
     * 同时未命中也只执行一次loader，Redis不保存明文Token。
     */
    @Test
    void concurrentRequestsShareOneRefreshAndEncryptedValue() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        var calls = new AtomicInteger();
        var ready = new CountDownLatch(1);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<String>>();
            for (int i = 0; i < 12; i++) {
                futures.add(executor.submit(() -> {
                    ready.await();
                    return cache.get(source, ProviderApplicationTokenCache.APPLICATION_TOKEN, () -> {
                        calls.incrementAndGet();
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException();
                        }
                        return new ProviderApplicationTokenCache.TokenValue("fixture-app-token",
                                Duration.ofMinutes(10));
                    });
                }));
            }
            ready.countDown();
            for (var future : futures) {
                assertEquals("fixture-app-token", future.get(10, TimeUnit.SECONDS));
            }
            assertEquals(1, calls.get());
            for (String key : redis.keys("iam:provider:*:value")) {
                assertFalse(redis.opsForValue().get(key).contains("fixture-app-token"));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 真实锁过期换owner后，旧loader的结果不能覆盖或直接返回。
     */
    @Test
    void expiredOwnerDiscardsItsResultAndReturnsWinner() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var entered = new CountDownLatch(1);
        var resume = new CountDownLatch(1);
        try {
            var old = executor.submit(() -> cache.get(source, ProviderApplicationTokenCache.APPLICATION_TOKEN, () -> {
                entered.countDown();
                try {
                    assertTrue(resume.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException ex) {
                    throw new IllegalStateException();
                }
                return new ProviderApplicationTokenCache.TokenValue("old-result", Duration.ofMinutes(10));
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            String lock = redis.keys("iam:provider:*:lock").iterator().next();
            redis.expire(lock, Duration.ofMillis(1));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (Boolean.TRUE.equals(redis.hasKey(lock)) && System.nanoTime() < deadline) {
                Thread.sleep(2);
            }
            assertFalse(Boolean.TRUE.equals(redis.hasKey(lock)));
            assertEquals("new-result", cache.get(source, ProviderApplicationTokenCache.APPLICATION_TOKEN,
                    () -> new ProviderApplicationTokenCache.TokenValue("new-result", Duration.ofMinutes(10))));
            resume.countDown();
            assertEquals("new-result", old.get(5, TimeUnit.SECONDS));
            assertEquals("new-result", cache.get(source, ProviderApplicationTokenCache.APPLICATION_TOKEN,
                    () -> {
                        throw new AssertionError("winner must remain cached");
                    }));
        } finally {
            resume.countDown();
            executor.shutdownNow();
        }
    }

    /**
     * 篡改及跨来源替换不能降级为明文或任意缓存值。
     */
    @Test
    void envelopeRejectsTamperingAndDifferentSource() {
        String envelope = cipher.encrypt("source-a", "fixture-secret-value");
        assertEquals("fixture-secret-value", cipher.decrypt("source-a", envelope));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt("source-b", envelope));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt("source-a", "0" + envelope.substring(1)));
    }

    /**
     * 新增的非锚点能力可复用注册凭据，但调用期间撤销不能发布旧Token。
     */
    @Test
    void nonAnchorCapabilityUsesItsContractAndRevocationFencesTokenPublication() {
        var web = IdentitySourceTestFixtures.source(IdentitySourceType.WECOM_WEB, "WECOM", "fixture-corp",
                "fixture-secret", Map.of());
        var multi = new IdentityProviderConfigService.ProviderConfig(web.registrationId(), web.sourceType(), false,
                null,
                web.version(), web.providerType(), web.appId(), web.appSecret(), web.values(), web.secrets(), true,
                web.flow(),
                web.directoryPolicy(), null, null, null,
                Map.of("WECOM_BROWSER", "WECOM_WEB_V1", "WECOM_SDK", "WECOM_SDK_V1"), "WECOM_BROWSER");
        when(configs.requireEnabledById(1L, "WECOM")).thenReturn(multi);
        var sdk = multi.forCapability("WECOM_SDK");
        assertEquals("sdk-token", cache.get(sdk, "APPLICATION_TOKEN",
                () -> new ProviderApplicationTokenCache.TokenValue("sdk-token", Duration.ofMinutes(5))));
        var keys = redis.keys("iam:provider:*");
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
        assertThrows(RuntimeException.class, () -> cache.get(sdk, "APPLICATION_TOKEN", () -> {
            when(configs.requireEnabledById(1L, "WECOM")).thenReturn(web);
            return new ProviderApplicationTokenCache.TokenValue("must-not-publish", Duration.ofMinutes(5));
        }));
        assertTrue(redis.keys("iam:provider:*:value").isEmpty());
    }

    /**
     * 公共缓存保持原摘要密钥，只替换配置类型依赖。
     */
    private dev.macula.cloud.iam.authentication.thirdparty.cache.ProviderCacheSecurityProperties cacheSecurity(
            dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeSecurityProperties original) {
        var result = new dev.macula.cloud.iam.authentication.thirdparty.cache.ProviderCacheSecurityProperties();
        result.setDigestKey(original.getDigestKey());
        return result;
    }
}
