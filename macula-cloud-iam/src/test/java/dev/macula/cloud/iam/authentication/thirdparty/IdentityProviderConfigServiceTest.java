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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    void loadsAdditionalSecretsWithoutMixingThemIntoPublicValues() {
        SysIdentityProvider provider = new SysIdentityProvider();
        provider.setProviderType("WECHAT");
        provider.setAppId("website-id");
        provider.setAppSecret("website-secret");
        provider.setConfigJson("{\"appId\":\"website-id\",\"officialAccountAppId\":\"official-id\"}");
        provider.setSecretConfigJson("{\"officialAccountAppSecret\":\"official-secret\"}");
        when(providers.selectList(any())).thenReturn(List.of(provider));

        IdentityProviderConfigService.ProviderConfig config = service.requireEnabled("WECHAT");

        assertEquals("official-id", config.require("officialAccountAppId"));
        assertEquals("official-secret", config.requireSecret("officialAccountAppSecret"));
        assertFalse(config.values().containsKey("officialAccountAppSecret"));
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
    void rejectsMalformedAdditionalSecrets() {
        SysIdentityProvider malformed = provider("secret", "{\"appId\":\"id\"}");
        malformed.setSecretConfigJson("not-json");
        SysIdentityProvider nonObject = provider("secret", "{\"appId\":\"id\"}");
        nonObject.setSecretConfigJson("[]");
        SysIdentityProvider unknown = provider("secret", "{\"appId\":\"id\"}");
        unknown.setSecretConfigJson("{\"unknownSecret\":\"value\"}");
        when(providers.selectList(any())).thenReturn(List.of(malformed), List.of(nonObject), List.of(unknown));

        assertThrows(AuthenticationServiceException.class, () -> service.requireEnabled("WECHAT"));
        assertThrows(AuthenticationServiceException.class, () -> service.requireEnabled("WECHAT"));
        assertThrows(AuthenticationServiceException.class, () -> service.requireEnabled("WECHAT"));
    }

    @Test
    void rejectsEnabledProviderWithIncompleteModeConfiguration() {
        SysIdentityProvider legacyWechat = new SysIdentityProvider();
        legacyWechat.setProviderType("WECHAT");
        legacyWechat.setAppId("website-id");
        legacyWechat.setAppSecret("website-secret");
        legacyWechat.setConfigJson("{\"appId\":\"website-id\"}");
        when(providers.selectList(any())).thenReturn(List.of(legacyWechat));

        assertThrows(AuthenticationServiceException.class, () -> service.requireEnabled("WECHAT"));
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
