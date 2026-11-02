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
package dev.macula.cloud.iam.authentication.wecom;

import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService.ProviderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 验证企业微信各类 code 按官方响应字段解析成员标识。
 *
 * @author Rain
 * @since 6.1.0
 */
class WecomClientTest {
    @Test
    void nativeAppCodeReturnsDocumentedUserIdField() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WecomClient client = new WecomClient(builder.build());
        ProviderConfig config = new ProviderConfig("WECOM", "corp", "secret",
            Map.of("corpId", "corp", "agentId", "agent"));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                "https://qyapi.weixin.qq.com/cgi-bin/gettoken?")))
            .andRespond(withSuccess("{\"errcode\":0,\"access_token\":\"access-token\"}",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                "https://qyapi.weixin.qq.com/cgi-bin/user/getuserinfo?")))
            .andExpect(queryParam("access_token", "access-token"))
            .andExpect(queryParam("code", "app-code"))
            .andRespond(withSuccess("{\"errcode\":0,\"UserId\":\"member00\"}",
                MediaType.APPLICATION_JSON));

        assertEquals("member00", client.fetchAppUserId(config, "app-code"));
        server.verify();
    }

    @Test
    void memberCodeReturnsLowercaseUseridField() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WecomClient client = new WecomClient(builder.build());
        ProviderConfig config = new ProviderConfig("WECOM", "corp", "secret",
            Map.of("corpId", "corp", "agentId", "agent"));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                "https://qyapi.weixin.qq.com/cgi-bin/gettoken?")))
            .andRespond(withSuccess("{\"errcode\":0,\"access_token\":\"access-token\"}",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo?")))
            .andRespond(withSuccess("{\"errcode\":0,\"userid\":\"member01\"}",
                MediaType.APPLICATION_JSON));

        assertEquals("member01", client.fetchUserId(config, "member-code"));
        server.verify();
    }

    @Test
    void differentlyCasedUserIdFieldIsRejected() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WecomClient client = new WecomClient(builder.build());
        ProviderConfig config = new ProviderConfig("WECOM", "corp", "secret",
            Map.of("corpId", "corp", "agentId", "agent"));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                "https://qyapi.weixin.qq.com/cgi-bin/gettoken?")))
            .andRespond(withSuccess("{\"errcode\":0,\"access_token\":\"access-token\"}",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo?")))
            .andRespond(withSuccess("{\"errcode\":0,\"UserId\":\"member01\"}",
                MediaType.APPLICATION_JSON));

        assertThrows(AuthenticationServiceException.class,
            () -> client.fetchUserId(config, "member-code"));
    }

    @Test
    void miniAppCodeReturnsLowercaseUseridField() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WecomClient client = new WecomClient(builder.build());
        ProviderConfig config = new ProviderConfig("WECOM", "corp", "secret",
            Map.of("corpId", "corp", "agentId", "agent"));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                "https://qyapi.weixin.qq.com/cgi-bin/gettoken?")))
            .andRespond(withSuccess("{\"errcode\":0,\"access_token\":\"access-token\"}",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                "https://qyapi.weixin.qq.com/cgi-bin/miniprogram/jscode2session?")))
            .andExpect(queryParam("access_token", "access-token"))
            .andExpect(queryParam("js_code", "miniapp-code"))
            .andExpect(queryParam("grant_type", "authorization_code"))
            .andRespond(withSuccess("{\"errcode\":0,\"userid\":\"member02\",\"session_key\":\"ignored\"}",
                MediaType.APPLICATION_JSON));

        assertEquals("member02", client.fetchMiniAppUserId(config, "miniapp-code"));
        server.verify();
    }
}
