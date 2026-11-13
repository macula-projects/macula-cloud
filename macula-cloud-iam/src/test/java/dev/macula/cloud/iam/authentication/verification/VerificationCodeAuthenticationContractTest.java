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
package dev.macula.cloud.iam.authentication.verification;

import dev.macula.cloud.iam.authentication.email.EmailCodeAuthenticationFilter;
import dev.macula.cloud.iam.authentication.email.EmailCodeAuthenticationProvider;
import dev.macula.cloud.iam.authentication.email.EmailCodeAuthenticationToken;
import dev.macula.cloud.iam.authentication.hosted.HostedLoginClientPolicyService;
import dev.macula.cloud.iam.authentication.sms.SmsAuthenticationFilter;
import dev.macula.cloud.iam.authentication.sms.SmsAuthenticationProvider;
import dev.macula.cloud.iam.authentication.sms.SmsAuthenticationToken;
import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2ClientRegistration;
import dev.macula.cloud.iam.service.application.IamApplicationTestFixtures;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 双通道共享认证合同：输入、Client、账号状态、角色和认证时间必须一致。
 *
 * @author Macula
 * @since 6.1.0
 */
class VerificationCodeAuthenticationContractTest {
    /** 真实通道适配接入模板，不直接测试抽象父类替身。
     * @since 6.1.0
     */
    private record Fixture(AbstractVerificationCodeAuthenticationFilter<?> filter,
            AbstractVerificationCodeAuthenticationProvider<?> provider, VerificationCodeChannelService codes,
            UserDetailsService users, String target, String parameter, String path, Class<?> tokenType) {
    }

    /** 固定可信Client，目录和一次性核验可控但不绕过认证模板。 */
    private Fixture fixture(VerificationChannel channel) {
        var codes = mock(VerificationCodeChannelService.class);
        var users = mock(UserDetailsService.class);
        var sms = channel == VerificationChannel.SMS;
        AbstractVerificationCodeAuthenticationFilter<?> filter = sms
                ? new SmsAuthenticationFilter() : new EmailCodeAuthenticationFilter();
        AbstractVerificationCodeAuthenticationProvider<?> provider = sms
                ? new SmsAuthenticationProvider(codes, users::loadUserByUsername)
                : new EmailCodeAuthenticationProvider(codes, users::loadUserByUsername);
        filter.setAuthenticationManager(provider::authenticate);
        return new Fixture(filter, provider, codes, users, sms ? "13800000000" : "alice@example.test",
                sms ? "phone" : "email", sms ? "/login/sms" : "/login/email",
                sms ? SmsAuthenticationToken.class : EmailCodeAuthenticationToken.class);
    }

    /** 请求自报client_id不得覆盖安全策略恢复的Client。 */
    private MockHttpServletRequest request(Fixture fixture) {
        var request = new MockHttpServletRequest("POST", fixture.path());
        request.addParameter(fixture.parameter(), fixture.target());
        request.addParameter("code", "123456");
        request.addParameter("client_id", "attacker");
        var client = new OAuth2ClientRegistration();
        client.setClientId("trusted");
        request.setAttribute(HostedLoginClientPolicyService.CONTEXT_ATTRIBUTE,
                new HostedLoginClientPolicyService.Context(client.getClientId(), IamApplicationTestFixtures.snapshot(client), Set.of()));
        return request;
    }

