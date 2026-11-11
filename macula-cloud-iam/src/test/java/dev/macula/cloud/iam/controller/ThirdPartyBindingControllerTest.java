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

import java.time.Instant;

import dev.macula.cloud.iam.authentication.thirdparty.PendingThirdPartyBindingRepository;
import dev.macula.cloud.iam.authentication.thirdparty.binding.ThirdPartyBindingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.ui.ExtendedModelMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证绑定控制器在本地账号证明成功前不建立认证会话。
 *
 * @author Rain
 * @since 6.1.0
 */
class ThirdPartyBindingControllerTest {
    private final PendingThirdPartyBindingRepository pending = new PendingThirdPartyBindingRepository();
    private final ThirdPartyBindingService service = mock(ThirdPartyBindingService.class);
    private final ThirdPartyBindingController controller = new ThirdPartyBindingController(pending, service,
            mock(dev.macula.cloud.iam.authentication.thirdparty.ExternalBindingTicketStore.class));
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingPendingIdentityCannotOpenBindingPage() {
        assertEquals("redirect:/login?error=authentication_rejected",
                controller.page(new ExtendedModelMap(), request,
                        new org.springframework.mock.web.MockHttpServletResponse(), null));
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
                .thenReturn(new ThirdPartyBindingService.BoundAccount(user, "/", false));
        assertEquals("redirect:/", controller.bind("alice", "password", "correct", null, null,
                request, response, new ExtendedModelMap(), null, null));
        assertEquals("alice", SecurityContextHolder.getContext().getAuthentication().getName());
        assertNotNull(
                request.getSession().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
    }

    @Test
    void cancelClearsPendingIdentityAndReturnsToLogin() {
        savePending();

        assertEquals("redirect:/login", controller.cancel(request));
        assertThrows(BadCredentialsException.class, () -> pending.current(request));
        assertEquals("redirect:/login?error=authentication_rejected",
                controller.page(new ExtendedModelMap(), request,
                        new org.springframework.mock.web.MockHttpServletResponse(), null));
    }

    /**
     * SDK完成证明不能建立SSO，也不能使用任何返回URL。
     */
    @Test
    void sdkCompletionNeverEstablishesReusableSession() {
        savePending();
        var user = User.withUsername("alice").password("n/a").authorities("USER").build();
        when(service.bind(eq(request), eq("alice"), eq("password"), any(), any(), any(), isNull()))
                .thenReturn(new ThirdPartyBindingService.BoundAccount(user, "https://untrusted.example", true));
        var model = new ExtendedModelMap();
        assertEquals("third-party-bind", controller.bind("alice", "password", "correct", null, null,
                request, response, model, null, null));
        assertEquals(true, model.get("bindingComplete"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(request.getSession().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals("no-referrer", response.getHeader("Referrer-Policy"));
    }

    /**
     * 基础设施失败必须向上传播，不能伪装成可重试的账号证明错误。
     */
    @Test
    void infrastructureFailureIsNotAuthenticationMismatch() {
        when(service.bind(eq(request), any(), any(), any(), any(), any(), any()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("unavailable"));
        assertThrows(org.springframework.dao.DataAccessResourceFailureException.class, () ->
                controller.bind("alice", "password", "proof", null, null, request, response, new ExtendedModelMap(),
                        null, null));
    }

    /**
     * 绑定后的普通会话须同时保留外部来源证明和SS7认证时间。
     */
    @Test
    void boundSessionPreservesSourceProofAndAuthenticationTime() {
        savePending();
        var user = new dev.macula.cloud.iam.service.userdetails.SysUserDetails();
        user.setUsername("alice");
        user.setUserId(9L);
        user.setTenantId(1L);
        user.setEnabled(true);
        var sourceContext = dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.context("GITHUB",
                "", "test-client", Instant.now(), "/");
        var sources = mock(dev.macula.cloud.iam.service.identity.IdentityProviderConfigService.class);
        when(sources.requireEnabledById(1L, "GITHUB")).thenReturn(
                dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.source(
                        dev.macula.cloud.system.enums.IdentitySourceType.GITHUB_OAUTH, "GITHUB", "fixture-app",
                        "fixture-secret", java.util.Map.of()));
        controller.setMfaCompletion(new dev.macula.cloud.iam.authentication.mfa.MfaCompletionService(context -> {
        }, sources));
        when(service.bind(eq(request), eq("alice"), eq("password"), any(), any(), any(), isNull()))
                .thenReturn(new ThirdPartyBindingService.BoundAccount(user, "/", false, sourceContext, "password"));
        assertEquals("redirect:/",
                controller.bind("alice", "password", "correct", null, null, request, response, new ExtendedModelMap(),
                        null, null));
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(value -> value instanceof org.springframework.security.core.authority.FactorGrantedAuthority
                        && value.getAuthority()
                        .equals(org.springframework.security.core.authority.FactorGrantedAuthority.AUTHORIZATION_CODE_AUTHORITY)));
        var proof = dev.macula.cloud.iam.authentication.mfa.MfaCompletionService.proof(authentication);
        assertEquals("test-client", proof.get("clientId"));
        assertEquals(9L, proof.get("userId"));
    }

    private void savePending() {
        pending.save(request,
                dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.bindingRequired("GITHUB",
                        "123",
                        dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.context("GITHUB", "",
                                "test-client", Instant.now(), "/")));
    }
}
