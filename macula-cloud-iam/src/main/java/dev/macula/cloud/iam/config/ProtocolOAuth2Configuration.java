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

import cn.hutool.core.lang.Assert;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.macula.boot.constants.GlobalConstants;
import dev.macula.cloud.iam.authentication.email.EmailAuthenticationProvider;
import dev.macula.cloud.iam.authentication.email.EmailService;
import dev.macula.cloud.iam.authentication.sms.SmsAuthenticationProvider;
import dev.macula.cloud.iam.authentication.sms.SmsService;
import dev.macula.cloud.iam.authentication.sms.SmsUserDetailsService;
import dev.macula.cloud.iam.controller.ExternalIdentityBindingController;
import dev.macula.cloud.iam.controller.ProtocolVerificationCodeController;
import dev.macula.cloud.iam.mapper.SysOAuth2ClientExternalProviderMapper;
import dev.macula.cloud.iam.protocol.oauth2.CustomOAuth2TokenCustomizer;
import dev.macula.cloud.iam.protocol.oauth2.CustomOidcTokenCustomizer;
import dev.macula.cloud.iam.protocol.oauth2.LocalAccessTokenIntrospector;
import dev.macula.cloud.iam.protocol.oauth2.endpoint.OAuth2AuthenticationExceptionEntryPoint;
import dev.macula.cloud.iam.protocol.oauth2.endpoint.OAuth2TokenEndpointFailureHandler;
import dev.macula.cloud.iam.protocol.oauth2.grant.CompatibilityRefreshTokenAuthenticationProvider;
import dev.macula.cloud.iam.protocol.oauth2.grant.CustomeOAuth2AccessTokenGenerator;
import dev.macula.cloud.iam.protocol.oauth2.grant.base.OAuth2UserGrantTokenIssuer;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeAccountResolver;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeReplayStore;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeVerifierRegistry;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.OAuth2ExternalCodeAuthenticationConverter;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.OAuth2ExternalCodeAuthenticationProvider;
import dev.macula.cloud.iam.protocol.oauth2.grant.email.OAuth2ResourceOwnerEmailAuthenticationConverter;
import dev.macula.cloud.iam.protocol.oauth2.grant.email.OAuth2ResourceOwnerEmailAuthenticationProvider;
import dev.macula.cloud.iam.protocol.oauth2.grant.password.OAuth2ResourceOwnerPasswordAuthenticationConverter;
import dev.macula.cloud.iam.protocol.oauth2.grant.password.OAuth2ResourceOwnerPasswordAuthenticationProvider;
import dev.macula.cloud.iam.protocol.oauth2.grant.sms.OAuth2ResourceOwnerSmsAuthenticationConverter;
import dev.macula.cloud.iam.protocol.oauth2.grant.sms.OAuth2ResourceOwnerSmsAuthenticationProvider;
import dev.macula.cloud.iam.service.oauth2.MaculaOAuth2AuthorizationConsentService;
import dev.macula.cloud.iam.service.oauth2.MaculaOAuth2AuthorizationService;
import dev.macula.cloud.iam.service.oauth2.MaculaRegisteredClientRepository;
import dev.macula.cloud.iam.service.support.SysOAuth2ClientService;
import dev.macula.cloud.iam.service.userdetails.ClientAwareAccountResolver;
import dev.macula.cloud.iam.service.userdetails.OAuthClientUserDetailsService;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationConsentAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationConsentAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenClaimsContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.oauth2.server.resource.authentication.OpaqueTokenAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * {@code AuthorizationServerConfiguration} 基于Oauth2协议的配置
 *
 * @author rain
 * @since 2023/3/11 22:25
 */
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "macula.cloud.iam")
@EnableAsync
public class ProtocolOAuth2Configuration {
    private static final String CUSTOM_CONSENT_PAGE_URI = "/oauth2/consent";
    private static final Duration TOKEN_CORS_MAX_AGE = Duration.ofMinutes(30);

    @Setter
    private String issuerUri;

    @Setter
    private List<String> tokenCorsAllowedOrigins = List.of();

