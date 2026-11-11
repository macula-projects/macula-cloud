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
package dev.macula.cloud.iam.authentication.mfa;

import dev.macula.cloud.iam.authentication.hosted.HostedLoginClientPolicyService;
import dev.macula.cloud.iam.authentication.sms.SmsAuthenticationToken;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyAuthenticationToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;

/**
 * 完整SecurityContext、Remember-Me及session fixation之前的认证完成检查。
 *
 * @author Rain
 * @since 6.1.0
 */
public final class MfaSessionAuthenticationStrategy implements SessionAuthenticationStrategy {
    private final MfaCompletionService completion;

    /**
     * 复用统一策略服务。
     */
    public MfaSessionAuthenticationStrategy(MfaCompletionService completion) {
        this.completion = completion;
    }

    /**
     * 仅使用已经验证的应用上下文；不读取请求client_id。
     */
    @Override
    public void onAuthentication(Authentication authentication, HttpServletRequest request,
            HttpServletResponse response) {
        String clientId = null;
        if (authentication instanceof ThirdPartyAuthenticationToken external) {
            clientId = external.getLoginContext().clientId();
        } else if (request.getAttribute(HostedLoginClientPolicyService.CONTEXT_ATTRIBUTE)
                instanceof HostedLoginClientPolicyService.Context context) {
            clientId = context.clientId();
        }
        AuthenticationMethod method =
                authentication instanceof dev.macula.cloud.iam.authentication.email.EmailCodeAuthenticationToken ?
                        AuthenticationMethod.EMAIL
                        : authentication instanceof SmsAuthenticationToken ? AuthenticationMethod.SMS
                        : authentication instanceof ThirdPartyAuthenticationToken ? AuthenticationMethod.EXTERNAL
                        : authentication instanceof UsernamePasswordAuthenticationToken ? AuthenticationMethod.PASSWORD
                        : AuthenticationMethod.UNKNOWN;
        completion.check(authentication, clientId, AuthenticationCheckpoint.BROWSER_SESSION,
                "hosted_login", method, MfaCompletionService.proof(authentication));
    }
}
