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

import java.io.Serializable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import dev.macula.cloud.iam.authentication.thirdparty.browser.ThirdPartyIdentityResolutionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

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
    private ExternalBindingTicketStore tickets;

    public PendingThirdPartyBindingRepository() {
        this(Clock.systemUTC());
    }

    PendingThirdPartyBindingRepository(Clock clock) {
        this.clock = clock;
    }

    /**
     * 注入SDK票据存储，浏览器pending仍使用会话有效期。
     */
    @org.springframework.beans.factory.annotation.Autowired
    public void setTickets(ExternalBindingTicketStore tickets) {
        this.tickets = tickets;
    }

    public void save(HttpServletRequest request, ThirdPartyIdentityResolutionService.BindingRequiredException ex) {
        HttpSession session = request.getSession(true);
        synchronized (session) {
            session.setAttribute(KEY, new Pending(ex.identity(), ex.context(), clock.instant(), 0, null));
        }
    }

    /**
     * SDK入口不能覆盖会话中另一个尚未完成的绑定。
     */
    public void saveSdk(HttpServletRequest request, ExternalBindingTicketStore.ClaimedTicket claimed) {
        HttpSession session = request.getSession(true);
        synchronized (session) {
            if (session.getAttribute(KEY) != null) {
                throw invalid();
            }
            ExternalBindingTicket value = claimed.value();
            VerifiedExternalIdentity identity = value.identity();
            ThirdPartyLoginContext context = new ThirdPartyLoginContext(identity.providerType(), "SDK",
                    value.clientId(),
                    clock.instant(), "/login/bind/complete", identity.registrationId(), identity.sourceType(),
                    value.tenantId(), value.userType(), identity.sourceVersion(), identity.flow(), null,
                    identity.loginCapability());
            session.setAttribute(KEY, new Pending(identity, context,
                    value.expiresAt().minus(MAX_AGE), value.failedAttempts(), claimed));
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
            if (pending.claimed() != null) {
                tickets.requireBrowserClaim(pending.claimed());
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
                if (pending.claimed() != null) {
                    tickets.failedBrowserAttempt(pending.claimed());
                }
                session.setAttribute(KEY, new Pending(pending.identity(), pending.context(),
                        pending.createdAt(), pending.attempts() + 1, pending.claimed()));
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
            if (current.claimed() != null) {
                tickets.consume(current.claimed());
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
            Object value = session.getAttribute(KEY);
            if (value instanceof Pending pending && pending.claimed() != null) {
                tickets.consume(pending.claimed());
            }
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
    public record Pending(VerifiedExternalIdentity identity, ThirdPartyLoginContext context,
                          Instant createdAt, int attempts, ExternalBindingTicketStore.ClaimedTicket claimed) implements
            Serializable {
    }
}
