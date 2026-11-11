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
package dev.macula.cloud.iam.authentication.thirdparty.provider;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationServiceException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 适配器注册必须唯一且精确，不能由顺序或默认厂商改变身份来源。
 *
 * @author Macula
 * @since 6.1.0
 */
class ThirdPartyProviderAdapterRegistryTest {
    /**
     * 唯一键可解析，未知键失败关闭。
     */
    @Test
    void resolvesOnlyExactKeys() {
        var adapter = mock(ThirdPartyProviderAdapter.class);
        when(adapter.providerType()).thenReturn("GITHUB");
        var registry = new ThirdPartyProviderAdapterRegistry(List.of(adapter));
        assertThat(registry.require("GITHUB")).isSameAs(adapter);
        assertThatThrownBy(() -> registry.require("WECHAT")).isInstanceOf(AuthenticationServiceException.class);
    }

    /**
     * 重复注册应阻止启动，而不是选择列表第一项。
     */
    @Test
    void rejectsDuplicateRegistrations() {
        var adapter = mock(ThirdPartyProviderAdapter.class);
        when(adapter.providerType()).thenReturn("GITHUB");
        assertThatThrownBy(() -> new ThirdPartyProviderAdapterRegistry(List.of(adapter, adapter)))
                .isInstanceOf(IllegalStateException.class);
    }
}
