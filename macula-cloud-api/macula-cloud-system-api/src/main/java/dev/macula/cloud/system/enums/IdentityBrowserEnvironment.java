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

import java.util.Locale;

/**
 * 第三方网页交互环境，只用于体验选择，不作为身份、组织或权限证明。
 * @author Rain
 * @since 6.1.0
 */
public enum IdentityBrowserEnvironment {
    DESKTOP_BROWSER, MOBILE_BROWSER, WECHAT_WEBVIEW, WECOM_WEBVIEW, FEISHU_WEBVIEW, DINGTALK_WEBVIEW;

    /**
     * 按明确容器标记优先识别，再区分普通手机浏览器；无标记沿用桌面入口兼容。
     * @param userAgent 请求声明的浏览器标记，可为空
     * @return 体验环境，最终身份仍必须由平台Code和服务端state证明
     */
    public static IdentityBrowserEnvironment fromUserAgent(String userAgent) {
        String agent = userAgent == null ? "" : userAgent.toLowerCase(Locale.ROOT);
        if (agent.contains("wxwork")) { return WECOM_WEBVIEW; }
        if (agent.contains("micromessenger")) { return WECHAT_WEBVIEW; }
        if (agent.contains("dingtalk")) { return DINGTALK_WEBVIEW; }
        if (agent.contains("lark") || agent.contains("feishu")) { return FEISHU_WEBVIEW; }
        if (agent.contains("mobile") || agent.contains("android") || agent.contains("iphone")
            || agent.contains("ipad") || agent.contains("ipod")) { return MOBILE_BROWSER; }
        return DESKTOP_BROWSER;
    }
}
