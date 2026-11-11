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
package dev.macula.cloud.iam.protocol.oauth2.configuration;

import dev.macula.cloud.iam.protocol.oauth2.ResourceTokenIntrospectionAuthenticationProvider;
import dev.macula.cloud.iam.protocol.oauth2.client.PublicTokenClientAuthenticationConverter;
import dev.macula.cloud.iam.protocol.oauth2.client.PublicTokenClientAuthenticationProvider;
import dev.macula.cloud.iam.protocol.oauth2.jwt.PolicyClientAssertionDecoderFactory;
import dev.macula.cloud.iam.service.client.SysOAuth2ClientService;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.JwtClientAssertionAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * OAuth Client身份适配（不是用户登录）：受限公共身份、私钥断言和资源服务器内省。
 *
 * @author Macula
 * @since 6.1.0
 */
public final class ClientAuthenticationExtension implements AuthorizationServerExtension {
    private final RegisteredClientRepository clients;
    private final RegisteredClientRepository baseClients;
    private final OAuth2AuthorizationService authorizations;
    private final SysOAuth2ClientService clientService;
    private final RequestMatcher tokenEndpoint;
    private final PolicyClientAssertionDecoderFactory decoders = new PolicyClientAssertionDecoderFactory();

    /**
     * 区分请求协议视图与只读恢复仓库，不能用目标Client选择内省调用方身份。
     */
    public ClientAuthenticationExtension(RegisteredClientRepository clients, RegisteredClientRepository baseClients,
            OAuth2AuthorizationService authorizations, SysOAuth2ClientService clientService,
            AuthorizationServerSettings settings) {
        this.clients = clients;
        this.baseClients = baseClients;
        this.authorizations = authorizations;
        this.clientService = clientService;
        tokenEndpoint = PathPatternRequestMatcher.pathPattern(HttpMethod.POST, settings.getTokenEndpoint());
    }

    /**
     * 客户端认证和内省各自保持明确的信任边界。
     */
    @Override
    public void configure(OAuth2AuthorizationServerConfigurer server) {
        server.clientAuthentication(client -> client
                .authenticationConverter(new PublicTokenClientAuthenticationConverter(tokenEndpoint))
                .authenticationProvider(new PublicTokenClientAuthenticationProvider(clients))
                .authenticationProviders(providers -> providers.forEach(provider -> {
                    if (provider instanceof JwtClientAssertionAuthenticationProvider assertion) {
                        assertion.setJwtDecoderFactory(decoders);
                    }
                })));
        server.tokenIntrospectionEndpoint(endpoint -> endpoint.authenticationProviders(providers -> {
            for (int index = 0; index < providers.size(); index++) {
                if (providers.get(index) instanceof OAuth2TokenIntrospectionAuthenticationProvider) {
                    providers.set(index,
                            new ResourceTokenIntrospectionAuthenticationProvider(baseClients, authorizations,
                                    clientService));
                }
            }
        }));
    }
}
