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
package dev.macula.cloud.iam.controller;

import dev.macula.cloud.iam.authentication.thirdparty.PendingThirdPartyBindingRepository;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyBindingService;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyIdentityResolutionService;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyLoginContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.ui.ExtendedModelMap;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 验证绑定控制器在本地账号证明成功前不建立认证会话。
 *
 * @author Rain
 * @since 6.1.0
 */
class ThirdPartyBindingControllerTest {
    private final PendingThirdPartyBindingRepository pending = new PendingThirdPartyBindingRepository();
    private final ThirdPartyBindingService service = mock(ThirdPartyBindingService.class);
    private final ThirdPartyBindingController controller = new ThirdPartyBindingController(pending, service);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingPendingIdentityCannotOpenBindingPage() {
        assertEquals("redirect:/login?error=authentication_rejected",
            controller.page(new ExtendedModelMap(), request, null));
    }

    @Test
    void failedProofNeverEstablishesSession() {
        savePending();
        when(service.bind(eq(request), eq("alice"), eq("password"), any(), any(), any(), isNull()))
            .thenThrow(new BadCredentialsException("wrong"));
        assertEquals("third-party-bind", controller.bind("alice", "password", "wrong", null, null,
            request, response, new ExtendedModelMap(), null, null));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(request.getSession().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
    }

    @Test
    void successfulProofEstablishesLocalSessionAndUsesServerTarget() {
        savePending();
        var user = User.withUsername("alice").password("n/a").authorities("USER").build();
        when(service.bind(eq(request), eq("alice"), eq("password"), any(), any(), any(), isNull()))
            .thenReturn(new ThirdPartyBindingService.BoundAccount(user, "/"));
        assertEquals("redirect:/", controller.bind("alice", "password", "correct", null, null,
            request, response, new ExtendedModelMap(), null, null));
        assertEquals("alice", SecurityContextHolder.getContext().getAuthentication().getName());
        assertNotNull(request.getSession().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
    }

    @Test
    void cancelClearsPendingIdentityAndReturnsToLogin() {
        savePending();

        assertEquals("redirect:/login", controller.cancel(request));
        assertThrows(BadCredentialsException.class, () -> pending.current(request));
        assertEquals("redirect:/login?error=authentication_rejected",
            controller.page(new ExtendedModelMap(), request, null));
    }

    private void savePending() {
        pending.save(request, new ThirdPartyIdentityResolutionService.BindingRequiredException("GITHUB", "123",
            new ThirdPartyLoginContext("GITHUB", "", Instant.now(), "/")));
    }
}
