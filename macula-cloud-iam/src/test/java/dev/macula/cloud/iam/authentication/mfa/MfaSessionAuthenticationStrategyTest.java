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
package dev.macula.cloud.iam.authentication.mfa;

import java.util.concurrent.atomic.AtomicReference;

import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 会话完成前的检查不得把请求自报client_id当可信应用，也不得预先建立Session。
 *
 * @author Rain
 * @since 6.1.0
 */
class MfaSessionAuthenticationStrategyTest {
    /**
     * 裸门户没有客户端上下文时保留未知，实际密码方式仍可识别。
     */
    @Test
    void ignoresCallerClientIdAndDoesNotCreateSession() {
        var capture = new AtomicReference<AuthenticationCompletionContext>();
        var strategy = new MfaSessionAuthenticationStrategy(new MfaCompletionService(capture::set));
        var user = new SysUserDetails();
        user.setUserId(7L);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
        var request = new MockHttpServletRequest();
        request.addParameter("client_id", "untrusted-client");
        strategy.onAuthentication(authentication, request, new MockHttpServletResponse());
        assertNull(capture.get().clientId());
        assertEquals(AuthenticationMethod.PASSWORD, capture.get().method());
        assertNull(request.getSession(false));
    }

    /**
     * 策略拒绝不能自己留下认证上下文或新会话。
     */
    @Test
    void denialPrecedesAnySessionMutation() {
        var strategy = new MfaSessionAuthenticationStrategy(new MfaCompletionService(context -> {
            throw new AdditionalAuthenticationRequiredException();
        }));
        var user = new SysUserDetails();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
        var request = new MockHttpServletRequest();
        SecurityContextHolder.clearContext();
        try {
            assertThrows(AdditionalAuthenticationRequiredException.class, () -> strategy.onAuthentication(
                    authentication, request, new MockHttpServletResponse()));
            assertNull(request.getSession(false));
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
