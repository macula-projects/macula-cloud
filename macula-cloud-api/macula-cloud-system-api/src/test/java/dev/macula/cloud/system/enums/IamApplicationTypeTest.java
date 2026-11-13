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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 独立字面量验证应用、平台Code和网页环境的允许/拒绝矩阵。
 * @author Rain
 * @since 6.1.0
 */
class IamApplicationTypeTest {
    /** 对全部已知Code逐项固定允许架构，不由被测scene生成预期。 */
    @Test
    void codeMatrixRejectsCrossTerminalCredentials() {
        for (ExternalCodeType code : ExternalCodeType.values()) {
            Set<IamApplicationType> expected = switch (code.name()) {
                case "FEISHU_APP_CODE", "WECOM_APP_CODE", "DINGTALK_APP_CODE", "WECHAT_OPEN_APP_CODE", "ALIYUN_MOBILE_TOKEN" -> Set.of(IamApplicationType.NATIVE);
                case "FEISHU_WEBVIEW_CODE", "DINGTALK_WEBVIEW_CODE" -> Set.of();
                default -> Set.of(IamApplicationType.MINIAPP);
            };
            String platform = code.name().startsWith("WECOM_") ? "WECOM" : "WECHAT";
            for (IamApplicationType type : IamApplicationType.values()) {
                assertEquals(expected.contains(type), type.supportsExternalCode(code,  platform, null), type + ":" + code);
            }
        }
        for (var type : Set.of(IamApplicationType.WEB_SERVER, IamApplicationType.SPA)) {
            assertTrue(type.supportsExternalCode(ExternalCodeType.FEISHU_WEBVIEW_CODE, null, "IN_APP"));
            assertTrue(type.supportsBrowserSource(IdentitySourceType.WECHAT_OFFICIAL_ACCOUNT, "IN_APP"));
            assertFalse(type.supportsBrowserSource(IdentitySourceType.GITHUB_OAUTH, "IN_APP"));
            assertFalse(type.supportsHostedLogin("IN_APP"));
            assertFalse(type.supportsGrant("refresh_token", "IN_APP"));
        }
        assertFalse(IamApplicationType.MINIAPP.supportsExternalCode(ExternalCodeType.WECOM_MINIAPP_CODE,  "WECHAT", null));
        assertFalse(IamApplicationType.MINIAPP.supportsExternalCode(ExternalCodeType.WECHAT_MINIAPP_LOGIN_CODE,  "WECOM", null));
        assertFalse(IamApplicationType.NATIVE.supportsExternalCode(null,  null, null));
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
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> IdentitySourceFlow.resolve(IdentitySourceType.FEISHU_WEBVIEW, "FEISHU_WEBVIEW_V1"));
        assertTrue(IdentitySourceType.WECHAT_OFFICIAL_ACCOUNT.supportsBrowserEnvironment(IdentityBrowserEnvironment.WECHAT_WEBVIEW, "WEBVIEW"));
        assertFalse(IamApplicationType.NATIVE.supportsBrowserSource(IdentitySourceType.WECHAT_OFFICIAL_ACCOUNT, null));
        assertFalse(IamApplicationType.SPA.supportsBrowserSource(IdentitySourceType.WECHAT_OFFICIAL_ACCOUNT, null));
        assertFalse(IamApplicationType.NATIVE.supportsBrowserSource(IdentitySourceType.GITHUB_OAUTH, null));
        assertFalse(IamApplicationType.MINIAPP.supportsBrowserSource(IdentitySourceType.GITHUB_OAUTH, null));
        assertFalse(IamApplicationType.SERVICE.supportsBrowserSource(IdentitySourceType.GITHUB_OAUTH, null));
        assertEquals(IdentityBrowserEnvironment.WECOM_WEBVIEW, IdentityBrowserEnvironment.fromUserAgent("Mobile MicroMessenger wxwork"));
        assertEquals(IdentityBrowserEnvironment.MOBILE_BROWSER, IdentityBrowserEnvironment.fromUserAgent("Android Chrome Mobile"));
        assertEquals(IdentityBrowserEnvironment.WECHAT_WEBVIEW, IdentityBrowserEnvironment.fromUserAgent("iPhone MicroMessenger"));
    }
}
