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
package dev.macula.cloud.iam.service.userdetails;

import dev.macula.cloud.system.security.UserIdentifierNormalizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证本地登录标识使用统一、不可由调用方覆盖的规范化规则。
 *
 * @author Rain
 * @since 6.1.0
 */
class LoginIdentifierNormalizerTest {
    private final UserIdentifierNormalizer normalizer = new UserIdentifierNormalizer();

    @Test
    void normalizesMobileAndPreservesInternationalPrefix() {
        assertThat(normalizer.mobile(" +86 138-0000-0000 ")).contains("+8613800000000");
        assertThat(normalizer.mobile("not-a-phone")).isEmpty();
    }

    @Test
    void normalizesEmailWithRootLocale() {
        assertThat(normalizer.email(" Sales.User@EXAMPLE.COM ")).contains("sales.user@example.com");
        assertThat(normalizer.email("invalid@example")).isEmpty();
    }
}
