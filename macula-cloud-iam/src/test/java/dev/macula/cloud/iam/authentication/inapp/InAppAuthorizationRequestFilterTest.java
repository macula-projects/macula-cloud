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
package dev.macula.cloud.iam.authentication.inapp;

import java.util.Map;

import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 端内缺失证明只隔离本次请求，其他应用SSO上下文必须完整保留。
 *
 * @author Rain
 * @since 6.1.0
 */
class InAppAuthorizationRequestFilterTest {
    /**
     * 清除线程上下文，避免测试之间互相污染。
     */
    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 普通密码会话不能签端内授权码；异常路径同样恢复原对象。
     */
    @Test
    void isolatesOrdinarySessionAndRestoresEvenOnFailure() throws Exception {
        var policy = mock(InAppLoginPolicyService.class);
        var client = new SysOAuth2Client();
        client.setApplicationType("IN_APP");
        when(policy.current("container")).thenReturn(client);
        var filter = new InAppAuthorizationRequestFilter(policy, AuthorizationServerSettings.builder().build());
        var original = SecurityContextHolder.createEmptyContext();
        var authentication = UsernamePasswordAuthenticationToken.authenticated("member", "", java.util.List.of());
        original.setAuthentication(authentication);
        SecurityContextHolder.setContext(original);
        var request = new MockHttpServletRequest("POST", "/oauth2/authorize");
        request.addParameter("client_id", "container");
        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> assertNull(SecurityContextHolder.getContext().getAuthentication()));
        assertSame(original, SecurityContextHolder.getContext());
        assertSame(authentication, original.getAuthentication());
        assertThrows(jakarta.servlet.ServletException.class,
                () -> filter.doFilter(newRequest(), new MockHttpServletResponse(), (req, res) -> {
                    assertNull(SecurityContextHolder.getContext().getAuthentication());
                    throw new jakarta.servlet.ServletException("failure");
                }));
        assertSame(original, SecurityContextHolder.getContext());
        assertSame(authentication, original.getAuthentication());
    }

    /**
     * 当前固定平台证明通过时保留认证供SAS执行原始回调和Scope校验。
     */
    @Test
    void preservesVerifiedContext() throws Exception {
        var policy = mock(InAppLoginPolicyService.class);
        var client = new SysOAuth2Client();
        client.setApplicationType("IN_APP");
        when(policy.current("container")).thenReturn(client);
        var original = SecurityContextHolder.getContext();
        var authentication = UsernamePasswordAuthenticationToken.authenticated("member", "", java.util.List.of());
        original.setAuthentication(authentication);
        when(policy.validProof(eq(client), eq(authentication), eq(Map.of()), eq("authorization_code"))).thenReturn(
                true);
        new InAppAuthorizationRequestFilter(policy, AuthorizationServerSettings.builder().build()).doFilter(
                newRequest(), new MockHttpServletResponse(),
                (req, res) -> assertSame(authentication, SecurityContextHolder.getContext().getAuthentication()));
        assertSame(original, SecurityContextHolder.getContext());
    }

    /**
     * 端内授权入口固定为配置的SAS地址。
     */
    private MockHttpServletRequest newRequest() {
        var request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        request.addParameter("client_id", "container");
        return request;
    }
}
