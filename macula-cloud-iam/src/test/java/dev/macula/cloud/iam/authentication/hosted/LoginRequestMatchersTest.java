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
package dev.macula.cloud.iam.authentication.hosted;

import java.util.List;

import dev.macula.cloud.iam.authentication.github.GithubAuthenticationFilter;
import dev.macula.cloud.iam.authentication.thirdparty.browser.ThirdPartyLoginFilterConfigurer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 匿名入口与第三方登记同源，近似路径和管理API不可扩大放行。
 *
 * @author Macula
 * @since 6.1.0
 */
class LoginRequestMatchersTest {
    /**
     * 精确入口可达，通配和空路径不能被第三方描述引入。
     */
    @Test
    void exactDeclaredRoutesOnly() {
        var login = new ThirdPartyLoginFilterConfigurer().add(new GithubAuthenticationFilter(), "GITHUB",
                "/login/github", "/login/github");
        var endpoints = LoginRequestMatchers.loginEndpoints(login);
        for (String path : List.of("/login", "/login/github", "/login/email", "/api/v1/email-code",
                "/login/bind/external")) {
            assertThat(endpoints.matches(new MockHttpServletRequest("GET", path))).as(path).isTrue();
        }
        for (String path : List.of("/login/admin", "/login/github/evil", "/api/v1/users", "/h2-console/",
                "/")) {
            assertThat(endpoints.matches(new MockHttpServletRequest("GET", path))).as(path).isFalse();
        }
        assertThatThrownBy(() -> login.add(new GithubAuthenticationFilter(), "GITHUB", "/login/github", "/login/**"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(LoginRequestMatchers.staticResources()
                .matches(new MockHttpServletRequest("GET", "/admin/css/other/error.css"))).isTrue();
        assertThat(LoginRequestMatchers.staticResources()
                .matches(new MockHttpServletRequest("GET", "/admin/api/users"))).isFalse();
    }
}
