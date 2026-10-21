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

import java.io.Serializable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 在浏览器会话中保存短时、限次的待绑定外部身份，不构造已认证主体。
 *
 * @author Rain
 * @since 6.1.0
 */
@Component
public class PendingThirdPartyBindingRepository {
    private static final String KEY = PendingThirdPartyBindingRepository.class.getName();
    private static final Duration MAX_AGE = Duration.ofMinutes(5);
    private static final int MAX_ATTEMPTS = 5;
    private final Clock clock;

    public PendingThirdPartyBindingRepository() {
        this(Clock.systemUTC());
    }

    PendingThirdPartyBindingRepository(Clock clock) {
        this.clock = clock;
    }

    public void save(HttpServletRequest request, ThirdPartyIdentityResolutionService.BindingRequiredException ex) {
        HttpSession session = request.getSession(true);
        synchronized (session) {
            session.setAttribute(KEY, new Pending(ex.providerType(), ex.subject(), ex.context(), clock.instant(), 0));
        }
    }

    public Pending current(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw invalid();
        }
        synchronized (session) {
            Object value = session.getAttribute(KEY);
            if (!(value instanceof Pending pending) || pending.createdAt().isAfter(clock.instant())
                || pending.createdAt().plus(MAX_AGE).isBefore(clock.instant())
                || pending.attempts() >= MAX_ATTEMPTS) {
                session.removeAttribute(KEY);
                throw invalid();
            }
            return pending;
        }
    }

    public void failedAttempt(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        synchronized (session) {
            try {
                Pending pending = current(request);
                session.setAttribute(KEY, new Pending(pending.providerType(), pending.subject(), pending.context(),
                    pending.createdAt(), pending.attempts() + 1));
            } catch (BadCredentialsException ignored) {
                // The pending identity was already expired or consumed; do not recreate it.
            }
        }
    }

    public void consume(HttpServletRequest request, Pending expected) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw invalid();
        }
        synchronized (session) {
            Pending current = current(request);
            if (!current.equals(expected)) {
                throw invalid();
            }
            session.removeAttribute(KEY);
        }
    }

    public void clear(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        synchronized (session) {
            session.removeAttribute(KEY);
        }
    }

    private BadCredentialsException invalid() {
        return new BadCredentialsException("第三方绑定状态无效");
    }

    /**
     * 等待本地证明的已验证身份；该状态不是已认证主体。
     *
     * @since 6.1.0
     */
    public record Pending(String providerType, String subject, ThirdPartyLoginContext context,
                          Instant createdAt, int attempts) implements Serializable { }
}
