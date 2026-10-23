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
package dev.macula.cloud.iam.message;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证邮箱验证码按 Client 隔离、摘要保存、限流并原子消费。
 *
 * @author Rain
 * @since 6.1.0
 */
class EmailVerificationCodeStoreTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final EmailVerificationProperties properties = new EmailVerificationProperties();
    private EmailVerificationCodeStore store;

    @BeforeEach
    void setUp() {
        properties.setDigestKey("fixture-email-digest-key");
        properties.setTtl(Duration.ofMinutes(5));
        properties.setSendInterval(Duration.ofMinutes(1));
        properties.setMaxClientSendsPerInterval(200);
        properties.setMaxIpSendsPerInterval(20);
        properties.setMaxAttempts(5);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), eq("1"), eq(Duration.ofMinutes(1)))).thenReturn(true);
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),
            any(java.util.List.class), anyString())).thenReturn(1L);
        store = new EmailVerificationCodeStore(redis, properties);
    }

    @Test
    void storesOnlyDigestInsideClientContext() {
        store.activate("user@example.test", "123456", "client-a");
        store.activate("user@example.test", "123456", "client-b");

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(values, org.mockito.Mockito.times(2)).set(keys.capture(), stored.capture(),
            eq(Duration.ofMinutes(5)));
        assertThat(keys.getAllValues()).doesNotHaveDuplicates()
            .allMatch(value -> !value.contains("user@example.test"));
        assertThat(stored.getAllValues()).doesNotHaveDuplicates()
            .allMatch(value -> !value.contains("123456") && !value.contains("user@example.test"));
    }

    @Test
    void issuesSixDigitCodeAndConsumesAtomically() {
        String code = store.issue("user@example.test", "127.0.0.1", "client-a");

        ArgumentCaptor<String> targetRateKey = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> ipRateKeys = ArgumentCaptor.forClass(List.class);
        assertThat(code).matches("[0-9]{6}");
        verify(values).setIfAbsent(targetRateKey.capture(), eq("1"), eq(Duration.ofMinutes(1)));
        verify(redis, org.mockito.Mockito.times(2)).execute(
            any(org.springframework.data.redis.core.script.RedisScript.class),
            ipRateKeys.capture(), eq("60000"));
        assertThat(targetRateKey.getValue()).startsWith("iam:email:send:")
            .doesNotContain("user@example.test");
        assertThat(ipRateKeys.getAllValues()).flatExtracting(value -> value)
            .anyMatch(value -> value.startsWith("iam:email:client:"))
            .anyMatch(value -> value.startsWith("iam:email:ip:"))
            .allMatch(value -> !value.contains("user@example.test") && !value.contains("127.0.0.1"));
        assertThat(store.consume("user@example.test", code, "client-a")).isTrue();
    }

    @Test
    void rejectsTargetRateLimitOverflow() {
        when(values.setIfAbsent(anyString(), eq("1"), eq(Duration.ofMinutes(1)))).thenReturn(false);

        assertThatThrownBy(() -> store.issue("user@example.test", "127.0.0.1", "client-a"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsClientRateLimitOverflow() {
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),
            any(java.util.List.class), anyString())).thenReturn(201L);

        assertThatThrownBy(() -> store.issue("user@example.test", "127.0.0.1", "client-a"))
            .isInstanceOf(IllegalStateException.class);
        verify(redis).delete(org.mockito.ArgumentMatchers.startsWith("iam:email:send:"));
    }
}
