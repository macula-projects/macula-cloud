/*
 * Copyright (c) 2023 Macula
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

package dev.macula.cloud.iam.playground;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenIntrospection;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;

/**
 * 在标准 introspection 认证后隔离演示令牌，不改变业务客户端之间的查询规则。
 * @author Rain
 * @since 6.1.0
 */
public final class PlaygroundTokenIntrospectionAuthenticationProvider implements AuthenticationProvider {
    private final AuthenticationProvider delegate;
    private final OAuth2AuthorizationService authorizations;

    public PlaygroundTokenIntrospectionAuthenticationProvider(AuthenticationProvider delegate,
        OAuth2AuthorizationService authorizations) {
        this.delegate = delegate;
        this.authorizations = authorizations;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        // Delegate first: never answer a token query before normal client authentication/validation.
        Authentication result = delegate.authenticate(authentication);
        if (!(result instanceof OAuth2TokenIntrospectionAuthenticationToken response)
            || !response.getTokenClaims().isActive()) return result;
        var client = ((OAuth2ClientAuthenticationToken) response.getPrincipal()).getRegisteredClient();
        var authorization = authorizations.findByToken(response.getToken(), null);
        if (authorization == null) return inactive(response);
        String owner = authorization.getRegisteredClientId();
        boolean demoOwner = PlaygroundRegisteredClientRepository.isPlayground(owner);
        boolean demoCaller = client != null && (PlaygroundRegisteredClientRepository.isPlayground(client.getId())
            || PlaygroundRegisteredClientRepository.isPlayground(client.getClientId()));
        if ((demoOwner || demoCaller) && (client == null || !client.getId().equals(owner))) {
            return inactive(response);
        }
        return result;
    }

    private Authentication inactive(OAuth2TokenIntrospectionAuthenticationToken response) {
        return new OAuth2TokenIntrospectionAuthenticationToken(response.getToken(),
            (Authentication) response.getPrincipal(), OAuth2TokenIntrospection.builder().active(false).build());
    }

    @Override
    public boolean supports(Class<?> authentication) { return delegate.supports(authentication); }
}
