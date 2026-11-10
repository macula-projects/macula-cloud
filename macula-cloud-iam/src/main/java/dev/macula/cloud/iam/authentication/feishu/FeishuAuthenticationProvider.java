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
package dev.macula.cloud.iam.authentication.feishu;

import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService;
import dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyAuthenticationToken;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyIdentityResolutionService;
import dev.macula.cloud.iam.enums.ThirdPartyProviderType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 飞书授权码认证，使用稳定OpenID识别身份，员工编号仅用于明确配置的匹配。
 *
 * @author Rain
 * @since 6.1.0
 */
@RequiredArgsConstructor
public class FeishuAuthenticationProvider implements AuthenticationProvider {
    private final FeishuClient client;
    private final IdentityProviderConfigService configs;
    private final ThirdPartyIdentityResolutionService identities;

    @Override
    public Authentication authenticate(Authentication authentication) {
        ThirdPartyAuthenticationToken token = (ThirdPartyAuthenticationToken)authentication;
        if (!ThirdPartyProviderType.FEISHU.providerType().equals(token.getProviderType())) {
            return null;
        }
        var config = configs.requireEnabledById(token.getLoginContext().registrationId(), ThirdPartyProviderType.FEISHU.providerType()).forCapability(token.getLoginContext().loginCapability());
        VerifiedExternalIdentity identity = client.fetchIdentity(config, token.getCredentials().toString(), token.getRedirectUri(),
            FeishuLoginMode.valueOf(token.getLoginContext().loginMode()), VerifiedExternalIdentity.BROWSER);
        UserDetails user = identities.resolve(identity,
            token.getLoginContext());
        return ThirdPartyAuthenticationToken.authenticated(ThirdPartyProviderType.FEISHU.providerType(), user,
            token.getLoginContext());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return ThirdPartyAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
