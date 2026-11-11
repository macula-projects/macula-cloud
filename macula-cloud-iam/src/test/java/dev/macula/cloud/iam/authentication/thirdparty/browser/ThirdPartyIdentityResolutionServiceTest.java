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
package dev.macula.cloud.iam.authentication.thirdparty.browser;

import java.time.Instant;
import java.util.Optional;

import dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyLoginContext;
import dev.macula.cloud.iam.service.account.SysUserService;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService;
import dev.macula.cloud.iam.service.identity.IdentitySourceBindingPolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.userdetails.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证所有来源统一执行配置策略，企微同名账号不能绕过待绑定流程。
 *
 * @author Rain
 * @since 6.1.0
 */
class ThirdPartyIdentityResolutionServiceTest {
    private final IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
    private final SysUserService users = mock(SysUserService.class);
    private final IdentitySourceBindingPolicyService policy = mock(IdentitySourceBindingPolicyService.class);
    private final ThirdPartyIdentityResolutionService service = new ThirdPartyIdentityResolutionService(policy);
    private final ThirdPartyLoginContext context =
            dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.context(
            "WECOM", "BROWSER", "test-client",
            Instant.now(), "/");

    @Test
    void wecomUserIdDoesNotImplicitlyMatchGlobalUsername() {
        var identity = IdentitySourceTestFixtures.browserIdentity(context, "wecom-user-id");
        when(policy.resolve(context.clientId(), context.tenantId(), context.userType(), identity)).thenReturn(
                Optional.empty());
        assertThrows(ThirdPartyIdentityResolutionService.BindingRequiredException.class,
                () -> service.resolve(identity, context));
        verifyNoInteractions(users, configs);
    }

    @Test
    void wecomUsesExistingBindingWhenNoUsernameMatches() {
        var identity = IdentitySourceTestFixtures.browserIdentity(context, "wecom-user-id");
        var principal = User.withUsername("alice").password("n/a").authorities("USER").build();
        when(policy.resolve(context.clientId(), context.tenantId(), context.userType(), identity)).thenReturn(
                Optional.of(principal));
        assertEquals("alice", service.resolve(identity, context).getUsername());
        verifyNoInteractions(users, configs);
    }

    @Test
    void conflictingWecomBindingFailsClosed() {
        var identity = IdentitySourceTestFixtures.browserIdentity(context, "wecom-user-id");
        when(policy.resolve(context.clientId(), context.tenantId(), context.userType(), identity))
                .thenThrow(new AuthenticationServiceException("binding conflict"));
        assertThrows(AuthenticationServiceException.class, () -> service.resolve(identity, context));
        verifyNoInteractions(users, configs);
    }

    @Test
    void githubNeverMatchesNicknameOrUsernameWithoutBinding() {
        var github = IdentitySourceTestFixtures.context("GITHUB", "", "test-client", Instant.now(), "/");
        var identity = IdentitySourceTestFixtures.browserIdentity(github, "123");
        when(policy.resolve(github.clientId(), github.tenantId(), github.userType(), identity)).thenReturn(
                Optional.empty());
        assertThrows(ThirdPartyIdentityResolutionService.BindingRequiredException.class,
                () -> service.resolve(identity, github));
        verifyNoInteractions(users, configs);
    }
}
