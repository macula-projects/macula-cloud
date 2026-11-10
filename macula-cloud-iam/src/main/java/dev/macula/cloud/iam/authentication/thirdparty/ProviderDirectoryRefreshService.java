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

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * 已验证且已绑定成员的有界、加密、可恢复刷新队列；不创建或覆盖本地账号。
 * owner和到期调度由Redis原子维护，进程崩溃后可由另一实例恢复。
 * @author Rain
 * @since 6.1.0
 */
@Service
public class ProviderDirectoryRefreshService {
    private static final String PREFIX = "iam:provider:{directory-jobs}:";
    private static final String QUEUE = PREFIX + "ready";
    private static final Duration WORK_TTL = Duration.ofHours(1);
    private static final DefaultRedisScript<Long> ENQUEUE = new DefaultRedisScript<>("""
        if redis.call('EXISTS',KEYS[2])==1 then return 0 end
        if redis.call('ZCARD',KEYS[1])>=tonumber(ARGV[4]) then return -1 end
        redis.call('HSET',KEYS[2],'payload',ARGV[2],'attempts',0)
        redis.call('PEXPIRE',KEYS[2],ARGV[3])
        redis.call('ZADD',KEYS[1],ARGV[1],KEYS[2])
        return 1
        """, Long.class);
    private static final DefaultRedisScript<String> CLAIM = new DefaultRedisScript<>("""
        local ready=redis.call('ZRANGEBYSCORE',KEYS[1],'-inf',ARGV[1],'LIMIT',0,1)
        if #ready==0 then return nil end
        local key=ready[1]
        local payload=redis.call('HGET',key,'payload')
        if not payload then redis.call('ZREM',KEYS[1],key) return nil end
        redis.call('HSET',key,'owner',ARGV[2])
        redis.call('ZADD',KEYS[1],tonumber(ARGV[1])+tonumber(ARGV[3]),key)
        return key..string.char(10)..payload
        """, String.class);
    private static final DefaultRedisScript<Long> FINISH = new DefaultRedisScript<>("""
        if redis.call('HGET',KEYS[2],'owner')~=ARGV[1] then return 0 end
        if ARGV[2]=='done' then
          redis.call('DEL',KEYS[2]) redis.call('ZREM',KEYS[1],KEYS[2]) return 1
        end
        local attempts=redis.call('HINCRBY',KEYS[2],'attempts',1)
        if attempts>=tonumber(ARGV[4]) then
          redis.call('DEL',KEYS[2]) redis.call('ZREM',KEYS[1],KEYS[2]) return 1
        end
        redis.call('HDEL',KEYS[2],'owner')
        redis.call('ZADD',KEYS[1],ARGV[3],KEYS[2]) return 1
        """, Long.class);
    private final StringRedisTemplate redis;
    private final ProviderCacheCipher cipher;
    private final ProviderMemberSnapshotStore snapshots;
    private final IdentityProviderConfigService sources;
    private final List<ProviderDirectoryAdapter> adapters;
    private final ProviderIntegrationProperties properties;
    private final JsonMapper json = JsonMapper.builder().build();
    private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

