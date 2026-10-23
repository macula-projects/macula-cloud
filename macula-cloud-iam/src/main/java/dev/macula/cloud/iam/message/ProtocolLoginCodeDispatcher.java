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
import dev.macula.cloud.iam.service.userdetails.ClientAwareAccountResolver;
import dev.macula.cloud.iam.service.userdetails.OAuthClientUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 在受控执行器中投递协议登录验证码，使供应商耗时和错误不影响统一公开响应。
 *
 * @author Rain
 * @since 6.1.0
 */
@Component
@RequiredArgsConstructor
public class ProtocolLoginCodeDispatcher {
    private final SmsService smsService;
    private final EmailService emailService;
    private final ClientAwareAccountResolver accounts;
    private final OAuthClientUserDetailsService userDetails;

    @Async("loginVerificationTaskExecutor")
    public void sms(String mobile, String remoteAddress, String clientId, Long tenantId, UserTypeEnum userType) {
        try {
            accounts.findByMobile(userType, mobile).filter(account -> canAuthenticate(clientId, tenantId, userType,
                account.getUsername())).ifPresent(account -> smsService.sendCode(mobile, remoteAddress, clientId));
        } catch (RuntimeException ignored) {
            // The public endpoint deliberately preserves a uniform response.
        }
    }

    @Async("loginVerificationTaskExecutor")
    public void email(String email, String remoteAddress, String clientId, String clientName,
        Long tenantId, UserTypeEnum userType) {
        try {
            accounts.findByEmail(userType, email).filter(account -> canAuthenticate(clientId, tenantId, userType,
                account.getUsername())).ifPresent(account -> emailService.sendCode(email, remoteAddress, clientId,
                    clientName));
        } catch (RuntimeException ignored) {
            // The public endpoint deliberately preserves a uniform response.
        }
    }

    private boolean canAuthenticate(String clientId, Long tenantId, UserTypeEnum userType, String username) {
        try {
            userDetails.loadUserByClient(clientId, tenantId, userType, username);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
