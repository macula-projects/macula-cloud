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

import dev.macula.cloud.iam.protocol.oauth2.ProtocolBoundAuthenticationProvider;
import dev.macula.cloud.iam.protocol.oauth2.endpoint.OAuth2TokenEndpointFailureHandler;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.OAuth2ExternalCodeAuthenticationConverter;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.OAuth2ExternalCodeAuthenticationProvider;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

/**
 * Token端点只装配唯一external_code扩展与当前协议围栏，标准签发仍委托框架。
 *
 * @author Macula
 * @since 6.1.0
 */
public final class TokenEndpointExtension implements AuthorizationServerExtension {
    private final OAuth2ExternalCodeAuthenticationProvider externalCode;
    private final RegisteredClientRepository clients;
    private final OAuth2AuthorizationService authorizations;
    private final String issuer;

    /**
     * 注入完整认证服务，配置扩展不创建或跳过安全依赖。
     */
    public TokenEndpointExtension(OAuth2ExternalCodeAuthenticationProvider externalCode,
            RegisteredClientRepository clients,
            OAuth2AuthorizationService authorizations, AuthorizationServerSettings settings) {
        this.externalCode = externalCode;
        this.clients = clients;
        this.authorizations = authorizations;
        issuer = settings.getIssuer();
    }

    /**
     * 保留框架provider并统一复核当前Client快照及退役Refresh边界。
     */
    @Override
    public void configure(OAuth2AuthorizationServerConfigurer server) {
        server.tokenEndpoint(endpoint -> endpoint
                .accessTokenRequestConverter(new OAuth2ExternalCodeAuthenticationConverter())
                .errorResponseHandler(new OAuth2TokenEndpointFailureHandler(issuer))
                .authenticationProviders(providers -> {
                    providers.add(externalCode);
                    for (int index = 0; index < providers.size(); index++) {
                        providers.set(index,
                                new ProtocolBoundAuthenticationProvider(providers.get(index), clients, authorizations));
                    }
                }));
    }
}
