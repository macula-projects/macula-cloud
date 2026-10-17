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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证邮件预览只恢复无属性安全标签并转义活动内容。
 *
 * @author Rain
 * @since 2026-10-17
 */
class MailHtmlSanitizerTest {
    private final MailHtmlSanitizer sanitizer = new MailHtmlSanitizer();

    @Test
    void preservesOnlyTheSmallSafeTagAllowlist() {
        String safe = sanitizer.sanitize("<p>hello <strong>world</strong></p><script>alert(1)</script>"
            + "<a href=\"https://example.test\">link</a><img src=x onerror=alert(1)>");

        assertThat(safe).contains("<p>", "<strong>world</strong>")
            .contains("&lt;script&gt;", "&lt;a href=&quot;https://example.test&quot;&gt;")
            .doesNotContain("<script>", "<a ", "<img ");
    }
}
