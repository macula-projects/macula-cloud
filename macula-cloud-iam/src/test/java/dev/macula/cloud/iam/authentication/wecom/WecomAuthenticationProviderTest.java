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

import dev.macula.cloud.iam.authentication.thirdparty.*;
import dev.macula.cloud.iam.enums.UserTypeEnum;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

/**
 * 验证企业微信 UserId 直接作为 username 并按 client 路由用户。
 *
 * @author Rain
 * @since 6.1.0
 */
class WecomAuthenticationProviderTest {
    @Test
    void authenticatesMemberUserIdWithoutBindingLookup() {
        WecomClient wecom = mock(WecomClient.class);
        IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
        ThirdPartyUserDetailsService users = mock(ThirdPartyUserDetailsService.class);
        var config = new IdentityProviderConfigService.ProviderConfig("WECOM", "corp", "secret",
            Map.of("corpId", "corp", "agentId", "agent"));
        var context = new ThirdPartyLoginContext("WECOM", "staff", 6L, UserTypeEnum.EMPLOYEE, Instant.now());
        when(configs.requireEnabled("WECOM")).thenReturn(config);
        when(wecom.fetchUserId(config, "code")).thenReturn("zhangsan");
        when(users.load(context, "zhangsan"))
            .thenReturn(User.withUsername("zhangsan").password("n/a").authorities("USER").build());

        var result = new WecomAuthenticationProvider(wecom, configs, users).authenticate(
            ThirdPartyAuthenticationToken.unauthenticated("WECOM", "code", context,
                "https://iam.example/login/wecom"));

        assertEquals("zhangsan", result.getName());
        assertEquals("WECOM", ((ThirdPartyAuthenticationToken)result).getProviderType());
        verify(users).load(context, "zhangsan");
    }

    @Test
    void rejectsAnotherProviderWithoutCallingExternalServices() {
        WecomClient wecom = mock(WecomClient.class);
        IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
        ThirdPartyUserDetailsService users = mock(ThirdPartyUserDetailsService.class);
        var context = new ThirdPartyLoginContext("GITHUB", "staff", 6L, UserTypeEnum.EMPLOYEE, Instant.now());

        var result = new WecomAuthenticationProvider(wecom, configs, users).authenticate(
            ThirdPartyAuthenticationToken.unauthenticated("GITHUB", "code", context,
                "https://iam.example/login/github"));

        assertNull(result);
        verifyNoInteractions(wecom, configs, users);
    }

    @Test
    void clientAcceptsOnlyMemberUserId() {
        var config = new IdentityProviderConfigService.ProviderConfig("WECOM", "corp", "secret",
            Map.of("corpId", "corp", "agentId", "agent"));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WecomClient client = new WecomClient(builder.build());
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                "https://qyapi.weixin.qq.com/cgi-bin/gettoken?")))
            .andRespond(withSuccess("{\"errcode\":0,\"access_token\":\"token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo?")))
            .andRespond(withSuccess("{\"errcode\":0,\"UserId\":\"zhangsan\"}", MediaType.APPLICATION_JSON));
        assertEquals("zhangsan", client.fetchUserId(config, "code"));
        server.verify();

        RestClient.Builder outsiderBuilder = RestClient.builder();
        MockRestServiceServer outsiderServer = MockRestServiceServer.bindTo(outsiderBuilder).build();
        WecomClient outsiderClient = new WecomClient(outsiderBuilder.build());
        outsiderServer.expect(requestTo(org.hamcrest.Matchers.startsWith(
                "https://qyapi.weixin.qq.com/cgi-bin/gettoken?")))
            .andRespond(withSuccess("{\"errcode\":0,\"access_token\":\"token\"}", MediaType.APPLICATION_JSON));
        outsiderServer.expect(requestTo(org.hamcrest.Matchers.startsWith(
                "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo?")))
            .andRespond(withSuccess("{\"errcode\":0,\"OpenId\":\"external\"}", MediaType.APPLICATION_JSON));
        assertThrows(AuthenticationServiceException.class, () -> outsiderClient.fetchUserId(config, "code"));
        outsiderServer.verify();

        String authorize = client.authorizationUri(config, "state-value", "https://iam.example/login/wecom");
        org.assertj.core.api.Assertions.assertThat(authorize)
            .startsWith("https://open.weixin.qq.com/connect/oauth2/authorize?")
            .contains("appid=corp", "scope=snsapi_base", "agentid=agent", "state=state-value",
                "redirect_uri=https://iam.example/login/wecom")
            .endsWith("#wechat_redirect");

        RestClient.Builder failedBuilder = RestClient.builder();
        MockRestServiceServer failedServer = MockRestServiceServer.bindTo(failedBuilder).build();
        WecomClient failedClient = new WecomClient(failedBuilder.build());
        failedServer.expect(requestTo(org.hamcrest.Matchers.startsWith(
                "https://qyapi.weixin.qq.com/cgi-bin/gettoken?")))
            .andRespond(withServerError());
        AuthenticationServiceException failure = assertThrows(AuthenticationServiceException.class,
            () -> failedClient.fetchUserId(config, "sensitive-code"));
        assertEquals("企业微信身份校验失败", failure.getMessage());
        failedServer.verify();
    }
}
