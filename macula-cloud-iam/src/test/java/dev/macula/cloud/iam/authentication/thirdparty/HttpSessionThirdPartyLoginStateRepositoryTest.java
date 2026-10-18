/*
 * Copyright (c) 2026 Macula
 *   macula.dev, China
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
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.BadCredentialsException;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证第三方登录 state 的会话绑定、一次消费和有效期。
 *
 * @author Rain
 * @since 6.1.0
 */
class HttpSessionThirdPartyLoginStateRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-10-18T00:00:00Z");
    private final HttpSessionThirdPartyLoginStateRepository repository =
        new HttpSessionThirdPartyLoginStateRepository(new SecureRandom(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void consumesStateExactlyOnce() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ThirdPartyLoginContext expected = new ThirdPartyLoginContext("GITHUB", "client", 9L,
            UserTypeEnum.MEMBER, NOW);
        String state = repository.create(request, expected);

        assertEquals(expected, repository.consume(request, "GITHUB", state));
        assertThrows(BadCredentialsException.class, () -> repository.consume(request, "GITHUB", state));
    }

    @Test
    void createsRandomStateAndReplacesThePreviousProviderState() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ThirdPartyLoginContext context = new ThirdPartyLoginContext("GITHUB", "client", 9L,
            UserTypeEnum.MEMBER, NOW);

        String first = repository.create(request, context);
        String second = repository.create(request, context);

        assertNotEquals(first, second);
        assertThrows(BadCredentialsException.class, () -> repository.consume(request, "GITHUB", first));
        assertThrows(BadCredentialsException.class, () -> repository.consume(request, "GITHUB", second));
    }

    @Test
    void rejectsWrongProviderAndExpiredState() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String state = repository.create(request, new ThirdPartyLoginContext("GITHUB", "client", 9L,
            UserTypeEnum.EMPLOYEE, NOW.minusSeconds(301)));
        assertThrows(BadCredentialsException.class, () -> repository.consume(request, "GITHUB", state));

        MockHttpServletRequest another = new MockHttpServletRequest();
        String anotherState = repository.create(another, new ThirdPartyLoginContext("GITHUB", "client", 9L,
            UserTypeEnum.EMPLOYEE, NOW));
        assertThrows(BadCredentialsException.class,
            () -> repository.consume(another, "WECOM", anotherState));
    }

    @Test
    void concurrentCallbacksCannotConsumeTheSameStateTwice() throws Exception {
        RacingSession session = new RacingSession();
        MockHttpServletRequest createRequest = new MockHttpServletRequest();
        createRequest.setSession(session);
        String state = repository.create(createRequest, new ThirdPartyLoginContext("GITHUB", "client", 9L,
            UserTypeEnum.EMPLOYEE, NOW));
        MockHttpServletRequest first = new MockHttpServletRequest();
        first.setSession(session);
        MockHttpServletRequest second = new MockHttpServletRequest();
        second.setSession(session);

        var executor = Executors.newFixedThreadPool(2);
        try {
            var firstResult = executor.submit(() -> consume(first, state));
            var secondResult = executor.submit(() -> consume(second, state));
            assertEquals(1, java.util.stream.Stream.of(firstResult.get(), secondResult.get())
                .filter(Boolean::booleanValue).count());
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean consume(MockHttpServletRequest request, String state) {
        try {
            repository.consume(request, "GITHUB", state);
            return true;
        } catch (BadCredentialsException ex) {
            return false;
        }
    }

    private static final class RacingSession extends MockHttpSession {
        private final CountDownLatch readers = new CountDownLatch(2);

        @Override
        public Object getAttribute(String name) {
            Object value = super.getAttribute(name);
            readers.countDown();
            try {
                readers.await(300, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return value;
        }
    }
}
