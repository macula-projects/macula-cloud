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
package dev.macula.cloud.iam.protocol.oauth2.grant.externalcode;

import dev.macula.cloud.system.enums.ExternalCodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证外部凭证防重放只存储 HMAC 摘要并原子拒绝重复使用。
 *
 * @author Rain
 * @since 6.1.0
 */
class ExternalCodeReplayStoreTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final ExternalCodeSecurityProperties properties = new ExternalCodeSecurityProperties();
    private ExternalCodeReplayStore store;

    @BeforeEach
    void setUp() {
        properties.setDigestKey("fixture-digest-key");
        properties.setReplayTtl(Duration.ofMinutes(10));
        when(redis.opsForValue()).thenReturn(values);
        store = new ExternalCodeReplayStore(redis, properties);
    }

    @Test
    void storesOnlyDigestWithConfiguredTtl() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        ExternalCodeReplayStore.ReplayClaim claim =
            store.claim(ExternalCodeType.WECOM_MINIAPP_CODE, "raw-sensitive-code");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> owner = ArgumentCaptor.forClass(String.class);
        verify(values).setIfAbsent(key.capture(), owner.capture(), eq(Duration.ofMinutes(10)));
        assertThat(key.getValue()).startsWith("iam:external-code:replay:")
            .doesNotContain("raw-sensitive-code", ExternalCodeType.WECOM_MINIAPP_CODE.name());
        assertThat(owner.getValue()).isEqualTo(claim.owner()).doesNotContain("raw-sensitive-code");
    }

    @Test
    void rejectsAnAlreadyClaimedCredential() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        OAuth2AuthenticationException error = catchThrowableOfType(
            () -> store.claim(ExternalCodeType.WECOM_MINIAPP_CODE, "duplicate-code"),
            OAuth2AuthenticationException.class);

        assertThat(error.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_GRANT);
    }

    @Test
    void releasesOnlyThroughOwnerCheckedScript() {
        ExternalCodeReplayStore.ReplayClaim claim =
            new ExternalCodeReplayStore.ReplayClaim("digest-key", "owner-nonce");

        store.release(claim);

        verify(redis).execute(any(org.springframework.data.redis.core.script.RedisScript.class),
            eq(java.util.List.of("digest-key")), eq("owner-nonce"));
    }
}
