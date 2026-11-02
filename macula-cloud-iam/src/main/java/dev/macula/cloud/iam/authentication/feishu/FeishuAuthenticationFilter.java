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

import dev.macula.cloud.iam.authentication.thirdparty.AbstractThirdPartyAuthenticationFilter;
import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService.ProviderConfig;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyLoginContext;
import dev.macula.cloud.iam.enums.ThirdPartyProviderType;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 飞书登录发起与回调过滤器。
 *
 * @author Rain
 * @since 6.1.0
 */
public class FeishuAuthenticationFilter extends AbstractThirdPartyAuthenticationFilter {
    private FeishuClient client;

    public FeishuAuthenticationFilter() {
        super(ThirdPartyProviderType.FEISHU.providerType(), ThirdPartyProviderType.FEISHU.loginPath());
    }

    void feishuClient(FeishuClient client) {
        this.client = client;
    }

    @Override
    protected String loginMode(HttpServletRequest request) {
        FeishuLoginMode mode = FeishuLoginMode.from(request);
        return mode == FeishuLoginMode.UNKNOWN ? null : mode.name();
    }

    @Override
    protected String authorizationUri(ProviderConfig config, String state, String redirectUri,
        ThirdPartyLoginContext context) {
        return client.authorizationUri(config, state, redirectUri, FeishuLoginMode.valueOf(context.loginMode()));
    }
}
