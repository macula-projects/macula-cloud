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
package dev.macula.cloud.system.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证能力与真实注册边界、旧能力等价投影及严格JSON解析。
 * @author Rain
 * @since 6.1.0
 */
class IdentityLoginCapabilityTest {
    /** 同一企微注册的Code不同；微信四种注册始终不同。 */
    @Test
    void preservesRegistrationBoundariesAndExactLegacyCapabilities() {
        assertEquals(IdentityCredentialType.WECOM_APPLICATION, IdentityLoginCapability.WECOM_MINIAPP.credentialType());
        assertNotEquals(IdentityLoginCapability.WECHAT_WEBSITE.credentialType(), IdentityLoginCapability.WECHAT_OFFICIAL_ACCOUNT.credentialType());
        assertEquals(2, IdentityLoginCapability.legacy(IdentitySourceType.WECOM_WEB, "WECOM_WEB_V1").size());
        assertEquals(1, IdentityLoginCapability.legacy(IdentitySourceType.WECOM_SDK, "WECOM_SDK_V1").size());
        assertEquals(2, IdentityLoginCapability.legacy(IdentitySourceType.WECHAT_MINIAPP, "WECHAT_MINIAPP_V1").size());
        assertTrue(IdentityLoginCapability.decode("{}", IdentitySourceType.WECOM_WEB, "WECOM_WEB_V1").isEmpty());
        assertThrows(RuntimeException.class, () -> IdentityLoginCapability.decode("{\"WECHAT_MOBILE_APP\":\"WECHAT_OPEN_APP_V1\"}", IdentitySourceType.WECHAT_WEBSITE, "WECHAT_WEBSITE_V1"));
        assertThrows(RuntimeException.class, () -> IdentityLoginCapability.decode("{\"WECOM_SDK\":\"WECOM_SDK_V1\",\"WECOM_SDK\":\"WECOM_SDK_V1\"}", IdentitySourceType.WECOM_WEB, "WECOM_WEB_V1"));
    }
    /** 两种微信小程序Code不可互换，环境限制不因为共用Secret而消失。 */
    @Test
    void codeAndBrowserEnvironmentSelectExactlyOneCapability() {
        assertEquals(IdentityLoginCapability.WECHAT_MINIAPP_PHONE, IdentityLoginCapability.forCode(ExternalCodeType.WECHAT_MINIAPP_PHONE_CODE));
        assertFalse(IdentityLoginCapability.WECOM_BROWSER.environments().contains(IdentityBrowserEnvironment.MOBILE_BROWSER));
        assertEquals(IdentityLoginCapability.WECOM_WEBVIEW, IdentityLoginCapability.browser(IdentitySourceType.WECOM_WEB,"WEBVIEW"));
        assertTrue(IdentityLoginCapability.DINGTALK_SDK.available());
    }
}
