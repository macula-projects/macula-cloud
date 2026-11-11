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

import java.util.Map;

import dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity;
import dev.macula.cloud.iam.authentication.thirdparty.directory.ProviderDirectoryResult;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService.ProviderConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 固定企业目录HTTP合同测试；不代表真实平台成员权限联调。
 *
 * @author Rain
 * @since 6.1.0
 */
class DingtalkDirectoryClientTest {
    private final ProviderConfig source = mock(ProviderConfig.class);
    private final DingtalkClient tokens = mock(DingtalkClient.class);
    private final VerifiedExternalIdentity identity = new VerifiedExternalIdentity(17L, "DINGTALK", "DINGTALK_SDK",
            0L, "DINGTALK_OAUTH_V1", "DINGTALK_APP_CODE", VerifiedExternalIdentity.MEMBER_ID, "member", Map.of());
    private DingtalkDirectoryClient client;
    private MockRestServiceServer server;

    /**
     * 只使用来源已登记企业，任意请求corpId不参与。
     */
    @BeforeEach
    void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new DingtalkDirectoryClient(builder.build(), tokens);
        when(source.providerType()).thenReturn("DINGTALK");
        when(source.registrationId()).thenReturn(17L);
        when(source.organizationId()).thenReturn("fixture-corp");
        when(source.require("corpId")).thenReturn("fixture-corp");
        when(tokens.appAccessToken(source)).thenReturn("fixture-token");
    }

    /**
     * 手机、工号等可选资料缺失不能误分类合法成员。
     */
    @Test
    void resolvesMemberWithoutOptionalProfile() {
        server.expect(requestTo("https://oapi.dingtalk.com/topapi/v2/user/get?access_token=fixture-token"))
                .andExpect(content().json("{\"userid\":\"member\"}"))
                .andRespond(withSuccess("{\"errcode\":0,\"result\":{\"userid\":\"member\",\"active\":true}}",
                        MediaType.APPLICATION_JSON));
        var result = client.lookup(source, identity);
        assertEquals(ProviderDirectoryResult.Status.MEMBER, result.status());
        assertEquals(Map.of("member_id", "member"), result.profile());
        server.verify();
    }

    /**
     * 停用和未核实业务拒绝不能降级成可选资料故障。
     */
    @Test
    void distinguishesDisabledFromUnknownProviderRejection() {
        server.expect(requestTo("https://oapi.dingtalk.com/topapi/v2/user/get?access_token=fixture-token"))
                .andRespond(withSuccess("{\"errcode\":0,\"result\":{\"userid\":\"member\",\"active\":false}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://oapi.dingtalk.com/topapi/v2/user/get?access_token=fixture-token"))
                .andRespond(withSuccess("{\"errcode\":987654}", MediaType.APPLICATION_JSON));
        assertEquals(ProviderDirectoryResult.Status.DISABLED, client.lookup(source, identity).status());
        assertFalse(client.lookup(source, identity).tolerableProfileFailure());
        server.verify();
    }
}
