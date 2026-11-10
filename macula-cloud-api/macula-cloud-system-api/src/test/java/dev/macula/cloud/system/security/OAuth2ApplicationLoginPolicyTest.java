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
package dev.macula.cloud.system.security;

import dev.macula.cloud.system.enums.OAuth2ClientApplicationType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 固定来源与端内排他规则的正反例。
 * @author Rain
 * @since 6.1.0
 */
class OAuth2ApplicationLoginPolicyTest {
    /** 小程序双Code只能来自同一真实注册。 */
    @Test
    void miniappCodesShareRegistration() {
        assertDoesNotThrow(() -> OAuth2ApplicationLoginPolicy.validate(OAuth2ClientApplicationType.MINIAPP, "WECHAT", Map.of(),
            Map.of("WECHAT_MINIAPP_LOGIN_CODE", 1L, "WECHAT_MINIAPP_PHONE_CODE", 1L), true));
        assertThrows(IllegalArgumentException.class, () -> OAuth2ApplicationLoginPolicy.validate(OAuth2ClientApplicationType.MINIAPP, "WECHAT", Map.of(),
            Map.of("WECHAT_MINIAPP_LOGIN_CODE", 1L, "WECHAT_MINIAPP_PHONE_CODE", 2L), true));
    }
    /** 同一钉钉注册也不能给移动应用开放两个SDK能力。 */
    @Test
    void nativeHasOneSdkAndNoBrowser() {
        assertDoesNotThrow(() -> OAuth2ApplicationLoginPolicy.validate(OAuth2ClientApplicationType.NATIVE, null, Map.of(), Map.of("DINGTALK_APP_CODE", 1L), true));
        assertThrows(IllegalArgumentException.class, () -> OAuth2ApplicationLoginPolicy.validate(OAuth2ClientApplicationType.NATIVE, null, Map.of(),
            Map.of("DINGTALK_APP_CODE", 1L, "FEISHU_APP_CODE", 1L), true));
        assertThrows(IllegalArgumentException.class, () -> OAuth2ApplicationLoginPolicy.validate(OAuth2ClientApplicationType.NATIVE, null,
            Map.of("WECOM_BROWSER", 1L), Map.of(), true));
    }
    /** 端内启用恰一能力，草稿可尚未配置，但不可混用网页和JSAPI。 */
    @Test
    void inAppSelectsOneContainerCapability() {
        assertDoesNotThrow(() -> OAuth2ApplicationLoginPolicy.validate(OAuth2ClientApplicationType.IN_APP, null, Map.of(), Map.of(), false));
        assertDoesNotThrow(() -> OAuth2ApplicationLoginPolicy.validate(OAuth2ClientApplicationType.IN_APP, null, Map.of("WECOM_WEBVIEW", 1L), Map.of(), true));
        assertDoesNotThrow(() -> OAuth2ApplicationLoginPolicy.validate(OAuth2ClientApplicationType.IN_APP, null, Map.of(), Map.of("FEISHU_WEBVIEW_CODE", 1L), true));
        assertThrows(IllegalArgumentException.class, () -> OAuth2ApplicationLoginPolicy.validate(OAuth2ClientApplicationType.IN_APP, null, Map.of(), Map.of(), true));
        assertThrows(IllegalArgumentException.class, () -> OAuth2ApplicationLoginPolicy.validate(OAuth2ClientApplicationType.IN_APP, null,
            Map.of("WECOM_WEBVIEW", 1L), Map.of("DINGTALK_WEBVIEW_CODE", 1L), true));
    }
    /** Web多源只允许通用网页，Service只允许机器授权。 */
    @Test
    void webAndServiceRemainSeparate() {
        assertDoesNotThrow(() -> OAuth2ApplicationLoginPolicy.validate(OAuth2ClientApplicationType.SPA, null,
            Map.of("WECOM_BROWSER", 1L, "FEISHU_WEB", 2L), Map.of(), true));
        assertThrows(IllegalArgumentException.class, () -> OAuth2ApplicationLoginPolicy.validate(OAuth2ClientApplicationType.SPA, null,
            Map.of(), Map.of("FEISHU_WEBVIEW_CODE", 1L), true));
        for (String grant : new String[]{"authorization_code", "refresh_token", "sms", "email", "urn:macula:params:oauth:grant-type:external_code"}) {
            org.junit.jupiter.api.Assertions.assertFalse(OAuth2ClientApplicationType.SERVICE.supportsGrant(grant));
        }
    }
}
