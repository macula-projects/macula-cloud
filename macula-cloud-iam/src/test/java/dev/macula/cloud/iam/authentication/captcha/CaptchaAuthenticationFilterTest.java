/*
 * Copyright (c) 2023 Macula
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

package dev.macula.cloud.iam.authentication.captcha;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CaptchaAuthenticationFilter} 的 Spring Security 7 兼容回归测试。
 *
 * @author rain
 * @since 6.1.0
 */
class CaptchaAuthenticationFilterTest {

    @Test
    void shouldMatchOnlyConfiguredPostEndpoint() {
        TestCaptchaAuthenticationFilter filter = new TestCaptchaAuthenticationFilter(authentication -> authentication);

        assertThat(filter.requiresAuthentication(request("POST", "/login/captcha"))).isTrue();
        assertThat(filter.requiresAuthentication(request("GET", "/login/captcha"))).isFalse();
        assertThat(filter.requiresAuthentication(request("POST", "/login/other"))).isFalse();
    }

    @Test
    void shouldTrimAndSubmitCaptchaCredentials() {
        AtomicReference<Authentication> submitted = new AtomicReference<>();
        AuthenticationManager authenticationManager = authentication -> {
            submitted.set(authentication);
            return authentication;
        };
        CaptchaAuthenticationFilter filter = new CaptchaAuthenticationFilter(authenticationManager);
        MockHttpServletRequest request = request("POST", "/login/captcha");
        request.addParameter("phone", " 13800138000 ");
        request.addParameter("captcha", " 123456 ");

        Authentication result = filter.attemptAuthentication(request, new MockHttpServletResponse());

        assertThat(result).isSameAs(submitted.get());
        assertThat(result.getPrincipal()).isEqualTo("13800138000");
        assertThat(result.getCredentials()).isEqualTo("123456");
    }

    @Test
    void shouldRejectNonPostAuthenticationAttempt() {
        CaptchaAuthenticationFilter filter = new CaptchaAuthenticationFilter(authentication -> authentication);

        assertThatThrownBy(() -> filter.attemptAuthentication(request("GET", "/login/captcha"),
            new MockHttpServletResponse())).isInstanceOf(AuthenticationServiceException.class)
                .hasMessageContaining("GET");
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }

    private static final class TestCaptchaAuthenticationFilter extends CaptchaAuthenticationFilter {

        private TestCaptchaAuthenticationFilter(AuthenticationManager authenticationManager) {
            super(authenticationManager);
        }

        private boolean requiresAuthentication(MockHttpServletRequest request) {
            return super.requiresAuthentication(request, new MockHttpServletResponse());
        }
    }
}
