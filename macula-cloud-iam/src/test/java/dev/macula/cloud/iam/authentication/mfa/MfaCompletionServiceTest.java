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

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import dev.macula.cloud.system.enums.IdentitySourceFlow;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证策略投影、异常闭合及原来源版本签发栅栏；不模拟MFA因子成功。
 *
 * @author Rain
 * @since 6.1.0
 */
class MfaCompletionServiceTest {
    /**
     * 密码和外部subject均不在策略可见类型中。
     */
    @Test
    void exposesOnlyStableServerIdentityAndUnknownClient() {
        var capture = new AtomicReference<AuthenticationCompletionContext>();
        var service = new MfaCompletionService(capture::set);
        var user = new SysUserDetails();
        user.setUserId(7L);
        user.setTenantId(9L);
        var principal = UsernamePasswordAuthenticationToken.authenticated(user, "fixture-password",
                user.getAuthorities());
        service.check(principal, null, AuthenticationCheckpoint.BROWSER_SESSION, "hosted_login",
                AuthenticationMethod.PASSWORD, Map.of());
        assertEquals(7L, capture.get().userId());
        assertEquals(9L, capture.get().tenantId());
        assertNull(capture.get().clientId());
        assertEquals(AuthenticationMethod.PASSWORD, capture.get().method());
    }

    /**
     * 策略自身故障不能泄漏异常正文，也不能默认放过签发。
     */
    @Test
    void closesOnPolicyFailureAndMapsTokenError() {
        var service = new MfaCompletionService(context -> {
            throw new IllegalStateException("private-policy-detail");
        });
        var browser = assertThrows(AdditionalAuthenticationRequiredException.class, () -> service.check(null, null,
                AuthenticationCheckpoint.BROWSER_SESSION, "hosted_login", AuthenticationMethod.UNKNOWN, Map.of()));
        assertNull(browser.getCause());
        assertEquals("Additional authentication is unavailable.", browser.getMessage());
        var token = assertThrows(OAuth2AuthenticationException.class, () -> service.checkToken(null, null,
                AuthenticationCheckpoint.USER_TOKEN, "authorization_code", Map.of()));
        assertEquals("server_error", token.getError().getErrorCode());
    }

    /**
     * 机器身份不要求用户MFA。
     */
    @Test
    void clientCredentialsDoesNotInvokeUserPolicy() {
        var policy = mock(MfaCompletionPolicy.class);
        new MfaCompletionService(policy).checkToken(null, null, AuthenticationCheckpoint.USER_TOKEN,
                "client_credentials", Map.of());
        verifyNoInteractions(policy);
    }

    /**
     * 核验后配置发生变化时，不能把新版本写入旧认证成果。
     */
    @Test
    void rejectsOriginalSourceVersionBeforePolicy() {
        var policy = mock(MfaCompletionPolicy.class);
        var sources = mock(IdentityProviderConfigService.class);
        var source = mock(IdentityProviderConfigService.ProviderConfig.class);
        when(source.version()).thenReturn(2L);
        when(source.flow()).thenReturn(IdentitySourceFlow.WECOM_SDK_V1);
        when(sources.requireEnabledById(17L, "WECOM")).thenReturn(source);
        var service = new MfaCompletionService(policy, sources);
        assertThrows(AdditionalAuthenticationRequiredException.class, () -> service.check(null, "client",
                AuthenticationCheckpoint.USER_TOKEN, "external_code", AuthenticationMethod.EXTERNAL,
                Map.of("registrationId", 17L, "providerType", "WECOM", "sourceVersion", 1L, "flow", "WECOM_SDK_V1")));
        verifyNoInteractions(policy);
    }
}
