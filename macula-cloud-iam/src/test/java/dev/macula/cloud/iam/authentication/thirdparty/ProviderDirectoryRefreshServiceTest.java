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
package dev.macula.cloud.iam.authentication.thirdparty;

import dev.macula.boot.starter.crypto.config.CryptoProperties;
import dev.macula.boot.starter.crypto.core.CryptoManager;
import dev.macula.boot.starter.crypto.enums.AlgorithmType;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeSecurityProperties;
import dev.macula.cloud.system.enums.IdentityDirectoryPolicy;
import dev.macula.cloud.system.enums.IdentitySourceFlow;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 两个服务实例配合独立Redis验证加密工作项、租约恢复和失租写入栅栏。
 * 不执行企业同步或修改业务数据库。
 * @author Rain
 * @since 6.1.0
 */
class ProviderDirectoryRefreshServiceTest {
    private static final String PREFIX = "iam:provider:{directory-jobs}:";
    private static final String QUEUE = PREFIX + "ready";
    private Process process;
    private RedissonClient redisson;
    private StringRedisTemplate redis;
    private ProviderCacheCipher cipher;
    private ProviderMemberSnapshotStore snapshots;
    private ProviderIntegrationProperties properties;
    private final IdentityProviderConfigService sources = mock(IdentityProviderConfigService.class);
    private final IdentityProviderConfigService.ProviderConfig source = mock(IdentityProviderConfigService.ProviderConfig.class);
    private final VerifiedExternalIdentity identity = IdentitySourceTestFixtures.sdkIdentity("WECOM", "fixture-member").identity();
    private final List<ProviderDirectoryRefreshService> services = new ArrayList<>();
    private final CountDownLatch releaseOld = new CountDownLatch(1);

