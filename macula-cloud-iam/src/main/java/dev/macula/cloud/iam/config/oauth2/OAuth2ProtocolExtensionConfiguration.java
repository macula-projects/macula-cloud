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
package dev.macula.cloud.iam.config.oauth2;

import dev.macula.cloud.iam.authentication.mfa.MfaCompletionService;
import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2ClientRegistrationService;
import dev.macula.cloud.iam.protocol.oauth2.configuration.AuthorizationEndpointExtension;
import dev.macula.cloud.iam.protocol.oauth2.configuration.AuthorizationServerExtension;
import dev.macula.cloud.iam.protocol.oauth2.configuration.ClientAuthenticationExtension;
import dev.macula.cloud.iam.protocol.oauth2.configuration.TokenEndpointExtension;
import dev.macula.cloud.iam.protocol.oauth2.grant.base.OAuth2UserGrantTokenIssuer;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeAccountResolver;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeRateLimiter;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeReplayStore;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeVerifierRegistry;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.OAuth2ExternalCodeAuthenticationProvider;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService;
import dev.macula.cloud.system.protocol.IamApplicationProtocolRegistry;
import dev.macula.cloud.system.protocol.OAuth2ProtocolDefinition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

/**
 * 装配独立协议扩展；实现负责端点语义，配置类只声明依赖关系。
 *
 * @author Macula
 * @since 6.1.0
 */
@Configuration(proxyBeanMethods = false)
public class OAuth2ProtocolExtensionConfiguration {
    /** 只由协议装配层选择已实现策略，公共应用服务不依赖OAuth具体类型。 */
    @Bean
    IamApplicationProtocolRegistry iamApplicationProtocolRegistry() {
        return OAuth2ProtocolDefinition.registry();
    }

    /**
     * 客户端认证使用请求视图，内省恢复使用明确限定的基础仓库。
     */
    @Bean
    AuthorizationServerExtension clientAuthenticationExtension(RegisteredClientRepository clients,
            @Qualifier("baseRegisteredClientRepository") RegisteredClientRepository base,
            OAuth2AuthorizationService authorizations, OAuth2ClientRegistrationService clientService,
            AuthorizationServerSettings settings) {
        return new ClientAuthenticationExtension(clients, base, authorizations, clientService, settings);
    }

    /**
     * 授权码端点保持原生校验后执行本次同意策略。
     */
    @Bean
    AuthorizationServerExtension authorizationEndpointExtension(OAuth2AuthorizationService authorizations) {
        return new AuthorizationEndpointExtension(authorizations);
    }

    /**
     * 协议grant Provider仅注册在Token端点，不能进入用户登录的全局认证管理器。
     */
    @Bean
    AuthorizationServerExtension tokenEndpointExtension(ExternalCodeVerifierRegistry verifiers,
            ExternalCodeReplayStore replay, ExternalCodeAccountResolver accounts,
            OAuth2AuthorizationService authorizations,
            OAuth2TokenGenerator<OAuth2Token> generator, MfaCompletionService mfa,
            IdentityProviderConfigService sources, ExternalCodeRateLimiter limiter,
            RegisteredClientRepository clients, AuthorizationServerSettings settings) {
        var externalCode = new OAuth2ExternalCodeAuthenticationProvider(verifiers, replay, accounts,
                new OAuth2UserGrantTokenIssuer(authorizations, generator, mfa), sources, limiter);
        return new TokenEndpointExtension(externalCode, clients, authorizations, settings);
    }
}
