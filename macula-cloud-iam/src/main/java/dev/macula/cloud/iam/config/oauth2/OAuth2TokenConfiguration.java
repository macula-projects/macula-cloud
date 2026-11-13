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

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.macula.cloud.iam.authentication.mfa.MfaCompletionService;
import dev.macula.cloud.iam.protocol.oauth2.CustomOAuth2TokenCustomizer;
import dev.macula.cloud.iam.protocol.oauth2.CustomOidcTokenCustomizer;
import dev.macula.cloud.iam.protocol.oauth2.ProtocolCheckingTokenGenerator;
import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2ClientRegistrationService;
import dev.macula.cloud.iam.protocol.oauth2.grant.CustomeOAuth2AccessTokenGenerator;
import dev.macula.cloud.iam.protocol.oauth2.token.claims.AccessTokenClaimsService;
import dev.macula.cloud.iam.protocol.oauth2.token.claims.impl.DefaultAccessTokenClaimsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenClaimsContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

/**
 * Token生成基础设施装配：格式适配、统一声明与发布前策略各自独立。
 *
 * @author Macula
 * @since 6.1.0
 */
@Configuration(proxyBeanMethods = false)
public class OAuth2TokenConfiguration {
    /**
     * 声明规则通过应用服务端口替换，不能因格式不同而复制安全规则。
     */
    @Bean
    @ConditionalOnMissingBean(AccessTokenClaimsService.class)
    AccessTokenClaimsService accessTokenClaimsService() {
        return new DefaultAccessTokenClaimsService();
    }

    /**
     * 不透明Token仅适配框架上下文。
     */
    @Bean
    OAuth2TokenCustomizer<OAuth2TokenClaimsContext> oAuth2TokenCustomizer(AccessTokenClaimsService claims) {
        return new CustomOAuth2TokenCustomizer(claims);
    }

    /**
     * 标准生成器外保留协议版本、资源许可和MFA发布围栏。
     */
    @Bean
    OAuth2TokenGenerator<OAuth2Token> tokenGenerator(JWKSource<SecurityContext> keys,
            OAuth2TokenCustomizer<OAuth2TokenClaimsContext> opaqueClaims, AccessTokenClaimsService claims,
            OAuth2ClientRegistrationService clients, MfaCompletionService mfa) {
        var opaque = new CustomeOAuth2AccessTokenGenerator();
        opaque.setAccessTokenCustomizer(opaqueClaims);
        var jwt = new JwtGenerator(new NimbusJwtEncoder(keys));
        jwt.setJwtCustomizer(new CustomOidcTokenCustomizer(claims));
        var generator = new ProtocolCheckingTokenGenerator(
                new DelegatingOAuth2TokenGenerator(jwt, opaque, new OAuth2RefreshTokenGenerator()), clients);
        generator.setMfaCompletion(mfa);
        return generator;
    }
}
