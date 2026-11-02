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
package dev.macula.cloud.iam.authentication.challenge;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * Redis CAPTCHA 实现，只保存题目答案和绑定上下文的 HMAC 摘要。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class RedisLoginCaptchaService implements LoginCaptchaService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DefaultRedisScript<Long> RATE = new DefaultRedisScript<>(
        "local n=redis.call('incr',KEYS[1]); "
            + "if n==1 then redis.call('pexpire',KEYS[1],ARGV[1]) end; return n", Long.class);
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>(
        "local v=redis.call('get',KEYS[1]); if not v then return 0 end; "
            + "redis.call('del',KEYS[1]); if v==ARGV[1] then return 1 else return 0 end", Long.class);

    private final StringRedisTemplate redis;
    private final LoginCaptchaProperties properties;

    @Override
    public CaptchaChallenge issue(String clientId, String origin, String remoteAddress) {
        rate(clientId, origin, remoteAddress);
        int left = RANDOM.nextInt(8) + 1;
        int right = RANDOM.nextInt(8) + 1;
        byte[] random = new byte[24];
        RANDOM.nextBytes(random);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        String answer = String.valueOf(left + right);
        redis.opsForValue().set(key(id), digest(clientId, origin, id, answer), properties.getTtl());
        return new CaptchaChallenge(id, left + " + " + right + " = ?");
    }

    @Override
    public boolean consume(String challengeId, String answer, String clientId, String origin) {
        if (challengeId == null || answer == null || clientId == null || origin == null) {
            return false;
        }
        return Long.valueOf(1).equals(redis.execute(CONSUME, List.of(key(challengeId)),
            digest(clientId, origin, challengeId, answer.trim())));
    }

    private void rate(String clientId, String origin, String remoteAddress) {
        Long clientCount = redis.execute(RATE,
            List.of("iam:captcha:client:" + digest("captcha-client", clientId, origin)),
            String.valueOf(properties.getRateWindow().toMillis()));
        if (clientCount == null || clientCount > properties.getMaxClientIssuesPerWindow()) {
            throw new IllegalStateException("CAPTCHA 申请过于频繁");
        }
        Long count = redis.execute(RATE,
            List.of("iam:captcha:rate:" + digest("captcha-rate", clientId, origin, remoteAddress)),
            String.valueOf(properties.getRateWindow().toMillis()));
        if (count == null || count > properties.getMaxIssuesPerWindow()) {
            throw new IllegalStateException("CAPTCHA 申请过于频繁");
        }
    }

    private String key(String id) {
        return "iam:captcha:" + digest("captcha-id", id);
    }

    private String digest(String... values) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getDigestKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(String.join("\0", values)
                .getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("CAPTCHA 摘要失败", ex);
        }
    }
}
