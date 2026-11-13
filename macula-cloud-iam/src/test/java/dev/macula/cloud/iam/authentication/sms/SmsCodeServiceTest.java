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
package dev.macula.cloud.iam.authentication.sms;

import dev.macula.cloud.iam.authentication.verification.SmsVerificationProperties;
import dev.macula.cloud.iam.authentication.verification.VerificationChannel;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeContext;
import dev.macula.cloud.iam.authentication.verification.VerificationCodePurpose;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeScope;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeService;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeStore;
import dev.macula.cloud.iam.message.sms.LoginSmsDispatcher;
import dev.macula.cloud.system.security.UserIdentifierNormalizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证短信提交成功后才激活验证码，并且验证过程一次性消费验证码。
 *
 * @author Rain
 * @since 2026-10-17
 */
class SmsCodeServiceTest {
    private final VerificationCodeStore store = mock(VerificationCodeStore.class);
    private final SmsVerificationProperties settings = new SmsVerificationProperties();
    private final LoginSmsDispatcher dispatcher = mock(LoginSmsDispatcher.class);
    private final SmsCodeService service = new SmsCodeService(new VerificationCodeService(store), dispatcher,
            new UserIdentifierNormalizer(), settings);

    @Test
    void activatesCodeOnlyAfterSupplierAcceptance() {
        when(store.issue(scope(null), "+8613800000000", "127.0.0.1", settings)).thenReturn("123456");

        service.sendCode("+86 138-0000-0000", "127.0.0.1", VerificationCodeContext.login(null));

        verify(dispatcher).send("+8613800000000", "123456");
        verify(store).activate(scope(null), "+8613800000000", "123456", settings);
    }

    @Test
    void supplierFailureDoesNotCreateUsableCodeOrReleaseAbuseLimits() {
        when(store.issue(scope(null), "13800000000", "127.0.0.1", settings)).thenReturn("123456");
        doThrow(new IllegalStateException("rejected")).when(dispatcher).send("13800000000", "123456");

        assertThatThrownBy(() -> service.sendCode("13800000000", "127.0.0.1", VerificationCodeContext.login(null)))
                .isInstanceOf(IllegalStateException.class);
        verify(store, never()).activate(scope(null), "13800000000", "123456", settings);
    }

    @Test
    void delegatesVerificationToAtomicStore() {
        when(store.consume(scope(null), "13800000000", "123456", settings)).thenReturn(true);

        assertThat(service.verifyCode("138-0000-0000", "123456", VerificationCodeContext.login(null))).isTrue();
        assertThat(service.verifyCode(null, "123456", VerificationCodeContext.login(null))).isFalse();
        verify(store).consume(scope(null), "13800000000", "123456", settings);
    }

    @Test
    void hostedCodeIsIssuedAndConsumedInsideClientContext() {
        when(store.issue(scope("client-a"), "13800000000", "127.0.0.1", settings)).thenReturn("123456");
        when(store.consume(scope("client-a"), "13800000000", "123456", settings)).thenReturn(true);

        service.sendCode("13800000000", "127.0.0.1", VerificationCodeContext.login("client-a"));

        verify(dispatcher).send("13800000000", "123456");
        verify(store).activate(scope("client-a"), "13800000000", "123456", settings);
        assertThat(service.verifyCode("13800000000", "123456", VerificationCodeContext.login("client-a"))).isTrue();
    }

    /**
     * 共享引擎中仍使用原SMS登录作用域。
     */
    private VerificationCodeScope scope(String clientId) {
        return new VerificationCodeScope(VerificationChannel.SMS, VerificationCodePurpose.LOGIN, clientId);
    }
}
