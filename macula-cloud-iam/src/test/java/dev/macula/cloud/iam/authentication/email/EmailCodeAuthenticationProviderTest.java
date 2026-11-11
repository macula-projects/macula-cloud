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
package dev.macula.cloud.iam.authentication.email;

import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 邮箱凭据只在一次性验证成功后加载合法本地账号。
 *
 * @author Macula
 * @since 6.1.0
 */
class EmailCodeAuthenticationProviderTest {
    /**
     * 验证码绑定可信Client，成功载体不保留验证码。
     */
    @Test
    void successClearsCredentialsAndChecksAccount() {
        var codes = mock(EmailCodeService.class);
        var users = mock(EmailUserDetailsService.class);
        var principal = new SysUserDetails();
        principal.setUsername("alice");
        principal.setEnabled(true);
        when(codes.verify("alice@example.test", "123456", "client-a")).thenReturn(true);
        when(users.loadUserByEmail("alice@example.test")).thenReturn(principal);
        var provider = new EmailCodeAuthenticationProvider(codes, users);
        var result = provider.authenticate(
                new EmailCodeAuthenticationToken("alice@example.test", "123456", "client-a"));
        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getCredentials()).isNull();
        assertThat(result.getName()).isEqualTo("alice");
        principal.setEnabled(false);
        assertThatThrownBy(() -> provider.authenticate(
                new EmailCodeAuthenticationToken("alice@example.test", "123456", "client-a")))
                .isInstanceOf(DisabledException.class);
    }

    /**
     * 错误码不触发用户目录查询，邮箱载体不替代SMS载体。
     */
    @Test
    void rejectsBeforeDirectoryLookup() {
        var codes = mock(EmailCodeService.class);
        var users = mock(EmailUserDetailsService.class);
        var provider = new EmailCodeAuthenticationProvider(codes, users);
        assertThatThrownBy(() -> provider.authenticate(
                new EmailCodeAuthenticationToken("alice@example.test", "000000", "client-b")))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(users);
        assertThat(provider.supports(dev.macula.cloud.iam.authentication.sms.SmsAuthenticationToken.class)).isFalse();
    }
}
