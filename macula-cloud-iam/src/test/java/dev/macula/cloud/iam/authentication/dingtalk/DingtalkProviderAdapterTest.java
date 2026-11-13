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
package dev.macula.cloud.iam.authentication.dingtalk;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyAuthenticationToken;
import dev.macula.cloud.iam.authentication.thirdparty.browser.ThirdPartyAuthenticationProvider;
import dev.macula.cloud.iam.authentication.thirdparty.browser.ThirdPartyIdentityResolutionService;
import dev.macula.cloud.iam.authentication.thirdparty.provider.ThirdPartyProviderAdapterRegistry;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService;
import dev.macula.cloud.system.enums.IdentitySourceType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.userdetails.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证钉钉组织成员 userId 通过统一身份解析器完成绑定登录。
 *
 * @author Rain
 * @since 6.1.0
 */
class DingtalkProviderAdapterTest {
    @Test
    void authenticatesOrganizationUserIdForBrowserMode() {
        DingtalkClient client = mock(DingtalkClient.class);
        IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
        ThirdPartyIdentityResolutionService identities = mock(ThirdPartyIdentityResolutionService.class);
        var config = IdentitySourceTestFixtures.source(
                IdentitySourceType.DINGTALK_WEB, "DINGTALK", "app", "secret",
                Map.of("appKey", "app", "corpId", "corp"));
        when(configs.requireEnabledById(1L, "DINGTALK")).thenReturn(config);
        var context = IdentitySourceTestFixtures.context("DINGTALK",
                DingtalkLoginMode.BROWSER.name(), "test-client",
                Instant.now(), "/");
        when(client.fetchUserId(config, "code", DingtalkLoginMode.BROWSER)).thenReturn("manager01");
        when(identities.resolve(
                IdentitySourceTestFixtures.browserIdentity(context,
                        "manager01"), context))
                .thenReturn(User.withUsername("sales").password("n/a").authorities("USER").build());
        var result = new ThirdPartyAuthenticationProvider(
                new ThirdPartyProviderAdapterRegistry(
                        List.of(new DingtalkProviderAdapter(client))), configs, identities).authenticate(
                ThirdPartyAuthenticationToken.unauthenticated("DINGTALK", "code", context,
                        "https://iam.example/login/dingtalk"));
        assertEquals("sales", result.getName());
    }

    @Test
    void ignoresTokensForAnotherProvider() {
        DingtalkClient client = mock(DingtalkClient.class);
        IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
        ThirdPartyIdentityResolutionService identities = mock(ThirdPartyIdentityResolutionService.class);
        var context = IdentitySourceTestFixtures.context("FEISHU",
                "BROWSER", "test-client", Instant.now(), "/");
        Assertions.assertThrows(
                AuthenticationServiceException.class,
                () -> new ThirdPartyAuthenticationProvider(
                        new ThirdPartyProviderAdapterRegistry(
                                List.of(new DingtalkProviderAdapter(client))), configs,
                        identities).authenticate(
                        ThirdPartyAuthenticationToken.unauthenticated("FEISHU", "code", context, "/login/feishu")));
        verifyNoInteractions(client, configs, identities);
    }
}
