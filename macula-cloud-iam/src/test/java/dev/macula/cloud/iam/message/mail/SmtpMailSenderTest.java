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

import dev.macula.cloud.iam.message.mail.impl.SmtpMailSender;
import dev.macula.cloud.iam.message.support.FakeStartTlsSmtpServer;
import org.springframework.mail.javamail.JavaMailSenderImpl;

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
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temporaryDirectory;
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

    /** TLS 升级成功才发送邮件，旧隐式 SSL 模式同样可以提交。 */
    @Test
    void submitsStartTlsAndImplicitTlsWithVerifiedCertificate() throws Exception {
        var tls = FakeStartTlsSmtpServer.context(temporaryDirectory);
        SmtpMailSender sender = new SmtpMailSender() {
            /** 使用仅测试信任的证书，并保留主机名校验。 */
            @Override
            protected JavaMailSenderImpl createSender(SysMessageProvider provider, MailOptions options) {
                JavaMailSenderImpl mail = super.createSender(provider, options);
                mail.getJavaMailProperties().put("mail.smtp.ssl.socketFactory", tls.getSocketFactory());
                return mail;
            }
        };
        for (boolean implicit : new boolean[] {false, true}) {
            try (var smtp = new FakeStartTlsSmtpServer(tls, true, implicit)) {
                var provider = provider(smtp.port());
                provider.setEndpoint("localhost");
                provider.setConfigJson("{\"port\":" + smtp.port() + ",\"securityMode\":\""
                        + (implicit ? "SSL" : "STARTTLS") + "\"}");
                sender.send(provider, "recipient@example.test", "Macula", "fixture", "<b>fixture-body</b>");
                org.assertj.core.api.Assertions.assertThat(smtp.message()).contains("fixture-body");
            }
        }
    }

    /** 不支持 STARTTLS 时拒绝认证，不能静默回退到明文。 */
    @Test
    void rejectsMissingStartTlsCapability() throws Exception {
        var tls = FakeStartTlsSmtpServer.context(temporaryDirectory);
        try (var smtp = new FakeStartTlsSmtpServer(tls, false, false)) {
            var provider = provider(smtp.port());
            provider.setEndpoint("localhost");
            provider.setConfigJson("{\"port\":" + smtp.port() + ",\"securityMode\":\"STARTTLS\"}");
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> new SmtpMailSender().send(provider,
                    "recipient@example.test", "Macula", "fixture", "fixture-body")).isInstanceOf(IllegalStateException.class);
        }
    }

    /** 未受信证书及主机名不匹配都必须拒绝，不能开放 trust-all。 */
    @Test
    void rejectsUntrustedOrMismatchedStartTlsCertificate() throws Exception {
        var tls = FakeStartTlsSmtpServer.context(temporaryDirectory);
        SmtpMailSender sender = new SmtpMailSender() {
            /** 使用仅测试信任的证书，并保留主机名校验。 */
            @Override
            protected JavaMailSenderImpl createSender(SysMessageProvider provider, MailOptions options) {
                JavaMailSenderImpl mail = super.createSender(provider, options);
                mail.getJavaMailProperties().put("mail.smtp.ssl.socketFactory", tls.getSocketFactory());
                return mail;
            }
        };
        for (boolean trust : new boolean[] {false, true}) {
            try (var smtp = new FakeStartTlsSmtpServer(tls, true, false)) {
                var provider = provider(smtp.port());
                provider.setEndpoint(trust ? "127.0.0.1" : "localhost");
                provider.setConfigJson("{\"port\":" + smtp.port() + ",\"securityMode\":\"STARTTLS\"}");
                var selected = trust ? sender : new SmtpMailSender();
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> selected.send(provider,
                        "recipient@example.test", "Macula", "fixture", "fixture-body")).isInstanceOf(IllegalStateException.class);
            }
        }
    }
}
