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

import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService.ProviderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证微信环境识别及网站应用、公众号授权入口。
 *
 * @author Rain
 * @since 6.1.0
 */
class WechatLoginModeTest {
    @Test
    void explicitPathsWinAndWechatAgentSelectsWebview() {
        MockHttpServletRequest browser = new MockHttpServletRequest("GET", "/login/wechat/browser");
        browser.addHeader("User-Agent", "MicroMessenger/8.0");
        assertEquals(WechatLoginMode.BROWSER, WechatLoginMode.from(browser));
        MockHttpServletRequest internal = new MockHttpServletRequest("GET", "/login/wechat");
        internal.addHeader("User-Agent", "Mozilla/5.0 MicroMessenger/8.0");
        assertEquals(WechatLoginMode.WEBVIEW, WechatLoginMode.from(internal));
        assertEquals(WechatLoginMode.UNKNOWN,
            WechatLoginMode.from(new MockHttpServletRequest("GET", "/login/wechat")));
    }

    @Test
    void bothModesUseTheSameFixedCallback() {
        WechatClient client = new WechatClient();
        ProviderConfig config = new ProviderConfig("WECHAT", "site-app", "site-secret",
            Map.of("appId", "site-app", "officialAccountAppId", "oa-app"),
            Map.of("officialAccountAppSecret", "oa-secret"));
        String callback = "https://iam.example/login/wechat";
        String browser = client.authorizationUri(config, "state-value", callback, WechatLoginMode.BROWSER);
        String webview = client.authorizationUri(config, "state-value", callback, WechatLoginMode.WEBVIEW);
        assertTrue(browser.startsWith("https://open.weixin.qq.com/connect/qrconnect?"));
        assertTrue(browser.contains("scope=snsapi_login"));
        assertTrue(webview.startsWith("https://open.weixin.qq.com/connect/oauth2/authorize?"));
        assertTrue(webview.contains("scope=snsapi_base"));
        assertTrue(browser.contains("redirect_uri=https://iam.example/login/wechat"));
        assertTrue(webview.contains("redirect_uri=https://iam.example/login/wechat"));
    }
}
