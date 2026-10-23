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

import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import dev.macula.cloud.iam.service.support.SysOAuth2ClientService;
import dev.macula.cloud.iam.service.userdetails.ClientAwareAccountResolver;
import dev.macula.cloud.iam.service.userdetails.OAuthClientUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 核验邮箱验证码并在显式 OAuth Client 路由内建立本地用户认证。
 *
 * @author Rain
 * @since 6.1.0
 */
@RequiredArgsConstructor
public class EmailAuthenticationProvider implements AuthenticationProvider {
    private final EmailService emailService;
    private final SysOAuth2ClientService clients;
    private final ClientAwareAccountResolver accounts;
    private final OAuthClientUserDetailsService userDetails;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        EmailAuthenticationToken token = (EmailAuthenticationToken)authentication;
        String clientId = token.getClientId();
        SysOAuth2Client client = clients.getClientByClientId(clientId);
        if (client == null || client.getTenantId() == null || client.getUserType() == null
            || !emailService.verifyCode(token.getName(), (String)token.getCredentials(), clientId)) {
            throw new BadCredentialsException("Email credentials are invalid.");
        }
        SysUser account = accounts.findByEmail(client.getUserType(), token.getName())
            .orElseThrow(() -> new BadCredentialsException("Email credentials are invalid."));
        UserDetails principal = userDetails.loadUserByClient(clientId, client.getTenantId(), client.getUserType(),
            account.getUsername());
        EmailAuthenticationToken result = new EmailAuthenticationToken(principal, principal.getAuthorities());
        result.setDetails(authentication.getDetails());
        return result;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return EmailAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
