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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 仅绑定回环地址的测试SMTP，捕获本地提交，不投递真实邮件。
 *
 * @author Macula
 * @since 6.1.0
 */
public final class FakeSmtpServer implements AutoCloseable {
    private final ServerSocket server;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Future<String> captured;

    /**
     * 使用系统分配端口，不复用邮件服务或业务凭据。
     */
    public FakeSmtpServer() throws Exception {
        server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        captured = executor.submit(this::receive);
    }

    /**
     * 返回本测试独占的SMTP端口。
     */
    public int port() {
        return server.getLocalPort();
    }

    /**
     * 等待提交完成，测试失败时有界超时而非无限阻塞。
     */
    public String message() throws Exception {
        return captured.get(10, TimeUnit.SECONDS);
    }

    /**
     * 支持JavaMail的认证及单封DATA提交，消息仅保存在内存。
     */
    private String receive() throws Exception {
        server.setSoTimeout(10000);
        try (var socket = server.accept();
             var input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             var output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                     true)) {
            socket.setSoTimeout(10000);
            reply(output, "220 localhost test SMTP");
            String data = "";
            String line;
            while ((line = input.readLine()) != null) {
                if (line.startsWith("EHLO") || line.startsWith("HELO")) {
                    reply(output, "250-localhost");
                    reply(output, "250 AUTH LOGIN PLAIN");
                } else if (line.startsWith("AUTH LOGIN")) {
                    reply(output, "334 VXNlcm5hbWU6");
                    input.readLine();
                    reply(output, "334 UGFzc3dvcmQ6");
                    input.readLine();
                    reply(output, "235 authenticated");
                } else if (line.startsWith("AUTH PLAIN")) {
                    if (line.equals("AUTH PLAIN")) {
                        reply(output, "334 ");
                        input.readLine();
                    }
                    reply(output, "235 authenticated");
                } else if (line.equals("DATA")) {
                    reply(output, "354 send data");
                    var body = new StringBuilder();
                    while ((line = input.readLine()) != null && !line.equals(".")) {
                        body.append(line).append("\r\n");
                    }
                    data = body.toString();
                    reply(output, "250 queued locally");
                } else if (line.equals("QUIT")) {
                    reply(output, "221 bye");
                    return data;
                } else {
                    reply(output, "250 ok");
                }
            }
            return data;
        }
    }

    /**
     * SMTP使用CRLF，禁止依赖宿主系统的换行符。
     */
    private void reply(PrintWriter output, String value) {
        output.print(value + "\r\n");
        output.flush();
    }

    /**
     * 只关闭本测试创建的线程与端口。
     */
    @Override
    public void close() throws Exception {
        server.close();
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }
}
