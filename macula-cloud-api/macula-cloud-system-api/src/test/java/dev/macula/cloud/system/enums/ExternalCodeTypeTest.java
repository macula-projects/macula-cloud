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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 固定外部凭证类型的协议值及来源语义。
 *
 * @author Rain
 * @since 6.1.0
 */
class ExternalCodeTypeTest {
    @Test
    void keepsStableProtocolValuesAndAppMappings() {
        assertArrayEquals(new ExternalCodeType[] {
            ExternalCodeType.FEISHU_APP_CODE,
            ExternalCodeType.WECOM_APP_CODE,
            ExternalCodeType.DINGTALK_APP_CODE,
            ExternalCodeType.WECHAT_OPEN_APP_CODE,
            ExternalCodeType.ALIYUN_MOBILE_TOKEN,
            ExternalCodeType.WECHAT_MINIAPP_LOGIN_CODE,
            ExternalCodeType.WECHAT_MINIAPP_PHONE_CODE,
            ExternalCodeType.WECOM_MINIAPP_CODE,
            ExternalCodeType.FEISHU_WEBVIEW_CODE,
            ExternalCodeType.DINGTALK_WEBVIEW_CODE,
            ExternalCodeType.FEISHU_BROWSER_CODE,
            ExternalCodeType.DINGTALK_BROWSER_CODE,
            ExternalCodeType.WECOM_BROWSER_CODE,
            ExternalCodeType.WECHAT_OPEN_BROWSER_CODE
        }, ExternalCodeType.values());
        assertEquals("WECOM", ExternalCodeType.WECOM_APP_CODE.providerType());
        assertEquals(ExternalCodeType.Scene.APP, ExternalCodeType.WECOM_APP_CODE.scene());
        assertEquals("DINGTALK", ExternalCodeType.DINGTALK_APP_CODE.providerType());
        assertEquals(ExternalCodeType.Scene.APP, ExternalCodeType.DINGTALK_APP_CODE.scene());
    }
}
