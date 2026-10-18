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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * 每个会话、每个 provider 仅保存一个五分钟有效的一次性 state。
 *
 * @author Rain
 * @since 6.1.0
 */
@Component
public class HttpSessionThirdPartyLoginStateRepository implements ThirdPartyLoginStateRepository {
    private static final String ATTRIBUTE_PREFIX = HttpSessionThirdPartyLoginStateRepository.class.getName() + ".";
    private static final Duration MAX_AGE = Duration.ofMinutes(5);
    private final SecureRandom random;
    private final Clock clock;

    public HttpSessionThirdPartyLoginStateRepository() {
        this(new SecureRandom(), Clock.systemUTC());
    }

    HttpSessionThirdPartyLoginStateRepository(SecureRandom random, Clock clock) {
        this.random = random;
        this.clock = clock;
    }

    @Override
    public String create(HttpServletRequest request, ThirdPartyLoginContext context) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        request.getSession(true).setAttribute(key(context.providerType()), new StoredState(state, context));
        return state;
    }

    @Override
    public ThirdPartyLoginContext consume(HttpServletRequest request, String providerType, String state) {
        HttpSession session = request.getSession(false);
        Object value = null;
        if (session != null) {
            synchronized (session) {
                value = session.getAttribute(key(providerType));
                session.removeAttribute(key(providerType));
            }
        }
        if (!(value instanceof StoredState stored) || state == null || !stored.state().equals(state)) {
            throw new BadCredentialsException("第三方登录状态无效");
        }
        Instant now = clock.instant();
        if (stored.context().createdAt().isAfter(now)
            || stored.context().createdAt().plus(MAX_AGE).isBefore(now)) {
            throw new BadCredentialsException("第三方登录状态已过期");
        }
        return stored.context();
    }

    private String key(String providerType) {
        return ATTRIBUTE_PREFIX + providerType;
    }

    private record StoredState(String state, ThirdPartyLoginContext context) implements java.io.Serializable {
    }
}
