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
import dev.macula.cloud.system.enums.ExternalCodeType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证飞书外部凭证类型固定选择对应的服务端换码流程。
 *
 * @author Rain
 * @since 6.1.0
 */
class FeishuExternalCodeVerifierTest {
    @Test
    void appCodeUsesNativeAppExchangeWithoutRedirectUri() {
        IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
        FeishuClient client = mock(FeishuClient.class);
        ProviderConfig config = new ProviderConfig("FEISHU", "app", "secret",
            Map.of("appId", "app"));
        when(configs.requireEnabledById(7L, "FEISHU")).thenReturn(config);
        when(client.fetchEmployeeNo(config, "app-code", null, FeishuLoginMode.APP))
            .thenReturn("E1001");
        FeishuExternalCodeVerifier verifier = new FeishuExternalCodeVerifier(configs, client);

        ExternalCodeVerification verification = verifier.verify(
            ExternalCodeType.FEISHU_APP_CODE, 7L, "app-code");

        assertEquals("FEISHU", verification.providerType());
        assertEquals("E1001", verification.subject());
        verify(client).fetchEmployeeNo(config, "app-code", null, FeishuLoginMode.APP);
    }

    @Test
    void browserCodeUsesConfiguredRedirectUri() {
        IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
        FeishuClient client = mock(FeishuClient.class);
        ProviderConfig config = new ProviderConfig("FEISHU", "app", "secret",
            Map.of("appId", "app", "externalRedirectUri", "https://app.example/callback"));
        when(configs.requireEnabledById(8L, "FEISHU")).thenReturn(config);
        when(client.fetchEmployeeNo(config, "browser-code", "https://app.example/callback",
            FeishuLoginMode.BROWSER)).thenReturn("E1002");
        FeishuExternalCodeVerifier verifier = new FeishuExternalCodeVerifier(configs, client);

        ExternalCodeVerification verification = verifier.verify(
            ExternalCodeType.FEISHU_BROWSER_CODE, 8L, "browser-code");

        assertEquals("E1002", verification.subject());
        verify(client).fetchEmployeeNo(config, "browser-code", "https://app.example/callback",
            FeishuLoginMode.BROWSER);
    }
}
