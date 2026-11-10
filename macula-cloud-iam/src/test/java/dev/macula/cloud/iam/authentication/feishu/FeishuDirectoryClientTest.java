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

import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService.ProviderConfig;
import dev.macula.cloud.iam.authentication.thirdparty.ProviderApplicationTokenCache;
import dev.macula.cloud.iam.authentication.thirdparty.ProviderDirectoryResult;
import dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 飞书目录HTTP合同与已确认权限错误分类；不是目标租户或真实权限联调证明。
 * @author Rain
 * @since 6.1.0
 */
class FeishuDirectoryClientTest {
    private static final String USER = "https://open.feishu.cn/open-apis/contact/v3/users/open-member?user_id_type=open_id";
    private final ProviderConfig source = mock(ProviderConfig.class);
    private final ProviderApplicationTokenCache tokens = mock(ProviderApplicationTokenCache.class);
    private final VerifiedExternalIdentity identity = new VerifiedExternalIdentity(17L, "FEISHU", "FEISHU_WEB", 0L,
        "FEISHU_OAUTH_V3", VerifiedExternalIdentity.BROWSER, VerifiedExternalIdentity.OPEN_ID, "open-member",
        Map.of("tenant_key", "fixture-tenant"));
    private MockRestServiceServer server;
    private FeishuDirectoryClient client;
    /** 固定租户来自平台已核验身份，scope使用open_id明确指定。 */
    @BeforeEach
    void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FeishuDirectoryClient(builder.build(), tokens);
        when(source.providerType()).thenReturn("FEISHU");
        when(source.registrationId()).thenReturn(17L);
        when(source.organizationId()).thenReturn("fixture-tenant");
        when(source.require("appId")).thenReturn("fixture-app");
        when(source.appSecret()).thenReturn("fixture-secret");
    }
    /** tenant token与用户token分离，缺少手机号等资料仍是合法成员。 */
    @Test
    void usesTenantTokenAndExplicitOpenIdType() {
        when(tokens.get(eq(source), anyString(), any())).thenAnswer(call -> {
            Supplier<ProviderApplicationTokenCache.TokenValue> loader = call.getArgument(2);
            return loader.get().token();
        });
        server.expect(requestTo("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal"))
            .andExpect(content().json("{\"app_id\":\"fixture-app\",\"app_secret\":\"fixture-secret\"}"))
            .andRespond(withSuccess("{\"code\":0,\"tenant_access_token\":\"fixture-tenant-token\",\"expire\":7200}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER)).andExpect(header("Authorization", "Bearer fixture-tenant-token"))
            .andRespond(withSuccess("{\"code\":0,\"data\":{\"user\":{\"open_id\":\"open-member\"}}}", MediaType.APPLICATION_JSON));
        var result = client.lookup(source, identity);
        assertEquals(ProviderDirectoryResult.Status.MEMBER, result.status());
        assertEquals(Map.of(), result.profile());
        server.verify();
    }
    /** 官方41050表示无权限，不能把它标为非成员或把用户加入其他企业。 */
    @Test
    void http400PermissionErrorIsNotNonMember() {
        when(tokens.get(eq(source), anyString(), any())).thenReturn("fixture-tenant-token");
        server.expect(requestTo(USER)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_JSON).body("{\"code\":41050,\"msg\":\"no user authority\"}"));
        var result = client.lookup(source, identity);
        assertEquals(ProviderDirectoryResult.Status.PERMISSION_DENIED, result.status());
        assertTrue(result.tolerableProfileFailure());
        server.verify();
    }
}
