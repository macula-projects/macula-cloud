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
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyLoginContext;
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
        var config = new IdentityProviderConfigService.ProviderConfig("FEISHU", "app", "secret",
            Map.of("appId", "app"));
        when(configs.requireEnabled("FEISHU")).thenReturn(config);
        var context = new ThirdPartyLoginContext("FEISHU", FeishuLoginMode.BROWSER.name(), "test-client",
            Instant.now(), "/");
        when(client.fetchEmployeeNo(config, "code", "https://iam.example/login/feishu", FeishuLoginMode.BROWSER))
            .thenReturn("E1001");
        when(identities.resolve("FEISHU", "E1001", context))
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
        var context = new ThirdPartyLoginContext("WECHAT", "BROWSER", "test-client", Instant.now(), "/");
        assertNull(new FeishuAuthenticationProvider(client, configs, identities).authenticate(
            ThirdPartyAuthenticationToken.unauthenticated("WECHAT", "code", context, "/login/wechat")));
        verifyNoInteractions(client, configs, identities);
    }
}
