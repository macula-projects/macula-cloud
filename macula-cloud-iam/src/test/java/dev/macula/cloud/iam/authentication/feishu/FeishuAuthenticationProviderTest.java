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
package dev.macula.cloud.iam.authentication.feishu;

import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyAuthenticationToken;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyIdentityResolutionService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证飞书 employee_no 通过统一身份解析器完成绑定登录。
 *
 * @author Rain
 * @since 6.1.0
 */
class FeishuAuthenticationProviderTest {
    @Test
    void authenticatesEmployeeNumberForBrowserMode() {
        FeishuClient client = mock(FeishuClient.class);
        IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
        ThirdPartyIdentityResolutionService identities = mock(ThirdPartyIdentityResolutionService.class);
        var config = dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.source(dev.macula.cloud.system.enums.IdentitySourceType.FEISHU_WEB, "FEISHU", "app", "secret",
            Map.of("appId", "app"));
        when(configs.requireEnabledById(1L, "FEISHU")).thenReturn(config);
        var context = dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.context("FEISHU", FeishuLoginMode.BROWSER.name(), "test-client",
            Instant.now(), "/");
        when(client.fetchIdentity(config, "code", "https://iam.example/login/feishu", FeishuLoginMode.BROWSER, "BROWSER"))
            .thenReturn(dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity.of(config, "BROWSER",
                "OPEN_ID", "E1001", Map.of()));
        when(identities.resolve(dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.browserIdentity(context, "E1001"), context))
            .thenReturn(User.withUsername("sales").password("n/a").authorities("USER").build());
        var result = new FeishuAuthenticationProvider(client, configs, identities).authenticate(
            ThirdPartyAuthenticationToken.unauthenticated("FEISHU", "code", context,
                "https://iam.example/login/feishu"));
        assertEquals("sales", result.getName());
    }

    @Test
    void ignoresTokensForAnotherProvider() {
        FeishuClient client = mock(FeishuClient.class);
        IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
        ThirdPartyIdentityResolutionService identities = mock(ThirdPartyIdentityResolutionService.class);
        var context = dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.context("WECHAT", "BROWSER", "test-client", Instant.now(), "/");
        assertNull(new FeishuAuthenticationProvider(client, configs, identities).authenticate(
            ThirdPartyAuthenticationToken.unauthenticated("WECHAT", "code", context, "/login/wechat")));
        verifyNoInteractions(client, configs, identities);
    }
}
