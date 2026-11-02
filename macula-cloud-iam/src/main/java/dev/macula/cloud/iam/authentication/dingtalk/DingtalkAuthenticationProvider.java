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
package dev.macula.cloud.iam.authentication.dingtalk;

import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyAuthenticationToken;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyIdentityResolutionService;
import dev.macula.cloud.iam.enums.ThirdPartyProviderType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 钉钉授权码认证，以配置组织内成员 userId 作为绑定主体。
 *
 * @author Rain
 * @since 6.1.0
 */
@RequiredArgsConstructor
public class DingtalkAuthenticationProvider implements AuthenticationProvider {
    private final DingtalkClient client;
    private final IdentityProviderConfigService configs;
    private final ThirdPartyIdentityResolutionService identities;

    @Override
    public Authentication authenticate(Authentication authentication) {
        ThirdPartyAuthenticationToken token = (ThirdPartyAuthenticationToken)authentication;
        if (!ThirdPartyProviderType.DINGTALK.providerType().equals(token.getProviderType())) {
            return null;
        }
        var config = configs.requireEnabled(ThirdPartyProviderType.DINGTALK.providerType());
        String subject = client.fetchUserId(config, token.getCredentials().toString(),
            DingtalkLoginMode.valueOf(token.getLoginContext().loginMode()));
        UserDetails user = identities.resolve(ThirdPartyProviderType.DINGTALK.providerType(), subject,
            token.getLoginContext());
        return ThirdPartyAuthenticationToken.authenticated(ThirdPartyProviderType.DINGTALK.providerType(), user,
            token.getLoginContext());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return ThirdPartyAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
