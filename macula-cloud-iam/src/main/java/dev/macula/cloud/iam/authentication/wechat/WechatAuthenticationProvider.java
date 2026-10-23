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
package dev.macula.cloud.iam.authentication.wechat;

import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyAuthenticationToken;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyIdentityResolutionService;
import dev.macula.cloud.iam.enums.ThirdPartyProviderType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 微信授权码认证，以开放平台 unionid 作为绑定主体。
 *
 * @author Rain
 * @since 6.1.0
 */
@RequiredArgsConstructor
public class WechatAuthenticationProvider implements AuthenticationProvider {
    private final WechatClient client;
    private final IdentityProviderConfigService configs;
    private final ThirdPartyIdentityResolutionService identities;

    @Override
    public Authentication authenticate(Authentication authentication) {
        ThirdPartyAuthenticationToken token = (ThirdPartyAuthenticationToken)authentication;
        if (!ThirdPartyProviderType.WECHAT.providerType().equals(token.getProviderType())) {
            return null;
        }
        var config = configs.requireEnabled(ThirdPartyProviderType.WECHAT.providerType());
        String subject = client.fetchUnionId(config, token.getCredentials().toString(),
            WechatLoginMode.valueOf(token.getLoginContext().loginMode()));
        UserDetails user = identities.resolve(ThirdPartyProviderType.WECHAT.providerType(), subject,
            token.getLoginContext());
        return ThirdPartyAuthenticationToken.authenticated(ThirdPartyProviderType.WECHAT.providerType(), user,
            token.getLoginContext());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return ThirdPartyAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
