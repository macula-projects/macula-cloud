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
package dev.macula.cloud.iam.authentication.wechat;

import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyAuthenticationToken;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyIdentityResolutionService;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyLoginContext;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证微信两种模式以同一 unionid 通过统一身份解析器登录。
 *
 * @author Rain
 * @since 6.1.0
 */
class WechatAuthenticationProviderTest {
    @Test
    void authenticatesTheSameUnionIdForBothModes() {
        WechatClient client = mock(WechatClient.class);
        IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
        ThirdPartyIdentityResolutionService identities = mock(ThirdPartyIdentityResolutionService.class);
        var config = new IdentityProviderConfigService.ProviderConfig("WECHAT", "site", "site-secret",
            Map.of("appId", "site", "officialAccountAppId", "oa"),
            Map.of("officialAccountAppSecret", "oa-secret"));
        when(configs.requireEnabled("WECHAT")).thenReturn(config);
        for (WechatLoginMode mode : new WechatLoginMode[] {WechatLoginMode.BROWSER, WechatLoginMode.WEBVIEW}) {
            var context = new ThirdPartyLoginContext("WECHAT", mode.name(), "test-client", Instant.now(), "/");
            when(client.fetchUnionId(config, "code", mode)).thenReturn("union-01");
            when(identities.resolve("WECHAT", "union-01", context))
                .thenReturn(User.withUsername("sales").password("n/a").authorities("USER").build());
            var result = new WechatAuthenticationProvider(client, configs, identities).authenticate(
                ThirdPartyAuthenticationToken.unauthenticated("WECHAT", "code", context,
                    "https://iam.example/login/wechat"));
            assertEquals("sales", result.getName());
        }
    }

    @Test
    void ignoresTokensForAnotherProvider() {
        WechatClient client = mock(WechatClient.class);
        IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
        ThirdPartyIdentityResolutionService identities = mock(ThirdPartyIdentityResolutionService.class);
        var context = new ThirdPartyLoginContext("DINGTALK", "BROWSER", "test-client", Instant.now(), "/");
        assertNull(new WechatAuthenticationProvider(client, configs, identities).authenticate(
            ThirdPartyAuthenticationToken.unauthenticated("DINGTALK", "code", context, "/login/dingtalk")));
        verifyNoInteractions(client, configs, identities);
    }
}
