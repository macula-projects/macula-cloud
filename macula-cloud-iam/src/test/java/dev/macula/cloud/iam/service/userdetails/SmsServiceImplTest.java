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
package dev.macula.cloud.iam.service.userdetails;

import dev.macula.cloud.iam.message.LoginSmsDispatcher;
import dev.macula.cloud.iam.message.SmsVerificationCodeStore;
import dev.macula.cloud.iam.service.support.impl.SmsServiceImpl;
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
class SmsServiceImplTest {
    private final SmsVerificationCodeStore store = mock(SmsVerificationCodeStore.class);
    private final LoginSmsDispatcher dispatcher = mock(LoginSmsDispatcher.class);
    private final SmsServiceImpl service = new SmsServiceImpl(store, dispatcher);

    @Test
    void activatesCodeOnlyAfterSupplierAcceptance() {
        when(store.issue("+8613800000000", "127.0.0.1")).thenReturn("123456");

        service.sendCode("+86 138-0000-0000", "127.0.0.1");

        verify(dispatcher).send("+8613800000000", "123456");
        verify(store).activate("+8613800000000", "123456");
    }

    @Test
    void supplierFailureDoesNotCreateUsableCodeOrReleaseAbuseLimits() {
        when(store.issue("13800000000", "127.0.0.1")).thenReturn("123456");
        doThrow(new IllegalStateException("rejected")).when(dispatcher).send("13800000000", "123456");

        assertThatThrownBy(() -> service.sendCode("13800000000", "127.0.0.1"))
            .isInstanceOf(IllegalStateException.class);
        verify(store, never()).activate("13800000000", "123456");
    }

    @Test
    void delegatesVerificationToAtomicStore() {
        when(store.consume("13800000000", "123456")).thenReturn(true);

        assertThat(service.verifyCode("138-0000-0000", "123456")).isTrue();
        assertThat(service.verifyCode(null, "123456")).isFalse();
        verify(store).consume("13800000000", "123456");
    }
}
