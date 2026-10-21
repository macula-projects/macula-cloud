/*
 * Copyright (c) 2026 Macula
 * macula.dev, China
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

import jakarta.servlet.http.HttpServletRequest;

import java.util.Locale;

/**
 * 选择企业微信系统浏览器登录或内置 WebView 授权；识别结果不作为安全凭据。
 *
 * @author Rain
 * @since 6.1.0
 */
public enum WecomLoginMode {
    BROWSER("/login/wecom/browser"),
    WEBVIEW("/login/wecom/webview"),
    UNKNOWN(null);

    private final String loginPath;

    WecomLoginMode(String loginPath) {
        this.loginPath = loginPath;
    }

    public static WecomLoginMode from(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.endsWith(BROWSER.loginPath)) {
            return BROWSER;
        }
        if (path.endsWith(WEBVIEW.loginPath)) {
            return WEBVIEW;
        }
        String agent = request.getHeader("User-Agent");
        if (agent == null) {
            return UNKNOWN;
        }
        String value = agent.toLowerCase(Locale.ROOT);
        if (value.contains("wxwork")) {
            return WEBVIEW;
        }
        if (value.contains("chrome/") || value.contains("firefox/") || value.contains("safari/")
            || value.contains("edg/")) {
            return BROWSER;
        }
        return UNKNOWN;
    }

    public String loginPath() {
        return loginPath;
    }
}
