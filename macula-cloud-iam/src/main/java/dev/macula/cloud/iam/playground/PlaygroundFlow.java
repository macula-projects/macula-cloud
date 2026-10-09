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

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 单次演示的有界状态，仅保存协议绑定和令牌，不保存输入密码、验证码或客户端密钥。
 * 不使用自动 toString，避免日志意外输出凭据。
 * @author Rain
 * @since 6.1.0
 */
public final class PlaygroundFlow implements Serializable {
    private static final long serialVersionUID = 1L;
    public enum Scenario { PUBLIC_CODE, CONFIDENTIAL_CODE, CLIENT_CREDENTIALS, PASSWORD, SMS, DEVICE }
    private final String id = UUID.randomUUID().toString();
    private final Scenario scenario;
    private final Instant createdAt;
    private Instant expiresAt;
    private String state;
    private String nonce;
    private String challenge;
    private boolean callbackConsumed;
    private Map<String, Object> tokens = Map.of();
    private String deviceCode;
    private Instant nextPollAt;
    private long pollIntervalSeconds = 5;
    private boolean stopped;
    private boolean oidcVerified;
    private String subject;

    PlaygroundFlow(Scenario scenario, Instant now) {
        this.scenario = scenario;
        this.createdAt = now;
        this.expiresAt = now.plusSeconds(600);
    }

    public String id() { return id; }
    public Scenario scenario() { return scenario; }
    public Instant createdAt() { return createdAt; }
    public Instant expiresAt() { return expiresAt; }
    public boolean expired(Instant now) { return !expiresAt.isAfter(now); }
    public void capExpiry(Instant expiry) { if (expiry.isBefore(expiresAt)) expiresAt = expiry; }
    public Map<String, Object> tokens() { return tokens; }
    public void tokens(Map<String, Object> values, Instant now) {
        // Persist only protocol output needed by subsequent actions, never arbitrary response fields.
        var accepted = new java.util.LinkedHashMap<String, Object>();
        for (String key : new String[]{"access_token", "refresh_token", "id_token", "token_type", "scope", "expires_in"}) {
            Object value = values.get(key);
            if (value instanceof String || value instanceof Number) accepted.put(key, value);
        }
        tokens = Map.copyOf(accepted);
        if (values.get("expires_in") instanceof Number seconds) {
            capExpiry(now.plusSeconds(Math.max(0, Math.min(600, seconds.longValue()))));
        }
    }
    public void bind(String state, String nonce, String challenge) {
        this.state = state;
        this.nonce = nonce;
        this.challenge = challenge;
    }
    public String nonce() { return nonce; }
    public String challenge() { return challenge; }
    public boolean oidcVerified() { return oidcVerified; }
    public String subject() { return subject; }
    public void verified(String subject) { this.subject = subject; this.oidcVerified = true; }

    public void consumeCallback(String state) {
        if (callbackConsumed || this.state == null || !constantEquals(this.state, state)) {
            throw new IllegalArgumentException("Invalid or already consumed callback binding");
        }
        callbackConsumed = true;
        this.state = null;
    }

    public void device(String code, long interval, long expiresIn, Instant now) {
        deviceCode = code;
        pollIntervalSeconds = Math.max(5, Math.min(600, interval));
        nextPollAt = now.plusSeconds(pollIntervalSeconds);
        capExpiry(now.plusSeconds(Math.max(0, Math.min(600, expiresIn))));
    }

    public String deviceCode() { return deviceCode; }
    public long pollIntervalSeconds() { return pollIntervalSeconds; }
    public Instant nextPollAt() { return nextPollAt; }

    public void beginPoll(Instant now) {
        if (stopped || deviceCode == null || expired(now) || nextPollAt == null || now.isBefore(nextPollAt)) {
            throw new IllegalStateException("Device polling is stopped, expired or too early");
        }
        nextPollAt = now.plusSeconds(pollIntervalSeconds);
    }

    public void slowDown(Instant now) {
        pollIntervalSeconds = Math.min(600, pollIntervalSeconds + 5);
        nextPollAt = now.plusSeconds(pollIntervalSeconds);
    }

    public void stopPolling() { stopped = true; deviceCode = null; }

    public void clear() {
        state = null;
        nonce = null;
        challenge = null;
        tokens = Map.of();
        subject = null;
        oidcVerified = false;
        stopPolling();
    }

    private static boolean constantEquals(String expected, String actual) {
        return actual != null && java.security.MessageDigest.isEqual(
            expected.getBytes(java.nio.charset.StandardCharsets.UTF_8), actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
