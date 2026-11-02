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
package dev.macula.cloud.iam.authentication.wechat;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 验证微信两种凭据只接受 unionid，并禁止 HTTP 重定向。
 *
 * @author Rain
 * @since 6.1.0
 */
@ExtendWith(OutputCaptureExtension.class)
class WechatClientTest {
    @Test
    void bothModesReturnTheSameUnionId() {
        ProviderConfig config = new ProviderConfig("WECHAT", "site", "site-secret",
            Map.of("appId", "site", "officialAccountAppId", "oa"),
            Map.of("officialAccountAppSecret", "oa-secret"));
        for (WechatLoginMode mode : new WechatLoginMode[] {WechatLoginMode.BROWSER, WechatLoginMode.WEBVIEW}) {
            RestClient.Builder builder = RestClient.builder();
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            WechatClient client = new WechatClient(builder.build());
            server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                    "https://api.weixin.qq.com/sns/oauth2/access_token?")))
                .andRespond(withSuccess("{\"access_token\":\"token\",\"unionid\":\"union-01\"}",
                    MediaType.APPLICATION_JSON));
            assertEquals("union-01", client.fetchUnionId(config, "code", mode));
            server.verify();
        }
    }

    @Test
    void mobileAppCodeUsesMobileApplicationAndRequiresUnionId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WechatClient client = new WechatClient(builder.build());
        ProviderConfig config = new ProviderConfig("WECHAT", "site", "site-secret",
            Map.of("mobileAppId", "mobile-app"), Map.of("mobileAppSecret", "mobile-secret"));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                "https://api.weixin.qq.com/sns/oauth2/access_token?appid=mobile-app")))
            .andRespond(withSuccess("{\"unionid\":\"union-mobile\"}", MediaType.APPLICATION_JSON));

        assertEquals("union-mobile", client.fetchOpenAppUnionId(config, "mobile-code"));
        server.verify();
    }

    @Test
    void miniappLoginCodeRequiresUnionId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WechatClient client = new WechatClient(builder.build());
        ProviderConfig config = new ProviderConfig("WECHAT", "site", "site-secret",
            Map.of("miniAppId", "mini-app"), Map.of("miniAppSecret", "mini-secret"));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                "https://api.weixin.qq.com/sns/jscode2session?appid=mini-app")))
            .andRespond(withSuccess("{\"openid\":\"open-mini\",\"unionid\":\"union-mini\"}",
                MediaType.APPLICATION_JSON));

        assertEquals("union-mini", client.fetchMiniappUnionId(config, "mini-code"));
        server.verify();
    }

    @Test
    void miniappPhoneCodeReturnsOnlyPhoneNumber() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WechatClient client = new WechatClient(builder.build());
        ProviderConfig config = new ProviderConfig("WECHAT", "site", "site-secret",
            Map.of("miniAppId", "mini-app"), Map.of("miniAppSecret", "mini-secret"));
        server.expect(requestTo("https://api.weixin.qq.com/cgi-bin/stable_token"))
            .andRespond(withSuccess("{\"access_token\":\"mini-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                "https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=mini-token"))
            .andRespond(withSuccess("{\"errcode\":0,\"phone_info\":{\"phoneNumber\":\"13800000000\"}}",
                MediaType.APPLICATION_JSON));

        assertEquals("13800000000", client.fetchMiniappMobile(config, "phone-code"));
        server.verify();
    }

    @Test
    void openIdWithoutUnionIdFailsClosed() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WechatClient client = new WechatClient(builder.build());
        ProviderConfig config = new ProviderConfig("WECHAT", "site", "site-secret", Map.of("appId", "site"));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                "https://api.weixin.qq.com/sns/oauth2/access_token?")))
            .andRespond(withSuccess("{\"access_token\":\"token\",\"openid\":\"open-01\"}",
                MediaType.APPLICATION_JSON));
        assertThrows(AuthenticationServiceException.class,
            () -> client.fetchUnionId(config, "code", WechatLoginMode.BROWSER));
    }

    @Test
    void providerErrorFailsClosedWithoutSensitiveLogs(CapturedOutput output) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WechatClient client = new WechatClient(builder.build());
        ProviderConfig config = new ProviderConfig("WECHAT", "site", "client-secret-marker",
            Map.of("appId", "site"));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                "https://api.weixin.qq.com/sns/oauth2/access_token?")))
            .andRespond(withSuccess("{\"errcode\":40029,\"errmsg\":\"provider-text-marker\"}",
                MediaType.APPLICATION_JSON));

        assertThrows(AuthenticationServiceException.class,
            () -> client.fetchUnionId(config, "code-marker", WechatLoginMode.BROWSER));
        String logs = output.toString();
        assertTrue(logs.contains("provider=WECHAT"));
        assertTrue(logs.contains("errcode=40029"));
        assertFalse(logs.contains("code-marker"));
        assertFalse(logs.contains("client-secret-marker"));
        assertFalse(logs.contains("provider-text-marker"));
    }

    @Test
    void malformedResponseFailsClosed() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WechatClient client = new WechatClient(builder.build());
        ProviderConfig config = new ProviderConfig("WECHAT", "site", "site-secret", Map.of("appId", "site"));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                "https://api.weixin.qq.com/sns/oauth2/access_token?")))
            .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertThrows(AuthenticationServiceException.class,
            () -> client.fetchUnionId(config, "code", WechatLoginMode.BROWSER));
    }

    @Test
    void nonSuccessHttpStatusFailsClosed() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WechatClient client = new WechatClient(builder.build());
        ProviderConfig config = new ProviderConfig("WECHAT", "site", "site-secret", Map.of("appId", "site"));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                "https://api.weixin.qq.com/sns/oauth2/access_token?")))
            .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThrows(AuthenticationServiceException.class,
            () -> client.fetchUnionId(config, "code", WechatLoginMode.BROWSER));
    }

    @Test
    void timeoutFailsClosedWithoutLeakingExceptionMessage(CapturedOutput output) {
        RestClient restClient = RestClient.builder().requestFactory((uri, method) -> {
            throw new SocketTimeoutException("timeout-message-marker");
        }).build();
        WechatClient client = new WechatClient(restClient);
        ProviderConfig config = new ProviderConfig("WECHAT", "site", "secret", Map.of("appId", "site"));

        assertThrows(AuthenticationServiceException.class,
            () -> client.fetchUnionId(config, "code", WechatLoginMode.BROWSER));
        assertFalse(output.toString().contains("timeout-message-marker"));
    }

    @Test
    void requestFactoryDoesNotFollowRedirectLocation() throws Exception {
        AtomicInteger targetRequests = new AtomicInteger();
        HttpServer server = redirectServer(targetRequests);
        try {
            URI start = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/start");
            try (var response = WechatClient.noRedirectRequestFactory()
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
