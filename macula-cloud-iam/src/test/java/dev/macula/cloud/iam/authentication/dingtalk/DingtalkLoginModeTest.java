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

import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService.ProviderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationServiceException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证钉钉浏览器模式识别和受控跳转。
 *
 * @author Rain
 * @since 6.1.0
 */
class DingtalkLoginModeTest {
    @Test
    void explicitPathWinsAndStandardBrowserAgentSelectsBrowserMode() {
        MockHttpServletRequest browser = new MockHttpServletRequest("GET", "/login/dingtalk/browser");
        browser.addHeader("User-Agent", "DingTalk/7.0");
        assertEquals(DingtalkLoginMode.BROWSER, DingtalkLoginMode.from(browser));
        MockHttpServletRequest internal = new MockHttpServletRequest("GET", "/login/dingtalk");
        internal.addHeader("User-Agent", "Mozilla/5.0 DingTalk/7.0 Safari/537.36");
        assertEquals(DingtalkLoginMode.BROWSER, DingtalkLoginMode.from(internal));
        assertEquals(DingtalkLoginMode.UNKNOWN,
            DingtalkLoginMode.from(new MockHttpServletRequest("GET", "/login/dingtalk")));
    }

    @Test
    void browserUsesOfficialEndpointAndWebviewLoginIsRejected() {
        DingtalkClient client = new DingtalkClient();
        ProviderConfig config = new ProviderConfig("DINGTALK", "app", "secret", Map.of(
            "appKey", "app", "corpId", "corp"));
        String callback = "https://iam.example/login/dingtalk";
        assertTrue(client.authorizationUri(config, "state-value", callback, DingtalkLoginMode.BROWSER)
            .startsWith("https://login.dingtalk.com/oauth2/auth?"));
        assertThrows(AuthenticationServiceException.class,
            () -> client.authorizationUri(config, "state-value", callback, DingtalkLoginMode.WEBVIEW));
    }
}
