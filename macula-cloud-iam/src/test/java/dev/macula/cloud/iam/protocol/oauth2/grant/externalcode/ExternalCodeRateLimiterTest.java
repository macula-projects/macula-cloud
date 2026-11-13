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
package dev.macula.cloud.iam.protocol.oauth2.grant.externalcode;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import dev.macula.boot.starter.crypto.config.CryptoProperties;
import dev.macula.boot.starter.crypto.core.CryptoManager;
import dev.macula.cloud.iam.authentication.thirdparty.cache.ProviderCacheCipher;
import dev.macula.cloud.iam.authentication.thirdparty.cache.ProviderCacheSecurityProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 独立Redis实测原子Client/IP计数与故障闭合；不连接业务Redis。
 *
 * @author Rain
 * @since 6.1.0
 */
class ExternalCodeRateLimiterTest {
    private Process process;
    private RedissonClient redisson;
    private StringRedisTemplate redis;
    private ExternalCodeSecurityProperties properties;
    private ExternalCodeRateLimiter limiter;

    /**
     * 每例使用自己的临时端口和进程，避免计数互相影响。
     */
    @BeforeEach
    void setup() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        process = new ProcessBuilder(
                System.getenv().getOrDefault("TEST_REDIS_SERVER", "/opt/homebrew/bin/redis-server"),
                "--bind", "127.0.0.1", "--port", Integer.toString(port), "--save", "", "--appendonly", "no")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        var config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:" + port).setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(2).setTimeout(500).setRetryAttempts(1);
        redisson = Redisson.create(config);
        redis = new StringRedisTemplate(new RedissonConnectionFactory(redisson));
        properties = new ExternalCodeSecurityProperties();
        properties.setDigestKey("fixture-digest-key");
        properties.setRateWindow(Duration.ofSeconds(5));
        properties.setClientRateLimit(3);
        properties.setIpRateLimit(100);
        limiter = new ExternalCodeRateLimiter(redis,
                new ProviderCacheCipher(new CryptoManager(),
                        new CryptoProperties(), cacheSecurity(properties)),
                properties);
    }

    /**
     * 只停止本测试创建的进程。
     */
    @AfterEach
    void close() throws Exception {
        if (redisson != null) {
            redisson.shutdown();
        }
        if (process != null) {
            process.destroy();
            process.waitFor(5, TimeUnit.SECONDS);
        }
    }

    /**
     * 并发请求恰好只有额度内的请求通过，计数键不出现明文应用或地址。
     */
    @Test
    void concurrentClientLimitIsAtomicAndPrivate() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        var passed = new AtomicInteger();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                futures.add(pool.submit(() -> {
                    try {
                        limiter.check("fixture-business-client", "192.0.2.1");
                        passed.incrementAndGet();
                    } catch (OAuth2AuthenticationException ex) {
                        assertEquals("invalid_grant", ex.getError().getErrorCode());
                    }
                }));
            }
            for (var future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
            assertEquals(3, passed.get());
            var keys = redis.keys("iam:external-code:{rate}:*");
            assertEquals(2, keys.size());
            assertTrue(keys.stream()
                    .noneMatch(key -> key.contains("fixture-business-client") || key.contains("192.0.2.1")));
            assertTrue(keys.stream().allMatch(key -> redis.getExpire(key, TimeUnit.MILLISECONDS) > 0));
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 不同Client仍共享连接地址额度，不能用换client_id绕过IP边界。
     */
    @Test
    void ipLimitIsSharedAcrossClients() {
        properties.setIpRateLimit(2);
        limiter.check("client-a", "192.0.2.1");
        limiter.check("client-b", "192.0.2.1");
        assertThrows(OAuth2AuthenticationException.class, () -> limiter.check("client-c", "192.0.2.1"));
    }

    /**
     * Redis断开时返回安全服务错误，不退回进程内计数。
     */
    @Test
    void redisFailureClosesAdmission() throws Exception {
        process.destroy();
        assertTrue(process.waitFor(5, TimeUnit.SECONDS));
        var error = assertThrows(OAuth2AuthenticationException.class,
                () -> limiter.check("client", "192.0.2.1"));
        assertEquals("server_error", error.getError().getErrorCode());
    }

    /**
     * 公共缓存保持原摘要密钥，只替换配置类型依赖。
     */
    private ProviderCacheSecurityProperties cacheSecurity(
            ExternalCodeSecurityProperties original) {
        var result = new ProviderCacheSecurityProperties();
        result.setDigestKey(original.getDigestKey());
        return result;
    }
}
