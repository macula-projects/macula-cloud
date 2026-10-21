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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
 * 验证企业微信成员 UserId 由全局账号解析器处理。
 *
 * @author Rain
 * @since 6.1.0
 */
@ExtendWith(OutputCaptureExtension.class)
class WecomAuthenticationProviderTest {
    @Test
    void authenticatesMemberUserIdWithoutBindingLookup() {
        WecomClient wecom = mock(WecomClient.class);
        IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
        ThirdPartyIdentityResolutionService identities = mock(ThirdPartyIdentityResolutionService.class);
        var config = new IdentityProviderConfigService.ProviderConfig("WECOM", "corp", "secret",
            Map.of("corpId", "corp", "agentId", "agent"));
        var context = new ThirdPartyLoginContext("WECOM", "BROWSER", Instant.now(), "/");
        when(configs.requireEnabled("WECOM")).thenReturn(config);
        when(wecom.fetchUserId(config, "code")).thenReturn("zhangsan");
        when(identities.resolve("WECOM", "zhangsan", context))
            .thenReturn(User.withUsername("zhangsan").password("n/a").authorities("USER").build());

        var result = new WecomAuthenticationProvider(wecom, configs, identities).authenticate(
            ThirdPartyAuthenticationToken.unauthenticated("WECOM", "code", context,
                "https://iam.example/login/wecom"));

        assertEquals("zhangsan", result.getName());
        assertEquals("WECOM", ((ThirdPartyAuthenticationToken)result).getProviderType());
        verify(identities).resolve("WECOM", "zhangsan", context);
    }

    @Test
    void rejectsAnotherProviderWithoutCallingExternalServices() {
        WecomClient wecom = mock(WecomClient.class);
        IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
        ThirdPartyIdentityResolutionService identities = mock(ThirdPartyIdentityResolutionService.class);
        var context = new ThirdPartyLoginContext("GITHUB", "", Instant.now(), "/");

        var result = new WecomAuthenticationProvider(wecom, configs, identities).authenticate(
            ThirdPartyAuthenticationToken.unauthenticated("GITHUB", "code", context,
                "https://iam.example/login/github"));

        assertNull(result);
        verifyNoInteractions(wecom, configs, identities);
    }

    @Test
    void clientAcceptsOnlyMemberUserId(CapturedOutput output) {
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
            .andRespond(withSuccess("{\"errcode\":0,\"userid\":\"zhangsan\"}", MediaType.APPLICATION_JSON));
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
            .andRespond(withSuccess("{\"errcode\":0,\"openid\":\"external\"}", MediaType.APPLICATION_JSON));
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
        org.assertj.core.api.Assertions.assertThat(output)
            .contains("provider=WECOM", "stage=token", "status=500")
            .doesNotContain("sensitive-code", "secret");
        failedServer.verify();
    }

    @Test
    void logsWecomErrorCodeWithoutProviderTextOrCredentials(CapturedOutput output) {
        var config = new IdentityProviderConfigService.ProviderConfig("WECOM", "corp", "corp-secret-marker",
            Map.of("corpId", "corp", "agentId", "agent"));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WecomClient client = new WecomClient(builder.build());
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                "https://qyapi.weixin.qq.com/cgi-bin/gettoken?")))
            .andRespond(withSuccess("{\"errcode\":0,\"access_token\":\"access-token-marker\"}",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo?")))
            .andRespond(withSuccess("{\"errcode\":40029,\"errmsg\":\"sensitive-provider-text\"}",
                MediaType.APPLICATION_JSON));

        AuthenticationServiceException failure = assertThrows(AuthenticationServiceException.class,
            () -> client.fetchUserId(config, "authorization-code-marker"));

        assertEquals("企业微信身份校验失败", failure.getMessage());
        org.assertj.core.api.Assertions.assertThat(output)
            .contains("provider=WECOM", "stage=user", "failure=provider_error", "errcode=40029")
            .doesNotContain("sensitive-provider-text", "authorization-code-marker",
                "access-token-marker", "corp-secret-marker");
        server.verify();
    }
}
