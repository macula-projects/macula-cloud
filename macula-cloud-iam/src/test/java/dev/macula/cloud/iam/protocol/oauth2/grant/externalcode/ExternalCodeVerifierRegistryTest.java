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

import java.util.List;
import java.util.Set;

import dev.macula.cloud.system.enums.ExternalCodeType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证每个 external code type 只能路由到唯一 verifier，且不存在回退探测。
 *
 * @author Rain
 * @since 6.1.0
 */
class ExternalCodeVerifierRegistryTest {
    @Test
    void returnsTheSingleVerifierForAType() {
        ExternalCodeVerifier verifier = mock(ExternalCodeVerifier.class);
        when(verifier.supportedTypes()).thenReturn(Set.of(ExternalCodeType.WECOM_MINIAPP_CODE));
        ExternalCodeVerifierRegistry registry = new ExternalCodeVerifierRegistry(List.of(verifier));

        assertThat(registry.require(ExternalCodeType.WECOM_MINIAPP_CODE)).isSameAs(verifier);
        assertThatThrownBy(() -> registry.require(ExternalCodeType.FEISHU_APP_CODE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsDuplicateVerifierRegistration() {
        ExternalCodeVerifier first = mock(ExternalCodeVerifier.class);
        ExternalCodeVerifier second = mock(ExternalCodeVerifier.class);
        when(first.supportedTypes()).thenReturn(Set.of(ExternalCodeType.WECOM_MINIAPP_CODE));
        when(second.supportedTypes()).thenReturn(Set.of(ExternalCodeType.WECOM_MINIAPP_CODE));

        assertThatThrownBy(() -> new ExternalCodeVerifierRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 未实现专用合同的适配器不能默默忽略回调或PKCE扩展材料。
     */
    @Test
    void defaultAdapterRejectsUnexpectedProviderContext() {
        var verifier = mock(ExternalCodeVerifier.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        assertThatThrownBy(() -> verifier.verify(ExternalCodeType.WECOM_APP_CODE, 7L, "fixture-code", null,
                "https://app.example/callback"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                () -> verifier.verify(ExternalCodeType.WECOM_APP_CODE, 7L, "fixture-code", "p".repeat(43), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

}
