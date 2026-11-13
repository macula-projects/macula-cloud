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

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import dev.macula.cloud.iam.authentication.thirdparty.provider.ProviderIntegrationProperties;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeConsumptionState;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeInfrastructureException;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService.ProviderConfig;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * 应用/企业Token的来源版本缓存；失去刷新锁的旧请求既不能发布也不能返回旧结果。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
public class ProviderApplicationTokenCache {
    public static final String APPLICATION_TOKEN = "application";
    public static final String TENANT_TOKEN = "tenant";
    private static final DefaultRedisScript<Long> PUBLISH = new DefaultRedisScript<>("""
            if redis.call('GET',KEYS[1]) ~= ARGV[1] then return 0 end
            redis.call('SET',KEYS[2],ARGV[2],'PX',ARGV[3])
            redis.call('DEL',KEYS[1])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('GET',KEYS[1]) == ARGV[1] then return redis.call('DEL',KEYS[1]) end
            return 0
            """, Long.class);
    private final StringRedisTemplate redis;
    private final ProviderCacheCipher cipher;
    private final ProviderIntegrationProperties properties;
    private final IdentityProviderConfigService configs;
    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * 共享服务只做缓存/并发，不接管厂商换码或身份判断。
     */
    public ProviderApplicationTokenCache(StringRedisTemplate redis, ProviderCacheCipher cipher,
            ProviderIntegrationProperties properties, IdentityProviderConfigService configs) {
        this.redis = redis;
        this.cipher = cipher;
        this.properties = properties;
        this.configs = configs;
    }

    /**
     * 每次命中也重新确认来源版本；loader只允许取得应用Token，不消费用户Code。
     */
    public String get(ProviderConfig source, String purpose, Supplier<TokenValue> loader) {
        requireCurrent(source);
        requireProperties();
        String context = source.registrationId() + "|" + source.version() + "|" + source.flow().value() + "|" + purpose;
        String slot = "iam:provider:{" + cipher.digest(context) + "}";
        String key = slot + ":value";
        String lock = slot + ":lock";
        long deadline = System.nanoTime() + properties.getTokenWait().toNanos();
        do {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                CachedToken value = json.readValue(cipher.decrypt(context, cached), CachedToken.class);
                if (value.expiresAt() > System.currentTimeMillis()) {
                    requireCurrent(source);
                    return value.token();
                }
            }
            String owner = UUID.randomUUID().toString();
            if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lock, owner, properties.getTokenLockLease()))) {
                try {
                    String winner = redis.opsForValue().get(key);
                    if (winner != null) {
                        CachedToken existing = json.readValue(cipher.decrypt(context, winner), CachedToken.class);
                        if (existing.expiresAt() > System.currentTimeMillis()) {
                            requireCurrent(source);
                            return existing.token();
                        }
                    }
                    long started = System.currentTimeMillis();
                    TokenValue value = loader.get();
                    if (value == null || value.token() == null || value.token().isBlank()
                            || value.lifetime() == null || value.lifetime().isNegative() || value.lifetime().isZero()
                            || value.lifetime().compareTo(Duration.ofDays(1)) > 0) {
                        throw unavailable();
                    }
                    long expires = started + value.lifetime().toMillis();
                    long ttl = expires - System.currentTimeMillis() -
                            Math.min(properties.getTokenExpirySkew().toMillis(), value.lifetime().toMillis() / 10);
                    if (ttl <= 0) {
                        throw unavailable();
                    }
                    requireCurrent(source);
                    String envelope = cipher.encrypt(context,
                            json.writeValueAsString(new CachedToken(value.token(), expires)));
                    Long published = redis.execute(PUBLISH, List.of(lock, key), owner, envelope, Long.toString(ttl));
                    if (Long.valueOf(1L).equals(published)) {
                        requireCurrent(source);
                        return value.token();
                    }
                    // 锁已失租：丢弃本次结果，下一轮只能读取获胜值或重新取得锁。
                } finally {
                    redis.execute(RELEASE, List.of(lock), owner);
                }
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw unavailable();
            }
        } while (System.nanoTime() < deadline);
        throw unavailable();
    }

    /**
     * 来源禁用或凭据轮换后，不能继续使用同一次请求读到的旧配置。
     */
    private void requireCurrent(ProviderConfig source) {
        ProviderConfig current = configs.requireEnabledById(source.registrationId(), source.providerType())
                .forCapability(source.loginCapability());
        if (!Objects.equals(current.version(), source.version()) || current.flow() != source.flow()) {
            throw unavailable();
        }
    }

    /**
     * 所有等待都有界，错误配置不能创建永久锁或忙轮询。
     */
    private void requireProperties() {
        if (properties.getTokenLockLease().compareTo(Duration.ofMillis(50)) < 0
                || properties.getTokenLockLease().compareTo(Duration.ofMinutes(2)) > 0
                || properties.getTokenWait().isNegative() || properties.getTokenWait().isZero()
                || properties.getTokenWait().compareTo(Duration.ofMinutes(1)) > 0
                || properties.getTokenExpirySkew().isNegative()) {
            throw unavailable();
        }
    }

    /**
     * 此服务不发送用户Code，失败可明确标记尚未提交。
     */
    private ExternalCodeInfrastructureException unavailable() {
        return new ExternalCodeInfrastructureException(ExternalCodeConsumptionState.NOT_SUBMITTED);
    }

    /**
     * 上游应用Token及明确有效期，只在服务端短暂使用。
     *
     * @since 6.1.0
     */
    public record TokenValue(String token, Duration lifetime) {
        /**
         * 不允许record默认实现暴露Token。
         */
        @Override
        public String toString() {
            return "ApplicationToken[redacted]";
        }
    }

    /**
     * 加密缓存载荷，过期时间也受信封MAC保护。
     *
     * @since 6.1.0
     */
    public record CachedToken(String token, long expiresAt) {
        /**
         * 不允许日志输出解密后的缓存。
         */
        @Override
        public String toString() {
            return "CachedApplicationToken[redacted]";
        }
    }
}
