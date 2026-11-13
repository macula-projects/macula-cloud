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
package dev.macula.cloud.iam.authentication.thirdparty.directory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity;
import dev.macula.cloud.iam.authentication.thirdparty.cache.ProviderCacheCipher;
import dev.macula.cloud.iam.authentication.thirdparty.provider.ProviderIntegrationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 按来源版本和已验证主体保存加密资料快照，不写本地用户和权限表。
 *
 * @author Rain
 * @since 6.1.0
 */
@Component
public class ProviderMemberSnapshotStore {
    private static final String PREFIX = "iam:provider:{directory-jobs}:member:";
    private final StringRedisTemplate redis;
    private final ProviderCacheCipher cipher;
    private final ProviderIntegrationProperties properties;
    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * 使用与应用Token相同的加密认证边界，独立用途域。
     */
    public ProviderMemberSnapshotStore(StringRedisTemplate redis, ProviderCacheCipher cipher,
            ProviderIntegrationProperties properties) {
        this.redis = redis;
        this.cipher = cipher;
        this.properties = properties;
    }

    /**
     * 不包含明文成员标识，旧来源版本无法命中新快照。
     */
    public String key(VerifiedExternalIdentity identity) {
        return PREFIX + cipher.digest("member-v1\0" + identity.registrationId() + "\0" + identity.sourceVersion()
                + "\0" + identity.flow() + "\0" + identity.identityKind() + "\0" + identity.identityValue());
    }

    /**
     * 到期时间包含在受认证密文中，Redis TTL被篡改也不能延长证明。
     */
    public void save(VerifiedExternalIdentity identity, ProviderDirectoryResult result) {
        Duration ttl = properties.getMemberSnapshotTtl();
        if (ttl == null || ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalStateException("目录快照有效期无效");
        }
        String key = key(identity);
        var snapshot = new Snapshot(result, Instant.now().plus(ttl));
        redis.opsForValue().set(key, cipher.encrypt(key, json.writeValueAsString(snapshot)), ttl);
    }

    /**
     * Lua同时检查owner与租约，失租任务不能发布自己的迟到结果。
     */
    public void saveOwned(VerifiedExternalIdentity identity, ProviderDirectoryResult result,
            String jobKey, String owner, StringRedisTemplate workRedis) {
        String key = key(identity);
        Duration ttl = properties.getMemberSnapshotTtl();
        if (ttl == null || ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalStateException("目录快照有效期无效");
        }
        String value = cipher.encrypt(key, json.writeValueAsString(new Snapshot(result, Instant.now().plus(ttl))));
        var publish = new DefaultRedisScript<Long>("""
                if redis.call('HGET',KEYS[1],'owner')~=ARGV[1] then return 0 end
                local deadline=redis.call('ZSCORE',KEYS[2],KEYS[1])
                if not deadline or tonumber(deadline)<=tonumber(ARGV[2]) then return 0 end
                redis.call('SET',KEYS[3],ARGV[3],'PX',ARGV[4]) return 1
                """, Long.class);
        Long published = workRedis.execute(publish, List.of(jobKey,
                        "iam:provider:{directory-jobs}:ready", key), owner, Long.toString(System.currentTimeMillis()),
                value, Long.toString(ttl.toMillis()));
        if (!Long.valueOf(1).equals(published)) {
            throw new IllegalStateException("目录刷新租约已失效");
        }
    }

    /**
     * 快照用于后台状态及资料，不代替REQUIRED的实时查询。
     */
    public ProviderDirectoryResult find(VerifiedExternalIdentity identity) {
        String key = key(identity);
        String value = redis.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        var snapshot = json.readValue(cipher.decrypt(key, value), Snapshot.class);
        return snapshot.expiresAt().isAfter(Instant.now()) ? snapshot.result() : null;
    }

    /**
     * 加密信封内的可信到期时间。
     *
     * @since 6.1.0
     */
    public record Snapshot(ProviderDirectoryResult result, Instant expiresAt) {
        /**
         * 禁止缺失结果及到期字段的旧记录。
         */
        public Snapshot {
            Objects.requireNonNull(result);
            Objects.requireNonNull(expiresAt);
        }

        /**
         * 防止日志意外输出资料。
         */
        @Override
        public String toString() {
            return "Snapshot[redacted]";
        }
    }
}
