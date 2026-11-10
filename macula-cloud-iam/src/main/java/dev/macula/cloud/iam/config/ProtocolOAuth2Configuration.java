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
import dev.macula.cloud.iam.protocol.oauth2.client.PublicTokenClientAuthenticationConverter;
import dev.macula.cloud.iam.protocol.oauth2.client.PublicTokenClientAuthenticationProvider;
import dev.macula.cloud.iam.protocol.oauth2.endpoint.OAuth2AuthenticationExceptionEntryPoint;
import dev.macula.cloud.iam.protocol.oauth2.endpoint.OAuth2TokenEndpointFailureHandler;
import dev.macula.cloud.iam.protocol.oauth2.endpoint.OAuth2TokenOriginFilter;
import dev.macula.cloud.iam.protocol.oauth2.grant.CustomeOAuth2AccessTokenGenerator;
import dev.macula.cloud.iam.protocol.oauth2.grant.base.OAuth2UserGrantTokenIssuer;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeAccountResolver;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeReplayStore;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeVerifierRegistry;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.OAuth2ExternalCodeAuthenticationConverter;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.OAuth2ExternalCodeAuthenticationProvider;
import dev.macula.cloud.iam.protocol.oauth2.grant.email.OAuth2ResourceOwnerEmailAuthenticationConverter;
import dev.macula.cloud.iam.protocol.oauth2.grant.email.OAuth2ResourceOwnerEmailAuthenticationProvider;
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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;


/**
 * {@code AuthorizationServerConfiguration} 基于Oauth2协议的配置
 *
 * @author rain
 * @since 2023/3/11 22:25
 */
@org.springframework.context.annotation.Import(MfaExtensionConfiguration.class)
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "macula.cloud.iam")
@EnableAsync
public class ProtocolOAuth2Configuration {
    private static final int MAX_ASSERTION_DECODERS = 256;
    private static final String CUSTOM_CONSENT_PAGE_URI = "/oauth2/consent";
    @Setter
    private String issuerUri;

