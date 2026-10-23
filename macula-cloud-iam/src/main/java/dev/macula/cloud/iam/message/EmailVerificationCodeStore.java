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
package dev.macula.cloud.iam.message;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

/**
 * Redis 中仅保存摘要并按 OAuth Client 原子消费的一次性邮箱验证码。
 *
 * @author Rain
 * @since 6.1.0
 */
@Component
@RequiredArgsConstructor
public class EmailVerificationCodeStore {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DefaultRedisScript<Long> RATE = new DefaultRedisScript<>(
        "local n=redis.call('incr',KEYS[1]); "
            + "if n==1 then redis.call('pexpire',KEYS[1],ARGV[1]) end; return n", Long.class);
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>(
        "local v=redis.call('get',KEYS[1]); if not v then return 0 end; "
            + "local p=string.find(v,':'); local d=string.sub(v,1,p-1); local n=tonumber(string.sub(v,p+1)); "
            + "if d==ARGV[1] then redis.call('del',KEYS[1]); return 1 end; "
            + "n=n-1; if n<=0 then redis.call('del',KEYS[1]) "
            + "else redis.call('set',KEYS[1],d..':'..n,'KEEPTTL') end; return 0", Long.class);

    private final StringRedisTemplate redis;
    private final EmailVerificationProperties properties;

    public String issue(String email, String remoteAddress, String clientId) {
        String targetRateKey = "iam:email:send:" + hmac("email-send", clientId, email);
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(targetRateKey, "1",
            properties.getSendInterval()))) {
            throw new IllegalStateException("发送过于频繁");
        }
        Long clientAttempts = redis.execute(RATE,
            List.of("iam:email:client:" + hmac("email-client", clientId)),
            String.valueOf(properties.getSendInterval().toMillis()));
        if (clientAttempts == null || clientAttempts > properties.getMaxClientSendsPerInterval()) {
            redis.delete(targetRateKey);
            throw new IllegalStateException("发送过于频繁");
        }
        Long attempts = redis.execute(RATE,
            List.of("iam:email:ip:" + hmac("email-ip", clientId, remoteAddress)),
            String.valueOf(properties.getSendInterval().toMillis()));
        if (attempts == null || attempts > properties.getMaxIpSendsPerInterval()) {
            redis.delete(targetRateKey);
            throw new IllegalStateException("发送过于频繁");
        }
        return "%06d".formatted(RANDOM.nextInt(1_000_000));
    }

    public void activate(String email, String code, String clientId) {
        redis.opsForValue().set(key(clientId, email), digest(clientId, email, code) + ':'
            + properties.getMaxAttempts(), properties.getTtl());
    }

    public boolean consume(String email, String code, String clientId) {
        return Long.valueOf(1).equals(redis.execute(CONSUME, List.of(key(clientId, email)),
            digest(clientId, email, code)));
    }

    private String key(String clientId, String email) {
        return "iam:email:code:" + hmac("email-code", clientId, email);
    }

    private String digest(String clientId, String email, String code) {
        return hmac("email-value", clientId, email, code);
    }

    private String hmac(String... values) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getDigestKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(String.join("\0", values)
                .getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("验证码摘要失败", ex);
        }
    }
}
