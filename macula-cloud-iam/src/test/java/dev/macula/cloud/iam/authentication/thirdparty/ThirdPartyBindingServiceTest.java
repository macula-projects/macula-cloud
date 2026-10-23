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

import dev.macula.cloud.iam.authentication.sms.SmsService;
import dev.macula.cloud.iam.mapper.SysUserIdentityBindingMapper;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import dev.macula.cloud.iam.pojo.entity.SysUserIdentityBinding;
import dev.macula.cloud.iam.service.support.SysUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 验证外部身份绑定必须先证明现有账号归属，错误尝试不写数据库。
 *
 * @author Rain
 * @since 6.1.0
 */
class ThirdPartyBindingServiceTest {
    PendingThirdPartyBindingRepository pending = new PendingThirdPartyBindingRepository();
    IdentityProviderConfigService configs = mock(IdentityProviderConfigService.class);
    SysUserService users = mock(SysUserService.class);
    ThirdPartyUserDetailsService details = mock(ThirdPartyUserDetailsService.class);
    SysUserIdentityBindingMapper bindings = mock(SysUserIdentityBindingMapper.class);
    PasswordEncoder passwords = mock(PasswordEncoder.class);
    SmsService sms = mock(SmsService.class);
    ThirdPartyBindingService service = new ThirdPartyBindingService(pending, configs, users, details,
        bindings, passwords, sms);
    MockHttpServletRequest request = new MockHttpServletRequest();
    SysUser account = new SysUser();

    @BeforeEach
    void setup() {
        account.setId(7L);
        account.setUsername("alice");
        account.setMobile("13800000000");
        account.setPassword("encoded");
        account.setStatus(1);
        when(users.findUniqueByUsername("alice")).thenReturn(account);
        when(details.load("alice")).thenReturn(User.withUsername("alice").password("encoded").authorities("USER").build());
        when(configs.findBoundUsername("GITHUB", "123")).thenReturn(Optional.empty());
        when(bindings.selectList(any())).thenReturn(List.of());
        pending.save(request, new ThirdPartyIdentityResolutionService.BindingRequiredException("GITHUB", "123",
            new ThirdPartyLoginContext("GITHUB", "", "test-client", Instant.now(), "/")));
    }

    @Test
    void passwordProofCreatesOneBindingAndConsumesPendingIdentity() {
        when(passwords.matches("correct", "encoded")).thenReturn(true);
        assertEquals("alice", service.bind(request, "alice", "password", "correct", null, null, null)
            .user().getUsername());
        verify(bindings).insert(org.mockito.ArgumentMatchers.<SysUserIdentityBinding>argThat(binding -> "GITHUB".equals(binding.getProviderType())
            && "123".equals(binding.getProviderSubject()) && "alice".equals(binding.getUsername())));
        assertThrows(BadCredentialsException.class, () -> pending.current(request));
    }

    @Test
    void wrongPasswordAndDuplicatePhoneDoNotBind() {
        assertThrows(BadCredentialsException.class,
            () -> service.bind(request, "alice", "password", "wrong", null, null, null));
        when(users.findUniqueByMobile("13800000000")).thenThrow(new BadCredentialsException("duplicate"));
        assertThrows(BadCredentialsException.class,
            () -> service.bind(request, "alice", "sms", null, "13800000000", "123456", null));
        verify(bindings, never()).insert(any(SysUserIdentityBinding.class));
        verify(sms, never()).verifyCode(any(), any());
    }

    @Test
    void smsProofRequiresTargetPhoneAndOneUseCode() {
        when(users.findUniqueByMobile("13800000000")).thenReturn(account);
        when(sms.verifyCode("13800000000", "123456")).thenReturn(true);
        service.bind(request, "alice", "sms", null, "13800000000", "123456", null);
        verify(sms).verifyCode("13800000000", "123456");
        verify(bindings).insert(any(SysUserIdentityBinding.class));
    }

    @Test
    void cancelledPendingIdentityCannotBeBound() {
        pending.clear(request);

        assertThrows(BadCredentialsException.class,
            () -> service.bind(request, "alice", "password", "correct", null, null, null));
        verify(bindings, never()).insert(any(SysUserIdentityBinding.class));
    }
}
