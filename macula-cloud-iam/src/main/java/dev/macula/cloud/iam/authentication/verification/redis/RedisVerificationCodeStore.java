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
package dev.macula.cloud.iam.authentication.verification.redis;

import dev.macula.cloud.iam.authentication.verification.VerificationCodeScope;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeSettings;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeStore;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 共享Redis验证码算法，SMS保留原键、HMAC输入、TTL及Lua语义。
 *
 * @author Macula
 * @since 6.1.0
 */
@Component
@RequiredArgsConstructor
public class RedisVerificationCodeStore implements VerificationCodeStore {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DefaultRedisScript<Long> RATE = new DefaultRedisScript<>(
            "local n=redis.call('incr',KEYS[1]); " +
                    "if n==1 then redis.call('pexpire',KEYS[1],ARGV[1]) end; return n", Long.class);
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>(
            "local v=redis.call('get',KEYS[1]); if not v then return 0 end; " +
                    "local p=string.find(v,':'); local d=string.sub(v,1,p-1); local n=tonumber(string.sub(v,p+1)); " +
                    "if d==ARGV[1] then redis.call('del',KEYS[1]); return 1 end; " +
                    "n=n-1; if n<=0 then redis.call('del',KEYS[1]) else redis.call('set',KEYS[1],d..':'..n,'KEEPTTL')" +
                    " end; return 0",
            Long.class);
    private final StringRedisTemplate redis;

    /**
     * 发送额度跨用途共享，避免通过绑定/登录切换绕过限流。
     */
    @Override
    public String issue(VerificationCodeScope scope, String target, String ip, VerificationCodeSettings settings) {
        settings.validate();
        String sendKey = key(scope, "send", settings, label(scope, "send"), target);
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(sendKey, "1", settings.getSendInterval()))) {
            throw new IllegalStateException("发送过于频繁");
        }
        Long clientAttempts = redis.execute(RATE, List.of(key(scope, "client", settings,
                label(scope, "client"), scope.clientContext())), Long.toString(settings.getSendInterval().toMillis()));
        if (clientAttempts == null || clientAttempts > settings.getMaxClientSendsPerInterval()) {
            redis.delete(sendKey);
            throw new IllegalStateException("发送过于频繁");
        }
        Long ipAttempts = redis.execute(RATE, List.of(key(scope, "ip", settings,
                label(scope, "ip"), "all-purposes", ip)), Long.toString(settings.getSendInterval().toMillis()));
        if (ipAttempts == null || ipAttempts > settings.getMaxIpSendsPerInterval()) {
            redis.delete(sendKey);
            throw new IllegalStateException("发送过于频繁");
        }
        return "%06d".formatted(RANDOM.nextInt(1_000_000));
    }

    /**
     * 不保存明文验证码；TTL包含所有错误尝试。
     */
    @Override
    public void activate(VerificationCodeScope scope, String target, String code, VerificationCodeSettings settings) {
        settings.validate();
        redis.opsForValue().set(codeKey(scope, target, settings), digest(scope, target, code, settings)
                + ":" + settings.getMaxAttempts(), settings.getTtl());
    }

    /**
     * Lua只允许一次成功，失败次数递减且保持原TTL。
     */
    @Override
    public boolean consume(VerificationCodeScope scope, String target, String code, VerificationCodeSettings settings) {
        settings.validate();
        return Long.valueOf(1).equals(redis.execute(CONSUME, List.of(codeKey(scope, target, settings)),
                digest(scope, target, code, settings)));
    }

    /**
     * 通道和用途均参与摘要，不将个人信息写入key。
     */
    private String codeKey(VerificationCodeScope scope, String target, VerificationCodeSettings settings) {
        return key(scope, "code", settings, label(scope, "code"), scope.context(), target);
    }

    /**
     * 使用既有SMS用途标识，禁止改成枚举名称。
     */
    private String digest(VerificationCodeScope scope, String target, String code, VerificationCodeSettings settings) {
        return hmac(settings, label(scope, "value"), scope.context(), target, code);
    }

    /**
     * 只有EMAIL使用新域，SMS输入逐字节保持。
     */
    private String label(VerificationCodeScope scope, String purpose) {
        return scope.channel().value() + "-" + purpose;
    }

    /**
     * Redis前缀和值摘要分别保留明确的用途域。
     */
    private String key(VerificationCodeScope scope, String kind, VerificationCodeSettings settings, String... values) {
        return "iam:" + scope.channel().value() + ":" + kind + ":" + hmac(settings, values);
    }

    /**
     * MAC密钥只在服务端配置，异常不附带值。
     */
    private String hmac(VerificationCodeSettings settings, String... values) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(settings.getDigestKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(String.join("\0", values).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("验证码摘要失败");
        }
    }
}
