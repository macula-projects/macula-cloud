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

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import java.util.Objects;

/**
 * 原生grant处理前再次核对本次协议视图，拒绝陈旧或其他协议的已认证Client。
 * @author Rain
 * @since 6.1.0
 */
public final class ProtocolBoundAuthenticationProvider implements AuthenticationProvider {
    private final AuthenticationProvider delegate;
    private final RegisteredClientRepository clients;

    /** 原生provider负责证明验证和单次消费，不在此重新实现密码学或refresh逻辑。 */
    public ProtocolBoundAuthenticationProvider(AuthenticationProvider delegate, RegisteredClientRepository clients) {
        this.delegate = delegate;
        this.clients = clients;
    }

    /** 配置在客户端认证后发生变化也应拒绝，不能继续签发旧策略令牌。 */
    @Override
    public Authentication authenticate(Authentication authentication) {
        if (authentication instanceof OAuth2AuthorizationGrantAuthenticationToken request) {
            if (!(request.getPrincipal() instanceof OAuth2ClientAuthenticationToken client)
                || !client.isAuthenticated() || client.getRegisteredClient() == null) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
            }
            var actual = client.getRegisteredClient();
            var expected = clients.findByClientId(actual.getClientId());
            if (expected == null || !expected.getClientAuthenticationMethods().contains(client.getClientAuthenticationMethod())
                || !expected.getAuthorizationGrantTypes().contains(request.getGrantType())
                || !Objects.equals(expected.getClientSecret(), actual.getClientSecret())
                || !expected.getClientSettings().equals(actual.getClientSettings())
                || !expected.getTokenSettings().equals(actual.getTokenSettings())
                || !expected.getScopes().equals(actual.getScopes()) || !expected.getRedirectUris().equals(actual.getRedirectUris())) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
            }
        }
        return delegate.authenticate(authentication);
    }

    /** 保留原生provider支持类型和调用顺序。 */
    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }
}
