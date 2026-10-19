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
package dev.macula.cloud.iam.authentication.wecom;

import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyAuthenticationToken;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyUserDetailsService;
import dev.macula.cloud.iam.enums.ThirdPartyProviderType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 企业微信授权码认证，将成员 UserId 直接作为平台 username。
 *
 * @author Rain
 * @since 6.1.0
 */
@RequiredArgsConstructor
public class WecomAuthenticationProvider implements AuthenticationProvider {
    private final WecomClient wecomClient;
    private final IdentityProviderConfigService configService;
    private final ThirdPartyUserDetailsService userDetailsService;

    @Override
    public Authentication authenticate(Authentication authentication) {
        ThirdPartyAuthenticationToken token = (ThirdPartyAuthenticationToken)authentication;
        if (!ThirdPartyProviderType.WECOM.providerType().equals(token.getProviderType())) return null;
        var config = configService.requireEnabled(ThirdPartyProviderType.WECOM.providerType());
        String username = wecomClient.fetchUserId(config, token.getCredentials().toString());
        UserDetails user = userDetailsService.load(token.getLoginContext(), username);
        return ThirdPartyAuthenticationToken.authenticated(ThirdPartyProviderType.WECOM.providerType(), user,
            token.getLoginContext());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return ThirdPartyAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
