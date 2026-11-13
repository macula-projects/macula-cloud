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

import dev.macula.cloud.system.enums.HostedLoginMethod;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 表单和发码的拒绝路径必须在副作用之前执行。
 *
 * @author Rain
 * @since 6.1.0
 */
class HostedLoginPolicyFilterTest {
    /**
     * 发码返回403、表单走失败处理，不调用后续链；Token路径保持独立。
     */
    @Test
    void rejectsBeforeSideEffectsAndSkipsToken() throws Exception {
        HostedLoginClientPolicyService policy = mock(HostedLoginClientPolicyService.class);
        when(policy.resolve(any(), any())).thenReturn(new HostedLoginClientPolicyService.Context(null, Set.of()));
        AuthenticationFailureHandler failure = (request, response, ex) -> response.setStatus(302);
        HostedLoginPolicyFilter filter = new HostedLoginPolicyFilter(policy, failure);
        for (String path : Set.of("/login", "/login/sms", "/api/v1/sms-code")) {
            AtomicBoolean called = new AtomicBoolean();
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest("POST", path), response, (req, res) -> called.set(true));
            assertFalse(called.get());
            assertEquals(path.contains("sms-code") ? 403 : 302, response.getStatus());
        }
        when(policy.resolve(any(), any())).thenReturn(
                new HostedLoginClientPolicyService.Context(null, Set.of(HostedLoginMethod.SMS)));
        AtomicBoolean called = new AtomicBoolean();
        filter.doFilter(new MockHttpServletRequest("POST", "/login/sms"), new MockHttpServletResponse(),
                (req, res) -> called.set(true));
        assertTrue(called.get());
        called.set(false);
        filter.doFilter(new MockHttpServletRequest("POST", "/oauth2/token"), new MockHttpServletResponse(),
                (req, res) -> called.set(true));
        assertTrue(called.get());
    }
}
