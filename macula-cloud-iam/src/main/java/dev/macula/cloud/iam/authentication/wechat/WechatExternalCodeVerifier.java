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

import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService;
import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService.ProviderConfig;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeVerification;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeVerifier;
import dev.macula.cloud.system.enums.ExternalCodeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 使用 Client 固定微信 registration 核验开放平台或小程序凭证。
 *
 * @author Rain
 * @since 6.1.0
 */
@Component
@RequiredArgsConstructor
public class WechatExternalCodeVerifier implements ExternalCodeVerifier {
    private static final String PROVIDER_TYPE = "WECHAT";
    private static final Set<ExternalCodeType> TYPES = Set.of(ExternalCodeType.WECHAT_OPEN_APP_CODE,
        ExternalCodeType.WECHAT_MINIAPP_LOGIN_CODE, ExternalCodeType.WECHAT_MINIAPP_PHONE_CODE,
        ExternalCodeType.WECHAT_OPEN_BROWSER_CODE);

    private final IdentityProviderConfigService configs;
    private final WechatClient client;

    @Override
    public Set<ExternalCodeType> supportedTypes() {
        return TYPES;
    }

    @Override
    public ExternalCodeVerification verify(ExternalCodeType codeType, Long identityProviderId, String code) {
        ProviderConfig config = configs.requireEnabledById(identityProviderId, PROVIDER_TYPE);
        return switch (codeType) {
            case WECHAT_OPEN_APP_CODE -> ExternalCodeVerification.identity(PROVIDER_TYPE,
                client.fetchOpenAppUnionId(config, code));
            case WECHAT_MINIAPP_LOGIN_CODE -> ExternalCodeVerification.identity(PROVIDER_TYPE,
                client.fetchMiniappUnionId(config, code));
            case WECHAT_MINIAPP_PHONE_CODE -> ExternalCodeVerification.mobile(
                client.fetchMiniappMobile(config, code));
            case WECHAT_OPEN_BROWSER_CODE -> ExternalCodeVerification.identity(PROVIDER_TYPE,
                client.fetchUnionId(config, code, WechatLoginMode.BROWSER));
            default -> throw new IllegalArgumentException("Unsupported WeChat external code type");
        };
    }
}
