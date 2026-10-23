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
import dev.macula.cloud.system.enums.ExternalCodeType;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
    @Test
    void appCodeUsesNativeSdkExchange() {
        IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
        DingtalkClient client = mock(DingtalkClient.class);
        ProviderConfig config = new ProviderConfig("DINGTALK", "app", "secret",
            Map.of("appKey", "app", "corpId", "corp"));
        when(configs.requireEnabledById(9L, "DINGTALK")).thenReturn(config);
        when(client.fetchUserId(config, "app-code", DingtalkLoginMode.APP)).thenReturn("manager01");
        DingtalkExternalCodeVerifier verifier = new DingtalkExternalCodeVerifier(configs, client);

        ExternalCodeVerification verification = verifier.verify(
            ExternalCodeType.DINGTALK_APP_CODE, 9L, "app-code");

        assertEquals("DINGTALK", verification.providerType());
        assertEquals("manager01", verification.subject());
        assertTrue(verifier.supportedTypes().contains(ExternalCodeType.DINGTALK_APP_CODE));
        verify(client).fetchUserId(config, "app-code", DingtalkLoginMode.APP);
    }
}
