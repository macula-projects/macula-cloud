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
package dev.macula.cloud.iam.authentication.thirdparty.browser;

import java.time.Instant;
import java.util.List;

import dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyAuthenticationToken;
import dev.macula.cloud.iam.authentication.thirdparty.provider.ThirdPartyProviderAdapter;
import dev.macula.cloud.iam.authentication.thirdparty.provider.ThirdPartyProviderAdapterRegistry;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.userdetails.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 统一编排在平台核验前检查当前来源，身份解析成功后才构造认证结果。
 *
 * @author Macula
 * @since 6.1.0
 */
class ThirdPartyAuthenticationProviderTest {
    /**
     * 每家厂商都执行来源、平台核验、本地身份解析的同一顺序。
     */
    @Test
    void checksSourceBeforeAdapterAndIdentityResolution() {
        for (String vendor : List.of("GITHUB", "WECOM", "FEISHU", "DINGTALK", "WECHAT")) {
            var adapter = mock(ThirdPartyProviderAdapter.class);
            when(adapter.providerType()).thenReturn(vendor);
            var configs = mock(IdentityProviderConfigService.class);
            var config = mock(IdentityProviderConfigService.ProviderConfig.class, Mockito.RETURNS_SELF);
            var identities = mock(ThirdPartyIdentityResolutionService.class);
            var context = IdentitySourceTestFixtures.context(vendor, vendor.equals("GITHUB") ? "" : "BROWSER",
                    "test-client", Instant.now(), "/");
            var identity = IdentitySourceTestFixtures.browserIdentity(context, "verified-subject");
            var user = User.withUsername("fixture-user").password("unused").authorities("USER").build();
            when(configs.requireEnabledById(context.registrationId(), vendor)).thenReturn(config);
            when(adapter.verify(config, "short-code", "/callback", context)).thenReturn(identity);
            when(identities.resolve(identity, context)).thenReturn(user);
            var provider = new ThirdPartyAuthenticationProvider(new ThirdPartyProviderAdapterRegistry(List.of(adapter)),
                    configs, identities);
            var result = provider.authenticate(
                    ThirdPartyAuthenticationToken.unauthenticated(vendor, "short-code", context, "/callback"));
            assertThat(result.isAuthenticated()).isTrue();
            assertThat(result.getName()).isEqualTo("fixture-user");
            var order = inOrder(configs, adapter, identities);
            order.verify(configs).requireEnabledById(context.registrationId(), vendor);
            order.verify(adapter).verify(config, "short-code", "/callback", context);
            order.verify(identities).resolve(identity, context);
        }
    }

    /**
     * 当前来源停用时不向平台提交Code，也不解析本地账号。
     */
    @Test
    void disabledSourceStopsBeforeExternalExchange() {
        var adapter = mock(ThirdPartyProviderAdapter.class);
        when(adapter.providerType()).thenReturn("GITHUB");
        var configs = mock(IdentityProviderConfigService.class);
        when(configs.requireEnabledById(any(), any())).thenThrow(new AuthenticationServiceException("disabled"));
        var identities = mock(ThirdPartyIdentityResolutionService.class);
        var context = IdentitySourceTestFixtures.context("GITHUB", "", "test-client", Instant.now(), "/");
        var provider = new ThirdPartyAuthenticationProvider(new ThirdPartyProviderAdapterRegistry(List.of(adapter)),
                configs, identities);
        Mockito.clearInvocations(adapter);
        assertThatThrownBy(() -> provider.authenticate(
                ThirdPartyAuthenticationToken.unauthenticated("GITHUB", "code", context, "/callback")))
                .isInstanceOf(AuthenticationServiceException.class);
        verifyNoInteractions(identities);
        Mockito.verify(adapter, Mockito.never()).verify(any(), any(), any(), any());
    }
}
