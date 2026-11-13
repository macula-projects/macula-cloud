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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 验证码认证模板：先消费证明，再加载账号并检查状态，认证成功后清除凭据。
 *
 * @author Macula
 * @since 6.1.0
 */
public abstract class AbstractVerificationCodeAuthenticationProvider<T extends AbstractAuthenticationToken>
        implements AuthenticationProvider {
    private final Class<T> tokenType;
    private final VerificationCodeChannelService codes;
    private final Function<String, UserDetails> users;
    private final AccountStatusUserDetailsChecker accountChecker = new AccountStatusUserDetailsChecker();

    /** 构造时固定通道依赖，不使用可变Service定位器或全局Provider注册。 */
    protected AbstractVerificationCodeAuthenticationProvider(Class<T> tokenType,
            VerificationCodeChannelService codes, Function<String, UserDetails> users) {
        this.tokenType = Objects.requireNonNull(tokenType);
        this.codes = Objects.requireNonNull(codes);
        this.users = Objects.requireNonNull(users);
    }

    /** 核验失败不查询目录；账号不可用时不产生认证结果或Session。 */
    @Override
    public final Authentication authenticate(Authentication authentication) {
        if (!tokenType.isInstance(authentication)) {
            throw new BadCredentialsException("不支持的认证凭据");
        }
        T token = tokenType.cast(authentication);
        if (!(token.getCredentials() instanceof String code)
                || !codes.verifyCode(token.getName(), code, VerificationCodeContext.login(clientId(token)))) {
            throw new BadCredentialsException("账号或验证码无效");
        }
        UserDetails user = users.apply(token.getName());
        accountChecker.check(user);
        var authorities = new ArrayList<GrantedAuthority>(user.getAuthorities());
        // 真实主认证时间用于auth_time；不表示已完成MFA，也不伪造acr/amr。
        authorities.add(FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.OTT_AUTHORITY));
        T result = authenticatedToken(user, authorities);
        result.setDetails(token.getDetails());
        return result;
    }

    /** 各通道仅处理自己的载体，不能跨通道或接管协议grant。 */
    @Override
    public final boolean supports(Class<?> type) {
        return tokenType.isAssignableFrom(type);
    }

    /** Client由受保护表单过滤器写入原有Token字段。 */
    protected abstract String clientId(T token);

    /** 具体适配器创建原载体，必须将一次性凭据置空。 */
    protected abstract T authenticatedToken(UserDetails user, Collection<? extends GrantedAuthority> authorities);
}
