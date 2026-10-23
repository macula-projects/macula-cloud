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
package dev.macula.cloud.iam.authentication.dingtalk;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 验证钉钉 APP、网页身份到组织 userId 的解析及禁止 HTTP 重定向。
 *
 * @author Rain
 * @since 6.1.0
 */
@ExtendWith(OutputCaptureExtension.class)
class DingtalkClientTest {
    @Test
    void appCodeResolvesToOrganizationMember() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DingtalkClient client = new DingtalkClient(builder.build());
        ProviderConfig config = new ProviderConfig("DINGTALK", "app", "secret",
            Map.of("appKey", "app", "corpId", "corp"));
        server.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/accessToken"))
            .andRespond(withSuccess("{\"accessToken\":\"app-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/userAccessToken"))
            .andRespond(withSuccess("{\"accessToken\":\"user-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.dingtalk.com/v1.0/contact/users/me"))
            .andRespond(withSuccess("{\"unionId\":\"union-00\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(
            "https://oapi.dingtalk.com/topapi/user/getbyunionid?access_token=app-token"))
            .andRespond(withSuccess("{\"errcode\":0,\"result\":{\"userid\":\"manager00\"}}",
                MediaType.APPLICATION_JSON));

        assertEquals("manager00", client.fetchUserId(config, "app-code", DingtalkLoginMode.APP));
        server.verify();
    }

    @Test
    void browserUnionIdMustResolveToOrganizationMember() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DingtalkClient client = new DingtalkClient(builder.build());
        ProviderConfig config = new ProviderConfig("DINGTALK", "app", "secret",
            Map.of("appKey", "app", "corpId", "corp"));
        server.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/accessToken"))
            .andRespond(withSuccess("{\"accessToken\":\"app-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/userAccessToken"))
            .andRespond(withSuccess("{\"accessToken\":\"user-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.dingtalk.com/v1.0/contact/users/me"))
            .andRespond(withSuccess("{\"unionId\":\"union-01\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(
            "https://oapi.dingtalk.com/topapi/user/getbyunionid?access_token=app-token"))
            .andRespond(withSuccess("{\"errcode\":0,\"result\":{\"userid\":\"manager01\"}}",
                MediaType.APPLICATION_JSON));
        assertEquals("manager01", client.fetchUserId(config, "code", DingtalkLoginMode.BROWSER));
        server.verify();
    }

    @Test
    void nonMemberFailsClosed() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DingtalkClient client = new DingtalkClient(builder.build());
        ProviderConfig config = new ProviderConfig("DINGTALK", "app", "secret",
            Map.of("appKey", "app", "corpId", "corp"));
        server.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/accessToken"))
            .andRespond(withSuccess("{\"accessToken\":\"app-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/userAccessToken"))
            .andRespond(withSuccess("{\"accessToken\":\"user-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.dingtalk.com/v1.0/contact/users/me"))
            .andRespond(withSuccess("{\"unionId\":\"union-01\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(
            "https://oapi.dingtalk.com/topapi/user/getbyunionid?access_token=app-token"))
            .andRespond(withSuccess("{\"errcode\":0,\"result\":{}}", MediaType.APPLICATION_JSON));
        assertThrows(AuthenticationServiceException.class,
            () -> client.fetchUserId(config, "code", DingtalkLoginMode.BROWSER));
    }

    @Test
    void webviewCodeResolvesDirectlyToOrganizationUserId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DingtalkClient client = new DingtalkClient(builder.build());
        ProviderConfig config = new ProviderConfig("DINGTALK", "app", "secret",
            Map.of("appKey", "app", "corpId", "corp"));
        server.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/accessToken"))
            .andRespond(withSuccess("{\"accessToken\":\"app-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(
            "https://oapi.dingtalk.com/topapi/v2/user/getuserinfo?access_token=app-token"))
            .andRespond(withSuccess("{\"errcode\":0,\"result\":{\"userid\":\"manager01\"}}",
                MediaType.APPLICATION_JSON));

        assertEquals("manager01", client.fetchUserId(config, "code", DingtalkLoginMode.WEBVIEW));
        server.verify();
    }

    @Test
    void providerErrorFailsClosedWithoutSensitiveLogs(CapturedOutput output) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DingtalkClient client = new DingtalkClient(builder.build());
        ProviderConfig config = new ProviderConfig("DINGTALK", "app", "client-secret-marker",
            Map.of("appKey", "app", "corpId", "corp"));
        server.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/accessToken"))
            .andRespond(withSuccess("{\"accessToken\":\"access-token-marker\"}",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(
            "https://oapi.dingtalk.com/topapi/v2/user/getuserinfo?access_token=access-token-marker"))
            .andRespond(withSuccess("{\"errcode\":60011,\"errmsg\":\"provider-text-marker\"}",
                MediaType.APPLICATION_JSON));

        assertThrows(AuthenticationServiceException.class,
            () -> client.fetchUserId(config, "code-marker", DingtalkLoginMode.WEBVIEW));
        String logs = output.toString();
        assertTrue(logs.contains("provider=DINGTALK"));
        assertTrue(logs.contains("errcode=60011"));
        assertFalse(logs.contains("code-marker"));
        assertFalse(logs.contains("access-token-marker"));
        assertFalse(logs.contains("client-secret-marker"));
        assertFalse(logs.contains("provider-text-marker"));
    }

    @Test
    void malformedResponseFailsClosed() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DingtalkClient client = new DingtalkClient(builder.build());
        ProviderConfig config = new ProviderConfig("DINGTALK", "app", "secret",
            Map.of("appKey", "app", "corpId", "corp"));
        server.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/accessToken"))
            .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertThrows(AuthenticationServiceException.class,
            () -> client.fetchUserId(config, "code", DingtalkLoginMode.WEBVIEW));
    }

    @Test
    void nonSuccessHttpStatusFailsClosed() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DingtalkClient client = new DingtalkClient(builder.build());
        ProviderConfig config = new ProviderConfig("DINGTALK", "app", "secret",
            Map.of("appKey", "app", "corpId", "corp"));
        server.expect(requestTo("https://api.dingtalk.com/v1.0/oauth2/accessToken"))
            .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThrows(AuthenticationServiceException.class,
            () -> client.fetchUserId(config, "code", DingtalkLoginMode.WEBVIEW));
    }

    @Test
    void timeoutFailsClosedWithoutLeakingExceptionMessage(CapturedOutput output) {
        RestClient restClient = RestClient.builder().requestFactory((uri, method) -> {
            throw new SocketTimeoutException("timeout-message-marker");
        }).build();
        DingtalkClient client = new DingtalkClient(restClient);
        ProviderConfig config = new ProviderConfig("DINGTALK", "app", "secret",
            Map.of("appKey", "app", "corpId", "corp"));

        assertThrows(AuthenticationServiceException.class,
            () -> client.fetchUserId(config, "code", DingtalkLoginMode.WEBVIEW));
        assertFalse(output.toString().contains("timeout-message-marker"));
    }

    @Test
    void requestFactoryDoesNotFollowRedirectLocation() throws Exception {
        AtomicInteger targetRequests = new AtomicInteger();
        HttpServer server = redirectServer(targetRequests);
        try {
            URI start = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/start");
            try (var response = DingtalkClient.noRedirectRequestFactory()
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
