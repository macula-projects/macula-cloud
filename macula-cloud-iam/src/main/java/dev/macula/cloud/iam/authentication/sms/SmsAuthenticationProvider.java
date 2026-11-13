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

import java.util.Collection;

import dev.macula.cloud.iam.authentication.verification.AbstractVerificationCodeAuthenticationProvider;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeChannelService;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Sms认证策略适配，复用验证码核验与账号状态检查模板。
 *
 * @author Macula
 * @since 6.1.0
 */
public final class SmsAuthenticationProvider extends AbstractVerificationCodeAuthenticationProvider<SmsAuthenticationToken> {
    /** 显式依赖通道端口与对应账号目录。 */
    public SmsAuthenticationProvider(VerificationCodeChannelService codes, SmsUserDetailsService users) {
        super(SmsAuthenticationToken.class, codes, users::loadUserByPhone);
    }

    /** 读取服务端已核验的Client，不从外部输入重新推断。 */
    @Override
    protected String clientId(SmsAuthenticationToken token) {
        return token.getClientId();
    }

    /** 成功载体不保存验证码，角色与真实认证时间由模板提供。 */
    @Override
    protected SmsAuthenticationToken authenticatedToken(UserDetails user,
            Collection<? extends GrantedAuthority> authorities) {
        return new SmsAuthenticationToken(user, null, authorities);
    }
}
