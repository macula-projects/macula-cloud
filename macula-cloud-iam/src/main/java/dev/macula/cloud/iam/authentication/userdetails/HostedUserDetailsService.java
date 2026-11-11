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
package dev.macula.cloud.iam.authentication.userdetails;

import dev.macula.cloud.iam.authentication.email.EmailUserDetailsService;
import dev.macula.cloud.iam.authentication.hosted.HostedLoginClientPolicyService;
import dev.macula.cloud.iam.authentication.sms.SmsUserDetailsService;
import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.service.account.ClientAccountDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;

import static dev.macula.boot.starter.web.utils.RequestUtil.getCurrentRequest;

/**
 * 托管请求到公共账号端口的薄适配；只读取过滤器校验后的上下文。
 *
 * @author Macula
 * @since 6.1.0
 */
@RequiredArgsConstructor
public class HostedUserDetailsService implements UserDetailsService, UserDetailsPasswordService,
        SmsUserDetailsService, EmailUserDetailsService {
    private final ClientAccountDetailsService accounts;

    /**
     * 原用户名/手机号/邮箱标识解析遵循既有托管用户名目录优先级。
     */
    @Override
    public UserDetails loadUserByUsername(String username) {
        return load(username, ClientAccountDetailsService.IdentifierType.USERNAME);
    }

    /**
     * 手机验证码核验后，目录主体必须与手机号唯一候选一致。
     */
    @Override
    public UserDetails loadUserByPhone(String phone) {
        return load(phone, ClientAccountDetailsService.IdentifierType.MOBILE);
    }

    /**
     * 邮箱验证码核验后，不能从客户端字段切换账号目录。
     */
    @Override
    public UserDetails loadUserByEmail(String email) {
        return load(email, ClientAccountDetailsService.IdentifierType.EMAIL);
    }

    /**
     * 框架密码升级委托公共账号服务。
     */
    @Override
    public UserDetails updatePassword(UserDetails user, String encodedPassword) {
        return accounts.updatePassword(user, encodedPassword);
    }

    /**
     * 无可信策略属性时失败，不回退到请求参数、header或cookie。
     */
    private UserDetails load(String identifier, ClientAccountDetailsService.IdentifierType kind) {
        var context = HostedLoginClientPolicyService.context(getCurrentRequest());
        var client = context.client();
        return accounts.loadForHostedLogin(context.clientId(), client == null ? 1L : client.getTenantId(),
                client == null ? UserTypeEnum.EMPLOYEE : client.getUserType(), identifier, kind);
    }
}
