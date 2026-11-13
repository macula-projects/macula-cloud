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

import java.time.Instant;
import java.util.List;
import java.util.Map;

import dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyAuthenticationToken;
import dev.macula.cloud.iam.authentication.thirdparty.browser.ThirdPartyAuthenticationProvider;
import dev.macula.cloud.iam.authentication.thirdparty.browser.ThirdPartyIdentityResolutionService;
import dev.macula.cloud.iam.authentication.thirdparty.provider.ThirdPartyProviderAdapterRegistry;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService;
import dev.macula.cloud.system.enums.IdentitySourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 验证 GitHub 稳定 subject 仅经过全局绑定解析用户。
 *
 * @author Rain
 * @since 6.1.0
 */
@ExtendWith(OutputCaptureExtension.class)
class GithubProviderAdapterTest {
    @Test
    void authenticatesOnlyThroughNumericSubjectBinding() {
        GithubClient github = mock(GithubClient.class);
        IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
        ThirdPartyIdentityResolutionService identities = mock(ThirdPartyIdentityResolutionService.class);
        var config = IdentitySourceTestFixtures.source(
                IdentitySourceType.GITHUB_OAUTH, "GITHUB", "id", "secret",
                Map.of("clientId", "id"));
        var context = IdentitySourceTestFixtures.context("GITHUB", "",
                "test-client", Instant.now(), "/");
        when(configs.requireEnabledById(1L, "GITHUB")).thenReturn(config);
        when(github.fetchSubject(config, "code", "https://iam.example/login/github")).thenReturn("12345");
        when(identities.resolve(
                IdentitySourceTestFixtures.browserIdentity(context,
                        "12345"), context))
                .thenReturn(User.withUsername("alice").password("n/a").authorities("USER").build());

        var result = new ThirdPartyAuthenticationProvider(
                new ThirdPartyProviderAdapterRegistry(
                        List.of(new GithubProviderAdapter(github))), configs, identities).authenticate(
                ThirdPartyAuthenticationToken.unauthenticated("GITHUB", "code", context,
                        "https://iam.example/login/github"));

        assertEquals("alice", result.getName());
        assertEquals("GITHUB", ((ThirdPartyAuthenticationToken) result).getProviderType());
        verify(identities).resolve(
                IdentitySourceTestFixtures.browserIdentity(context,
                        "12345"), context);
    }

    @Test
    void rejectsAnotherProviderWithoutCallingExternalServices() {
        GithubClient github = mock(GithubClient.class);
        IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
        ThirdPartyIdentityResolutionService identities = mock(ThirdPartyIdentityResolutionService.class);
        var context = IdentitySourceTestFixtures.context("WECOM",
                "BROWSER", "test-client", Instant.now(), "/");

        assertThrows(
                AuthenticationServiceException.class,
                () -> new ThirdPartyAuthenticationProvider(
                        new ThirdPartyProviderAdapterRegistry(
                                List.of(new GithubProviderAdapter(github))), configs,
                        identities).authenticate(
                        ThirdPartyAuthenticationToken.unauthenticated("WECOM", "code", context,
                                "https://iam.example/login/wecom")));
        verifyNoInteractions(github, configs, identities);
    }

    @Test
    void clientUsesTokenAndUserEndpointsButNeverFallsBackToLogin(CapturedOutput output) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GithubClient client = new GithubClient(builder.build());
        var config = IdentitySourceTestFixtures.source(
                IdentitySourceType.GITHUB_OAUTH, "GITHUB", "id", "secret",
                Map.of("clientId", "id"));
        server.expect(once(), requestTo("https://github.com/login/oauth/access_token"))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"access_token\":\"token\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://api.github.com/user"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"id\":12345,\"login\":\"alice\"}", MediaType.APPLICATION_JSON));
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
        assertThat(authorize)
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
        assertThat(output)
                .contains("provider=GITHUB", "stage=token", "status=500")
                .doesNotContain("sensitive-code", "secret");
        failedServer.verify();
    }

    @Test
    void logsGithubFailureCategoryWithoutProviderTextOrCredentials(CapturedOutput output) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GithubClient client = new GithubClient(builder.build());
        var config = IdentitySourceTestFixtures.source(
                IdentitySourceType.GITHUB_OAUTH, "GITHUB", "id", "client-secret-marker",
                Map.of("clientId", "id"));
        server.expect(requestTo("https://github.com/login/oauth/access_token"))
                .andRespond(withSuccess("{\"error\":\"sensitive-provider-error\","
                        + "\"error_description\":\"sensitive-provider-description\"}", MediaType.APPLICATION_JSON));

        AuthenticationServiceException failure = assertThrows(AuthenticationServiceException.class,
                () -> client.fetchSubject(config, "authorization-code-marker",
                        "https://iam.example/login/github"));

        assertEquals("GitHub 身份校验失败", failure.getMessage());
        assertThat(output)
                .contains("provider=GITHUB", "stage=token", "failure=provider_rejected")
                .doesNotContain("sensitive-provider-error", "sensitive-provider-description",
                        "authorization-code-marker", "client-secret-marker");
        server.verify();
    }
}
