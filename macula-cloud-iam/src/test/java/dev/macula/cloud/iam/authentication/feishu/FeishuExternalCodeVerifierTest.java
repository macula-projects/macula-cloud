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

import java.util.Map;

import dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeVerification;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService.ProviderConfig;
import dev.macula.cloud.system.enums.ExternalCodeType;
import dev.macula.cloud.system.enums.IdentityDirectoryPolicy;
import dev.macula.cloud.system.enums.IdentitySourceFlow;
import dev.macula.cloud.system.enums.IdentitySourceType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
    /**
     * AppLink必须传本次授权回调及专用PKCE，来源中的历史回调不作兜底。
     */
    @Test
    void appLinkUsesRuntimeCallbackAndRejectsLegacyCalls() {
        IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
        FeishuClient client = mock(FeishuClient.class);
        ProviderConfig config = new ProviderConfig(7L, IdentitySourceType.FEISHU_APPLINK,
                false, null, 1L, "FEISHU", "app", "fixture-secret", Map.of("appId", "app"), Map.of(), true,
                IdentitySourceFlow.FEISHU_APPLINK_V3,
                IdentityDirectoryPolicy.IDENTITY_ONLY, null,
                "https://old.example/callback");
        when(configs.requireEnabledById(7L, "FEISHU")).thenReturn(config);
        String proof = "p".repeat(43);
        when(client.fetchIdentity(config, "app-code", "https://app.example/callback", FeishuLoginMode.APP,
                "FEISHU_APP_CODE", proof))
                .thenReturn(VerifiedExternalIdentity.of(config,
                        "FEISHU_APP_CODE",
                        "OPEN_ID", "fixture-open-id", Map.of()));
        FeishuExternalCodeVerifier verifier = new FeishuExternalCodeVerifier(configs, client);
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> verifier.verify(ExternalCodeType.FEISHU_APP_CODE, 7L, "app-code"));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> verifier.verify(ExternalCodeType.FEISHU_APP_CODE, 7L, "app-code", proof));
        ExternalCodeVerification result = verifier.verify(ExternalCodeType.FEISHU_APP_CODE, 7L, "app-code", proof,
                "https://app.example/callback");
        assertEquals("fixture-open-id", result.subject());
        verify(client).fetchIdentity(config, "app-code", "https://app.example/callback", FeishuLoginMode.APP,
                "FEISHU_APP_CODE", proof);
    }
}
