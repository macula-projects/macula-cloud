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
package dev.macula.cloud.iam.message;

import dev.macula.cloud.iam.authentication.email.EmailService;
import dev.macula.cloud.iam.authentication.sms.SmsService;
import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import dev.macula.cloud.iam.service.userdetails.ClientAwareAccountResolver;
import dev.macula.cloud.iam.service.userdetails.OAuthClientUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证协议验证码投递隐藏账号不存在和供应商异常。
 *
 * @author Rain
 * @since 6.1.0
 */
class ProtocolLoginCodeDispatcherTest {
    private final SmsService smsService = mock(SmsService.class);
    private final EmailService emailService = mock(EmailService.class);
    private final ClientAwareAccountResolver accounts = mock(ClientAwareAccountResolver.class);
    private final OAuthClientUserDetailsService userDetails = mock(OAuthClientUserDetailsService.class);
    private final ProtocolLoginCodeDispatcher dispatcher =
        new ProtocolLoginCodeDispatcher(smsService, emailService, accounts, userDetails);

    @Test
    void skipsDeliveryWhenAccountDoesNotExist() {
        dispatcher.sms("13800000000", "127.0.0.1", "client-a", 1L, UserTypeEnum.EMPLOYEE);
        dispatcher.email("user@example.test", "127.0.0.1", "client-a", "Client A", 1L,
            UserTypeEnum.EMPLOYEE);

        verifyNoInteractions(smsService, emailService);
    }

    @Test
    void dispatchesInsideClientContextWhenAccountExists() {
        accountExists();

        dispatcher.sms("13800000000", "127.0.0.1", "client-a", 1L, UserTypeEnum.EMPLOYEE);
        dispatcher.email("user@example.test", "127.0.0.1", "client-a", "Client A", 1L,
            UserTypeEnum.EMPLOYEE);

        verify(smsService).sendCode("13800000000", "127.0.0.1", "client-a");
        verify(emailService).sendCode("user@example.test", "127.0.0.1", "client-a", "Client A");
    }

    @Test
    void hidesSupplierFailuresFromPublicRequest() {
        accountExists();
        doThrow(new IllegalStateException("supplier marker"))
            .when(smsService).sendCode("13800000000", "127.0.0.1", "client-a");
        doThrow(new IllegalStateException("supplier marker"))
            .when(emailService).sendCode("user@example.test", "127.0.0.1", "client-a", "Client A");

        assertDoesNotThrow(() -> dispatcher.sms("13800000000", "127.0.0.1", "client-a", 1L,
            UserTypeEnum.EMPLOYEE));
        assertDoesNotThrow(() -> dispatcher.email("user@example.test", "127.0.0.1", "client-a", "Client A",
            1L, UserTypeEnum.EMPLOYEE));
    }

    private void accountExists() {
        SysUser account = new SysUser();
        account.setUsername("alice");
        when(accounts.findByMobile(UserTypeEnum.EMPLOYEE, "13800000000")).thenReturn(Optional.of(account));
        when(accounts.findByEmail(UserTypeEnum.EMPLOYEE, "user@example.test")).thenReturn(Optional.of(account));
        when(userDetails.loadUserByClient("client-a", 1L, UserTypeEnum.EMPLOYEE, "alice"))
            .thenReturn(mock(UserDetails.class));
    }
}
