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
package dev.macula.cloud.iam.message.support;

/**
 * 支持 STARTTLS 和隐式 TLS 的独占回环 SMTP 服务，使用合成证书。
 * @author Rain
 * @since 6.1.0
 */
public final class FakeStartTlsSmtpServer implements AutoCloseable {
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
