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

import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService;
import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService.ProviderConfig;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeVerification;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeVerifier;
import dev.macula.cloud.system.enums.ExternalCodeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 使用 Client 固定飞书 registration 将各场景 code 核验为 employee_no。
 *
 * @author Rain
 * @since 6.1.0
 */
@Component
@RequiredArgsConstructor
public class FeishuExternalCodeVerifier implements ExternalCodeVerifier {
    private static final String PROVIDER_TYPE = "FEISHU";
    private static final Set<ExternalCodeType> TYPES = Set.of(ExternalCodeType.FEISHU_APP_CODE,
        ExternalCodeType.FEISHU_WEBVIEW_CODE);

    private final IdentityProviderConfigService configs;
    private final FeishuClient client;

    @Override
    public Set<ExternalCodeType> supportedTypes() {
        return TYPES;
    }

    /** JSAPI沿独立合同核验；AppLink必须使用带本次回调与PKCE的专用重载。 */
    @Override
    public ExternalCodeVerification verify(ExternalCodeType codeType, Long identityProviderId, String code) {
        if (codeType == ExternalCodeType.FEISHU_APP_CODE) {
            throw new IllegalArgumentException("AppLink需提交本次授权回调和PKCE材料");
        }
        ProviderConfig config = configs.requireEnabledById(identityProviderId, PROVIDER_TYPE).forCode(codeType);
            if (!config.sourceType().supports(codeType)) { throw new IllegalArgumentException("来源不支持此凭证类型"); }
        FeishuLoginMode mode = switch (codeType) {
            case FEISHU_APP_CODE -> FeishuLoginMode.APP;
            case FEISHU_WEBVIEW_CODE -> FeishuLoginMode.WEBVIEW;
            default -> throw new IllegalArgumentException("Unsupported Feishu external code type");
        };
        return new ExternalCodeVerification(client.fetchIdentity(config, code, null, mode, codeType.name()));
    }
    /** 旧扩展调用缺少本次授权回调时不使用来源配置猜测补齐。 */
    @Override
    public ExternalCodeVerification verify(ExternalCodeType codeType, Long identityProviderId, String code,
        String providerCodeVerifier) {
        return verify(codeType, identityProviderId, code, providerCodeVerifier, null);
    }
    /** AppLink使用已认证Client白名单校验后的本次授权回调及专用PKCE材料。 */
    @Override
    public ExternalCodeVerification verify(ExternalCodeType codeType, Long identityProviderId, String code,
        String providerCodeVerifier, String providerRedirectUri) {
        ProviderConfig config = configs.requireEnabledById(identityProviderId, PROVIDER_TYPE).forCode(codeType);
        if (codeType != ExternalCodeType.FEISHU_APP_CODE || !config.sourceType().supports(codeType)
            || !config.flow().providerPkce() || providerCodeVerifier == null
            || !providerCodeVerifier.matches("[A-Za-z0-9._~-]{43,128}")
            || providerRedirectUri == null || providerRedirectUri.isBlank()) {
            throw new IllegalArgumentException("当前飞书能力缺少授权回调或PKCE材料");
        }
        return new ExternalCodeVerification(client.fetchIdentity(config, code, providerRedirectUri,
            FeishuLoginMode.APP, codeType.name(), providerCodeVerifier));
    }

}
