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
package dev.macula.cloud.iam.protocol.oauth2.endpoint;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 证明退役路径精确匹配并在所有方法下无Session、重定向或下游副作用。
 *
 * @author Macula
 * @since 6.1.0
 */
class RetiredLoginEndpointFilterTest {
    /**
     * 反向代理前缀不影响退役路径；预检不能恢复发码。
     */
    @Test
    void returnsGoneForExactPathsAndEveryMethod() throws Exception {
        for (String path : new String[]{"/api/v1/oauth2/sms-codes", "/api/v1/oauth2/email-codes",
                "/api/v1/oauth2/login-challenges/captcha"}) {
            for (String method : new String[]{"GET", "POST", "OPTIONS"}) {
                var request = new MockHttpServletRequest(method, "/iam" + path);
                request.setContextPath("/iam");
                var response = new MockHttpServletResponse();
                var chain = new MockFilterChain();
                new RetiredLoginEndpointFilter().doFilter(request, response, chain);
                assertThat(response.getStatus()).isEqualTo(410);
                assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"endpoint_retired\"}");
                assertThat(response.getRedirectedUrl()).isNull();
                assertThat(request.getSession(false)).isNull();
                assertThat(chain.getRequest()).isNull();
            }
        }
    }

    /**
     * 托管、绑定发码和前缀相近路径继续经过原安全链。
     */
    @Test
    void preservesHostedBindingAndSimilarPaths() throws Exception {
        for (String path : new String[]{"/api/v1/sms-code", "/login/bind/sms-code", "/api/v1/oauth2/sms-codes-extra"}) {
            var chain = new MockFilterChain();
            var request = new MockHttpServletRequest("POST", path);
            new RetiredLoginEndpointFilter().doFilter(request, new MockHttpServletResponse(), chain);
            assertThat(chain.getRequest()).isSameAs(request);
        }
    }
}
