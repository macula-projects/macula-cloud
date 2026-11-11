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

import java.util.ArrayList;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 只核验托管邮件凭据并加载本地账号，不签发Token或处理协议grant。
 *
 * @author Macula
 * @since 6.1.0
 */
@RequiredArgsConstructor
public class EmailCodeAuthenticationProvider implements AuthenticationProvider {
    private final EmailCodeService codes;
    private final EmailUserDetailsService users;

    /**
     * 一次性凭据核验后仍检查当前账号状态，失败不创建Session。
     */
    @Override
    public Authentication authenticate(Authentication authentication) {
        EmailCodeAuthenticationToken token = (EmailCodeAuthenticationToken) authentication;
        if (!codes.verify(token.getName(), (String) token.getCredentials(), token.getClientId())) {
            throw new BadCredentialsException("邮箱或验证码无效");
        }
        UserDetails user = users.loadUserByEmail(token.getName());
        new AccountStatusUserDetailsChecker().check(user);
        var authorities = new ArrayList<GrantedAuthority>(user.getAuthorities());
        // 框架以真实主认证因子时间生成auth_time；不等同于完成MFA，也不伪造acr/amr。
        authorities.add(FactorGrantedAuthority.fromAuthority(
                FactorGrantedAuthority.OTT_AUTHORITY));
        var result = new EmailCodeAuthenticationToken(user, null, authorities);
        result.setDetails(token.getDetails());
        return result;
    }

    /**
     * 不能处理退役邮箱grant或短信认证载体。
     */
    @Override
    public boolean supports(Class<?> type) {
        return EmailCodeAuthenticationToken.class.isAssignableFrom(type);
    }
}
