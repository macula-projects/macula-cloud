/*
 * Copyright (c) 2023 Macula
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

package dev.macula.cloud.iam.playground;

import java.time.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import static org.assertj.core.api.Assertions.*;

/**
 * 验证短期状态的会话归属、生命周期、限额和轮询并发边界。
 * @author Rain
 * @since 6.1.0
 */
class PlaygroundSessionStoreTest {
    static final Instant NOW = Instant.parse("2026-10-09T00:00:00Z");

    @Test void limitsFlowsToFiveWithoutDiscardingAnActiveAuthorization() {
        var store = new PlaygroundSessionStore(Clock.fixed(NOW, ZoneOffset.UTC));
        var session = new MockHttpSession();
        for (int i = 0; i < 5; i++) store.create(session, PlaygroundFlow.Scenario.PUBLIC_CODE);
        assertThatThrownBy(() -> store.create(session, PlaygroundFlow.Scenario.DEVICE)).isInstanceOf(IllegalStateException.class);
    }

    @Test void anotherSessionCannotReadRemoveOrConsumeFlow() {
        var store = new PlaygroundSessionStore();
        var owner = new MockHttpSession();
        var other = new MockHttpSession();
        var flow = store.create(owner, PlaygroundFlow.Scenario.PUBLIC_CODE);
        flow.bind("fixture-state", "fixture-nonce", "fixture-challenge");
        assertThatThrownBy(() -> store.use(other, flow.id(), found -> found.nonce())).isInstanceOf(IllegalArgumentException.class);
        store.remove(other, flow.id());
        String nonce = store.use(owner, flow.id(), found -> found.nonce());
        assertThat(nonce).isEqualTo("fixture-nonce");
    }

    @Test void loginSessionIdRotationKeepsFlowAndInvalidationClearsSecrets() {
        var store = new PlaygroundSessionStore();
        var session = new MockHttpSession();
        var flow = store.create(session, PlaygroundFlow.Scenario.PUBLIC_CODE);
        flow.tokens(Map.of("access_token", "fixture-token"), Instant.now());
        String previousId = session.getId();
        session.changeSessionId();
        assertThat(session.getId()).isNotEqualTo(previousId);
        assertThat(store.use(session, flow.id(), found -> found.tokens()).get("access_token")).isEqualTo("fixture-token");
        session.invalidate();
        assertThat(flow.tokens()).isEmpty();
    }

    @Test void expiresAtTenMinutesOrEarlierTokenExpiryAndPurgesReferences() {
        var clock = new MutableClock();
        var store = new PlaygroundSessionStore(clock);
        var session = new MockHttpSession();
        var flow = store.create(session, PlaygroundFlow.Scenario.CLIENT_CREDENTIALS);
        flow.tokens(Map.of("access_token", "fixture-token", "expires_in", 15), NOW);
        assertThat(flow.expiresAt()).isEqualTo(NOW.plusSeconds(15));
        clock.now = NOW.plusSeconds(15);
        assertThatThrownBy(() -> store.use(session, flow.id(), found -> found.tokens())).isInstanceOf(IllegalArgumentException.class);
        assertThat(flow.tokens()).isEmpty();
        var next = store.create(session, PlaygroundFlow.Scenario.PUBLIC_CODE);
        assertThat(next.expiresAt()).isEqualTo(clock.now.plusSeconds(600));
        clock.now = clock.now.plusSeconds(600);
        assertThatThrownBy(() -> store.use(session, next.id(), found -> found.id())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void callbackBindingIsSingleUseAndDoesNotPersistVerifierOrCredentials() {
        var flow = new PlaygroundFlow(PlaygroundFlow.Scenario.PUBLIC_CODE, NOW);
        flow.bind("fixture-state", "fixture-nonce", "challenge");
        assertThatThrownBy(() -> flow.consumeCallback("wrong")).isInstanceOf(IllegalArgumentException.class);
        flow.consumeCallback("fixture-state");
        assertThatThrownBy(() -> flow.consumeCallback("fixture-state")).isInstanceOf(IllegalArgumentException.class);
        flow.tokens(Map.of("access_token", "fixture", "password", "never-store", "captcha", "never-store",
            "client_secret", "never-store", "code_verifier", "never-store"), NOW);
        assertThat(flow.tokens()).containsOnlyKeys("access_token");
        assertThat(flow.toString()).doesNotContain("fixture", "never-store");
    }

    @Test void deviceHonorsIntervalSlowDownAndStop() {
        var flow = new PlaygroundFlow(PlaygroundFlow.Scenario.DEVICE, NOW);
        flow.device("fixture-device-code", 1, 300, NOW);
        assertThat(flow.pollIntervalSeconds()).isEqualTo(5);
        assertThatThrownBy(() -> flow.beginPoll(NOW)).isInstanceOf(IllegalStateException.class);
        flow.beginPoll(NOW.plusSeconds(5));
        flow.slowDown(NOW.plusSeconds(5));
        assertThat(flow.pollIntervalSeconds()).isEqualTo(10);
        assertThatThrownBy(() -> flow.beginPoll(NOW.plusSeconds(10))).isInstanceOf(IllegalStateException.class);
        flow.beginPoll(NOW.plusSeconds(15));
        flow.stopPolling();
        assertThat(flow.deviceCode()).isNull();
        assertThatThrownBy(() -> flow.beginPoll(NOW.plusSeconds(30))).isInstanceOf(IllegalStateException.class);
    }

    @Test void serializesConcurrentMutationsWithinSession() throws Exception {
        var store = new PlaygroundSessionStore();
        var session = new MockHttpSession();
        var flow = store.create(session, PlaygroundFlow.Scenario.DEVICE);
        var executor = Executors.newFixedThreadPool(2);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var active = new AtomicInteger();
        var maximum = new AtomicInteger();
        try {
            Future<?> first = executor.submit(() -> store.use(session, flow.id(), found -> {
                maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("test release timed out"); }
                catch (InterruptedException ex) { throw new AssertionError(ex); }
                active.decrementAndGet();
                return null;
            }));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> second = executor.submit(() -> store.use(session, flow.id(), found -> {
                maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                active.decrementAndGet();
                return null;
            }));
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertThat(maximum.get()).isEqualTo(1);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static final class MutableClock extends Clock {
        Instant now = NOW;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
