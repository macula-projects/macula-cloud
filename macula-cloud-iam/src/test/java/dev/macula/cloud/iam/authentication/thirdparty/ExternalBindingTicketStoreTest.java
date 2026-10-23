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

import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeSecurityProperties;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeVerification;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

/**
 * 验证绑定 ticket、手机号和第三方主体不会以明文进入 Redis 调用参数。
 *
 * @author Rain
 * @since 6.1.0
 */
class ExternalBindingTicketStoreTest {
    @Test
    void storesSensitiveBindingValuesAsCiphertextBehindDigestKey() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class, invocation -> {
            if ("execute".equals(invocation.getMethod().getName())) {
                return 1L;
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        ExternalCodeSecurityProperties properties = new ExternalCodeSecurityProperties();
        properties.setDigestKey("fixture-binding-digest-key");
        ExternalBindingTicketStore store = new ExternalBindingTicketStore(redis, properties);

        String mobileTicket = store.issue("client-a", 1L, UserTypeEnum.EMPLOYEE,
            ExternalCodeVerification.mobile("13800000000"));
        String identityTicket = store.issue("client-a", 1L, UserTypeEnum.EMPLOYEE,
            ExternalCodeVerification.identity("WECOM", "member-sensitive-subject"));

        String redisArguments = mockingDetails(redis).getInvocations().stream()
            .filter(invocation -> "execute".equals(invocation.getMethod().getName()))
            .flatMap(invocation -> Arrays.stream(invocation.getArguments()))
            .flatMap(ExternalBindingTicketStoreTest::strings)
            .reduce("", (left, right) -> left + '\n' + right);
        assertThat(redisArguments)
            .doesNotContain("13800000000", "member-sensitive-subject", mobileTicket, identityTicket);
    }

    private static Stream<String> strings(Object value) {
        if (value instanceof Object[] values) {
            return Arrays.stream(values).flatMap(ExternalBindingTicketStoreTest::strings);
        }
        if (value instanceof Iterable<?> values) {
            return java.util.stream.StreamSupport.stream(values.spliterator(), false)
                .flatMap(ExternalBindingTicketStoreTest::strings);
        }
        return Stream.of(String.valueOf(value));
    }
}
