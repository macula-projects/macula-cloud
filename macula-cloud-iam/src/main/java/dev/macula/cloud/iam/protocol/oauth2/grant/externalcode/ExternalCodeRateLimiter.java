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

import dev.macula.cloud.iam.authentication.thirdparty.ProviderCacheCipher;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.stereotype.Component;

/**
 * 固定Client与连接地址的Redis原子限流；不相信任意X-Forwarded-For。
 * @author Rain
 * @since 6.1.0
 */
@Component
public class ExternalCodeRateLimiter {
    private static final String PREFIX = "iam:external-code:{rate}:";
    private static final DefaultRedisScript<Long> LIMIT = new DefaultRedisScript<>("""
        local client=redis.call('INCR',KEYS[1])
        if client==1 then redis.call('PEXPIRE',KEYS[1],ARGV[1]) end
        local ip=redis.call('INCR',KEYS[2])
        if ip==1 then redis.call('PEXPIRE',KEYS[2],ARGV[1]) end
        if client>tonumber(ARGV[2]) or ip>tonumber(ARGV[3]) then return 0 end
        return 1
        """, Long.class);
    private final StringRedisTemplate redis;
    private final ProviderCacheCipher cipher;
    private final ExternalCodeSecurityProperties properties;
    /** 仅复用服务端摘要，不依赖用户Token或客户端Secret。 */
    public ExternalCodeRateLimiter(StringRedisTemplate redis, ProviderCacheCipher cipher,
        ExternalCodeSecurityProperties properties) {
        this.redis = redis;
        this.cipher = cipher;
        this.properties = properties;
    }
    /** 在合法Client/来源/Scope校验后、Code claim之前限流，Redis故障失败关闭。 */
    public void check(String clientId, String remoteAddress) {
        try {
            Duration window = properties.getRateWindow();
            if (clientId == null || clientId.isBlank() || remoteAddress == null || remoteAddress.isBlank()
                || window == null || window.compareTo(Duration.ofSeconds(5)) < 0
                || window.compareTo(Duration.ofHours(1)) > 0 || properties.getClientRateLimit() < 1
                || properties.getIpRateLimit() < 1) { throw new IllegalStateException(); }
            Long allowed = redis.execute(LIMIT, List.of(PREFIX + "client:" + cipher.digest("rate-client\0" + clientId),
                PREFIX + "ip:" + cipher.digest("rate-ip\0" + remoteAddress)), Long.toString(window.toMillis()),
                Integer.toString(properties.getClientRateLimit()), Integer.toString(properties.getIpRateLimit()));
            if (!Long.valueOf(1).equals(allowed)) {
                throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT,
                    "External authentication is temporarily unavailable.", null));
            }
        } catch (OAuth2AuthenticationException ex) { throw ex; }
        catch (RuntimeException ex) { throw new OAuth2AuthenticationException(OAuth2ErrorCodes.SERVER_ERROR); }
    }
}
