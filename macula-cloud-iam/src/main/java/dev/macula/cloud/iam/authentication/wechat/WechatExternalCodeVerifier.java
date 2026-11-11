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
import java.util.Set;

import dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeVerification;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeVerifier;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService.ProviderConfig;
import dev.macula.cloud.system.enums.ExternalCodeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
            ExternalCodeType.WECHAT_MINIAPP_LOGIN_CODE, ExternalCodeType.WECHAT_MINIAPP_PHONE_CODE);

    private final IdentityProviderConfigService configs;
    private final WechatClient client;

    @Override
    public Set<ExternalCodeType> supportedTypes() {
        return TYPES;
    }

    @Override
    public ExternalCodeVerification verify(ExternalCodeType codeType, Long identityProviderId, String code) {
        ProviderConfig config = configs.requireEnabledById(identityProviderId, PROVIDER_TYPE).forCode(codeType);
        if (!config.sourceType().supports(codeType)) {
            throw new IllegalArgumentException("来源不支持此凭证类型");
        }
        String value = switch (codeType) {
            case WECHAT_OPEN_APP_CODE -> client.fetchOpenAppOpenId(config, code);
            case WECHAT_MINIAPP_LOGIN_CODE -> client.fetchMiniappOpenId(config, code);
            case WECHAT_MINIAPP_PHONE_CODE -> client.fetchMiniappMobile(config, code);
            default -> throw new IllegalArgumentException("Unsupported WeChat external code type");
        };
        boolean mobile = codeType == ExternalCodeType.WECHAT_MINIAPP_PHONE_CODE;
        if (mobile) {
            value = new dev.macula.cloud.system.security.UserIdentifierNormalizer().mobile(value)
                    .orElseThrow(() -> new IllegalArgumentException("已核验手机号格式无效"));
        }
        return new ExternalCodeVerification(VerifiedExternalIdentity.of(config, codeType.name(),
                mobile ? VerifiedExternalIdentity.MOBILE_PROOF : VerifiedExternalIdentity.OPEN_ID, value,
                mobile ? Map.of("verified_mobile", value) : Map.of()));
    }
}
