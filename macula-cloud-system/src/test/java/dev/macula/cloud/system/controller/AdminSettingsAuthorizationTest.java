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
package dev.macula.cloud.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.macula.cloud.system.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证管理控制器将请求交给业务服务，不硬编码 ROOT 角色检查。
 *
 * @author Rain
 * @since 2026-10-13
 */
class AdminSettingsAuthorizationTest {
    private final IdentityProviderService identity = mock(IdentityProviderService.class);
    private final MessageSettingsService message = mock(MessageSettingsService.class);
    private final OAuth2ClientConfigService oauth = mock(OAuth2ClientConfigService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
        new IdentityProviderController(identity),
        new MessageSettingsController(message),
        new OAuth2ClientConfigController(oauth)).build();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void nonRootCanReachManagementReadsWhenRouteAllows() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
            new TestingAuthenticationToken("config-manager", "unused", "ROLE_CONFIG_MANAGER"));
        when(identity.page(anyInt(), anyInt(), any(), any(), any())).thenReturn(new Page<>());
        when(oauth.page(anyInt(), anyInt(), any())).thenReturn(new Page<>());
        when(message.providers("MAIL")).thenReturn(List.of());
        mvc.perform(get("/api/v1/identity-providers")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/identity-providers/catalog")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/message-settings/providers").param("channel", "MAIL"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/oauth2-clients")).andExpect(status().isOk());
        verify(identity).catalog();
        verify(message).providers("MAIL");
    }

    @Test
    void nonRootCanReachManagementWriteWhenRouteAllows() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
            new TestingAuthenticationToken("config-manager", "unused", "ROLE_CONFIG_MANAGER"));
        mvc.perform(delete("/api/v1/oauth2-clients/1")).andExpect(status().isOk());
        verify(oauth).delete(1L);
    }
}
