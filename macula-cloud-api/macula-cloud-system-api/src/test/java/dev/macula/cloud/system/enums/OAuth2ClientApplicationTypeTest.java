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
package dev.macula.cloud.system.enums;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 独立字面量验证应用、平台Code和网页环境的允许/拒绝矩阵。
 * @author Rain
 * @since 6.1.0
 */
class OAuth2ClientApplicationTypeTest {
    /** 对全部已知Code逐项固定允许架构，不由被测scene生成预期。 */
    @Test
    void codeMatrixRejectsCrossTerminalCredentials() {
        for (ExternalCodeType code : ExternalCodeType.values()) {
            Set<OAuth2ClientApplicationType> expected = switch (code.name()) {
                case "FEISHU_APP_CODE", "WECOM_APP_CODE", "DINGTALK_APP_CODE", "WECHAT_OPEN_APP_CODE", "ALIYUN_MOBILE_TOKEN" -> Set.of(OAuth2ClientApplicationType.NATIVE);
                case "FEISHU_WEBVIEW_CODE", "DINGTALK_WEBVIEW_CODE" -> Set.of(OAuth2ClientApplicationType.IN_APP);
                default -> Set.of(OAuth2ClientApplicationType.MINIAPP);
            };
            String platform = code.name().startsWith("WECOM_") ? "WECOM" : "WECHAT";
            for (OAuth2ClientApplicationType type : OAuth2ClientApplicationType.values()) {
                assertEquals(expected.contains(type), type.supportsExternalCode(code, platform), type + ":" + code);
            }
        }
        assertFalse(OAuth2ClientApplicationType.MINIAPP.supportsExternalCode(ExternalCodeType.WECOM_MINIAPP_CODE, "WECHAT"));
        assertFalse(OAuth2ClientApplicationType.MINIAPP.supportsExternalCode(ExternalCodeType.WECHAT_MINIAPP_LOGIN_CODE, "WECOM"));
        assertFalse(OAuth2ClientApplicationType.NATIVE.supportsExternalCode(null, null));
    }

    /** 网站、公众号与原生SDK分别约束，UA识别不产生认证身份。 */
    @Test
    void browserMatrixSeparatesDesktopMobileAndContainers() {
        assertFalse(IdentitySourceType.WECHAT_WEBSITE.supportsBrowserEnvironment(IdentityBrowserEnvironment.MOBILE_BROWSER, "BROWSER"));
        assertTrue(IdentitySourceType.WECHAT_WEBSITE.supportsBrowserEnvironment(IdentityBrowserEnvironment.DESKTOP_BROWSER, "BROWSER"));
        assertTrue(IdentitySourceType.GITHUB_OAUTH.supportsBrowserEnvironment(IdentityBrowserEnvironment.MOBILE_BROWSER, ""));
        assertTrue(IdentitySourceType.WECOM_WEB.supportsBrowserEnvironment(IdentityBrowserEnvironment.WECOM_WEBVIEW, "WEBVIEW"));
        assertFalse(IdentitySourceType.WECOM_WEB.supportsBrowserEnvironment(IdentityBrowserEnvironment.WECOM_WEBVIEW, "BROWSER"));
        assertFalse(IdentitySourceType.WECOM_WEB.supportsBrowserEnvironment(IdentityBrowserEnvironment.MOBILE_BROWSER, "BROWSER"));
        assertTrue(IdentitySourceType.FEISHU_WEB.supportsBrowserEnvironment(IdentityBrowserEnvironment.MOBILE_BROWSER, "BROWSER"));
        assertTrue(IdentitySourceType.FEISHU_WEB.supportsBrowserEnvironment(IdentityBrowserEnvironment.FEISHU_WEBVIEW, "BROWSER"));
        assertEquals(IdentitySourceFlow.FEISHU_APPLINK_V3, IdentitySourceFlow.defaultFor(IdentitySourceType.FEISHU_APPLINK));
        assertEquals(IdentitySourceFlow.FEISHU_WEBVIEW_V3, IdentitySourceFlow.defaultFor(IdentitySourceType.FEISHU_WEBVIEW));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> IdentitySourceFlow.resolve(IdentitySourceType.FEISHU_WEBVIEW, "FEISHU_WEBVIEW_V1"));
        assertTrue(IdentitySourceType.WECHAT_OFFICIAL_ACCOUNT.supportsBrowserEnvironment(IdentityBrowserEnvironment.WECHAT_WEBVIEW, "WEBVIEW"));
        assertFalse(OAuth2ClientApplicationType.NATIVE.supportsBrowserSource(IdentitySourceType.WECHAT_OFFICIAL_ACCOUNT));
        assertFalse(OAuth2ClientApplicationType.SPA.supportsBrowserSource(IdentitySourceType.WECHAT_OFFICIAL_ACCOUNT));
        assertFalse(OAuth2ClientApplicationType.NATIVE.supportsBrowserSource(IdentitySourceType.GITHUB_OAUTH));
        assertFalse(OAuth2ClientApplicationType.MINIAPP.supportsBrowserSource(IdentitySourceType.GITHUB_OAUTH));
        assertFalse(OAuth2ClientApplicationType.SERVICE.supportsBrowserSource(IdentitySourceType.GITHUB_OAUTH));
        assertEquals(IdentityBrowserEnvironment.WECOM_WEBVIEW, IdentityBrowserEnvironment.fromUserAgent("Mobile MicroMessenger wxwork"));
        assertEquals(IdentityBrowserEnvironment.MOBILE_BROWSER, IdentityBrowserEnvironment.fromUserAgent("Android Chrome Mobile"));
        assertEquals(IdentityBrowserEnvironment.WECHAT_WEBVIEW, IdentityBrowserEnvironment.fromUserAgent("iPhone MicroMessenger"));
    }
}
