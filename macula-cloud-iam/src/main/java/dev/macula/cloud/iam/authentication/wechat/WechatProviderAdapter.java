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

import java.util.Map;

import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyLoginContext;
import dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity;
import dev.macula.cloud.iam.authentication.thirdparty.provider.ThirdPartyProviderAdapter;
import dev.macula.cloud.iam.enums.ThirdPartyProviderType;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService.ProviderConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Wechat网页协议适配，仅处理厂商授权地址与核验身份。
 *
 * @author Macula
 * @since 6.1.0
 */
@Component
@RequiredArgsConstructor
public final class WechatProviderAdapter implements ThirdPartyProviderAdapter {
    private final WechatClient client;

    /**
     * 返回稳定厂商标识。
     */
    @Override
    public String providerType() {
        return ThirdPartyProviderType.WECHAT.providerType();
    }

    /**
     * 使用当前固定来源生成厂商授权地址。
     */
    @Override
    public String authorizationUri(ProviderConfig config, String state, String redirectUri,
            ThirdPartyLoginContext context) {
        return client.authorizationUri(config, state, redirectUri, WechatLoginMode.valueOf(context.loginMode()));
    }

    /**
     * 只返回经平台服务端核验的稳定身份，不负责本地账号或Token发布。
     */
    @Override
    public VerifiedExternalIdentity verify(ProviderConfig config, String code, String redirectUri,
            ThirdPartyLoginContext context) {
        String subject = client.fetchOpenId(config, code, WechatLoginMode.valueOf(context.loginMode()));
        return VerifiedExternalIdentity.of(config, VerifiedExternalIdentity.BROWSER,
                VerifiedExternalIdentity.OPEN_ID, subject, Map.of());
    }
}
