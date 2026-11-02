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
package dev.macula.cloud.iam.message;

import dev.macula.cloud.iam.pojo.entity.SysMessageProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.Properties;
import java.util.Set;

/**
 * 使用共享 SMTP 配置发送 IAM 登录验证邮件。
 *
 * @author Rain
 * @since 6.1.0
 */
@Component
public class LoginMailSender {
    private static final Set<String> SECURITY_MODES = Set.of("NONE", "SSL");
    private final JsonMapper json = JsonMapper.builder().build();

    public void send(SysMessageProvider provider, String target, String senderName, String subject, String content) {
        MailOptions options = StringUtils.hasText(provider.getConfigJson())
            ? json.readValue(provider.getConfigJson(), MailOptions.class) : new MailOptions(465, "SSL");
        validate(provider, options);
        JavaMailSenderImpl sender = createSender(provider, options);
        try {
            MimeMessageHelper message = new MimeMessageHelper(sender.createMimeMessage(), "UTF-8");
            if (StringUtils.hasText(senderName)) {
                message.setFrom(provider.getSender(), senderName);
            } else {
                message.setFrom(provider.getSender());
            }
            message.setTo(target);
            message.setSubject(subject);
            message.setText(content, true);
            sender.send(message.getMimeMessage());
        } catch (Exception ex) {
            throw new IllegalStateException("登录邮件提交失败");
        }
    }

    private void validate(SysMessageProvider provider, MailOptions options) {
        boolean valid = options.getPort() > 0 && options.getPort() <= 65535
            && SECURITY_MODES.contains(options.getSecurityMode())
            && StringUtils.hasText(provider.getEndpoint())
            && StringUtils.hasText(provider.getAccount())
            && StringUtils.hasText(provider.getCredential())
            && StringUtils.hasText(provider.getSender());
        if (!valid) {
            throw new IllegalStateException("登录邮件配置不完整");
        }
    }

    private JavaMailSenderImpl createSender(SysMessageProvider provider, MailOptions options) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(provider.getEndpoint());
        sender.setPort(options.getPort());
        sender.setUsername(provider.getAccount());
        sender.setPassword(provider.getCredential());
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "false");
        properties.put("mail.smtp.starttls.required", "false");
        properties.put("mail.smtp.ssl.enable", String.valueOf("SSL".equals(options.getSecurityMode())));
        properties.put("mail.smtp.ssl.checkserveridentity", "true");
        properties.put("mail.smtp.connectiontimeout", "5000");
        properties.put("mail.smtp.timeout", "5000");
        properties.put("mail.smtp.writetimeout", "5000");
        return sender;
    }

    /**
     * SMTP 端口与传输安全模式。
     *
     * @since 6.1.0
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MailOptions {
        private int port;
        private String securityMode;
    }
}
