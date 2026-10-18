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
package dev.macula.cloud.iam.authentication.github;

import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyAuthenticationToken;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * GitHub 授权码认证，只按数值 subject 查找预置全局绑定。
 *
 * @author Rain
 * @since 6.1.0
 */
@RequiredArgsConstructor
public class GithubAuthenticationProvider implements AuthenticationProvider {
    private final GithubClient githubClient;
    private final IdentityProviderConfigService configService;
    private final ThirdPartyUserDetailsService userDetailsService;

    @Override
    public Authentication authenticate(Authentication authentication) {
        ThirdPartyAuthenticationToken token = (ThirdPartyAuthenticationToken)authentication;
        if (!"GITHUB".equals(token.getProviderType())) return null;
        var config = configService.requireEnabled("GITHUB");
        String subject = githubClient.fetchSubject(config, token.getCredentials().toString(), token.getRedirectUri());
        String username = configService.requireBoundUsername("GITHUB", subject);
        UserDetails user = userDetailsService.load(token.getLoginContext(), username);
        return ThirdPartyAuthenticationToken.authenticated("GITHUB", user, token.getLoginContext());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return ThirdPartyAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
