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

import dev.macula.cloud.iam.enums.ThirdPartyProviderType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

import java.util.Locale;

/**
 * 选择微信开放平台网站登录或公众号网页授权模式。
 *
 * @author Rain
 * @since 6.1.0
 */
public enum WechatLoginMode {
    BROWSER(ThirdPartyProviderType.WECHAT.loginPath() + "/browser"),
    WEBVIEW(ThirdPartyProviderType.WECHAT.loginPath() + "/webview"),
    UNKNOWN(null);

    private final String loginPath;

    WechatLoginMode(String loginPath) {
        this.loginPath = loginPath;
    }

    public static WechatLoginMode from(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.endsWith(BROWSER.loginPath)) {
            return BROWSER;
        }
        if (path.endsWith(WEBVIEW.loginPath)) {
            return WEBVIEW;
        }
        String agent = request.getHeader(HttpHeaders.USER_AGENT);
        if (agent == null) {
            return UNKNOWN;
        }
        String value = agent.toLowerCase(Locale.ROOT);
        if (value.contains("micromessenger")) {
            return WEBVIEW;
        }
        if (value.contains("chrome/") || value.contains("firefox/") || value.contains("safari/") ||
            value.contains("edg/")) {
            return BROWSER;
        }
        return UNKNOWN;
    }

    public String loginPath() {
        return loginPath;
    }
}
