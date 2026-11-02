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

import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService.ProviderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationServiceException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证飞书浏览器模式识别和受控跳转。
 *
 * @author Rain
 * @since 6.1.0
 */
class FeishuLoginModeTest {
    @Test
    void explicitPathWinsAndStandardBrowserAgentSelectsBrowserMode() {
        MockHttpServletRequest browser = new MockHttpServletRequest("GET", "/login/feishu/browser");
        browser.addHeader("User-Agent", "Lark/7.0");
        assertEquals(FeishuLoginMode.BROWSER, FeishuLoginMode.from(browser));
        MockHttpServletRequest internal = new MockHttpServletRequest("GET", "/login/feishu");
        internal.addHeader("User-Agent", "Mozilla/5.0 Feishu/7.0 Safari/537.36");
        assertEquals(FeishuLoginMode.BROWSER, FeishuLoginMode.from(internal));
        assertEquals(FeishuLoginMode.UNKNOWN,
            FeishuLoginMode.from(new MockHttpServletRequest("GET", "/login/feishu")));
    }

    @Test
    void browserUsesOfficialEndpointAndWebviewLoginIsRejected() {
        FeishuClient client = new FeishuClient();
        ProviderConfig config = new ProviderConfig("FEISHU", "app", "secret", Map.of("appId", "app"));
        String callback = "https://iam.example/login/feishu";
        assertTrue(client.authorizationUri(config, "state-value", callback, FeishuLoginMode.BROWSER)
            .startsWith("https://accounts.feishu.cn/open-apis/authen/v1/authorize?"));
        assertThrows(AuthenticationServiceException.class,
            () -> client.authorizationUri(config, "state-value", callback, FeishuLoginMode.WEBVIEW));
    }
}