    /** trim后合法输入成功，保留角色/details，清除凭据并记录本次OTT时间。 */
    @ParameterizedTest
    @EnumSource(VerificationChannel.class)
    void successContract(VerificationChannel channel) {
        var fixture = fixture(channel);
        when(fixture.codes().verifyCode(fixture.target(), "123456", VerificationCodeContext.login("trusted")))
                .thenReturn(true);
        when(fixture.users().loadUserByUsername(fixture.target())).thenReturn(
                new User("alice", "unused", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        var request = request(fixture);
        request.setParameter(fixture.parameter(), "  " + fixture.target() + "  ");
        request.setParameter("code", " 123456 ");
        Instant before = Instant.now();
        var result = fixture.filter().attemptAuthentication(request, new MockHttpServletResponse());
        assertThat(result).isInstanceOf(fixture.tokenType());
        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getCredentials()).isNull();
        assertThat(result.getDetails()).isEqualTo(new WebAuthenticationDetails(request));
        assertThat(result.getAuthorities()).extracting(GrantedAuthority::getAuthority).contains("ROLE_USER");
        var factor = result.getAuthorities().stream().filter(FactorGrantedAuthority.class::isInstance)
                .map(FactorGrantedAuthority.class::cast).findFirst().orElseThrow();
        assertThat(factor.getAuthority()).isEqualTo(FactorGrantedAuthority.OTT_AUTHORITY);
        assertThat(factor.getIssuedAt()).isBetween(before.minusSeconds(1), Instant.now());
        assertThat(fixture.provider().supports(fixture.tokenType())).isTrue();
        assertThat(fixture.provider().supports(channel == VerificationChannel.SMS
                ? EmailCodeAuthenticationToken.class : SmsAuthenticationToken.class)).isFalse();
    }

    /** 歧义、缺失、巨量空白及非六位输入在核验或目录查询之前失败。 */
    @ParameterizedTest
    @EnumSource(VerificationChannel.class)
    void invalidFieldsHaveNoSideEffects(VerificationChannel channel) {
        var fixture = fixture(channel);
        for (String field : List.of(fixture.parameter(), "code")) {
            int limit = field.equals("code") ? 6 : channel == VerificationChannel.SMS ? 24 : 128;
            String[][] invalid = {new String[0], {""}, {"   "}, {"a", "b"}, {"x".repeat(limit + 1)}, {" ".repeat(1025)}};
            for (String[] values : invalid) {
                var request = request(fixture);
                request.setParameter(field, values);
                assertThatThrownBy(() -> fixture.filter().attemptAuthentication(request, new MockHttpServletResponse()))
                        .isInstanceOf(BadCredentialsException.class);
            }
        }
        for (String code : List.of("12345", "1234567", "abcdef", "１２３４５６")) {
            var request = request(fixture);
            request.setParameter("code", code);
            assertThatThrownBy(() -> fixture.filter().attemptAuthentication(request, new MockHttpServletResponse()))
                    .isInstanceOf(BadCredentialsException.class);
        }
        var request = request(fixture);
        request.setMethod("GET");
        assertThatThrownBy(() -> fixture.filter().attemptAuthentication(request, new MockHttpServletResponse()))
                .isInstanceOf(AuthenticationServiceException.class);
        verifyNoInteractions(fixture.codes(), fixture.users());
    }

    /** 无效证明不能触发目录查询；有效证明也不能绕过四类账号状态。 */
    @ParameterizedTest
    @EnumSource(VerificationChannel.class)
    void proofAndAccountChecks(VerificationChannel channel) {
        var fixture = fixture(channel);
        assertThatThrownBy(() -> fixture.filter().attemptAuthentication(request(fixture), new MockHttpServletResponse()))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(fixture.users());
        when(fixture.codes().verifyCode(fixture.target(), "123456", VerificationCodeContext.login("trusted")))
                .thenReturn(true);
        List<Class<? extends AuthenticationException>> failures = List.of(DisabledException.class,
                LockedException.class, AccountExpiredException.class, CredentialsExpiredException.class);
        for (int status = 0; status < failures.size(); status++) {
            when(fixture.users().loadUserByUsername(fixture.target())).thenReturn(
                    new User("alice", "unused", status != 0, status != 2, status != 3, status != 1, List.of()));
            assertThatThrownBy(() -> fixture.filter().attemptAuthentication(request(fixture), new MockHttpServletResponse()))
                    .isInstanceOf(failures.get(status));
        }
        when(fixture.users().loadUserByUsername(fixture.target())).thenThrow(new UsernameNotFoundException("unavailable"));
        assertThatThrownBy(() -> fixture.filter().attemptAuthentication(request(fixture), new MockHttpServletResponse()))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
