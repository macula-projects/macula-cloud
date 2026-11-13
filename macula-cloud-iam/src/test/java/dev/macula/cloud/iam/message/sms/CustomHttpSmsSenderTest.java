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
package dev.macula.cloud.iam.message.sms;

import com.sun.net.httpserver.HttpServer;
import dev.macula.cloud.iam.message.sms.impl.CustomHttpSmsSender;
import dev.macula.cloud.iam.pojo.entity.SysMessageProvider;
import dev.macula.cloud.iam.pojo.entity.SysMessageTemplate;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 两服务共享固定请求和成功判定样例；全部请求只发送到本地模拟服务。
 * @author Rain
 * @since 6.1.0
 */
class CustomHttpSmsSenderTest {
    private final JsonMapper json = JsonMapper.builder().build();

    /** 捕获实际 POST 请求，拒绝把模板外字段传递给短信服务。 */
    @Test
    void submitsTheAgreedContract() throws Exception {
        var body = new AtomicReference<String>();
        var method = new AtomicReference<String>();
        var auth = new AtomicReference<String>();
        HttpServer server = server();
        server.createContext("/send", exchange -> {
            method.set(exchange.getRequestMethod());
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"success\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            send(provider(server));
            assertThat(method.get()).isEqualTo("POST");
            assertThat(auth.get()).isEqualTo("Bearer fixture-only");
            assertThat(json.readTree(body.get())).isEqualTo(json.readTree("""
                    {"phone":"+8613800000000","signName":"fixture-sign","templateCode":"LOGIN_001",
                     "templateParams":{"code":"123456"},"scene":"SMS_LOGIN","languageCode":"zh-CN"}
                    """));
        } finally {
            server.stop(0);
        }
    }

    /** 业务失败、格式错误、超大响应和跳转都不能被当成受理成功。 */
    @Test
    void failsClosedWithoutFollowingRedirects() throws Exception {
        AtomicReference<String> responseBody = new AtomicReference<>();
        AtomicInteger status = new AtomicInteger(200);
        AtomicInteger redirected = new AtomicInteger();
        HttpServer server = server();
        server.createContext("/send", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Location", "/other");
            byte[] bytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.createContext("/other", exchange -> {
            redirected.incrementAndGet();
            exchange.close();
        });
        server.start();
        try {
            for (String value : java.util.List.of("{\"success\":false}", "{}", "{\"success\":\"true\"}",
                    "", "not-json", "x".repeat(65537))) {
                responseBody.set(value);
                assertThatThrownBy(() -> send(provider(server))).isInstanceOf(IllegalStateException.class)
                        .hasMessage("自定义短信提交失败");
            }
            responseBody.set("{\"success\":true}");
            for (int value : new int[] {302, 400, 500}) {
                status.set(value);
                assertThatThrownBy(() -> send(provider(server))).isInstanceOf(IllegalStateException.class);
            }
            assertThat(redirected.get()).isZero();
        } finally {
            server.stop(0);
        }
    }

    /** 超时覆盖已返回响应头但未完成的正文，并且不重新提交请求。 */
    @Test
    void timesOutWhileReadingSlowResponseBody() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        HttpServer server = server();
        server.setExecutor(executor);
        server.createContext("/send", exchange -> {
            calls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write('{');
            exchange.getResponseBody().flush();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        long start = System.nanoTime();
        try {
            assertThatThrownBy(() -> send(provider(server))).isInstanceOf(IllegalStateException.class);
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isBetween(4000L, 8000L);
            assertThat(calls.get()).isEqualTo(1);
        } finally {
            release.countDown();
            server.stop(0);
            executor.shutdownNow();
        }
    }

    /** 创建独占回环地址及随机端口的服务。 */
    private HttpServer server() throws Exception {
        return HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    }

    /** 仅构造合成配置，不使用真实手机号和凭据。 */
    private SysMessageProvider provider(HttpServer server) {
        var provider = new SysMessageProvider();
        provider.setProviderType("CUSTOM_HTTP");
        provider.setEndpoint("http://localhost:" + server.getAddress().getPort() + "/send");
        provider.setCredential("{\"Authorization\":\"Bearer fixture-only\"}");
        provider.setSender("fixture-sign");
        return provider;
    }

    /** 使用与生产相同的适配器生成请求，保持两服务的契约样例一致。 */
    private void send(SysMessageProvider provider) {
        var template = new SysMessageTemplate();
        template.setProviderTemplateCode("LOGIN_001");
        new CustomHttpSmsSender().send(provider, template, "+8613800000000", "123456");
    }
}
