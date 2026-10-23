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
package dev.macula.cloud.iam.authentication.thirdparty;

import dev.macula.cloud.iam.pojo.entity.SysUser;
import dev.macula.cloud.iam.service.support.SysUserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.userdetails.User;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证企微成员 ID 的全局账号优先规则及社交身份待绑定边界。
 *
 * @author Rain
 * @since 6.1.0
 */
class ThirdPartyIdentityResolutionServiceTest {
    private final IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
    private final SysUserService users = mock(SysUserService.class);
    private final ThirdPartyUserDetailsService details = mock(ThirdPartyUserDetailsService.class);
    private final ThirdPartyIdentityResolutionService service = new ThirdPartyIdentityResolutionService(configs, users, details);
    private final ThirdPartyLoginContext context = new ThirdPartyLoginContext("WECOM", "BROWSER", "test-client",
        Instant.now(), "/");

    @Test
    void wecomUserIdMatchesGlobalUsernameBeforeBinding() {
        SysUser account = new SysUser();
        account.setUsername("wecom-user-id");
        when(users.findUniqueByUsername("wecom-user-id")).thenReturn(account);
        when(configs.findBoundUsername("WECOM", "wecom-user-id")).thenReturn(Optional.empty());
        when(details.load("wecom-user-id")).thenReturn(User.withUsername("wecom-user-id")
            .password("n/a").authorities("USER").build());
        assertEquals("wecom-user-id", service.resolve("WECOM", "wecom-user-id", context).getUsername());
        var order = inOrder(users, configs);
        order.verify(users).findUniqueByUsername("wecom-user-id");
        order.verify(configs).findBoundUsername("WECOM", "wecom-user-id");
    }

    @Test
    void wecomUsesExistingBindingWhenNoUsernameMatches() {
        when(configs.findBoundUsername("WECOM", "wecom-user-id")).thenReturn(Optional.of("alice"));
        when(details.load("alice")).thenReturn(User.withUsername("alice")
            .password("n/a").authorities("USER").build());
        assertEquals("alice", service.resolve("WECOM", "wecom-user-id", context).getUsername());
    }

    @Test
    void conflictingWecomBindingFailsClosed() {
        when(users.findUniqueByUsername("wecom-user-id")).thenReturn(new SysUser());
        when(configs.findBoundUsername("WECOM", "wecom-user-id")).thenReturn(Optional.of("other"));
        assertThrows(AuthenticationServiceException.class,
            () -> service.resolve("WECOM", "wecom-user-id", context));
        verifyNoInteractions(details);
    }

    @Test
    void githubNeverMatchesNicknameOrUsernameWithoutBinding() {
        when(configs.findBoundUsername("GITHUB", "123")).thenReturn(Optional.empty());
        assertThrows(ThirdPartyIdentityResolutionService.BindingRequiredException.class,
            () -> service.resolve("GITHUB", "123", context));
        verifyNoInteractions(users, details);
    }
}
