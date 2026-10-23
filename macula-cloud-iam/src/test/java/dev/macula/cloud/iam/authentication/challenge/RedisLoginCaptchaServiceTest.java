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
package dev.macula.cloud.iam.authentication.challenge;

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
 * 验证 CAPTCHA 使用不可预测 ID、摘要存储、一次性消费和限流。
 *
 * @author Rain
 * @since 6.1.0
 */
class RedisLoginCaptchaServiceTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final LoginCaptchaProperties properties = new LoginCaptchaProperties();
    private RedisLoginCaptchaService service;

    @BeforeEach
    void setUp() {
        properties.setDigestKey("fixture-digest-key");
        properties.setTtl(Duration.ofMinutes(2));
        properties.setRateWindow(Duration.ofMinutes(1));
        properties.setMaxClientIssuesPerWindow(200);
        properties.setMaxIssuesPerWindow(20);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),
            any(java.util.List.class), anyString())).thenReturn(1L);
        service = new RedisLoginCaptchaService(redis, properties);
    }

    @Test
    void storesOnlyDigestUnderNonRawKey() {
        LoginCaptchaService.CaptchaChallenge challenge =
            service.issue("client-a", "https://app.example.test", "127.0.0.1");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> storedValue = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> rateKeys = ArgumentCaptor.forClass(List.class);
        verify(values).set(key.capture(), storedValue.capture(), eq(Duration.ofMinutes(2)));
        verify(redis, org.mockito.Mockito.times(2)).execute(
            any(org.springframework.data.redis.core.script.RedisScript.class),
            rateKeys.capture(), eq("60000"));
        assertThat(challenge.id()).hasSizeGreaterThan(24);
        assertThat(challenge.prompt()).contains("+");
        assertThat(key.getValue()).startsWith("iam:captcha:").doesNotContain(challenge.id());
        assertThat(storedValue.getValue()).doesNotContain("client-a", "https://app.example.test");
        assertThat(rateKeys.getAllValues()).flatExtracting(rateKey -> rateKey)
            .anyMatch(rateKey -> rateKey.startsWith("iam:captcha:client:"))
            .anyMatch(rateKey -> rateKey.startsWith("iam:captcha:rate:"))
            .allMatch(rateKey -> !rateKey.contains("client-a") && !rateKey.contains("https://app.example.test")
                && !rateKey.contains("127.0.0.1"));
    }

    @Test
    void consumesInsideClientAndOriginContext() {
        assertThat(service.consume("challenge", "2", "client-a", "https://app.example.test")).isTrue();
        verify(redis).execute(any(org.springframework.data.redis.core.script.RedisScript.class),
            any(java.util.List.class), anyString());
    }

    @Test
    void rejectsRateLimitOverflow() {
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),
            any(java.util.List.class), anyString())).thenReturn(21L);

        assertThatThrownBy(() -> service.issue("client-a", "https://app.example.test", "127.0.0.1"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsClientRateLimitOverflow() {
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),
            any(java.util.List.class), anyString())).thenReturn(201L);

        assertThatThrownBy(() -> service.issue("client-a", "https://app.example.test", "127.0.0.1"))
            .isInstanceOf(IllegalStateException.class);
    }
}
