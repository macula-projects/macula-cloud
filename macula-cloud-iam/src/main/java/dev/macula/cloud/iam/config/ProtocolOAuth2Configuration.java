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

package dev.macula.cloud.iam.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.macula.cloud.iam.jose.Jwks;
import dev.macula.cloud.iam.service.oauth2.MaculaOAuth2AuthorizationConsentService;
import dev.macula.cloud.iam.service.oauth2.MaculaOAuth2AuthorizationService;
import dev.macula.cloud.iam.service.oauth2.MaculaRegisteredClientRepository;
import dev.macula.cloud.iam.service.support.SysOAuth2ClientService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * {@code AuthorizationServerConfiguration} 基于Oauth2协议的配置
 *
 * @author rain
 * @since 2023/3/11 22:25
 */
@Configuration(proxyBeanMethods = false)
public class ProtocolOAuth2Configuration {

    private static final String CUSTOM_CONSENT_PAGE_URI = "/oauth2/consent";

    @Bean("authorizationServerSecurityFilterChain")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        // @formatter:off
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();
        //  把自定义的授权确认URI加入配置
        authorizationServerConfigurer
            .authorizationEndpoint(authorizationEndpoint ->
                authorizationEndpoint
                    .consentPage(CUSTOM_CONSENT_PAGE_URI))
            .oidc(Customizer.withDefaults());

        RequestMatcher endpointsMatcher = authorizationServerConfigurer.getEndpointsMatcher();

        // 拦截 授权服务器相关的请求端点
        http
            .requestMatcher(endpointsMatcher)
            .authorizeRequests().anyRequest().authenticated().and()
            // 忽略掉相关端点的csrf
            .csrf().ignoringRequestMatchers(endpointsMatcher).and()
            .exceptionHandling().authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")).and()
            // 应用 授权服务器的配置
            .apply(authorizationServerConfigurer);
        return http.build();
        // @formatter:on
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder().build();
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        RSAKey rsaKey = Jwks.generateRsa();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return (jwkSelector, securityContext) -> jwkSelector.select(jwkSet);
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    OAuth2AuthorizationConsentService oauth2AuthorizationConsentService(RedisTemplate<String, Object> redisTemplate) {
        return new MaculaOAuth2AuthorizationConsentService(redisTemplate);
    }

    @Bean
    OAuth2AuthorizationService oauth2AuthorizationService(RedisTemplate<String, Object> redisTemplate) {
        return new MaculaOAuth2AuthorizationService(redisTemplate);
    }

    @Bean
    RegisteredClientRepository registeredClientRepository(SysOAuth2ClientService sysOAuth2ClientService) {
        return new MaculaRegisteredClientRepository(sysOAuth2ClientService);
    }
}
