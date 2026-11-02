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

import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeSecurityProperties;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeVerification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 生成不可解析票据，并只以摘要作为 Redis key 保存已核验绑定材料。
 *
 * @author Rain
 * @since 6.1.0
 */
@Component
public class ExternalBindingTicketStore {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final String ENCRYPTION_KEY_CONTEXT = "binding-ticket-encryption\0";
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_NONCE_BYTES = 12;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String KEY_PREFIX = "iam:external-binding:";
    private static final String STATE_READY = "READY";
    private static final String FIELD_STATE = "state";
    private static final String FIELD_CLIENT_ID = "clientId";
    private static final String FIELD_TENANT_ID = "tenantId";
    private static final String FIELD_USER_TYPE = "userType";
    private static final String FIELD_BINDING_TYPE = "bindingType";
    private static final String FIELD_PROVIDER_TYPE = "providerType";
    private static final String FIELD_SUBJECT = "subject";
    private static final String FIELD_MOBILE = "mobile";
    private static final String FIELD_FAILED_ATTEMPTS = "failedAttempts";
    private static final String FIELD_OWNER = "owner";
    private static final String FIELD_LEASE_UNTIL = "leaseUntil";
    private static final DefaultRedisScript<Long> ISSUE = new DefaultRedisScript<>("""
        if redis.call('exists', KEYS[1]) == 1 then return 0 end
        for i = 1, #ARGV - 1, 2 do
            redis.call('hset', KEYS[1], ARGV[i], ARGV[i + 1])
        end
        redis.call('pexpire', KEYS[1], ARGV[#ARGV])
        return 1
        """, Long.class);
    private static final DefaultRedisScript<Long> CLAIM = new DefaultRedisScript<>("""
        local state = redis.call('hget', KEYS[1], 'state')
        if not state then return 0 end
        local lease = tonumber(redis.call('hget', KEYS[1], 'leaseUntil') or '0')
        if state == 'READY' or (state == 'CLAIMED' and lease < tonumber(ARGV[2])) then
            redis.call('hset', KEYS[1], 'state', 'CLAIMED', 'owner', ARGV[1], 'leaseUntil', ARGV[3])
            return 1
        end
        return 0
        """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
        if redis.call('hget', KEYS[1], 'state') ~= 'CLAIMED' or
            redis.call('hget', KEYS[1], 'owner') ~= ARGV[1] then return 0 end
        redis.call('hset', KEYS[1], 'state', 'READY')
        redis.call('hdel', KEYS[1], 'owner', 'leaseUntil')
        return 1
        """, Long.class);
    private static final DefaultRedisScript<Long> FAILED = new DefaultRedisScript<>("""
        if redis.call('hget', KEYS[1], 'state') ~= 'CLAIMED' or
            redis.call('hget', KEYS[1], 'owner') ~= ARGV[1] then return 0 end
        local attempts = redis.call('hincrby', KEYS[1], 'failedAttempts', 1)
        if attempts >= tonumber(ARGV[2]) then
            redis.call('hset', KEYS[1], 'state', 'TERMINATED')
        else
            redis.call('hset', KEYS[1], 'state', 'READY')
        end
        redis.call('hdel', KEYS[1], 'owner', 'leaseUntil')
        return attempts
        """, Long.class);
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>("""
        if redis.call('hget', KEYS[1], 'state') ~= 'CLAIMED' or
            redis.call('hget', KEYS[1], 'owner') ~= ARGV[1] then return 0 end
        redis.call('del', KEYS[1])
        return 1
        """, Long.class);

    private final StringRedisTemplate redis;
    private final ExternalCodeSecurityProperties properties;
    private final StringKeyGenerator ticketGenerator = new Base64StringKeyGenerator();

    public ExternalBindingTicketStore(StringRedisTemplate redis, ExternalCodeSecurityProperties properties) {
        Assert.hasText(properties.getDigestKey(), "external-code digestKey cannot be empty");
        Assert.isTrue(!properties.getBindingTicketTtl().isNegative()
            && !properties.getBindingTicketTtl().isZero()
            && properties.getBindingTicketTtl().compareTo(java.time.Duration.ofMinutes(5)) <= 0,
            "binding ticket TTL must be between zero and five minutes");
        Assert.isTrue(properties.getBindingTicketMaxAttempts() > 0,
            "binding ticket max attempts must be positive");
        Assert.isTrue(!properties.getBindingTicketClaimLease().isNegative()
            && !properties.getBindingTicketClaimLease().isZero(),
            "binding ticket claim lease must be positive");
        this.redis = redis;
        this.properties = properties;
    }

    /**
     * 保存已核验身份并返回只向调用方公开一次的随机 ticket。
     *
     * @param clientId OAuth Client ID
     * @param tenantId Client 租户快照
     * @param userType Client 用户类型快照
     * @param verification 已核验身份
     * @return opaque ticket
     */
    public String issue(String clientId, Long tenantId, UserTypeEnum userType,
        ExternalCodeVerification verification) {
        String ticket = ticketGenerator.generateKey();
        String key = key(ticket);
        Map<String, String> values = new LinkedHashMap<>();
        values.put(FIELD_STATE, STATE_READY);
        values.put(FIELD_CLIENT_ID, clientId);
        values.put(FIELD_TENANT_ID, tenantId.toString());
        values.put(FIELD_USER_TYPE, userType.getType());
        values.put(FIELD_FAILED_ATTEMPTS, "0");
        if (verification.mobile() != null) {
            values.put(FIELD_BINDING_TYPE, ExternalBindingTicket.BindingType.MOBILE.name());
            values.put(FIELD_MOBILE, encrypt(FIELD_MOBILE, verification.mobile()));
        } else {
            values.put(FIELD_BINDING_TYPE, ExternalBindingTicket.BindingType.EXTERNAL_IDENTITY.name());
            values.put(FIELD_PROVIDER_TYPE, verification.providerType());
            values.put(FIELD_SUBJECT, encrypt(FIELD_SUBJECT, verification.subject()));
        }
        java.util.ArrayList<String> arguments = new java.util.ArrayList<>(values.size() * 2 + 1);
        values.forEach((field, value) -> {
            arguments.add(field);
            arguments.add(value);
        });
        arguments.add(Long.toString(properties.getBindingTicketTtl().toMillis()));
        Long issued = redis.execute(ISSUE, List.of(key), arguments.toArray());
        if (!Long.valueOf(1).equals(issued)) {
            throw new IllegalStateException("绑定票据创建失败");
        }
        return ticket;
    }

    /**
     * 原子占用待绑定票据；租约超时后允许另一个请求恢复处理。
     *
     * @param ticket 调用方提交的 opaque ticket
     * @return 带 owner nonce 的服务端票据
     */
    public ClaimedTicket claim(String ticket) {
        String owner = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        long leaseUntil = now + properties.getBindingTicketClaimLease().toMillis();
        String key = key(ticket);
        Long claimed = redis.execute(CLAIM, List.of(key), owner, Long.toString(now), Long.toString(leaseUntil));
        if (!Long.valueOf(1).equals(claimed)) {
            throw invalid();
        }
        try {
            Map<Object, Object> values = redis.opsForHash().entries(key);
            if (values == null) {
                throw invalid();
            }
            ExternalBindingTicket value = new ExternalBindingTicket(required(values, FIELD_CLIENT_ID),
                Long.valueOf(required(values, FIELD_TENANT_ID)), userType(required(values, FIELD_USER_TYPE)),
                ExternalBindingTicket.BindingType.valueOf(required(values, FIELD_BINDING_TYPE)),
                text(values, FIELD_PROVIDER_TYPE), decrypt(FIELD_SUBJECT, text(values, FIELD_SUBJECT)),
                decrypt(FIELD_MOBILE, text(values, FIELD_MOBILE)),
                Integer.parseInt(required(values, FIELD_FAILED_ATTEMPTS)));
            return new ClaimedTicket(key, owner, value);
        } catch (BadCredentialsException | IllegalArgumentException ex) {
            redis.execute(CONSUME, List.of(key), owner);
            throw invalid();
        } catch (RuntimeException ex) {
            redis.execute(RELEASE, List.of(key), owner);
            throw ex;
        }
    }

    public void failedAttempt(ClaimedTicket claimed) {
        Long attempts = redis.execute(FAILED, List.of(claimed.key()), claimed.owner(),
            Integer.toString(properties.getBindingTicketMaxAttempts()));
        if (attempts == null || attempts == 0) {
            throw invalid();
        }
    }

    public void release(ClaimedTicket claimed) {
        redis.execute(RELEASE, List.of(claimed.key()), claimed.owner());
    }

    public void consume(ClaimedTicket claimed) {
        Long consumed = redis.execute(CONSUME, List.of(claimed.key()), claimed.owner());
        if (!Long.valueOf(1).equals(consumed)) {
            throw invalid();
        }
    }

    private String required(Map<Object, Object> values, String field) {
        String value = text(values, field);
        if (value == null || value.isBlank()) {
            throw invalid();
        }
        return value;
    }

    private String text(Map<Object, Object> values, String field) {
        Object value = values.get(field);
        return value instanceof String text ? text : null;
    }

    private UserTypeEnum userType(String value) {
        for (UserTypeEnum userType : UserTypeEnum.values()) {
            if (userType.getType().equals(value)) {
                return userType;
            }
        }
        throw invalid();
    }

    private BadCredentialsException invalid() {
        return new BadCredentialsException("绑定票据无效");
    }

    private String key(String ticket) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(properties.getDigestKey().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] payload = ("binding-ticket\0" + ticket).getBytes(StandardCharsets.UTF_8);
            return KEY_PREFIX + HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("绑定票据摘要失败", ex);
        }
    }

    private String encrypt(String field, String value) {
        try {
            byte[] nonce = new byte[GCM_NONCE_BYTES];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(field.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, payload, 0, nonce.length);
            System.arraycopy(ciphertext, 0, payload, nonce.length, ciphertext.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("绑定票据加密失败", ex);
        }
    }

    private String decrypt(String field, String value) {
        if (value == null) {
            return null;
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(value);
            if (payload.length <= GCM_NONCE_BYTES) {
                throw new GeneralSecurityException("invalid encrypted binding value");
            }
            byte[] nonce = java.util.Arrays.copyOfRange(payload, 0, GCM_NONCE_BYTES);
            byte[] ciphertext = java.util.Arrays.copyOfRange(payload, GCM_NONCE_BYTES, payload.length);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(field.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw invalid();
        }
    }

    private SecretKeySpec encryptionKey() throws GeneralSecurityException {
        byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest((ENCRYPTION_KEY_CONTEXT + properties.getDigestKey()).getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }

    /**
     * 已由 owner nonce 和短租约占用的服务端票据。
     *
     * @since 6.1.0
     */
    public record ClaimedTicket(String key, String owner, ExternalBindingTicket value) {
    }
}
