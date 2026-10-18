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
package dev.macula.cloud.system.message;

import dev.macula.cloud.system.enums.MessageTemplateType;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 消息模板变量白名单和转义测试。
 *
 * @author Rain
 * @since 2026-10-13
 */
class TemplateRendererTest {
    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void escapesInsertedValues() {
        String html = renderer.render(MessageTemplateType.MAIL_LOGIN, MessageTemplateType.MAIL_LOGIN.content(),
            Map.of("client_name", "Macula Cloud", "user_email", "user@example.com", "code", "<123>",
                "expire_time", "5", "now_time", "2026-10-18 10:00"));

        assertTrue(html.contains("亲爱的用户：user@example.com"));
        assertTrue(html.contains("&lt;123&gt;"));
        assertTrue(html.contains("Macula Cloud 官方团队"));
    }

    @Test
    void rejectsUnknownAndMissingVariables() {
        assertThrows(ResponseStatusException.class, () -> renderer.render(MessageTemplateType.MAIL_LOGIN,
            "${password}", Map.of("code", "123")));
        assertThrows(ResponseStatusException.class, () -> renderer.render(MessageTemplateType.MAIL_LOGIN,
            "${code}", Map.of()));
        assertThrows(ResponseStatusException.class, () -> renderer.render(MessageTemplateType.MAIL_LOGIN,
            "${}", Map.of("code", "123")));
        assertThrows(ResponseStatusException.class, () -> renderer.render(MessageTemplateType.SMS_LOGIN,
            "固定短信内容", Map.of()));
    }

    @Test
    void smsValuesAreNotHtmlEncoded() {
        assertEquals("登录验证码 <123>，请勿泄露。", renderer.render(MessageTemplateType.SMS_LOGIN,
            MessageTemplateType.SMS_LOGIN.content(), Map.of("code", "<123>")));
    }
}
