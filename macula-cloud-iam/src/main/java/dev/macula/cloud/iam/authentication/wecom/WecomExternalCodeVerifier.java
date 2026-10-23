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

import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService;
import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService.ProviderConfig;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeVerification;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeVerifier;
import dev.macula.cloud.system.enums.ExternalCodeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 使用 Client 固定企业微信 registration 将各场景 code 核验为成员 userid。
 *
 * @author Rain
 * @since 6.1.0
 */
@Component
@RequiredArgsConstructor
public class WecomExternalCodeVerifier implements ExternalCodeVerifier {
    private static final String PROVIDER_TYPE = "WECOM";
    private static final Set<ExternalCodeType> TYPES = Set.of(ExternalCodeType.WECOM_APP_CODE,
        ExternalCodeType.WECOM_MINIAPP_CODE, ExternalCodeType.WECOM_BROWSER_CODE);

    private final IdentityProviderConfigService configs;
    private final WecomClient client;

    @Override
    public Set<ExternalCodeType> supportedTypes() {
        return TYPES;
    }

    @Override
    public ExternalCodeVerification verify(ExternalCodeType codeType, Long identityProviderId, String code) {
        ProviderConfig config = configs.requireEnabledById(identityProviderId, PROVIDER_TYPE);
        String userId = switch (codeType) {
            case WECOM_APP_CODE -> client.fetchAppUserId(config, code);
            case WECOM_MINIAPP_CODE -> client.fetchMiniAppUserId(config, code);
            case WECOM_BROWSER_CODE -> client.fetchUserId(config, code);
            default -> throw new IllegalArgumentException("Unsupported WeCom external code type");
        };
        return ExternalCodeVerification.identity(PROVIDER_TYPE, userId);
    }
}
