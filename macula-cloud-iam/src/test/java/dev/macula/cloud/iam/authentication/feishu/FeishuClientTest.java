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
package dev.macula.cloud.iam.authentication.feishu;

import com.sun.net.httpserver.HttpServer;
import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService.ProviderConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 验证飞书换码、employee_no 提取及禁止 HTTP 重定向。
 *
 * @author Rain
 * @since 6.1.0
 */
@ExtendWith(OutputCaptureExtension.class)
class FeishuClientTest {
    @Test
    void browserCodeResolvesOnlyNonBlankEmployeeNumber() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FeishuClient client = new FeishuClient(builder.build());
        ProviderConfig config = new ProviderConfig("FEISHU", "app", "secret", Map.of("appId", "app"));
        server.expect(requestTo("https://open.feishu.cn/open-apis/authen/v2/oauth/token"))
            .andExpect(content().string(containsString(
                "\"redirect_uri\":\"https://iam.example/login/feishu\"")))
            .andRespond(withSuccess("{\"access_token\":\"token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://open.feishu.cn/open-apis/authen/v1/user_info"))
            .andRespond(withSuccess("{\"code\":0,\"data\":{\"employee_no\":\"E1001\"}}",
                MediaType.APPLICATION_JSON));
        assertEquals("E1001", client.fetchEmployeeNo(config, "code",
            "https://iam.example/login/feishu", FeishuLoginMode.BROWSER));
        server.verify();
    }

    @Test
    void appCodeUsesOauthTokenEndpointWithoutRedirectUri() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FeishuClient client = new FeishuClient(builder.build());
        ProviderConfig config = new ProviderConfig("FEISHU", "app", "secret", Map.of("appId", "app"));
        server.expect(requestTo("https://open.feishu.cn/open-apis/authen/v2/oauth/token"))
            .andRespond(withSuccess("{\"access_token\":\"token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://open.feishu.cn/open-apis/authen/v1/user_info"))
            .andRespond(withSuccess("{\"code\":0,\"data\":{\"employee_no\":\"E1002\"}}",
                MediaType.APPLICATION_JSON));

        assertEquals("E1002", client.fetchEmployeeNo(config, "code", null, FeishuLoginMode.APP));
        server.verify();
    }

    @Test
    void webviewCodeResolvesOnlyEmployeeNumber() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FeishuClient client = new FeishuClient(builder.build());
        ProviderConfig config = new ProviderConfig("FEISHU", "app", "secret", Map.of("appId", "app"));
        server.expect(requestTo("https://open.feishu.cn/open-apis/auth/v3/app_access_token/internal"))
            .andRespond(withSuccess("{\"code\":0,\"app_access_token\":\"app-token\"}",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://open.feishu.cn/open-apis/authen/v1/access_token"))
            .andRespond(withSuccess("{\"code\":0,\"data\":{\"access_token\":\"user-token\"}}",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://open.feishu.cn/open-apis/authen/v1/user_info"))
            .andRespond(withSuccess("{\"code\":0,\"data\":{\"employee_no\":\"E1002\"}}",
                MediaType.APPLICATION_JSON));

        assertEquals("E1002", client.fetchEmployeeNo(config, "code", null, FeishuLoginMode.WEBVIEW));
        server.verify();
    }

    @Test
    void missingEmployeeNumberFailsClosed() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FeishuClient client = new FeishuClient(builder.build());
        ProviderConfig config = new ProviderConfig("FEISHU", "app", "secret", Map.of("appId", "app"));
        server.expect(requestTo("https://open.feishu.cn/open-apis/authen/v2/oauth/token"))
            .andRespond(withSuccess("{\"access_token\":\"token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://open.feishu.cn/open-apis/authen/v1/user_info"))
            .andRespond(withSuccess("{\"code\":0,\"data\":{}}", MediaType.APPLICATION_JSON));
        assertThrows(AuthenticationServiceException.class, () -> client.fetchEmployeeNo(config, "code",
            "https://iam.example/login/feishu", FeishuLoginMode.BROWSER));
    }

    @Test
    void providerErrorFailsClosedWithoutSensitiveLogs(CapturedOutput output) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FeishuClient client = new FeishuClient(builder.build());
        ProviderConfig config = new ProviderConfig("FEISHU", "app", "client-secret-marker",
            Map.of("appId", "app"));
        server.expect(requestTo("https://open.feishu.cn/open-apis/authen/v2/oauth/token"))
            .andRespond(withSuccess("{\"code\":20003,\"msg\":\"provider-text-marker\"}",
                MediaType.APPLICATION_JSON));

        assertThrows(AuthenticationServiceException.class, () -> client.fetchEmployeeNo(config, "code-marker",
            "https://iam.example/login/feishu", FeishuLoginMode.BROWSER));
        String logs = output.toString();
        assertTrue(logs.contains("provider=FEISHU"));
        assertTrue(logs.contains("errcode=20003"));
        assertFalse(logs.contains("code-marker"));
        assertFalse(logs.contains("client-secret-marker"));
        assertFalse(logs.contains("provider-text-marker"));
    }

    @Test
    void malformedResponseFailsClosed() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FeishuClient client = new FeishuClient(builder.build());
        ProviderConfig config = new ProviderConfig("FEISHU", "app", "secret", Map.of("appId", "app"));
        server.expect(requestTo("https://open.feishu.cn/open-apis/authen/v2/oauth/token"))
            .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertThrows(AuthenticationServiceException.class, () -> client.fetchEmployeeNo(config, "code",
            "https://iam.example/login/feishu", FeishuLoginMode.BROWSER));
    }

    @Test
    void nonSuccessHttpStatusFailsClosed() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FeishuClient client = new FeishuClient(builder.build());
        ProviderConfig config = new ProviderConfig("FEISHU", "app", "secret", Map.of("appId", "app"));
        server.expect(requestTo("https://open.feishu.cn/open-apis/authen/v2/oauth/token"))
            .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThrows(AuthenticationServiceException.class, () -> client.fetchEmployeeNo(config, "code",
            "https://iam.example/login/feishu", FeishuLoginMode.BROWSER));
    }

    @Test
    void timeoutFailsClosedWithoutLeakingExceptionMessage(CapturedOutput output) {
        RestClient restClient = RestClient.builder().requestFactory((uri, method) -> {
            throw new SocketTimeoutException("timeout-message-marker");
        }).build();
        FeishuClient client = new FeishuClient(restClient);
        ProviderConfig config = new ProviderConfig("FEISHU", "app", "secret", Map.of("appId", "app"));

        assertThrows(AuthenticationServiceException.class, () -> client.fetchEmployeeNo(config, "code",
            "https://iam.example/login/feishu", FeishuLoginMode.BROWSER));
        assertFalse(output.toString().contains("timeout-message-marker"));
    }

    @Test
    void requestFactoryDoesNotFollowRedirectLocation() throws Exception {
        AtomicInteger targetRequests = new AtomicInteger();
        HttpServer server = redirectServer(targetRequests);
        try {
            URI start = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/start");
            try (var response = FeishuClient.noRedirectRequestFactory()
                .createRequest(start, HttpMethod.GET).execute()) {
                assertEquals(HttpStatus.FOUND, response.getStatusCode());
            }
            assertEquals(0, targetRequests.get());
        } finally {
            server.stop(0);
        }
    }

    private HttpServer redirectServer(AtomicInteger targetRequests) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/start", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" +
                exchange.getLocalAddress().getPort() + "/target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/target", exchange -> {
            targetRequests.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        return server;
    }
}
