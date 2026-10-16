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

import dev.macula.cloud.system.pojo.entity.SysMessageProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Properties;

/**
 * 使用管理员保存的 SMTP 配置测试投递邮件，Exchange 使用 SMTP AUTH。
 *
 * @author Rain
 * @since 2026-10-13
 */
@Component
public class MailTestSender {
    private final JsonMapper json = JsonMapper.builder().build();

    public void send(SysMessageProvider provider, String target, String subject, String content) {
        if (!target.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱地址无效");
        }
        MailOptions options = StringUtils.hasText(provider.getConfigJson())
            ? json.readValue(provider.getConfigJson(), MailOptions.class) : new MailOptions(587, true, false);
        if (options.getPort() < 1 || options.getPort() > 65535 || options.isStartTls() == options.isSsl() ||
            !StringUtils.hasText(provider.getEndpoint()) ||
            !StringUtils.hasText(provider.getAccount()) || !StringUtils.hasText(provider.getCredential()) ||
            !StringUtils.hasText(provider.getSender())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SMTP 配置不完整");
        }
        JavaMailSenderImpl sender = createSender();
        sender.setHost(provider.getEndpoint());
        sender.setPort(options.getPort());
        sender.setUsername(provider.getAccount());
        sender.setPassword(provider.getCredential());
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", String.valueOf(options.isStartTls()));
        properties.put("mail.smtp.starttls.required", String.valueOf(options.isStartTls()));
        properties.put("mail.smtp.ssl.enable", String.valueOf(options.isSsl()));
        properties.put("mail.smtp.ssl.checkserveridentity", "true");
        properties.put("mail.smtp.connectiontimeout", "5000");
        properties.put("mail.smtp.timeout", "5000");
        properties.put("mail.smtp.writetimeout", "5000");
        try {
            MimeMessageHelper message = new MimeMessageHelper(sender.createMimeMessage(), "UTF-8");
            message.setFrom(provider.getSender());
            message.setTo(target);
            message.setSubject(subject);
            message.setText(content, true);
            sender.send(message.getMimeMessage());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "邮件提交失败");
        }
    }

    JavaMailSenderImpl createSender() {
        return new JavaMailSenderImpl();
    }

    /** SMTP 端口与传输加密选项。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MailOptions {
        private int port;
        private boolean startTls;
        private boolean ssl;
    }
}
