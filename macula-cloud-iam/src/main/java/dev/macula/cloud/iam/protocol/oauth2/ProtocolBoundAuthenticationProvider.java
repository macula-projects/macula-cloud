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
package dev.macula.cloud.iam.protocol.oauth2;

import java.util.Objects;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * 原生grant处理前再次核对本次协议视图，拒绝陈旧或其他协议的已认证Client。
 *
 * @author Rain
 * @since 6.1.0
 */
public final class ProtocolBoundAuthenticationProvider implements AuthenticationProvider {
    private final AuthenticationProvider delegate;
    private final RegisteredClientRepository clients;
    private final org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService authorizations;

    /**
     * 原生provider处理密码学与单次消费；授权服务只用于拒绝退役Refresh。
     */
    public ProtocolBoundAuthenticationProvider(AuthenticationProvider delegate, RegisteredClientRepository clients,
            org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService authorizations) {
        this.delegate = delegate;
        this.clients = clients;
        this.authorizations = Objects.requireNonNull(authorizations);
    }

    /**
     * 配置在客户端认证后发生变化也应拒绝，不能继续签发旧策略令牌。
     */
    @Override
    public Authentication authenticate(Authentication authentication) {
        if (authentication instanceof OAuth2AuthorizationGrantAuthenticationToken request) {
            if (!(request.getPrincipal() instanceof OAuth2ClientAuthenticationToken client)
                    || !client.isAuthenticated() || client.getRegisteredClient() == null) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
            }
            var actual = client.getRegisteredClient();
            if (request instanceof org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken refresh) {
                var original = authorizations.findByToken(refresh.getRefreshToken(),
                        org.springframework.security.oauth2.server.authorization.OAuth2TokenType.REFRESH_TOKEN);
                if (original != null && actual.getId().equals(original.getRegisteredClientId())
                        && dev.macula.cloud.system.security.OAuth2GrantPolicy.retired(
                        original.getAuthorizationGrantType().getValue())) {
                    throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
                }
            }
            var expected = clients.findByClientId(actual.getClientId());
            if (expected == null ||
                    !expected.getClientAuthenticationMethods().contains(client.getClientAuthenticationMethod())
                    || !expected.getAuthorizationGrantTypes().contains(request.getGrantType())
                    || !Objects.equals(expected.getClientSecret(), actual.getClientSecret())
                    || !expected.getClientSettings().equals(actual.getClientSettings())
                    || !expected.getTokenSettings().equals(actual.getTokenSettings())
                    || !expected.getScopes().equals(actual.getScopes()) ||
                    !expected.getRedirectUris().equals(actual.getRedirectUris())) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
            }
        }
        return delegate.authenticate(authentication);
    }

    /**
     * 保留原生provider支持类型和调用顺序。
     */
    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }
}
