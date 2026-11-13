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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import dev.macula.cloud.iam.message.support.FakeSmtpServer;
import dev.macula.cloud.iam.pojo.entity.SysMessageProvider;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 真实JavaMail通过隔离SMTP提交，验证HTML、收件人及配置拒绝。
 *
 * @author Macula
 * @since 6.1.0
 */
class SmtpMailSenderTest {
    /**
     * 捕获真实SMTP命令，不连接公网。
     */
    @Test
    void submitsHtmlToLoopbackSmtp() throws Exception {
        try (var smtp = new FakeSmtpServer()) {
            var provider = provider(smtp.port());
            new SmtpMailSender().send(provider, "alice@example.test", "Macula", "Login", "<b>123456</b>");
            var message = new MimeMessage(
                    Session.getInstance(new Properties()),
                    new ByteArrayInputStream(smtp.message().getBytes(StandardCharsets.UTF_8)));
            assertThat(message.getAllRecipients()[0].toString()).isEqualTo("alice@example.test");
            assertThat(message.getSubject()).isEqualTo("Login");
            assertThat(message.getContent().toString()).contains("<b>123456</b>");
        }
    }

    /**
     * 缺失认证字段和未知模式在建立连接前拒绝。
     */
    @Test
    void rejectsUnconfiguredChannel() {
        var provider = provider(1);
        provider.setCredential(null);
        assertThatThrownBy(() -> new SmtpMailSender().send(provider, "alice@example.test", "", "Login", "123456"))
                .isInstanceOf(IllegalStateException.class).hasMessage("登录邮件配置不完整");
    }

    /**
     * 构造仅用于回环SMTP的合成渠道。
     */
    private SysMessageProvider provider(int port) {
        var provider = new SysMessageProvider();
        provider.setEndpoint("127.0.0.1");
        provider.setAccount("fixture-user");
        provider.setCredential("fixture-password");
        provider.setSender("iam@example.test");
        provider.setConfigJson("{\"port\":" + port + ",\"securityMode\":\"NONE\"}");
        return provider;
    }
}
