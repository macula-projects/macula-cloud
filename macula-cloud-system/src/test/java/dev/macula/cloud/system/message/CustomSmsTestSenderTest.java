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

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import dev.macula.cloud.system.pojo.entity.SysMessageProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 自定义短信接口的默认拒绝边界测试。
 *
 * @author Rain
 * @since 2026-10-13
 */
class CustomSmsTestSenderTest {
    @TempDir Path temporaryDirectory;

    @Test
    void rejectsUnlistedOrPrivateDestinations() {
        SysMessageProvider provider = new SysMessageProvider();
        provider.setEndpoint("https://127.0.0.1/send");
        assertThrows(ResponseStatusException.class,
            () -> new CustomSmsTestSender("").send(provider, "13000000000", "test", ""));
    }

    @Test
    void postsMappedParametersAndCredentialToAllowedHttpsEndpoint() throws Exception {
        var tls = LocalTlsTestSupport.context(temporaryDirectory);
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> method = new AtomicReference<>();
        AtomicInteger status = new AtomicInteger(200);
        AtomicInteger delayMillis = new AtomicInteger();
        HttpsServer server = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(tls));
        server.createContext("/send", exchange -> {
            method.set(exchange.getRequestMethod());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            java.util.concurrent.locks.LockSupport.parkNanos(
                Duration.ofMillis(delayMillis.get()).toNanos());
            byte[] response = "{\"code\":\"OK\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            HttpClient client = HttpClient.newBuilder().sslContext(tls).connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
            CustomSmsTestSender sender = new CustomSmsTestSender("localhost", client,
                host -> new InetAddress[] {publicAddress()});
            SysMessageProvider provider = new SysMessageProvider();
            provider.setEndpoint("https://localhost:" + server.getAddress().getPort() + "/send");
            provider.setCredential("fixture-token");
            provider.setConfigJson("{\"phoneParameter\":\"mobile\",\"contentParameter\":\"text\","
                + "\"templateParameter\":\"template\",\"fixedParameters\":{\"source\":\"admin-test\"},"
                + "\"authHeader\":\"Authorization\",\"authPrefix\":\"Bearer \","
                + "\"successField\":\"code\",\"successValue\":\"OK\"}");
            assertDoesNotThrow(() -> sender.send(provider, "13800000000", "hello", "TPL-1"));
            assertEquals("POST", method.get());
            assertEquals("Bearer fixture-token", authorization.get());
            Map<?, ?> fields = JsonMapper.builder().build().readValue(body.get(), Map.class);
            assertEquals("13800000000", fields.get("mobile"));
            assertEquals("hello", fields.get("text"));
            assertEquals("TPL-1", fields.get("template"));
            assertEquals("admin-test", fields.get("source"));
            String safeConfig = provider.getConfigJson();
            provider.setConfigJson(safeConfig.replace("\"authHeader\":",
                "\"headers\":{\"X-Auth\":\"plaintext\"},\"authHeader\":"));
            assertThrows(ResponseStatusException.class,
                () -> sender.send(provider, "13800000000", "hello", "TPL-1"));
            provider.setConfigJson(safeConfig);
            status.set(500);
            assertThrows(ResponseStatusException.class,
                () -> sender.send(provider, "13800000000", "hello", "TPL-1"));
            provider.setConfigJson(provider.getConfigJson().replace("\"successValue\":\"ACCEPTED\"",
                "\"successValue\":\"OK\""));
            delayMillis.set(6000);
            assertThrows(ResponseStatusException.class,
                () -> sender.send(provider, "13800000000", "hello", "TPL-1"));
            status.set(200);
            provider.setConfigJson(provider.getConfigJson().replace("\"successValue\":\"OK\"",
                "\"successValue\":\"ACCEPTED\""));
            assertThrows(ResponseStatusException.class,
                () -> sender.send(provider, "13800000000", "hello", "TPL-1"));
        } finally {
            server.stop(0);
        }
    }

    private static InetAddress publicAddress() {
        try { return InetAddress.getByAddress(new byte[] {8, 8, 8, 8}); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
