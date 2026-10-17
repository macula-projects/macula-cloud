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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证短信验证码仅以带有效期的摘要保存，并执行双维度发送限流。
 *
 * @author Rain
 * @since 2026-10-17
 */
class SmsVerificationCodeStoreTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final SmsVerificationProperties properties = new SmsVerificationProperties();
    private SmsVerificationCodeStore store;

    @BeforeEach
    void setUp() {
        properties.setDigestKey("fixture-digest-key");
        properties.setTtl(Duration.ofMinutes(5));
        properties.setSendInterval(Duration.ofSeconds(60));
        properties.setMaxIpSendsPerInterval(20);
        properties.setMaxAttempts(5);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(60)))).thenReturn(true);
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),
            any(java.util.List.class), anyString())).thenReturn(1L);
        store = new SmsVerificationCodeStore(redis, properties);
    }

    @Test
    void issuesSixDigitCodeAfterPhoneAndIpLimitsPass() {
        String code = store.issue("13800000000", "127.0.0.1");

        assertThat(code).matches("[0-9]{6}");
        verify(values).setIfAbsent(org.mockito.ArgumentMatchers.startsWith("iam:sms:send:"),
            eq("1"), eq(Duration.ofSeconds(60)));
        verify(redis).execute(any(org.springframework.data.redis.core.script.RedisScript.class),
            any(java.util.List.class), eq("60000"));
    }

    @Test
    void rejectsWhenEitherRateLimitAlreadyExists() {
        when(values.setIfAbsent(org.mockito.ArgumentMatchers.startsWith("iam:sms:send:"),
            eq("1"), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> store.issue("13800000000", "127.0.0.1"))
            .isInstanceOf(IllegalStateException.class).hasMessage("发送过于频繁");
    }

    @Test
    void activatesDigestInsteadOfRawCodeWithTtlAndAttempts() {
        store.activate("13800000000", "123456");

        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(values).set(org.mockito.ArgumentMatchers.startsWith("iam:sms:code:"), stored.capture(),
            eq(Duration.ofMinutes(5)));
        assertThat(stored.getValue()).doesNotContain("123456").endsWith(":5");
    }

    @Test
    void rejectsWhenIpWindowExceedsItsSharedNatAllowance() {
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),
            any(java.util.List.class), anyString())).thenReturn(21L);

        assertThatThrownBy(() -> store.issue("13800000000", "127.0.0.1"))
            .isInstanceOf(IllegalStateException.class).hasMessage("发送过于频繁");
        verify(redis).delete(org.mockito.ArgumentMatchers.startsWith("iam:sms:send:"));
    }
}
