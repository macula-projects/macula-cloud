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

import java.util.Map;

import dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeVerification;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService.ProviderConfig;
import dev.macula.cloud.system.enums.ExternalCodeType;
import dev.macula.cloud.system.enums.IdentitySourceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证钉钉外部凭证类型固定选择对应的服务端换码流程。
 *
 * @author Rain
 * @since 6.1.0
 */
class DingtalkExternalCodeVerifierTest {
    /**
     * 同一应用从网页锚点选择SDK能力，保留具体registration及企业身份。
     */
    @Test
    void appCodeUsesNativeSdkExchange() {
        IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
        DingtalkClient client = mock(DingtalkClient.class);
        var base = IdentitySourceTestFixtures.source(
                IdentitySourceType.DINGTALK_WEB, "DINGTALK", "app", "secret",
                Map.of("appKey", "app", "corpId", "corp"));
        ProviderConfig config = new ProviderConfig(9L, base.sourceType(), false, null, base.version(),
                base.providerType(),
                base.appId(), base.appSecret(), base.values(), base.secrets(), true, base.flow(),
                base.directoryPolicy(),
                base.organizationId(), null, null,
                Map.of("DINGTALK_WEB", "DINGTALK_OAUTH_V1", "DINGTALK_SDK", "DINGTALK_OAUTH_V1",
                        "DINGTALK_WEBVIEW", "DINGTALK_CORP_H5_V1"), "DINGTALK_WEB");
        var sdk = config.forCode(ExternalCodeType.DINGTALK_APP_CODE);
        when(configs.requireEnabledById(9L, "DINGTALK")).thenReturn(config);
        when(client.fetchUserId(sdk, "app-code", DingtalkLoginMode.APP)).thenReturn("manager01");
        DingtalkExternalCodeVerifier verifier = new DingtalkExternalCodeVerifier(configs, client);

        ExternalCodeVerification verification = verifier.verify(
                ExternalCodeType.DINGTALK_APP_CODE, 9L, "app-code");

        assertEquals("DINGTALK", verification.providerType());
        assertEquals("manager01", verification.subject());
        assertEquals(9L, verification.identity().registrationId());
        assertEquals("DINGTALK_SDK", verification.identity().loginCapability());
        assertTrue(verifier.supportedTypes().contains(ExternalCodeType.DINGTALK_APP_CODE));
        verify(client).fetchUserId(sdk, "app-code", DingtalkLoginMode.APP);
    }
}
