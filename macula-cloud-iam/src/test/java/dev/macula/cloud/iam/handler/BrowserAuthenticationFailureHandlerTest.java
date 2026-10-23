/*
 * Copyright (c) 2026 Macula
 * macula.dev, China
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
package dev.macula.cloud.iam.handler;

import java.time.Instant;
import java.util.List;

import dev.macula.cloud.iam.authentication.thirdparty.PendingThirdPartyBindingRepository;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyIdentityResolutionService;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyLoginContext;
import dev.macula.cloud.iam.enums.ThirdPartyProviderType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.WebAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 验证浏览器认证失败统一为受控原因，且待绑定分流和异常内容不会泄漏。
 *
 * @author Rain
 * @since 6.1.0
 */
@ExtendWith(OutputCaptureExtension.class)
class BrowserAuthenticationFailureHandlerTest {
    private static final String TRUSTED_ISSUER = "https://iam.example";

    @Test
    void savesVerifiedIdentityAndRedirectsToBindingPage(CapturedOutput output) throws Exception {
        PendingThirdPartyBindingRepository pending = mock(PendingThirdPartyBindingRepository.class);
        BrowserAuthenticationFailureHandler handler = new BrowserAuthenticationFailureHandler(pending, TRUSTED_ISSUER);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        var context = new ThirdPartyLoginContext(ThirdPartyProviderType.GITHUB.providerType(), "", "test-client",
            Instant.now(), "/");
        var required = new ThirdPartyIdentityResolutionService.BindingRequiredException(
            ThirdPartyProviderType.GITHUB.providerType(), "external-subject", context);

        handler.onAuthenticationFailure(request, response, required);

        assertThat(response.getRedirectedUrl()).isEqualTo(TRUSTED_ISSUER + "/login/bind");
        verify(pending).save(request, required);
        assertThat(output).doesNotContain("external-subject");
    }

    @Test
    void accountAndCredentialFailuresUseSamePublicReason(CapturedOutput output) throws Exception {
        List<AuthenticationException> failures = List.of(
            new UsernameNotFoundException("missing-user-marker"),
            new BadCredentialsException("bad-credential-marker"),
            new LockedException("locked-account-marker"),
            new DisabledException("disabled-account-marker"),
            new AccountExpiredException("expired-account-marker"),
            new CredentialsExpiredException("expired-credential-marker"));

        for (AuthenticationException failure : failures) {
            MockHttpServletResponse response = handle(failure);
            assertThat(response.getRedirectedUrl())
                .isEqualTo(TRUSTED_ISSUER + "/login?error=authentication_rejected");
        }
        assertThat(output).doesNotContain("marker");
    }

    @Test
    void unexpectedFailureUsesUnavailableReasonWithoutLeakingDetails(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = handle(request,
            new AuthenticationServiceException("provider-secret-response-marker"));

        assertThat(response.getRedirectedUrl())
            .isEqualTo(TRUSTED_ISSUER + "/login?error=authentication_unavailable");
        assertThat(output).doesNotContain("provider-secret-response-marker");
        assertThat(request.getSession(false)).isNull();
        assertThat(request.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION)).isNull();
        assertThat(AuthenticationFailureReason.fromCode("forged-error"))
            .isEqualTo(AuthenticationFailureReason.AUTHENTICATION_UNAVAILABLE);
    }

    private MockHttpServletResponse handle(AuthenticationException failure) throws Exception {
        return handle(new MockHttpServletRequest(), failure);
    }

    private MockHttpServletResponse handle(MockHttpServletRequest request, AuthenticationException failure)
        throws Exception {
        BrowserAuthenticationFailureHandler handler =
            new BrowserAuthenticationFailureHandler(mock(PendingThirdPartyBindingRepository.class), TRUSTED_ISSUER);
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationFailure(request, response, failure);
        return response;
    }
}
