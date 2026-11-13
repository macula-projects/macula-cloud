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
package dev.macula.cloud.iam.authentication.verification;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 共用生命周期与真实隔离Redis证明凭据隔离、一次消费及原有效期。
 *
 * @author Macula
 * @since 6.1.0
 */
class VerificationCodeServiceTest {
    /**
     * 供应商拒绝不激活验证码，也不撤回已使用的滥用额度。
     */
    @Test
    void failedDeliveryNeverActivates() {
        var store = mock(VerificationCodeStore.class);
        var settings = settings();
        var scope = scope(VerificationChannel.EMAIL, "a", VerificationCodePurpose.LOGIN);
        when(store.issue(scope, "alice@example.test", "127.0.0.1", settings)).thenReturn("123456");
        assertThatThrownBy(
                () -> new VerificationCodeService(store).send(scope, "alice@example.test", "127.0.0.1", settings,
                        code -> {
                            throw new IllegalStateException("rejected");
                        })).isInstanceOf(IllegalStateException.class);
        verify(store, never()).activate(any(), any(), any(), any());
    }

    /**
     * 真Redis验证通道/用途/Client隔离、错误上限、并发一次消费和TTL。
     */
    @Test
    void redisIsolationAndAtomicConsumption() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        var log = Files.createTempFile("iam-email-redis-", ".log");
        Process process = new ProcessBuilder("redis-server", "--bind", "127.0.0.1", "--port", Integer.toString(port),
                "--save", "", "--appendonly", "no").redirectErrorStream(true).redirectOutput(log.toFile()).start();
        RedissonClient client = null;
        try {
            for (int i = 0; i < 100; i++) {
                try (var socket = new Socket("127.0.0.1", port)) {
                    break;
                } catch (IOException ex) {
                    Thread.sleep(20);
                }
            }
            var config = new Config();
            config.useSingleServer().setAddress("redis://127.0.0.1:" + port).setConnectionMinimumIdleSize(1)
                    .setConnectionPoolSize(2);
            client = Redisson.create(config);
            var store = new RedisVerificationCodeStore(new StringRedisTemplate(new RedissonConnectionFactory(client)));
            var settings = settings();
            var sms = scope(VerificationChannel.SMS, "a", VerificationCodePurpose.LOGIN);
            var email = scope(VerificationChannel.EMAIL, "a", VerificationCodePurpose.LOGIN);
            store.activate(sms, "alice@example.test", "123456", settings);
            assertThat(store.consume(email, "alice@example.test", "123456", settings)).isFalse();
            assertThat(store.consume(scope(VerificationChannel.SMS, "b", VerificationCodePurpose.LOGIN),
                    "alice@example.test", "123456", settings)).isFalse();
            assertThat(store.consume(scope(VerificationChannel.SMS, "a", VerificationCodePurpose.IDENTITY_BINDING),
                    "alice@example.test", "123456", settings)).isFalse();
            var start = new CountDownLatch(1);
            var executor = Executors.newFixedThreadPool(8);
            try {
                var calls = new ArrayList<Future<Boolean>>();
                for (int i = 0; i < 8; i++) {
                    calls.add(executor.submit(() -> {
                        start.await();
                        return store.consume(sms, "alice@example.test", "123456", settings);
                    }));
                }
                start.countDown();
                int success = 0;
                for (var result : calls) {
                    if (result.get()) {
                        success++;
                    }
                }
                assertThat(success).isEqualTo(1);
            } finally {
                executor.shutdownNow();
            }
            settings.setMaxAttempts(2);
            store.activate(email, "alice@example.test", "123456", settings);
            assertThat(store.consume(email, "alice@example.test", "000000", settings)).isFalse();
            assertThat(store.consume(email, "alice@example.test", "000000", settings)).isFalse();
            assertThat(store.consume(email, "alice@example.test", "123456", settings)).isFalse();
            settings.setTtl(Duration.ofMillis(70));
            store.activate(email, "alice@example.test", "123456", settings);
            Thread.sleep(120);
            assertThat(store.consume(email, "alice@example.test", "123456", settings)).isFalse();
        } finally {
            if (client != null) {
                client.shutdown();
            }
            process.destroy();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor();
            }
            Files.deleteIfExists(log);
        }
    }

    /**
     * 合成摘要配置，不使用现有环境密钥。
     */
    private SmsVerificationProperties settings() {
        var result = new SmsVerificationProperties();
        result.setDigestKey("fixture-email-isolation-key");
        return result;
    }

    /**
     * 作用域全部由测试服务端生成。
     */
    private VerificationCodeScope scope(VerificationChannel channel, String client, VerificationCodePurpose purpose) {
        return new VerificationCodeScope(channel, purpose, client);
    }
}
