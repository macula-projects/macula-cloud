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

import java.util.List;

import dev.macula.cloud.iam.authentication.inapp.InAppAuthorizationRequestFilter;
import dev.macula.cloud.iam.authentication.inapp.InAppLoginPolicyService;
import dev.macula.cloud.iam.config.MfaExtensionConfiguration;
import dev.macula.cloud.iam.protocol.oauth2.LocalAccessTokenIntrospector;
import dev.macula.cloud.iam.protocol.oauth2.binding.ExternalIdentityBindingController;
import dev.macula.cloud.iam.protocol.oauth2.configuration.AuthorizationServerExtension;
import dev.macula.cloud.iam.protocol.oauth2.endpoint.OAuth2CorsConfigurationSource;
import dev.macula.cloud.iam.protocol.oauth2.endpoint.OAuth2ProtocolAuthenticationEntryPoint;
import dev.macula.cloud.iam.protocol.oauth2.endpoint.OAuth2TokenOriginFilter;
import dev.macula.cloud.iam.protocol.oauth2.endpoint.RetiredLoginEndpointFilter;
import dev.macula.cloud.iam.protocol.oauth2.profile.ProfileController;
import dev.macula.cloud.iam.service.client.SysOAuth2ClientService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.resource.authentication.OpaqueTokenAuthenticationProvider;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.session.DisableEncodeUrlFilter;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;

/**
 * OAuth协议链装配入口；消费已认证身份，用户登录由DefaultSecurityConfiguration独立负责。
 *
 * @author Macula
 * @since 6.1.0
 */
@Configuration(proxyBeanMethods = false)
@Import({MfaExtensionConfiguration.class, OAuth2ProtocolExtensionConfiguration.class,
        OAuth2TokenConfiguration.class, OAuth2PersistenceConfiguration.class})
public class ProtocolOAuth2Configuration {
    /**
     * 声明端点及过滤顺序，保持CSRF、受控Origin与端内证明的原安全边界。
     */
    @Bean("authorizationServerSecurityFilterChain")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
            List<AuthorizationServerExtension> extensions, AuthorizationServerSettings settings,
            OAuth2AuthorizationService authorizations, SysOAuth2ClientService clients,
            InAppLoginPolicyService inApp, RequestCache browserRequestCache,
            OAuth2CorsConfigurationSource cors) throws Exception {
        var server = new OAuth2AuthorizationServerConfigurer();
        extensions.forEach(extension -> extension.configure(server));
        server.oidc(Customizer.withDefaults());
        RequestMatcher endpoints = server.getEndpointsMatcher();
        RequestMatcher binding = PathPatternRequestMatcher.pathPattern(HttpMethod.POST,
                ExternalIdentityBindingController.BINDING_PATH);
        http.securityMatcher(protocolMatcher(endpoints, binding, settings))
                .requestCache(cache -> cache.requestCache(browserRequestCache))
                .cors(configuration -> configuration.configurationSource(cors))
                .authorizeHttpRequests(
                        authorize -> authorize.requestMatchers(ProfileController.PATH).permitAll().anyRequest()
                                .authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers(endpoints, binding))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        new OAuth2ProtocolAuthenticationEntryPoint(settings)))
                .with(server, Customizer.withDefaults())
                .addFilterBefore(new RetiredLoginEndpointFilter(), DisableEncodeUrlFilter.class)
                .addFilterAfter(new InAppAuthorizationRequestFilter(inApp, settings), SecurityContextHolderFilter.class)
                .addFilterBefore(new OAuth2TokenOriginFilter(settings.getTokenEndpoint(), clients),
                        UsernamePasswordAuthenticationFilter.class);
        var bearer = new OpaqueTokenAuthenticationProvider(new LocalAccessTokenIntrospector(authorizations));
        http.oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.authenticationManager(bearer::authenticate)));
        return http.build();
    }

    /**
     * 协议与元数据预检使用同一受控链；退役路径不能掉入托管登录。
     */
    private RequestMatcher protocolMatcher(RequestMatcher endpoints, RequestMatcher binding,
            AuthorizationServerSettings settings) {
        return new OrRequestMatcher(endpoints, binding, RetiredLoginEndpointFilter::matches,
                PathPatternRequestMatcher.pathPattern(ProfileController.PATH),
                PathPatternRequestMatcher.pathPattern(HttpMethod.OPTIONS, settings.getTokenEndpoint()),
                PathPatternRequestMatcher.pathPattern(HttpMethod.OPTIONS, OAuth2CorsConfigurationSource.DISCOVERY_PATH),
                PathPatternRequestMatcher.pathPattern(HttpMethod.OPTIONS, OAuth2CorsConfigurationSource.JWKS_PATH),
                PathPatternRequestMatcher.pathPattern(HttpMethod.OPTIONS,
                        OAuth2CorsConfigurationSource.OAUTH_METADATA_PATH));
    }

    /**
     * issuer仍使用既有配置键，缺失时启动失败，不猜测代理请求地址。
     */
    @Bean
    AuthorizationServerSettings authorizationServerSettings(@Value("${macula.cloud.iam.issuer-uri:}") String issuer) {
        Assert.hasText(issuer, "OAuth2 Server Issuer Uri Cann't be Empty");
        return AuthorizationServerSettings.builder().issuer(issuer).build();
    }

    /**
     * 跨域规则只依赖当前Client Origin目录和明确Token端点。
     */
    @Bean
    OAuth2CorsConfigurationSource protocolCorsConfigurationSource(AuthorizationServerSettings settings,
            SysOAuth2ClientService clients) {
        return new OAuth2CorsConfigurationSource(clients, settings.getTokenEndpoint());
    }
}