    /** 创建专用进程和合成加密密钥，不读取业务配置。 */
    @BeforeEach
    void setup() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        process = new ProcessBuilder(System.getenv().getOrDefault("TEST_REDIS_SERVER", "/opt/homebrew/bin/redis-server"),
            "--bind", "127.0.0.1", "--port", Integer.toString(port), "--save", "", "--appendonly", "no")
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        var config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:" + port).setConnectionMinimumIdleSize(1)
            .setConnectionPoolSize(2).setTimeout(1000).setRetryAttempts(2);
        redisson = Redisson.create(config);
        redis = new StringRedisTemplate(new RedissonConnectionFactory(redisson));
        var crypto = new CryptoProperties();
        crypto.setAlgorithm(AlgorithmType.AES);
        crypto.setPassword("0123456789abcdef");
        var security = new ExternalCodeSecurityProperties();
        security.setDigestKey("fixture-digest-key");
        cipher = new ProviderCacheCipher(new CryptoManager(), crypto, security);
        properties = new ProviderIntegrationProperties();
        properties.setRefreshLease(Duration.ofSeconds(10));
        snapshots = new ProviderMemberSnapshotStore(redis, cipher, properties);
        when(sources.requireEnabledById(identity.registrationId(), "WECOM")).thenReturn(source);
        when(source.forCapability(identity.loginCapability())).thenReturn(source);
        when(source.version()).thenReturn(identity.sourceVersion());
        when(source.flow()).thenReturn(IdentitySourceFlow.WECOM_SDK_V1);
        when(source.directoryPolicy()).thenReturn(IdentityDirectoryPolicy.BACKGROUND);
        when(source.providerType()).thenReturn("WECOM");
    }
    /** 先等正在运行的任务结束，再停止本测试的Redis。 */
    @AfterEach
    void close() throws Exception {
        releaseOld.countDown();
        for (var service : services) {
            var worker = (ThreadPoolTaskExecutor) ReflectionTestUtils.getField(service, "executor");
            if (worker != null && worker.isRunning()) { await(() -> worker.getActiveCount() == 0); }
            service.close();
        }
        if (redisson != null) { redisson.shutdown(); }
        if (process != null) {
            process.destroy();
            process.waitFor(5, TimeUnit.SECONDS);
        }
    }
    /** 入队去重和容量上限是真实Redis操作，工作项不能包含明文主体。 */
    @Test
    void queueIsBoundedDeduplicatedAndEncrypted() {
        properties.setRefreshQueueCapacity(1);
        var service = service(mock(ProviderDirectoryAdapter.class), false);
        service.enqueueBound(identity);
        service.enqueueBound(identity);
        assertEquals(1, redis.opsForZSet().zCard(QUEUE));
        String key = redis.opsForZSet().range(QUEUE, 0, 0).iterator().next();
        String payload = (String) redis.opsForHash().get(key, "payload");
        assertFalse(payload.contains("fixture-member"));
        var other = IdentitySourceTestFixtures.sdkIdentity("WECOM", "another-member").identity();
        assertThrows(IllegalStateException.class, () -> service.enqueueBound(other));
    }
    /** 旧任务的来源版本失效后取消，不调用任何厂商接口。 */
    @Test
    void sourceChangeCancelsOldWork() throws Exception {
        var adapter = mock(ProviderDirectoryAdapter.class);
        var service = service(adapter, false);
        service.enqueueBound(identity);
        when(source.version()).thenReturn(identity.sourceVersion() + 1);
        service.start();
        await(() -> Long.valueOf(0).equals(redis.opsForZSet().zCard(QUEUE)));
        verify(adapter, never()).lookup(any(), any());
        assertEquals(null, snapshots.find(identity));
    }
    /** 模拟旧节点请求挂起及租约到期，第二节点恢复任务，旧响应不能覆盖获胜结果。 */
    @Test
    void lostOwnerCannotPublishLateSnapshot() throws Exception {
        var started = new CountDownLatch(1);
        var oldAdapter = mock(ProviderDirectoryAdapter.class);
        when(oldAdapter.providerType()).thenReturn("WECOM");
        when(oldAdapter.lookup(source, identity)).thenAnswer(call -> {
            started.countDown();
            assertTrue(releaseOld.await(5, TimeUnit.SECONDS));
            return result("old");
        });
        var old = service(oldAdapter, false);
        old.enqueueBound(identity);
        old.start();
        assertTrue(started.await(3, TimeUnit.SECONDS));
        // 暂停旧节点取新工作，保留它仍挂起的旧请求，模拟节点局部失联。
        ((ThreadPoolTaskScheduler) ReflectionTestUtils.getField(old, "scheduler")).shutdown();
        String key = redis.opsForZSet().range(QUEUE, 0, 0).iterator().next();
        redis.opsForZSet().add(QUEUE, key, System.currentTimeMillis() - 1);
        var winnerAdapter = mock(ProviderDirectoryAdapter.class);
        when(winnerAdapter.providerType()).thenReturn("WECOM");
        when(winnerAdapter.lookup(source, identity)).thenReturn(result("winner"));
        service(winnerAdapter, true);
        await(() -> snapshots.find(identity) != null);
        assertEquals("winner", snapshots.find(identity).profile().get("employee_no"));
        releaseOld.countDown();
        var oldWorker = (ThreadPoolTaskExecutor) ReflectionTestUtils.getField(old, "executor");
        await(() -> oldWorker.getActiveCount() == 0);
        assertEquals("winner", snapshots.find(identity).profile().get("employee_no"));
    }
    /** 每个实例使用自己的有界Spring执行器，但共享本测试Redis和加密边界。 */
    private ProviderDirectoryRefreshService service(ProviderDirectoryAdapter adapter, boolean start) {
        var service = new ProviderDirectoryRefreshService(redis, cipher, snapshots, sources, List.of(adapter), properties);
        services.add(service);
        if (start) { service.start(); }
        return service;
    }
    /** 目录返回的最小合成资料，不模拟平台登录。 */
    private ProviderDirectoryResult result(String employeeNo) {
        return new ProviderDirectoryResult(ProviderDirectoryResult.Status.MEMBER, "fixture-corp",
            Map.of("employee_no", employeeNo), Instant.now(), false);
    }
    /** 有界等待真实异步状态，不用固定长等待冒充任务完成。 */
    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(10); }
        assertTrue(condition.getAsBoolean());
    }
}