    @Bean("authorizationServerSecurityFilterChain")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
        OAuth2AuthorizationService authorizationService, OAuth2TokenGenerator<OAuth2Token> tokenGenerator,
        SmsUserDetailsService smsUsers, SmsService smsService,
        EmailService emailService, SysOAuth2ClientService clientService,
        ClientAwareAccountResolver accountResolver, OAuthClientUserDetailsService userDetails,
        ExternalCodeVerifierRegistry externalCodeVerifiers, ExternalCodeReplayStore externalCodeReplayStore,
        ExternalCodeAccountResolver externalCodeAccountResolver,
        RegisteredClientRepository registeredClientRepository,
        AuthorizationServerSettings authorizationServerSettings) throws Exception {

        // @formatter:off

        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();

        //  把自定义的授权确认URI加入配置
        authorizationServerConfigurer
            .authorizationEndpoint(authorizationEndpoint ->
                authorizationEndpoint
                    .consentPage(CUSTOM_CONSENT_PAGE_URI)
                    .authenticationProviders(providers -> providers.forEach(provider -> {
                        if (provider instanceof OAuth2AuthorizationConsentAuthenticationProvider consentProvider) {
                            consentProvider.setAuthorizationConsentCustomizer(context -> {
                                OAuth2AuthorizationConsentAuthenticationToken consent = context.getAuthentication();
                                if (!"deny".equals(consent.getAdditionalParameters().get("decision"))) {
                                    return;
                                }
                                // The framework has already validated state, principal and registered redirect.
                                // Deny this request even when previous consent exists; preserve that earlier consent.
                                var request = context.getAuthorizationRequest();
                                String redirect = request.getRedirectUri() != null ? request.getRedirectUri()
                                    : context.getRegisteredClient().getRedirectUris().iterator().next();
                                authorizationService.remove(context.getAuthorization());
                                throw new OAuth2AuthorizationCodeRequestAuthenticationException(
                                    new OAuth2Error(OAuth2ErrorCodes.ACCESS_DENIED),
                                    new OAuth2AuthorizationCodeRequestAuthenticationToken(consent.getAuthorizationUri(),
                                        consent.getClientId(), (Authentication) consent.getPrincipal(), redirect,
                                        request.getState(), request.getScopes(), null));
                            });
                        }
                    }))
            )
            .tokenEndpoint(tokenEndpoint ->
                tokenEndpoint
                    .accessTokenRequestConverter(new OAuth2ResourceOwnerPasswordAuthenticationConverter())
                    .accessTokenRequestConverter(new OAuth2ResourceOwnerSmsAuthenticationConverter())
                    .accessTokenRequestConverter(new OAuth2ResourceOwnerEmailAuthenticationConverter())
                    .accessTokenRequestConverter(new OAuth2ExternalCodeAuthenticationConverter())
                    .errorResponseHandler(new OAuth2TokenEndpointFailureHandler())
                    .authenticationProviders(providers -> {
                        providers.replaceAll(provider -> provider instanceof OAuth2RefreshTokenAuthenticationProvider
                            ? new CompatibilityRefreshTokenAuthenticationProvider(provider, authorizationService, tokenGenerator)
                            : provider);
                        // The shared manager exists at request time, after HttpSecurity has been built.
                        AuthenticationManager userAuthentication = authentication ->
                            http.getSharedObject(AuthenticationManager.class).authenticate(authentication);
                        providers.add(new OAuth2ResourceOwnerPasswordAuthenticationProvider(
                            userAuthentication, authorizationService, tokenGenerator));
                        providers.add(new OAuth2ResourceOwnerSmsAuthenticationProvider(
                            userAuthentication, authorizationService, tokenGenerator));
                        providers.add(new OAuth2ResourceOwnerEmailAuthenticationProvider(
                            userAuthentication, authorizationService, tokenGenerator));
                        providers.add(new OAuth2ExternalCodeAuthenticationProvider(externalCodeVerifiers,
                            externalCodeReplayStore, externalCodeAccountResolver,
                            new OAuth2UserGrantTokenIssuer(authorizationService, tokenGenerator)));
                    })
            )
            .oidc(Customizer.withDefaults());

        RequestMatcher endpointsMatcher = authorizationServerConfigurer.getEndpointsMatcher();
        RequestMatcher externalBindingMatcher = PathPatternRequestMatcher.pathPattern(HttpMethod.POST,
            ExternalIdentityBindingController.BINDING_PATH);
        RequestMatcher verificationMatcher = new OrRequestMatcher(
            PathPatternRequestMatcher.pathPattern(ProtocolVerificationCodeController.CAPTCHA_PATH),
            PathPatternRequestMatcher.pathPattern(ProtocolVerificationCodeController.SMS_CODE_PATH),
            PathPatternRequestMatcher.pathPattern(ProtocolVerificationCodeController.EMAIL_CODE_PATH));
        RequestMatcher protocolMatcher = new OrRequestMatcher(endpointsMatcher,
            PathPatternRequestMatcher.pathPattern(HttpMethod.OPTIONS,
                authorizationServerSettings.getTokenEndpoint()), externalBindingMatcher, verificationMatcher);

        // 拦截 授权服务器相关的请求端点
        http
            .securityMatcher(protocolMatcher)
            .cors(cors -> cors.configurationSource(protocolCorsConfigurationSource(authorizationServerSettings,
                registeredClientRepository, clientService)))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(verificationMatcher).permitAll()
                .anyRequest().authenticated())
            // 忽略掉相关端点的csrf
            .csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher, externalBindingMatcher,
                verificationMatcher))
            .exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(new BearerTokenAuthenticationEntryPoint(),
                    externalBindingMatcher)
                .authenticationEntryPoint(new OAuth2AuthenticationExceptionEntryPoint("/login")))
            // 应用 授权服务器的配置
            .with(authorizationServerConfigurer, Customizer.withDefaults())
            .authenticationProvider(new SmsAuthenticationProvider(smsUsers, smsService))
            .authenticationProvider(new EmailAuthenticationProvider(emailService, clientService, accountResolver,
                userDetails));

        // Security 7 installs a JWT resource-server configurer for UserInfo. Supply its
        // authentication manager explicitly so both persisted JWT and opaque tokens work.
        OpaqueTokenAuthenticationProvider bearerProvider = new OpaqueTokenAuthenticationProvider(
            new LocalAccessTokenIntrospector(authorizationService));

        http.oauth2ResourceServer(resource -> resource
            .jwt(jwt -> jwt.authenticationManager(bearerProvider::authenticate)));

        // @formatter:on
        return http.build();
    }

    private CorsConfigurationSource tokenCorsConfigurationSource(
        AuthorizationServerSettings authorizationServerSettings) {
        List<String> allowedOrigins = tokenCorsAllowedOrigins.stream()
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .toList();
        if (allowedOrigins.contains(CorsConfiguration.ALL)) {
            throw new IllegalStateException("OAuth2 token CORS 不允许通配来源");
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of(HttpMethod.POST.name(), HttpMethod.OPTIONS.name()));
        configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE,
            GlobalConstants.TENANT_ID_NAME));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(TOKEN_CORS_MAX_AGE);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(authorizationServerSettings.getTokenEndpoint(), configuration);
        return source;
    }

    private CorsConfigurationSource protocolCorsConfigurationSource(
        AuthorizationServerSettings authorizationServerSettings,
        RegisteredClientRepository registeredClientRepository, SysOAuth2ClientService sysOAuth2ClientService) {
        CorsConfigurationSource tokenSource = tokenCorsConfigurationSource(authorizationServerSettings);
        CorsConfigurationSource loginSource =
            new ProtocolLoginCorsConfigurationSource(registeredClientRepository, sysOAuth2ClientService);
        return request -> {
            CorsConfiguration loginConfiguration = loginSource.getCorsConfiguration(request);
            return loginConfiguration != null ? loginConfiguration : tokenSource.getCorsConfiguration(request);
        };
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        Assert.notBlank(issuerUri, "OAuth2 Server Issuer Uri Cann't be Empty");
        return AuthorizationServerSettings.builder().issuer(issuerUri).build();
    }

    @Bean("loginVerificationTaskExecutor")
    TaskExecutor loginVerificationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("iam-login-code-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.initialize();
        return executor;
    }

    @Bean
    OAuth2AuthorizationConsentService oauth2AuthorizationConsentService(RedisTemplate<String, Object> redisTemplate) {
        return new MaculaOAuth2AuthorizationConsentService(redisTemplate);
    }

    @Bean
    OAuth2AuthorizationService oauth2AuthorizationService(RedisTemplate<String, Object> redisTemplate,
        RegisteredClientRepository registeredClientRepository) {
        return new MaculaOAuth2AuthorizationService(redisTemplate, registeredClientRepository);
    }

    @Bean
    RegisteredClientRepository registeredClientRepository(SysOAuth2ClientService sysOAuth2ClientService,
        SysOAuth2ClientExternalProviderMapper externalProviderMapper) {
        return new MaculaRegisteredClientRepository(sysOAuth2ClientService, externalProviderMapper);
    }

    @Bean
    OAuth2TokenCustomizer<OAuth2TokenClaimsContext> oAuth2TokenCustomizer() {
        return new CustomOAuth2TokenCustomizer();
    }

    @Bean
    OAuth2TokenGenerator<OAuth2Token> tokenGenerator(JWKSource<SecurityContext> jwkSource,
        OAuth2TokenCustomizer<OAuth2TokenClaimsContext> accessTokenCustomizer) {
        CustomeOAuth2AccessTokenGenerator accessTokenGenerator = new CustomeOAuth2AccessTokenGenerator();
        accessTokenGenerator.setAccessTokenCustomizer(accessTokenCustomizer);
        JwtGenerator jwtGenerator = new JwtGenerator(new NimbusJwtEncoder(jwkSource));
        jwtGenerator.setJwtCustomizer(new CustomOidcTokenCustomizer());
        return new DelegatingOAuth2TokenGenerator(jwtGenerator, accessTokenGenerator,
            new OAuth2RefreshTokenGenerator());
    }
}
