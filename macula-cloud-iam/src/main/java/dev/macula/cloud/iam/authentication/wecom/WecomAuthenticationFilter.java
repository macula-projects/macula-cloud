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

import dev.macula.cloud.iam.authentication.thirdparty.browser.AbstractThirdPartyAuthenticationFilter;
import dev.macula.cloud.iam.enums.ThirdPartyProviderType;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 企业微信登录发起与回调过滤器。
 *
 * @author Rain
 * @since 6.1.0
 */
public class WecomAuthenticationFilter extends AbstractThirdPartyAuthenticationFilter {

    /**
     * 仅接受该厂商已登记入口。
     */
    public WecomAuthenticationFilter() {
        super(ThirdPartyProviderType.WECOM.providerType(), ThirdPartyProviderType.WECOM.loginPath());
    }

    /**
     * 从精确入口解析既有浏览器/容器模式。
     */
    @Override
    protected String loginMode(HttpServletRequest request) {
        WecomLoginMode mode = WecomLoginMode.from(request);
        return mode == WecomLoginMode.UNKNOWN ? null : mode.name();
    }

}
