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
package dev.macula.cloud.iam.authentication.github;

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
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

/**
 * 验证 GitHub 稳定 subject 经过全局绑定后再按 client 路由用户。
 *
 * @author Rain
 * @since 6.1.0
 */
class GithubAuthenticationProviderTest {
    @Test
    void authenticatesOnlyThroughNumericSubjectBinding() {
        GithubClient github = mock(GithubClient.class);
        IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
        ThirdPartyUserDetailsService users = mock(ThirdPartyUserDetailsService.class);
        var config = new IdentityProviderConfigService.ProviderConfig("GITHUB", "id", "secret",
            Map.of("clientId", "id"));
        var context = new ThirdPartyLoginContext("GITHUB", "portal", 3L, UserTypeEnum.MEMBER, Instant.now());
        when(configs.requireEnabled("GITHUB")).thenReturn(config);
        when(github.fetchSubject(config, "code", "https://iam.example/login/github")).thenReturn("12345");
        when(configs.requireBoundUsername("GITHUB", "12345")).thenReturn("alice");
        when(users.load(context, "alice"))
            .thenReturn(User.withUsername("alice").password("n/a").authorities("USER").build());

        var result = new GithubAuthenticationProvider(github, configs, users).authenticate(
            ThirdPartyAuthenticationToken.unauthenticated("GITHUB", "code", context,
                "https://iam.example/login/github"));

        assertEquals("alice", result.getName());
        assertEquals("GITHUB", ((ThirdPartyAuthenticationToken)result).getProviderType());
        verify(configs).requireBoundUsername("GITHUB", "12345");
    }

    @Test
    void rejectsAnotherProviderWithoutCallingExternalServices() {
        GithubClient github = mock(GithubClient.class);
        IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
        ThirdPartyUserDetailsService users = mock(ThirdPartyUserDetailsService.class);
        var context = new ThirdPartyLoginContext("WECOM", "portal", 3L, UserTypeEnum.MEMBER, Instant.now());

        var result = new GithubAuthenticationProvider(github, configs, users).authenticate(
            ThirdPartyAuthenticationToken.unauthenticated("WECOM", "code", context,
                "https://iam.example/login/wecom"));

        assertNull(result);
        verifyNoInteractions(github, configs, users);
    }

    @Test
    void clientUsesTokenAndUserEndpointsButNeverFallsBackToLogin() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GithubClient client = new GithubClient(builder.build());
        var config = new IdentityProviderConfigService.ProviderConfig("GITHUB", "id", "secret",
            Map.of("clientId", "id"));
        server.expect(once(), requestTo("https://github.com/login/oauth/access_token"))
            .andExpect(method(POST)).andRespond(withSuccess("{\"access_token\":\"token\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://api.github.com/user"))
            .andExpect(method(GET)).andRespond(withSuccess("{\"id\":12345,\"login\":\"alice\"}", MediaType.APPLICATION_JSON));
        assertEquals("12345", client.fetchSubject(config, "code", "https://iam.example/login/github"));
        server.verify();

        RestClient.Builder missingIdBuilder = RestClient.builder();
        MockRestServiceServer missingIdServer = MockRestServiceServer.bindTo(missingIdBuilder).build();
        GithubClient missingIdClient = new GithubClient(missingIdBuilder.build());
        missingIdServer.expect(requestTo("https://github.com/login/oauth/access_token"))
            .andRespond(withSuccess("{\"access_token\":\"token\"}", MediaType.APPLICATION_JSON));
        missingIdServer.expect(requestTo("https://api.github.com/user"))
            .andRespond(withSuccess("{\"login\":\"alice\"}", MediaType.APPLICATION_JSON));
        assertThrows(AuthenticationServiceException.class,
            () -> missingIdClient.fetchSubject(config, "code", "https://iam.example/login/github"));
        missingIdServer.verify();

        String authorize = client.authorizationUri(config, "state-value", "https://iam.example/login/github");
        org.assertj.core.api.Assertions.assertThat(authorize)
            .startsWith("https://github.com/login/oauth/authorize?")
            .contains("client_id=id", "state=state-value", "redirect_uri=https://iam.example/login/github")
            .doesNotContain("scope=");

        RestClient.Builder failedBuilder = RestClient.builder();
        MockRestServiceServer failedServer = MockRestServiceServer.bindTo(failedBuilder).build();
        GithubClient failedClient = new GithubClient(failedBuilder.build());
        failedServer.expect(requestTo("https://github.com/login/oauth/access_token"))
            .andRespond(withServerError());
        AuthenticationServiceException failure = assertThrows(AuthenticationServiceException.class,
            () -> failedClient.fetchSubject(config, "sensitive-code", "https://iam.example/login/github"));
        assertEquals("GitHub 身份校验失败", failure.getMessage());
        failedServer.verify();
    }
}