    /** 队列只接收来源明确的已验证成员，不保存Secret或用户Token。 */
    public ProviderDirectoryRefreshService(StringRedisTemplate redis, ProviderCacheCipher cipher,
        ProviderMemberSnapshotStore snapshots, IdentityProviderConfigService sources,
        List<ProviderDirectoryAdapter> adapters, ProviderIntegrationProperties properties) {
        this.redis = redis;
        this.cipher = cipher;
        this.snapshots = snapshots;
        this.sources = sources;
        this.adapters = List.copyOf(adapters);
        this.properties = properties;
    }
    /** 独立有界Spring执行器，不启用应用其他尚未启用的Scheduled任务。 */
    @PostConstruct
    public void start() {
        if (properties.getRefreshQueueCapacity() < 1 || properties.getRefreshQueueCapacity() > 10000
            || properties.getRefreshMaxAttempts() < 1 || properties.getRefreshMaxAttempts() > 10
            || properties.getRefreshLease() == null || properties.getRefreshLease().compareTo(Duration.ofSeconds(10)) < 0
            || properties.getRefreshLease().compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalStateException("目录刷新配置无效");
        }
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(2);
        executor.setThreadNamePrefix("iam-directory-worker-");
        executor.initialize();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("iam-directory-poll-");
        scheduler.initialize();
        scheduler.scheduleWithFixedDelay(this::poll, Duration.ofSeconds(2));
    }
    /** 任务按来源版本和主体去重；积压达到上限时不无限添加。 */
    public void enqueueBound(VerifiedExternalIdentity identity) {
        String key = PREFIX + cipher.digest("directory-work-v1\0" + snapshots.key(identity));
        String payload = cipher.encrypt(key, json.writeValueAsString(new Work(identity, Instant.now().plus(WORK_TTL))));
        Long accepted = redis.execute(ENQUEUE, List.of(QUEUE, key), Long.toString(System.currentTimeMillis()),
            payload, Long.toString(WORK_TTL.toMillis()), Integer.toString(properties.getRefreshQueueCapacity()));
        if (accepted == null || accepted < 0) { throw new IllegalStateException("目录刷新队列暂不可用"); }
    }
    /** 仅扫描本功能队列，失败保留租约等待恢复，不能输出工作项正文。 */
    public void poll() {
        try {
            if (executor.getActiveCount() >= 2 || executor.getThreadPoolExecutor().getQueue().size() >= 2) { return; }
            String owner = UUID.randomUUID().toString();
            String claimed = redis.execute(CLAIM, List.of(QUEUE), Long.toString(System.currentTimeMillis()), owner,
                Long.toString(properties.getRefreshLease().toMillis()));
            if (claimed == null) { return; }
            int separator = claimed.indexOf('\n');
            if (separator <= 0) { throw new IllegalStateException("目录刷新工作项无效"); }
            String key = claimed.substring(0, separator);
            String payload = claimed.substring(separator + 1);
            try { executor.execute(() -> run(key, owner, payload)); }
            catch (RuntimeException ex) { finish(key, owner, false); }
        } catch (RuntimeException ex) {
            // 周期任务不可因Redis短暂故障永久终止；下次只恢复本队列的到期任务。
        }
    }
    /** 每次运行重新查来源并限定版本；失租请求不得发布资料快照。 */
    private void run(String key, String owner, String payload) {
        boolean done = false;
        try {
            Work work = json.readValue(cipher.decrypt(key, payload), Work.class);
            var identity = work.identity();
            var source = sources.requireEnabledById(identity.registrationId(), identity.providerType()).forCapability(identity.loginCapability());
            if (!work.expiresAt().isAfter(Instant.now()) || !source.version().equals(identity.sourceVersion())
                || !source.flow().value().equals(identity.flow())
                || source.directoryPolicy() != dev.macula.cloud.system.enums.IdentityDirectoryPolicy.BACKGROUND) {
                done = true;
                return;
            }
            var adapter = adapters.stream().filter(item -> item.providerType().equals(source.providerType()))
                .findFirst().orElseThrow();
            var result = adapter.lookup(source, identity);
            var current = sources.requireEnabledById(identity.registrationId(), identity.providerType()).forCapability(identity.loginCapability());
            if (!current.version().equals(identity.sourceVersion()) || !current.flow().value().equals(identity.flow())) {
                done = true;
                return;
            }
            // Snapshot发布也必须owner原子核验，不能check-then-write跨过租约。
            snapshots.saveOwned(identity, result, key, owner, redis);
            done = result.status() != ProviderDirectoryResult.Status.UNAVAILABLE;
        } catch (org.springframework.security.authentication.AuthenticationServiceException ex) {
            // 来源停用或配置已失效时取消旧任务，不为旧身份重新选择来源。
            done = true;
        } catch (RuntimeException ex) {
            // 不记录身份资料；超时、失租后按有限次数恢复/清理。
        } finally { finish(key, owner, done); }
    }
    /** 成功清理与失败重排都只能由当前owner执行。 */
    private void finish(String key, String owner, boolean done) {
        redis.execute(FINISH, List.of(QUEUE, key), owner, done ? "done" : "retry",
            Long.toString(System.currentTimeMillis() + properties.getRefreshLease().toMillis()),
            Integer.toString(properties.getRefreshMaxAttempts()));
    }
    /** 关闭本功能线程池，不清理持久工作项。 */
    @PreDestroy
    public void close() {
        scheduler.shutdown();
        executor.shutdown();
    }
    /**
     * 加密持久工作项具有独立用途和到期。
     * @since 6.1.0
     */
    public record Work(VerifiedExternalIdentity identity, Instant expiresAt) {
        /** 旧记录缺少必填字段直接失效，不能补猜。 */
        public Work {
            java.util.Objects.requireNonNull(identity);
            java.util.Objects.requireNonNull(expiresAt);
        }
        /** 不输出已验证身份或资料。 */
        @Override
        public String toString() { return "Work[redacted]"; }
    }
}
