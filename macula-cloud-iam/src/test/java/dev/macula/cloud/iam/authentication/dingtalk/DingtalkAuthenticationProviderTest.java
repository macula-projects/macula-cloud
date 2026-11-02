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
 * 验证钉钉组织成员 userId 通过统一身份解析器完成绑定登录。
 *
 * @author Rain
 * @since 6.1.0
 */
class DingtalkAuthenticationProviderTest {
    @Test
    void authenticatesOrganizationUserIdForBrowserMode() {
        DingtalkClient client = mock(DingtalkClient.class);
        IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
        ThirdPartyIdentityResolutionService identities = mock(ThirdPartyIdentityResolutionService.class);
        var config = new IdentityProviderConfigService.ProviderConfig("DINGTALK", "app", "secret",
            Map.of("appKey", "app", "corpId", "corp"));
        when(configs.requireEnabled("DINGTALK")).thenReturn(config);
        var context = new ThirdPartyLoginContext("DINGTALK", DingtalkLoginMode.BROWSER.name(), "test-client",
            Instant.now(), "/");
        when(client.fetchUserId(config, "code", DingtalkLoginMode.BROWSER)).thenReturn("manager01");
        when(identities.resolve("DINGTALK", "manager01", context))
            .thenReturn(User.withUsername("sales").password("n/a").authorities("USER").build());
        var result = new DingtalkAuthenticationProvider(client, configs, identities).authenticate(
            ThirdPartyAuthenticationToken.unauthenticated("DINGTALK", "code", context,
                "https://iam.example/login/dingtalk"));
        assertEquals("sales", result.getName());
    }

    @Test
    void ignoresTokensForAnotherProvider() {
        DingtalkClient client = mock(DingtalkClient.class);
        IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
        ThirdPartyIdentityResolutionService identities = mock(ThirdPartyIdentityResolutionService.class);
        var context = new ThirdPartyLoginContext("FEISHU", "BROWSER", "test-client", Instant.now(), "/");
        assertNull(new DingtalkAuthenticationProvider(client, configs, identities).authenticate(
            ThirdPartyAuthenticationToken.unauthenticated("FEISHU", "code", context, "/login/feishu")));
        verifyNoInteractions(client, configs, identities);
    }
}
