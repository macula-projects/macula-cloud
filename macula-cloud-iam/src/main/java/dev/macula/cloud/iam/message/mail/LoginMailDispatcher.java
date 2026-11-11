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
package dev.macula.cloud.iam.message.mail;

import java.time.Duration;
import java.time.OffsetDateTime;

import dev.macula.cloud.iam.message.audit.LoginMessageAudit;
import dev.macula.cloud.iam.message.template.LoginMessageConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * 使用共享MAIL_LOGIN合同渲染并提交验证码，不参与账号认证。
 *
 * @author Macula
 * @since 6.1.0
 */
@Component
@RequiredArgsConstructor
public class LoginMailDispatcher {
    private final LoginMessageConfigurationService configurations;
    private final MailSender sender;
    private final LoginMessageAudit audit;

    /**
     * SMTP接受提交后才返回；调用方随后激活验证码。
     */
    public void send(String email, String code, String clientName, Duration ttl) {
        try {
            var configuration = configurations.mail();
            var template = configuration.template();
            sender.send(configuration.provider(), email, template.getSenderName(),
                    render(template.getTitle(), email, code, clientName, ttl),
                    render(template.getContent(), email, code, clientName, ttl));
            audit.submitted(LoginMessageAudit.Scene.MAIL, "SMTP", email);
        } catch (RuntimeException ex) {
            audit.failed(LoginMessageAudit.Scene.MAIL, "SMTP", email);
            throw ex;
        }
    }

    /**
     * 只替换固定变量并进行HTML转义，不解释模板表达式。
     */
    private String render(String source, String email, String code, String clientName, Duration ttl) {
        return source
                .replace("${client_name}", escape(clientName))
                .replace("${user_email}", escape(email))
                .replace("${code}", escape(code))
                .replace("${expire_time}", String.valueOf(Math.max(1L, ttl.toMinutes())))
                .replace("${now_time}", escape(OffsetDateTime.now().toString()));
    }

    /**
     * 用户资料只能作为HTML文本，不得注入标签。
     */
    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

}
