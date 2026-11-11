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
package dev.macula.cloud.iam.authentication.thirdparty.binding;

import java.time.Instant;

import dev.macula.cloud.iam.authentication.sms.SmsService;
import dev.macula.cloud.iam.authentication.thirdparty.BindingTestTransactions;
import dev.macula.cloud.iam.authentication.thirdparty.PendingThirdPartyBindingRepository;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import dev.macula.cloud.iam.service.account.SysUserService;
import dev.macula.cloud.iam.service.identity.RegistrationIdentityBindingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证外部身份绑定必须先证明现有账号归属，错误尝试不写数据库。
 *
 * @author Rain
 * @since 6.1.0
 */
class ThirdPartyBindingServiceTest {
    PendingThirdPartyBindingRepository pending = new PendingThirdPartyBindingRepository();

    SysUserService users = mock(SysUserService.class);

    RegistrationIdentityBindingService bindings = mock(RegistrationIdentityBindingService.class);
    PasswordEncoder passwords = mock(PasswordEncoder.class);
    SmsService sms = mock(SmsService.class);
    ThirdPartyBindingService service = new ThirdPartyBindingService(pending, bindings, users, passwords, sms,
            new dev.macula.cloud.system.security.UserIdentifierNormalizer(), BindingTestTransactions.directory(),
            new BindingTestTransactions());
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
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        when(bindings.requireClient(any(), any(), eq(false), eq(1L),
                eq(dev.macula.cloud.iam.enums.UserTypeEnum.EMPLOYEE)))
                .thenReturn(new dev.macula.cloud.iam.pojo.entity.SysOAuth2Client());
        when(bindings.bind(any(), eq(1L), eq(dev.macula.cloud.iam.enums.UserTypeEnum.EMPLOYEE), any(), eq(7L),
                eq(false), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(User.withUsername("alice").password("encoded").authorities("USER").build());
        pending.save(request,
                dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.bindingRequired("GITHUB",
                        "123",
                        dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.context("GITHUB", "",
                                "test-client", Instant.now(), "/")));
    }

    @Test
    void passwordProofCreatesOneBindingAndConsumesPendingIdentity() {
        when(passwords.matches("correct", "encoded")).thenReturn(true);
        assertEquals("alice", service.bind(request, "alice", "password", "correct", null, null, null)
                .user().getUsername());
        verify(bindings).bind(eq("test-client"), eq(1L), eq(dev.macula.cloud.iam.enums.UserTypeEnum.EMPLOYEE),
                argThat(identity -> "GITHUB".equals(identity.providerType()) && "123".equals(identity.identityValue())),
                eq(7L), eq(false), org.mockito.ArgumentMatchers.isNull());
        org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
        assertThrows(BadCredentialsException.class, () -> pending.current(request));
    }

    @Test
    void wrongPasswordAndDuplicatePhoneDoNotBind() {
        assertThrows(BadCredentialsException.class,
                () -> service.bind(request, "alice", "password", "wrong", null, null, null));
        when(users.findUniqueByMobile("13800000000")).thenThrow(new BadCredentialsException("duplicate"));
        assertThrows(BadCredentialsException.class,
                () -> service.bind(request, "alice", "sms", null, "13800000000", "123456", null));
        verify(bindings, never()).bind(any(), any(), any(), any(), any(), anyBoolean(),
                org.mockito.ArgumentMatchers.isNull());
        verify(sms, never()).verifyCode(any(), any());
    }

    @Test
    void smsProofRequiresTargetPhoneAndOneUseCode() {
        when(users.findUniqueByMobile("13800000000")).thenReturn(account);
        when(sms.verifyCode("13800000000", "123456", "test-client",
                dev.macula.cloud.iam.authentication.verification.VerificationCodePurpose.IDENTITY_BINDING)).thenReturn(
                true);
        service.bind(request, "alice", "sms", null, "13800000000", "123456", null);
        verify(sms).verifyCode("13800000000", "123456", "test-client",
                dev.macula.cloud.iam.authentication.verification.VerificationCodePurpose.IDENTITY_BINDING);
        verify(bindings).bind(any(), any(), any(), any(), eq(7L), eq(false), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void cancelledPendingIdentityCannotBeBound() {
        pending.clear(request);

        assertThrows(BadCredentialsException.class,
                () -> service.bind(request, "alice", "password", "correct", null, null, null));
        verify(bindings, never()).bind(any(), any(), any(), any(), any(), anyBoolean(),
                org.mockito.ArgumentMatchers.isNull());
    }

    /**
     * 每个用例清理事务同步上下文。
     */
    @org.junit.jupiter.api.AfterEach
    void clearTransaction() {
        org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
    }

}
