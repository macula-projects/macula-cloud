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
        assertEquals("您的验证码是 &lt;123&gt;，请勿泄露。",
            renderer.render(MessageTemplateType.MAIL_VERIFY, MessageTemplateType.MAIL_VERIFY.content(),
                Map.of("code", "<123>")));
    }

    @Test
    void rejectsUnknownAndMissingVariables() {
        assertThrows(ResponseStatusException.class, () -> renderer.render(MessageTemplateType.MAIL_VERIFY,
            "${password}", Map.of("code", "123")));
        assertThrows(ResponseStatusException.class, () -> renderer.render(MessageTemplateType.MAIL_VERIFY,
            "${code}", Map.of()));
        assertThrows(ResponseStatusException.class, () -> renderer.render(MessageTemplateType.MAIL_VERIFY,
            "${}", Map.of("code", "123")));
        assertThrows(ResponseStatusException.class, () -> renderer.render(MessageTemplateType.SMS_VERIFY,
            "固定短信内容", Map.of()));
    }

    @Test
    void smsValuesAreNotHtmlEncoded() {
        assertEquals("验证码 <123>，请勿泄露。", renderer.render(MessageTemplateType.SMS_VERIFY,
            MessageTemplateType.SMS_VERIFY.content(), Map.of("code", "<123>")));
    }
}
