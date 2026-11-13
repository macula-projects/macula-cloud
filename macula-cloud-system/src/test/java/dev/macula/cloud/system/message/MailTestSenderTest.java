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

import dev.macula.cloud.system.message.mail.impl.MailTestSender;


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
 * 使用环回 TLS SMTP AUTH 服务验证固定邮件服务的实际协议发送与拒绝路径。
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
                protected JavaMailSenderImpl createSender() {
                    JavaMailSenderImpl mail = new JavaMailSenderImpl();
                    mail.getJavaMailProperties().put("mail.smtp.ssl.socketFactory", tls.getSocketFactory());
                    return mail;
                }
            };
            sender.send(provider, "recipient@example.test", "Macula Cloud", "测试邮件", "投递成功");
            String message = received.get(5, TimeUnit.SECONDS);
            assertTrue(message.contains("recipient@example.test"));
            String mimeSource = message.substring(message.indexOf("===DATA===\n") + "===DATA===\n".length());
            MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()),
                new ByteArrayInputStream(mimeSource.getBytes(StandardCharsets.UTF_8)));
            assertTrue(mime.getContent().toString().contains("投递成功"));
            assertTrue(mime.getFrom()[0].toString().contains("Macula Cloud"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsInvalidAddressAndIncompleteTlsSettings() {
        SysMessageProvider provider = provider(465);
        MailTestSender sender = new MailTestSender();
        assertThrows(ResponseStatusException.class,
            () -> sender.send(provider, "invalid-address", "Macula Cloud", "test", "content"));
        provider.setConfigJson("{\"port\":465}");
        assertThrows(ResponseStatusException.class,
            () -> sender.send(provider, "recipient@example.test", "Macula Cloud", "test", "content"));
    }

    private static SysMessageProvider provider(int port) {
        SysMessageProvider provider = new SysMessageProvider();
        provider.setProviderType("CUSTOM");
        provider.setEndpoint("localhost");
        provider.setAccount("smtp-user");
        provider.setCredential("fixture-password");
        provider.setSender("sender@example.test");
        provider.setConfigJson("{\"port\":" + port + ",\"securityMode\":\"SSL\"}");
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
                    } else {
                        message.append(line).append('\n');
                    }
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
/**
 * 支持 STARTTLS 和隐式 TLS 的独占回环 SMTP 服务，使用合成证书。
 * @since 6.1.0
 */
private static final class FakeStartTlsSmtpServer implements AutoCloseable {
    private final java.net.ServerSocket server;
    private final javax.net.ssl.SSLContext tls;
    private final boolean advertise;
    private final boolean implicit;
    private final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.concurrent.Future<String> captured;
    private volatile java.net.Socket current;

    /** 创建无真实收件人的单连接 SMTP 服务。 */
    public FakeStartTlsSmtpServer(javax.net.ssl.SSLContext tls, boolean advertise, boolean implicit) throws Exception {
        this.tls = tls;
        this.advertise = advertise;
        this.implicit = implicit;
        server = implicit ? tls.getServerSocketFactory().createServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())
                : new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress());
        server.setSoTimeout(8000);
        captured = executor.submit(this::receive);
    }

    /** 返回本测试独占端口。 */
    public int port() {
        return server.getLocalPort();
    }

    /** 有界等待服务端捕获的邮件。 */
    public String message() throws Exception {
        return captured.get(8, java.util.concurrent.TimeUnit.SECONDS);
    }

    /** 只在 STARTTLS 握手成功后公布认证能力。 */
    private String receive() throws Exception {
        try (var raw = server.accept()) {
            current = raw;
            raw.setSoTimeout(5000);
            var input = input(raw);
            var output = output(raw);
            boolean encrypted = implicit;
            reply(output, "220 localhost fixture");
            String line;
            StringBuilder body = new StringBuilder();
            while ((line = input.readLine()) != null) {
                if (line.startsWith("EHLO") || line.startsWith("HELO")) {
                    reply(output, "250-localhost");
                    reply(output, advertise && !encrypted ? "250 STARTTLS" : "250 AUTH PLAIN LOGIN");
                } else if (line.equals("STARTTLS")) {
                    reply(output, "220 Ready for TLS");
                    var secure = (javax.net.ssl.SSLSocket) tls.getSocketFactory()
                            .createSocket(raw, "localhost", raw.getPort(), true);
                    secure.setUseClientMode(false);
                    secure.setSoTimeout(5000);
                    current = secure;
                    secure.startHandshake();
                    input = input(secure);
                    output = output(secure);
                    encrypted = true;
                } else if (line.startsWith("AUTH PLAIN")) {
                    if (line.equals("AUTH PLAIN")) {
                        reply(output, "334 ");
                        input.readLine();
                    }
                    reply(output, "235 authenticated");
                } else if (line.startsWith("AUTH LOGIN")) {
                    reply(output, "334 VXNlcm5hbWU6");
                    input.readLine();
                    reply(output, "334 UGFzc3dvcmQ6");
                    input.readLine();
                    reply(output, "235 authenticated");
                } else if (line.equals("DATA")) {
                    reply(output, "354 data");
                    while ((line = input.readLine()) != null && !line.equals(".")) {
                        body.append(line).append('\n');
                    }
                    reply(output, "250 queued");
                } else if (line.equals("QUIT")) {
                    reply(output, "221 bye");
                    return body.toString();
                } else {
                    reply(output, "250 ok");
                }
            }
            return body.toString();
        }
    }

    /** 为当前连接建立协议读取器。 */
    private java.io.BufferedReader input(java.net.Socket socket) throws Exception {
        return new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(),
                java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 为当前连接建立协议写入器。 */
    private java.io.PrintWriter output(java.net.Socket socket) throws Exception {
        return new java.io.PrintWriter(socket.getOutputStream(), false, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** SMTP 必须使用 CRLF，不能依赖宿主操作系统换行。 */
    private void reply(java.io.PrintWriter output, String line) {
        output.print(line + "\r\n");
        output.flush();
    }

    /** 为单个测试生成可信的 localhost 证书，不修改 JVM 全局信任配置。 */
    public static javax.net.ssl.SSLContext context(java.nio.file.Path directory) throws Exception {
        var file = directory.resolve("fixture.p12");
        Process process = new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", "fixture", "-keyalg", "RSA", "-keysize", "2048", "-keystore", file.toString(),
                "-storetype", "PKCS12", "-storepass", "changeit", "-keypass", "changeit", "-dname", "CN=localhost",
                "-ext", "SAN=dns:localhost", "-validity", "1", "-noprompt").redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IllegalStateException("测试证书生成失败: " + output);
        }
        var store = java.security.KeyStore.getInstance(file.toFile(), "changeit".toCharArray());
        var keys = javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, "changeit".toCharArray());
        var trust = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
        trust.init(store);
        var context = javax.net.ssl.SSLContext.getInstance("TLS");
        context.init(keys.getKeyManagers(), trust.getTrustManagers(), null);
        return context;
    }

    /** 关闭独占连接和线程，即使握手失败也不遗留阻塞任务。 */
    @Override
    public void close() throws Exception {
        server.close();
        if (current != null) {
            current.close();
        }
        executor.shutdownNow();
        executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
    }
}


    /** TLS 升级成功才发送邮件，旧隐式 SSL 模式同样可以提交。 */
    @Test
    void submitsStartTlsAndImplicitTlsWithVerifiedCertificate() throws Exception {
        var tls = FakeStartTlsSmtpServer.context(temporaryDirectory);
        MailTestSender sender = new MailTestSender() {
            /** 使用本测试证书信任库，不影响全局邮件或 TLS 设置。 */
            @Override
            protected JavaMailSenderImpl createSender() {
                JavaMailSenderImpl mail = new JavaMailSenderImpl();
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
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> new MailTestSender().send(provider,
                    "recipient@example.test", "Macula", "fixture", "fixture-body")).isInstanceOf(ResponseStatusException.class);
        }
    }

    /** 未受信证书及主机名不匹配都必须拒绝，不能开放 trust-all。 */
    @Test
    void rejectsUntrustedOrMismatchedStartTlsCertificate() throws Exception {
        var tls = FakeStartTlsSmtpServer.context(temporaryDirectory);
        MailTestSender sender = new MailTestSender() {
            /** 使用本测试证书信任库，不影响全局邮件或 TLS 设置。 */
            @Override
            protected JavaMailSenderImpl createSender() {
                JavaMailSenderImpl mail = new JavaMailSenderImpl();
                mail.getJavaMailProperties().put("mail.smtp.ssl.socketFactory", tls.getSocketFactory());
                return mail;
            }
        };
        for (boolean trust : new boolean[] {false, true}) {
            try (var smtp = new FakeStartTlsSmtpServer(tls, true, false)) {
                var provider = provider(smtp.port());
                provider.setEndpoint(trust ? "127.0.0.1" : "localhost");
                provider.setConfigJson("{\"port\":" + smtp.port() + ",\"securityMode\":\"STARTTLS\"}");
                var selected = trust ? sender : new MailTestSender();
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> selected.send(provider,
                        "recipient@example.test", "Macula", "fixture", "fixture-body")).isInstanceOf(ResponseStatusException.class);
            }
        }
    }
}
