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

import dev.macula.cloud.iam.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.iam.mapper.SysUserIdentityBindingMapper;
import dev.macula.cloud.iam.pojo.entity.SysIdentityProvider;
import dev.macula.cloud.iam.pojo.entity.SysUserIdentityBinding;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationServiceException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 验证第三方配置与全局账号绑定在缺失、重复或损坏时失败关闭。
 *
 * @author Rain
 * @since 6.1.0
 */
class IdentityProviderConfigServiceTest {
    private final SysIdentityProviderMapper providers = mock(SysIdentityProviderMapper.class);
    private final SysUserIdentityBindingMapper bindings = mock(SysUserIdentityBindingMapper.class);
    private final IdentityProviderConfigService service = new IdentityProviderConfigService(providers, bindings);

    @Test
    void loadsExactlyOneEnabledProviderWithoutExposingFallbacks() {
        SysIdentityProvider provider = new SysIdentityProvider();
        provider.setProviderType("GITHUB");
        provider.setAppId("legacy-id");
        provider.setAppSecret("decrypted-secret");
        provider.setConfigJson("{\"clientId\":\"client-id\"}");
        when(providers.selectList(any())).thenReturn(List.of(provider));

        IdentityProviderConfigService.ProviderConfig config = service.requireEnabled("GITHUB");

        assertEquals("client-id", config.require("clientId"));
        assertEquals("decrypted-secret", config.appSecret());
    }

    @Test
    void rejectsMissingDuplicateAndMalformedEnabledProviders() {
        when(providers.selectList(any())).thenReturn(List.of());
        assertThrows(AuthenticationServiceException.class, () -> service.requireEnabled("GITHUB"));

        SysIdentityProvider first = provider("secret", "{\"clientId\":\"one\"}");
        SysIdentityProvider second = provider("secret", "{\"clientId\":\"two\"}");
        when(providers.selectList(any())).thenReturn(List.of(first, second), List.of(provider("secret", "not-json")));
        assertThrows(AuthenticationServiceException.class, () -> service.requireEnabled("GITHUB"));
        assertThrows(AuthenticationServiceException.class, () -> service.requireEnabled("GITHUB"));
    }

    @Test
    void visibilityFailuresAreClosed() {
        when(providers.selectCount(any())).thenReturn(1L, 0L).thenThrow(new IllegalStateException("db unavailable"));

        assertTrue(service.isVisible("GITHUB"));
        assertFalse(service.isVisible("GITHUB"));
        assertFalse(service.isVisible("GITHUB"));
    }

    @Test
    void requiresOneGlobalUsernameBinding() {
        SysUserIdentityBinding binding = new SysUserIdentityBinding();
        binding.setProviderType("GITHUB");
        binding.setProviderSubject("12345");
        binding.setUsername("alice");
        when(bindings.selectList(any())).thenReturn(List.of(binding), List.of(), List.of(binding, binding));

        assertEquals("alice", service.requireBoundUsername("GITHUB", "12345"));
        assertThrows(AuthenticationServiceException.class,
            () -> service.requireBoundUsername("GITHUB", "12345"));
        assertThrows(AuthenticationServiceException.class,
            () -> service.requireBoundUsername("GITHUB", "12345"));
    }

    private SysIdentityProvider provider(String secret, String json) {
        SysIdentityProvider provider = new SysIdentityProvider();
        provider.setAppSecret(secret);
        provider.setConfigJson(json);
        return provider;
    }
}
