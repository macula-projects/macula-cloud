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

import dev.macula.cloud.iam.authentication.thirdparty.BindingTestTransactions;
import dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures;
import dev.macula.cloud.iam.authentication.thirdparty.PendingThirdPartyBindingRepository;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeChannelService;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeContext;
import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2ClientRegistration;
import dev.macula.cloud.iam.service.account.SysUserService;
import dev.macula.cloud.iam.service.application.IamApplicationTestFixtures;
import dev.macula.cloud.iam.service.identity.IdentityProviderUserBindingService;
import dev.macula.cloud.system.security.UserIdentifierNormalizer;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
@ExtendWith(OutputCaptureExtension.class)
class ThirdPartyBindingServiceTest {
    PendingThirdPartyBindingRepository pending = new PendingThirdPartyBindingRepository();

    SysUserService users = mock(SysUserService.class);

    IdentityProviderUserBindingService bindings = mock(IdentityProviderUserBindingService.class);
    PasswordEncoder passwords = mock(PasswordEncoder.class);
    VerificationCodeChannelService sms = mock(VerificationCodeChannelService.class);
    ThirdPartyBindingService service = new ThirdPartyBindingService(pending, bindings, users, passwords, sms,
            new UserIdentifierNormalizer(), BindingTestTransactions.directory(),
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
        TransactionSynchronizationManager.initSynchronization();
        var client = new OAuth2ClientRegistration();
        client.setApplicationType("SPA");
        when(bindings.requireApplication(any(), any(), eq(false), eq(1L),
                eq(UserTypeEnum.EMPLOYEE)))
                .thenReturn(IamApplicationTestFixtures.snapshot(client));
        when(bindings.bind(any(), eq(1L), eq(UserTypeEnum.EMPLOYEE), any(), eq(7L),
                eq(false), ArgumentMatchers.isNull()))
                .thenReturn(User.withUsername("alice").password("encoded").authorities("USER").build());
        pending.save(request,
                IdentitySourceTestFixtures.bindingRequired("GITHUB",
                        "123",
                        IdentitySourceTestFixtures.context("GITHUB", "",
                                "test-client", Instant.now(), "/")));
    }

    /** 成功绑定只在提交后消费pending，不记录拒绝日志。 */
    @Test
    void passwordProofCreatesOneBindingAndConsumesPendingIdentity(CapturedOutput output) {
        when(passwords.matches("correct", "encoded")).thenReturn(true);
        assertEquals("alice", service.bind(request, "alice", "password", "correct", null, null, null)
                .user().getUsername());
        verify(bindings).bind(eq("test-client"), eq(1L), eq(UserTypeEnum.EMPLOYEE),
                argThat(identity -> "GITHUB".equals(identity.providerType()) && "123".equals(identity.identityValue())),
                eq(7L), eq(false), ArgumentMatchers.isNull());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        assertThrows(BadCredentialsException.class, () -> pending.current(request));
        assertFalse(output.toString().contains("第三方账号绑定拒绝"));
    }

    /** 原始拒绝在包装前保留受控代码位置，失败次数及pending语义不变。 */
    @Test
    void bindingWriteFailureReportsSafeLocationAndKeepsPending(CapturedOutput output) {
        when(passwords.matches("correct", "encoded")).thenReturn(true);
        var failure = new BadCredentialsException("private-password-token-user-marker");
        when(bindings.bind(any(), eq(1L), eq(UserTypeEnum.EMPLOYEE), any(), eq(7L),
                eq(false), ArgumentMatchers.isNull())).thenThrow(failure);
        assertThrows(BadCredentialsException.class,
                () -> service.bind(request, "alice", "password", "correct", null, null, null));
        assertTrue(output.toString().contains("stage=binding_write"));
        assertTrue(output.toString().contains("site=IdentityProviderUserBindingService.bind:"));
        assertFalse(output.toString().contains("private-"));
        assertEquals(1, pending.current(request).attempts());
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }

    /** 错误密码仍拒绝，诊断不记录提交的账号或密码，也不写入绑定。 */
    @Test
    void invalidPasswordReportsOnlyProofStage(CapturedOutput output) {
        assertThrows(BadCredentialsException.class,
                () -> service.bind(request, "alice", "password", "private-password-marker", null, null, null));
        assertTrue(output.toString().contains("stage=account_proof"));
        assertFalse(output.toString().contains("private-password-marker"));
        verify(bindings, never()).bind(any(), any(), any(), any(), any(), anyBoolean(), ArgumentMatchers.isNull());
        assertEquals(1, pending.current(request).attempts());
    }

    /** 真实绑定服务保留通道可用性，不以是否存在公共端口Bean代替设施判定。 */
    @Test
    void bindingSmsAvailabilityFollowsChannelFacilities() {
        assertFalse(service.isSmsAvailable());
        when(sms.isAvailable()).thenReturn(true);
        assertTrue(service.isSmsAvailable());
        when(sms.isAvailable()).thenReturn(false);
        assertFalse(service.isSmsAvailable());
    }

    @Test
    void wrongPasswordAndDuplicatePhoneDoNotBind() {
        assertThrows(BadCredentialsException.class,
                () -> service.bind(request, "alice", "password", "wrong", null, null, null));
        when(users.findUniqueByMobile("13800000000")).thenThrow(new BadCredentialsException("duplicate"));
        assertThrows(BadCredentialsException.class,
                () -> service.bind(request, "alice", "sms", null, "13800000000", "123456", null));
        verify(bindings, never()).bind(any(), any(), any(), any(), any(), anyBoolean(),
                ArgumentMatchers.isNull());
        verify(sms, never()).verifyCode(any(), any(), any());
    }

    @Test
    void smsProofRequiresTargetPhoneAndOneUseCode() {
        when(users.findUniqueByMobile("13800000000")).thenReturn(account);
        when(sms.verifyCode("13800000000", "123456", VerificationCodeContext.binding("test-client"))).thenReturn(
                true);
        service.bind(request, "alice", "sms", null, "13800000000", "123456", null);
        verify(sms).verifyCode("13800000000", "123456", VerificationCodeContext.binding("test-client"));
        verify(bindings).bind(any(), any(), any(), any(), eq(7L), eq(false), ArgumentMatchers.isNull());
    }

    @Test
    void cancelledPendingIdentityCannotBeBound() {
        pending.clear(request);

        assertThrows(BadCredentialsException.class,
                () -> service.bind(request, "alice", "password", "correct", null, null, null));
        verify(bindings, never()).bind(any(), any(), any(), any(), any(), anyBoolean(),
                ArgumentMatchers.isNull());
    }

    /**
     * 每个用例清理事务同步上下文。
     */
    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.clearSynchronization();
    }

}
