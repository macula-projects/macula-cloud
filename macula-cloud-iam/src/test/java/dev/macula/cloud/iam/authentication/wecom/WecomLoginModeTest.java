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
package dev.macula.cloud.iam.authentication.wecom;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证企业微信环境识别仅用于选择入口，不作为回调凭据。
 *
 * @author Rain
 * @since 6.1.0
 */
class WecomLoginModeTest {
    @Test
    void explicitPathsWinOverUserAgentAndUnknownNeedsAChoice() {
        MockHttpServletRequest browser = new MockHttpServletRequest("GET", "/login/wecom/browser");
        browser.addHeader("User-Agent", "wxwork/4.0");
        assertEquals(WecomLoginMode.BROWSER, WecomLoginMode.from(browser));
        assertEquals(WecomLoginMode.WEBVIEW,
            WecomLoginMode.from(new MockHttpServletRequest("GET", "/login/wecom/webview")));
        assertEquals(WecomLoginMode.UNKNOWN,
            WecomLoginMode.from(new MockHttpServletRequest("GET", "/login/wecom")));
        MockHttpServletRequest internal = new MockHttpServletRequest("GET", "/login/wecom");
        internal.addHeader("User-Agent", "Mozilla/5.0 wxwork/4.0");
        assertEquals(WecomLoginMode.WEBVIEW, WecomLoginMode.from(internal));
    }

    @Test
    void buildsCorpAppAndWebviewUrlsWithTheSameCallback() {
        WecomClient client = new WecomClient();
        var config = new dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService.ProviderConfig(
            "WECOM", "corp", "secret", Map.of("corpId", "corp", "agentId", "agent"));
        String callback = "https://iam.example/login/wecom";
        String browser = client.authorizationUri(config, "state-value", callback, WecomLoginMode.BROWSER);
        assertTrue(browser.startsWith("https://login.work.weixin.qq.com/wwlogin/sso/login?"));
        assertTrue(browser.contains("login_type=CorpApp"));
        assertTrue(browser.contains("appid=corp"));
        assertTrue(browser.contains("agentid=agent"));
        assertTrue(browser.contains("state=state-value"));
        assertTrue(browser.contains("redirect_uri=https://iam.example/login/wecom"));
        String webview = client.authorizationUri(config, "state-value", callback, WecomLoginMode.WEBVIEW);
        assertTrue(webview.startsWith("https://open.weixin.qq.com/connect/oauth2/authorize?"));
        assertTrue(webview.contains("scope=snsapi_base"));
    }
}
