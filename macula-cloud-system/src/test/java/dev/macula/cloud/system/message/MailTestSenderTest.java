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
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.server.ResponseStatusException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 使用环回 TLS SMTP AUTH 服务验证 Exchange 邮件的实际协议发送与拒绝路径。
 *
 * @author Rain
 * @since 2026-10-13
 */
class MailTestSenderTest {
    @TempDir Path temporaryDirectory;

    @Test
    void submitsMailThroughSmtpAuthOverTls() throws Exception {
        SSLContext tls = LocalTlsTestSupport.context(temporaryDirectory);
        var executor = Executors.newSingleThreadExecutor();
        try (SSLServerSocket server = (SSLServerSocket)tls.getServerSocketFactory()
            .createServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(5000);
            var received = executor.submit(() -> receiveMessage(server));
            SysMessageProvider provider = provider(server.getLocalPort());
            MailTestSender sender = new MailTestSender() {
                @Override
                JavaMailSenderImpl createSender() {
                    JavaMailSenderImpl mail = new JavaMailSenderImpl();
                    mail.getJavaMailProperties().put("mail.smtp.ssl.socketFactory", tls.getSocketFactory());
                    return mail;
                }
            };
            sender.send(provider, "recipient@example.test", "测试邮件", "投递成功");
            String message = received.get(5, TimeUnit.SECONDS);
            assertTrue(message.contains("recipient@example.test"));
            String mimeSource = message.substring(message.indexOf("===DATA===\n") + "===DATA===\n".length());
            MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()),
                new ByteArrayInputStream(mimeSource.getBytes(StandardCharsets.UTF_8)));
            assertTrue(mime.getContent().toString().contains("投递成功"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsInvalidAddressAndIncompleteTlsSettings() {
        SysMessageProvider provider = provider(465);
        MailTestSender sender = new MailTestSender();
        assertThrows(ResponseStatusException.class,
            () -> sender.send(provider, "invalid-address", "test", "content"));
        provider.setConfigJson("{\"port\":465,\"startTls\":false,\"ssl\":false}");
        assertThrows(ResponseStatusException.class,
            () -> sender.send(provider, "recipient@example.test", "test", "content"));
    }

    private static SysMessageProvider provider(int port) {
        SysMessageProvider provider = new SysMessageProvider();
        provider.setProviderType("EXCHANGE");
        provider.setEndpoint("localhost");
        provider.setAccount("smtp-user");
        provider.setCredential("fixture-password");
        provider.setSender("sender@example.test");
        provider.setConfigJson("{\"port\":" + port + ",\"startTls\":false,\"ssl\":true}");
        return provider;
    }

    private static String receiveMessage(SSLServerSocket server) throws Exception {
        try (SSLSocket socket = (SSLSocket)server.accept();
             BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter output = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
            socket.setSoTimeout(5000);
            output.println("220 localhost ESMTP");
            StringBuilder message = new StringBuilder();
            int login = 0;
            boolean data = false;
            String line;
            while ((line = input.readLine()) != null) {
                if (data) {
                    if (".".equals(line)) {
                        output.println("250 queued");
                        data = false;
                    } else message.append(line).append('\n');
                } else if (login == 1) {
                    output.println("334 UGFzc3dvcmQ6");
                    login = 2;
                } else if (login == 2) {
                    output.println("235 authenticated");
                    login = 0;
                } else if (line.startsWith("EHLO")) {
                    output.println("250-localhost");
                    output.println("250 AUTH LOGIN PLAIN");
                } else if (line.startsWith("AUTH LOGIN")) {
                    login = line.length() > "AUTH LOGIN".length() ? 2 : 1;
                    output.println(login == 1 ? "334 VXNlcm5hbWU6" : "334 UGFzc3dvcmQ6");
                } else if (line.startsWith("AUTH PLAIN")) {
                    output.println("235 authenticated");
                } else if (line.startsWith("MAIL FROM") || line.startsWith("RCPT TO")) {
                    message.append(line).append('\n');
                    output.println("250 OK");
                } else if (line.equals("DATA")) {
                    output.println("354 end with dot");
                    message.append("===DATA===\n");
                    data = true;
                } else if (line.equals("QUIT")) {
                    output.println("221 bye");
                    break;
                } else {
                    output.println("250 OK");
                }
            }
            return message.toString();
        }
    }
}
