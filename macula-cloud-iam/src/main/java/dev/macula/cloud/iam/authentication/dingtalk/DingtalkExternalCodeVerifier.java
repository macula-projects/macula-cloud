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
package dev.macula.cloud.iam.authentication.dingtalk;

import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService;
import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService.ProviderConfig;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeVerification;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeVerifier;
import dev.macula.cloud.system.enums.ExternalCodeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 使用 Client 固定钉钉 registration 将各场景 code 核验为组织成员 userId。
 *
 * @author Rain
 * @since 6.1.0
 */
@Component
@RequiredArgsConstructor
public class DingtalkExternalCodeVerifier implements ExternalCodeVerifier {
    private static final String PROVIDER_TYPE = "DINGTALK";
    private static final Set<ExternalCodeType> TYPES = Set.of(ExternalCodeType.DINGTALK_APP_CODE,
        ExternalCodeType.DINGTALK_WEBVIEW_CODE, ExternalCodeType.DINGTALK_BROWSER_CODE);

    private final IdentityProviderConfigService configs;
    private final DingtalkClient client;

    @Override
    public Set<ExternalCodeType> supportedTypes() {
        return TYPES;
    }

    @Override
    public ExternalCodeVerification verify(ExternalCodeType codeType, Long identityProviderId, String code) {
        ProviderConfig config = configs.requireEnabledById(identityProviderId, PROVIDER_TYPE);
        DingtalkLoginMode mode = switch (codeType) {
            case DINGTALK_APP_CODE -> DingtalkLoginMode.APP;
            case DINGTALK_BROWSER_CODE -> DingtalkLoginMode.BROWSER;
            case DINGTALK_WEBVIEW_CODE -> DingtalkLoginMode.WEBVIEW;
            default -> throw new IllegalArgumentException("Unsupported DingTalk external code type");
        };
        return ExternalCodeVerification.identity(PROVIDER_TYPE, client.fetchUserId(config, code, mode));
    }
}
