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

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionBindingListener;
import java.io.Serializable;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 会话内最多保留五个十分钟流程；不以 session ID 作外部索引，登录轮换不丢失绑定。
 * @author Rain
 * @since 6.1.0
 */
public final class PlaygroundSessionStore {
    private static final String KEY = PlaygroundSessionStore.class.getName();
    private final Clock clock;

    public PlaygroundSessionStore() { this(Clock.systemUTC()); }
    PlaygroundSessionStore(Clock clock) { this.clock = clock; }

    public PlaygroundFlow create(HttpSession session, PlaygroundFlow.Scenario scenario) {
        var flows = bucket(session);
        synchronized (flows) {
            purge(flows);
            if (flows.values.size() >= 5) throw new IllegalStateException("At most five active playground flows per session");
            var flow = new PlaygroundFlow(scenario, clock.instant());
            flows.values.put(flow.id(), flow);
            return flow;
        }
    }

    /** Execute atomically within this session, including polling so duplicate requests cannot overlap. */
    public <T> T use(HttpSession session, String id, Function<PlaygroundFlow, T> action) {
        var flows = bucket(session);
        synchronized (flows) {
            purge(flows);
            var flow = flows.values.get(id);
            if (flow == null) throw new IllegalArgumentException("Flow is missing or expired in this session");
            return action.apply(flow);
        }
    }

    public void remove(HttpSession session, String id) {
        var flows = bucket(session);
        synchronized (flows) {
            var flow = flows.values.remove(id);
            if (flow != null) flow.clear();
        }
    }

    public void clear(HttpSession session) {
        var flows = bucket(session);
        synchronized (flows) { flows.clear(); }
    }

    private Bucket bucket(HttpSession session) {
        synchronized (session) {
            Object existing = session.getAttribute(KEY);
            if (existing instanceof Bucket bucket) return bucket;
            var bucket = new Bucket();
            session.setAttribute(KEY, bucket);
            return bucket;
        }
    }

    private void purge(Bucket bucket) {
        bucket.values.values().removeIf(flow -> {
            if (!flow.expired(clock.instant())) return false;
            flow.clear();
            return true;
        });
    }

    /** Clears in-memory token references when the containing session is invalidated. */
    private static final class Bucket implements Serializable, HttpSessionBindingListener {
        private static final long serialVersionUID = 1L;
        private final Map<String, PlaygroundFlow> values = new LinkedHashMap<>();
        synchronized void clear() {
            values.values().forEach(PlaygroundFlow::clear);
            values.clear();
        }
        @Override public void valueUnbound(HttpSessionBindingEvent event) { clear(); }
    }
}