    /** 安装协议原生安全链；端内证明隔离位于上下文加载后，回调/Scope/PKCE仍由SAS校验。 */
    @Bean("authorizationServerSecurityFilterChain")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
        OAuth2AuthorizationService authorizationService, OAuth2TokenGenerator<OAuth2Token> tokenGenerator,
        SmsUserDetailsService smsUsers, SmsService smsService,
        EmailService emailService, SysOAuth2ClientService clientService,
        ClientAwareAccountResolver accountResolver, OAuthClientUserDetailsService userDetails,
        ExternalCodeVerifierRegistry externalCodeVerifiers, ExternalCodeReplayStore externalCodeReplayStore,
        ExternalCodeAccountResolver externalCodeAccountResolver,
        dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService identitySources,
        dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeRateLimiter externalCodeLimiter,
        dev.macula.cloud.iam.authentication.mfa.MfaCompletionService mfaCompletion,
        RegisteredClientRepository registeredClientRepository,
        @org.springframework.beans.factory.annotation.Qualifier("baseRegisteredClientRepository") RegisteredClientRepository baseRegisteredClientRepository,
        AuthorizationServerSettings authorizationServerSettings,
        dev.macula.cloud.iam.authentication.inapp.InAppLoginPolicyService inAppPolicy,
        org.springframework.security.web.savedrequest.RequestCache browserRequestCache) throws Exception {

        // @formatter:off

        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();
        RequestMatcher tokenEndpointMatcher = PathPatternRequestMatcher.pathPattern(HttpMethod.POST,
            authorizationServerSettings.getTokenEndpoint());

        var assertionDecoders = protocolClientAssertionDecoders();

        //  把自定义的授权确认URI加入配置
        authorizationServerConfigurer
            .clientAuthentication(clientAuthentication -> clientAuthentication
                .authenticationConverter(new PublicTokenClientAuthenticationConverter(tokenEndpointMatcher))
                .authenticationProvider(new PublicTokenClientAuthenticationProvider(registeredClientRepository))
                .authenticationProviders(providers -> providers.forEach(provider -> {
                    if (provider instanceof org.springframework.security.oauth2.server.authorization.authentication.JwtClientAssertionAuthenticationProvider assertion) {
                        assertion.setJwtDecoderFactory(assertionDecoders);
                    }
                })))
            .tokenIntrospectionEndpoint(introspection -> introspection.authenticationProviders(providers -> {
                for (int index = 0; index < providers.size(); index++) {
                    if (providers.get(index) instanceof org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationProvider) {
                        // 调用方已在clientAuthentication中使用有效协议完成认证。
                        // 原生内省此处按ID读取的是目标Client，仅用于恢复claims，不能对公共目标再做机密认证选择。
                        providers.set(index, new dev.macula.cloud.iam.protocol.oauth2.ResourceTokenIntrospectionAuthenticationProvider(
                            baseRegisteredClientRepository, authorizationService, clientService));
                    }
                }
            }))
            .authorizationEndpoint(authorizationEndpoint ->
                authorizationEndpoint
                    .consentPage(CUSTOM_CONSENT_PAGE_URI)
                    .authenticationProviders(providers -> providers.forEach(provider -> {
                        if (provider instanceof org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider codeProvider) {
                            codeProvider.setAuthenticationValidator(new dev.macula.cloud.iam.protocol.oauth2.OAuth2ClientProtocolValidator());
                        }
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
                    .accessTokenRequestConverter(new OAuth2ResourceOwnerSmsAuthenticationConverter())
                    .accessTokenRequestConverter(new OAuth2ResourceOwnerEmailAuthenticationConverter())
                    .accessTokenRequestConverter(new OAuth2ExternalCodeAuthenticationConverter())
                    .errorResponseHandler(new OAuth2TokenEndpointFailureHandler(authorizationServerSettings.getIssuer()))
                    .authenticationProviders(providers -> {
                        // The shared manager exists at request time, after HttpSecurity has been built.
                        AuthenticationManager userAuthentication = authentication ->
                            http.getSharedObject(AuthenticationManager.class).authenticate(authentication);
                        var smsProvider = new OAuth2ResourceOwnerSmsAuthenticationProvider(
                            userAuthentication, authorizationService, tokenGenerator);
                        smsProvider.setMfaCompletion(mfaCompletion);
                        providers.add(smsProvider);
                        var emailProvider = new OAuth2ResourceOwnerEmailAuthenticationProvider(
                            userAuthentication, authorizationService, tokenGenerator);
                        emailProvider.setMfaCompletion(mfaCompletion);
                        providers.add(emailProvider);
                        providers.add(new OAuth2ExternalCodeAuthenticationProvider(externalCodeVerifiers,
                            externalCodeReplayStore, externalCodeAccountResolver,
                            new OAuth2UserGrantTokenIssuer(authorizationService, tokenGenerator, mfaCompletion), identitySources, externalCodeLimiter));
                        for (int index = 0; index < providers.size(); index++) {
                            providers.set(index, new dev.macula.cloud.iam.protocol.oauth2.ProtocolBoundAuthenticationProvider(
                                providers.get(index), registeredClientRepository));
                        }
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
                authorizationServerSettings.getTokenEndpoint()),
            // 元数据预检也必须经过同一受控 CORS，不能落入托管登录链产生 302。
            PathPatternRequestMatcher.pathPattern(HttpMethod.OPTIONS, ProtocolLoginCorsConfigurationSource.DISCOVERY_PATH),
            PathPatternRequestMatcher.pathPattern(HttpMethod.OPTIONS, ProtocolLoginCorsConfigurationSource.JWKS_PATH),
            PathPatternRequestMatcher.pathPattern(HttpMethod.OPTIONS, ProtocolLoginCorsConfigurationSource.OAUTH_METADATA_PATH),
            externalBindingMatcher, verificationMatcher,
            PathPatternRequestMatcher.pathPattern(dev.macula.cloud.iam.controller.ProfileController.PATH));

        http.addFilterAfter(new dev.macula.cloud.iam.authentication.inapp.InAppAuthorizationRequestFilter(
            inAppPolicy, authorizationServerSettings),
            org.springframework.security.web.context.SecurityContextHolderFilter.class);
        // 拦截 授权服务器相关的请求端点
        http
            .securityMatcher(protocolMatcher)
            .requestCache(cache -> cache.requestCache(browserRequestCache))
            .cors(cors -> cors.configurationSource(protocolCorsConfigurationSource(authorizationServerSettings,
                registeredClientRepository, clientService)))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(verificationMatcher).permitAll()
                .requestMatchers(dev.macula.cloud.iam.controller.ProfileController.PATH).permitAll()
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
            .addFilterBefore(new OAuth2TokenOriginFilter(authorizationServerSettings.getTokenEndpoint(), clientService),
                UsernamePasswordAuthenticationFilter.class)
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

    /**
     * 原生factory仅按Client ID缓存解码器，不能用于同ID不同协议/JWKS。
     * 此处以权威策略作为有界缓存键；签名、claims及audience仍完全由原生factory校验。
     */
    private org.springframework.security.oauth2.jwt.JwtDecoderFactory<org.springframework.security.oauth2.server.authorization.client.RegisteredClient> protocolClientAssertionDecoders() {
        var cache = new java.util.LinkedHashMap<ClientAssertionPolicy, org.springframework.security.oauth2.jwt.JwtDecoder>(16, 0.75f, true);
        return client -> {
            var settings = client.getClientSettings();
            ClientAssertionPolicy key = new ClientAssertionPolicy(client.getId(),
                settings.getSetting(dev.macula.cloud.iam.protocol.oauth2.ApplicationProtocolResolver.EFFECTIVE_PROTOCOL),
                settings.getJwkSetUrl(), settings.getTokenEndpointAuthenticationSigningAlgorithm().getName());
            synchronized (cache) {
                var decoder = cache.get(key);
                if (decoder == null) {
                    decoder = new org.springframework.security.oauth2.server.authorization.authentication.JwtClientAssertionDecoderFactory().createDecoder(client);
                    if (cache.size() >= MAX_ASSERTION_DECODERS) {
                        cache.remove(cache.keySet().iterator().next());
                    }
                    cache.put(key, decoder);
                }
                return decoder;
            }
        };
    }

    /**
     * 客户端断言验证器的策略身份，不包含原始断言或应用Secret。
     * @since 6.1.0
     */
    private record ClientAssertionPolicy(String clientId, String protocol, String jwksUrl, String algorithm) { }

    private CorsConfigurationSource protocolCorsConfigurationSource(
        AuthorizationServerSettings authorizationServerSettings,
        RegisteredClientRepository registeredClientRepository, SysOAuth2ClientService sysOAuth2ClientService) {
        return new ProtocolLoginCorsConfigurationSource(registeredClientRepository, sysOAuth2ClientService,
            authorizationServerSettings.getTokenEndpoint());
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
        @org.springframework.beans.factory.annotation.Qualifier("baseRegisteredClientRepository") RegisteredClientRepository registeredClientRepository,
        SysOAuth2ClientService clients) {
        return new MaculaOAuth2AuthorizationService(redisTemplate, registeredClientRepository, clients);
    }

    /** 授权恢复专用基础仓库，不依赖HTTP请求或授权服务。 */
    @Bean("baseRegisteredClientRepository")
    RegisteredClientRepository baseRegisteredClientRepository(SysOAuth2ClientService clients,
        SysOAuth2ClientExternalProviderMapper mappings) {
        return new MaculaRegisteredClientRepository(clients, mappings);
    }

    /** 原生provider使用无缓存的协议视图；授权服务通过Qualifier固定使用基础仓库。 */
    @Bean
    @org.springframework.context.annotation.Primary
    RegisteredClientRepository registeredClientRepository(
        @org.springframework.beans.factory.annotation.Qualifier("baseRegisteredClientRepository") RegisteredClientRepository base,
        SysOAuth2ClientService clients,
        org.springframework.beans.factory.ObjectProvider<OAuth2AuthorizationService> authorizations) {
        return new dev.macula.cloud.iam.protocol.oauth2.ProtocolAwareRegisteredClientRepository(base, clients, authorizations);
    }

    @Bean
    OAuth2TokenCustomizer<OAuth2TokenClaimsContext> oAuth2TokenCustomizer() {
        return new CustomOAuth2TokenCustomizer();
    }

    @Bean
    OAuth2TokenGenerator<OAuth2Token> tokenGenerator(JWKSource<SecurityContext> jwkSource,
        OAuth2TokenCustomizer<OAuth2TokenClaimsContext> accessTokenCustomizer, SysOAuth2ClientService clients,
        dev.macula.cloud.iam.authentication.mfa.MfaCompletionService mfaCompletion) {
        CustomeOAuth2AccessTokenGenerator accessTokenGenerator = new CustomeOAuth2AccessTokenGenerator();
        accessTokenGenerator.setAccessTokenCustomizer(accessTokenCustomizer);
        JwtGenerator jwtGenerator = new JwtGenerator(new NimbusJwtEncoder(jwkSource));
        jwtGenerator.setJwtCustomizer(new CustomOidcTokenCustomizer());
        var generator = new dev.macula.cloud.iam.protocol.oauth2.ProtocolCheckingTokenGenerator(
            new DelegatingOAuth2TokenGenerator(jwtGenerator, accessTokenGenerator, new OAuth2RefreshTokenGenerator()), clients);
        generator.setMfaCompletion(mfaCompletion);
        return generator;
    }
}
