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

import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService.ProviderConfig;
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
        ProviderConfig config = dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.source(
                dev.macula.cloud.system.enums.IdentitySourceType.FEISHU_WEB, "FEISHU", "app", "secret",
                Map.of("appId", "app"));
        server.expect(requestTo("https://accounts.feishu.cn/oauth/v3/token"))
                .andExpect(content().string(containsString(
                        "\"redirect_uri\":\"https://iam.example/login/feishu\"")))
                .andRespond(withSuccess("{\"access_token\":\"token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://open.feishu.cn/open-apis/authen/v1/user_info"))
                .andRespond(
                        withSuccess("{\"code\":0,\"data\":{\"open_id\":\"stable-open-id\",\"employee_no\":\"E1001\"}}",
                                MediaType.APPLICATION_JSON));
        assertEquals("E1001", client.fetchIdentity(config, "code",
                        "https://iam.example/login/feishu", FeishuLoginMode.BROWSER, "BROWSER").attributes()
                .get("employee_no"));
        server.verify();
    }

    /**
     * AppLink严格发送本次回调及平台PKCE证明，不把IAM PKCE当作上游已核验。
     */
    @Test
    void appLinkSendsExactRuntimeCallbackAndProviderVerifier() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FeishuClient client = new FeishuClient(builder.build());
        String proof = "p".repeat(43);
        var config = new ProviderConfig(9L, dev.macula.cloud.system.enums.IdentitySourceType.FEISHU_APPLINK,
                false, null, 1L, "FEISHU", "app", "fixture-secret", Map.of("appId", "app"), Map.of(), true,
                dev.macula.cloud.system.enums.IdentitySourceFlow.FEISHU_APPLINK_V3,
                dev.macula.cloud.system.enums.IdentityDirectoryPolicy.IDENTITY_ONLY, null,
                "https://obsolete.example/callback");
        assertThrows(AuthenticationServiceException.class, () -> client.fetchIdentity(config, "code", null,
                FeishuLoginMode.APP, "FEISHU_APP_CODE", null));
        server.expect(requestTo("https://accounts.feishu.cn/oauth/v3/token"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(HttpMethod.POST))
                .andExpect(content().json(
                        "{\"grant_type\":\"authorization_code\",\"client_id\":\"app\"," +
                                "\"client_secret\":\"fixture-secret\","
                                +
                                "\"code\":\"code\",\"redirect_uri\":\"https://app.example/callback\"," +
                                "\"code_verifier\":\"" +
                                proof + "\"}"))
                .andRespond(withSuccess("{\"access_token\":\"fixture-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://open.feishu.cn/open-apis/authen/v1/user_info"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("Authorization",
                        "Bearer fixture-token"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"open_id\":\"open-fixture\"}}",
                        MediaType.APPLICATION_JSON));
        assertEquals("open-fixture", client.fetchIdentity(config, "code", "https://app.example/callback",
                FeishuLoginMode.APP, "FEISHU_APP_CODE", proof).identityValue());
        server.verify();
    }

    /**
     * 非AppLink合同不能通过APP模式绕过回调与专用PKCE。
     */
    @Test
    void appCodeRejectsWebContractWithoutProviderProof() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FeishuClient client = new FeishuClient(builder.build());
        ProviderConfig config = dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.source(
                dev.macula.cloud.system.enums.IdentitySourceType.FEISHU_WEB, "FEISHU", "app", "secret",
                Map.of("appId", "app"));
        assertThrows(AuthenticationServiceException.class,
                () -> client.fetchIdentity(config, "code", null, FeishuLoginMode.APP, "FEISHU_APP_CODE"));
        server.verify();
    }

    @Test
    void webviewCodeUsesV3AndResolvesVerifiedIdentity() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FeishuClient client = new FeishuClient(builder.build());
        var config = new ProviderConfig(10L, dev.macula.cloud.system.enums.IdentitySourceType.FEISHU_WEBVIEW,
                false, null, 1L, "FEISHU", "app", "fixture-secret", Map.of("appId", "app"), Map.of(), true,
                dev.macula.cloud.system.enums.IdentitySourceFlow.FEISHU_WEBVIEW_V3,
                dev.macula.cloud.system.enums.IdentityDirectoryPolicy.IDENTITY_ONLY, null, null);
        server.expect(requestTo("https://accounts.feishu.cn/oauth/v3/token"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(HttpMethod.POST))
                .andExpect(content().json("{\"grant_type\":\"authorization_code\",\"client_id\":\"app\","
                        + "\"client_secret\":\"fixture-secret\",\"code\":\"jsapi-code\"}"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("redirect_uri"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("code_verifier"))))
                .andRespond(withSuccess("{\"code\":0,\"access_token\":\"user-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://open.feishu.cn/open-apis/authen/v1/user_info"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("Authorization",
                        "Bearer user-token"))
                .andRespond(
                        withSuccess("{\"code\":0,\"data\":{\"open_id\":\"stable-open-id\",\"employee_no\":\"E1002\"}}",
                                MediaType.APPLICATION_JSON));
        var identity = client.fetchIdentity(config, "jsapi-code", null, FeishuLoginMode.WEBVIEW, "FEISHU_WEBVIEW_CODE");
        assertEquals("stable-open-id", identity.identityValue());
        assertEquals("E1002", identity.attributes().get("employee_no"));
        server.verify();
    }

    @Test
    void missingStableOpenIdFailsClosed() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FeishuClient client = new FeishuClient(builder.build());
        ProviderConfig config = dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.source(
                dev.macula.cloud.system.enums.IdentitySourceType.FEISHU_WEB, "FEISHU", "app", "secret",
                Map.of("appId", "app"));
        server.expect(requestTo("https://accounts.feishu.cn/oauth/v3/token"))
                .andRespond(withSuccess("{\"access_token\":\"token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://open.feishu.cn/open-apis/authen/v1/user_info"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{}}", MediaType.APPLICATION_JSON));
        assertThrows(AuthenticationServiceException.class, () -> client.fetchIdentity(config, "code",
                "https://iam.example/login/feishu", FeishuLoginMode.BROWSER, "BROWSER"));
    }

    @Test
    void providerErrorFailsClosedWithoutSensitiveLogs(CapturedOutput output) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FeishuClient client = new FeishuClient(builder.build());
        ProviderConfig config = dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.source(
                dev.macula.cloud.system.enums.IdentitySourceType.FEISHU_WEB, "FEISHU", "app", "client-secret-marker",
                Map.of("appId", "app"));
        server.expect(requestTo("https://accounts.feishu.cn/oauth/v3/token"))
                .andRespond(withSuccess("{\"code\":20003,\"msg\":\"provider-text-marker\"}",
                        MediaType.APPLICATION_JSON));

        assertThrows(AuthenticationServiceException.class, () -> client.fetchIdentity(config, "code-marker",
                "https://iam.example/login/feishu", FeishuLoginMode.BROWSER, "BROWSER"));
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
        ProviderConfig config = dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.source(
                dev.macula.cloud.system.enums.IdentitySourceType.FEISHU_WEB, "FEISHU", "app", "secret",
                Map.of("appId", "app"));
        server.expect(requestTo("https://accounts.feishu.cn/oauth/v3/token"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertThrows(AuthenticationServiceException.class, () -> client.fetchIdentity(config, "code",
                "https://iam.example/login/feishu", FeishuLoginMode.BROWSER, "BROWSER"));
    }

    @Test
    void nonSuccessHttpStatusFailsClosed() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FeishuClient client = new FeishuClient(builder.build());
        ProviderConfig config = dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.source(
                dev.macula.cloud.system.enums.IdentitySourceType.FEISHU_WEB, "FEISHU", "app", "secret",
                Map.of("appId", "app"));
        server.expect(requestTo("https://accounts.feishu.cn/oauth/v3/token"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThrows(AuthenticationServiceException.class, () -> client.fetchIdentity(config, "code",
                "https://iam.example/login/feishu", FeishuLoginMode.BROWSER, "BROWSER"));
    }

    @Test
    void timeoutFailsClosedWithoutLeakingExceptionMessage(CapturedOutput output) {
        RestClient restClient = RestClient.builder().requestFactory((uri, method) -> {
            throw new SocketTimeoutException("timeout-message-marker");
        }).build();
        FeishuClient client = new FeishuClient(restClient);
        ProviderConfig config = dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.source(
                dev.macula.cloud.system.enums.IdentitySourceType.FEISHU_WEB, "FEISHU", "app", "secret",
                Map.of("appId", "app"));

        assertThrows(AuthenticationServiceException.class, () -> client.fetchIdentity(config, "code",
                "https://iam.example/login/feishu", FeishuLoginMode.BROWSER, "BROWSER"));
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
