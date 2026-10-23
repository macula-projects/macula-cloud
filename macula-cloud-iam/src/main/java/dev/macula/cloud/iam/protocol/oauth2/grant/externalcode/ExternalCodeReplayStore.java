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

import dev.macula.cloud.system.enums.ExternalCodeType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * 仅保存 HMAC 摘要并通过 Redis SET NX 原子占用外部凭证。
 *
 * @author Rain
 * @since 6.1.0
 */
@Component
public class ExternalCodeReplayStore {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String KEY_PREFIX = "iam:external-code:replay:";
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
        "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end",
        Long.class);

    private final StringRedisTemplate redis;
    private final ExternalCodeSecurityProperties properties;

    public ExternalCodeReplayStore(StringRedisTemplate redis, ExternalCodeSecurityProperties properties) {
        Assert.hasText(properties.getDigestKey(), "external-code digestKey cannot be empty");
        this.redis = redis;
        this.properties = properties;
    }

    public ReplayClaim claim(ExternalCodeType codeType, String code) {
        String key = KEY_PREFIX + digest(codeType, code);
        String owner = UUID.randomUUID().toString();
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, owner, properties.getReplayTtl()))) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT,
                "External credential is invalid or has already been used.", null));
        }
        return new ReplayClaim(key, owner);
    }

    /**
     * 仅在当前请求仍拥有占用时释放，供明确的第三方基础设施瞬时失败重试。
     *
     * @param claim 当前请求取得的占用
     */
    public void release(ReplayClaim claim) {
        redis.execute(RELEASE, List.of(claim.key()), claim.owner());
    }

    private String digest(ExternalCodeType codeType, String code) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(properties.getDigestKey().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] payload = (codeType.name() + '\0' + code).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("外部凭证摘要失败", ex);
        }
    }

    /**
     * 防重放占用的不可猜测 owner 句柄。
     *
     * @param key 摘要 Redis key
     * @param owner owner nonce
     * @since 6.1.0
     */
    public record ReplayClaim(String key, String owner) {
    }
}
