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
import dev.macula.cloud.system.enums.ExternalCodeType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证企业微信外部凭证类型固定选择对应的服务端换码流程。
 *
 * @author Rain
 * @since 6.1.0
 */
class WecomExternalCodeVerifierTest {
    @Test
    void appCodeUsesNativeSdkExchange() {
        IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
        WecomClient client = mock(WecomClient.class);
        ProviderConfig config = new ProviderConfig("WECOM", "corp", "secret",
            Map.of("corpId", "corp", "agentId", "agent"));
        when(configs.requireEnabledById(6L, "WECOM")).thenReturn(config);
        when(client.fetchAppUserId(config, "app-code")).thenReturn("member01");
        WecomExternalCodeVerifier verifier = new WecomExternalCodeVerifier(configs, client);

        ExternalCodeVerification verification = verifier.verify(
            ExternalCodeType.WECOM_APP_CODE, 6L, "app-code");

        assertEquals("WECOM", verification.providerType());
        assertEquals("member01", verification.subject());
        assertTrue(verifier.supportedTypes().contains(ExternalCodeType.WECOM_APP_CODE));
        verify(client).fetchAppUserId(config, "app-code");
    }

    @Test
    void miniAppCodeUsesMiniAppSessionExchange() {
        IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
        WecomClient client = mock(WecomClient.class);
        ProviderConfig config = new ProviderConfig("WECOM", "corp", "secret",
            Map.of("corpId", "corp", "agentId", "agent"));
        when(configs.requireEnabledById(7L, "WECOM")).thenReturn(config);
        when(client.fetchMiniAppUserId(config, "miniapp-code")).thenReturn("member02");
        WecomExternalCodeVerifier verifier = new WecomExternalCodeVerifier(configs, client);

        ExternalCodeVerification verification = verifier.verify(
            ExternalCodeType.WECOM_MINIAPP_CODE, 7L, "miniapp-code");

        assertEquals("WECOM", verification.providerType());
        assertEquals("member02", verification.subject());
        verify(client).fetchMiniAppUserId(config, "miniapp-code");
    }
}
