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

import dev.macula.cloud.iam.authentication.challenge.LoginCaptchaService;
import dev.macula.cloud.iam.authentication.hosted.HostedLoginClientPolicyService;
import dev.macula.cloud.iam.authentication.dingtalk.DingtalkClient;
import dev.macula.cloud.iam.authentication.email.EmailService;
import dev.macula.cloud.iam.authentication.feishu.FeishuClient;
import dev.macula.cloud.iam.authentication.github.GithubClient;
import dev.macula.cloud.iam.authentication.sms.SmsService;
import dev.macula.cloud.iam.authentication.thirdparty.BrowserLoginClientPolicyService;
import dev.macula.cloud.iam.authentication.thirdparty.HttpSessionThirdPartyLoginStateRepository;
import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService;
import dev.macula.cloud.iam.authentication.thirdparty.PendingThirdPartyBindingRepository;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyBindingService;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyIdentityResolutionService;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyUserDetailsService;
import dev.macula.cloud.iam.authentication.wecom.WecomClient;
import dev.macula.cloud.iam.authentication.wechat.WechatClient;
import dev.macula.cloud.iam.controller.LoginController;
import dev.macula.cloud.iam.controller.ProtocolVerificationCodeController;
import dev.macula.cloud.iam.controller.SmsCodeController;
import dev.macula.cloud.iam.controller.ThirdPartyBindingController;
import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.handler.AuthenticationFailureReason;
import dev.macula.cloud.iam.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.iam.mapper.SysOAuth2ClientExternalProviderMapper;
import dev.macula.cloud.iam.mapper.SysUserIdentityBindingMapper;
import dev.macula.cloud.iam.message.ProtocolLoginCodeDispatcher;
import dev.macula.cloud.iam.pojo.dto.UserAuthInfo;
import dev.macula.cloud.iam.pojo.entity.SysIdentityProvider;
import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import dev.macula.cloud.iam.protocol.oauth2.endpoint.AuthorizationConsentController;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeAccountResolver;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeClientSettings;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeReplayStore;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeVerifierRegistry;
import dev.macula.cloud.iam.service.support.SysOAuth2ClientService;
import dev.macula.cloud.iam.service.support.SysUserService;
import dev.macula.cloud.iam.service.userdetails.ClientAwareAccountResolver;
import dev.macula.cloud.system.enums.ExternalCodeType;
import dev.macula.cloud.system.security.UserIdentifierNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * 不启动 Nacos/MySQL 的真实 SecurityFilterChain、Thymeleaf 与 OAuth/OIDC 协议回归。
 * @author rain
 * @since 6.1.0
 */
@SpringJUnitConfig(IamProtocolTest.App.class)
@WebAppConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("local")
@TestPropertySource(properties = {
    "macula.cloud.iam.issuer-uri=https://iam.example",
    "macula.cloud.iam.sms-verification.digest-key=fixture-digest-key",
    "macula.cloud.iam.email-verification.digest-key=fixture-digest-key",
    "macula.cloud.iam.login-captcha.digest-key=fixture-digest-key"
})
public class IamProtocolTest {
    @Autowired WebApplicationContext context;
    @Autowired JwtDecoder decoder;
    MockMvc mvc;
    ObjectMapper json = tools.jackson.databind.json.JsonMapper.builder().build();
    final String verifier = "01234567890123456789012345678901234567890123456789";

    @Test void tokenCorsAllowsOnlyClientOriginAndEndpoint() throws Exception {
        SysOAuth2ClientService clientService = context.getBean(SysOAuth2ClientService.class);
        when(clientService.allowedOrigins()).thenReturn(Set.of("https://client.example"));

        mvc.perform(options("/oauth2/token")
                .header(HttpHeaders.ORIGIN, "https://client.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                    HttpHeaders.AUTHORIZATION + ", " + HttpHeaders.CONTENT_TYPE + ", tenantId"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://client.example"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                org.hamcrest.Matchers.containsString(HttpMethod.POST.name())))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                org.hamcrest.Matchers.containsStringIgnoringCase(HttpHeaders.AUTHORIZATION)))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));

        mvc.perform(post("/oauth2/token")
                .header(HttpHeaders.ORIGIN, "https://client.example")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://client.example"))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));

        mvc.perform(options("/oauth2/token")
                .header(HttpHeaders.ORIGIN, "https://attacker.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name()))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

        mvc.perform(options("/login")
                .header(HttpHeaders.ORIGIN, "https://admin.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name()))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test void tokenOriginIsBoundToAuthenticatedClientBeforeGrantHandling() throws Exception {
        SysOAuth2ClientService clientService = context.getBean(SysOAuth2ClientService.class);
        when(clientService.allowedOrigins()).thenReturn(Set.of("https://client.example"));

        for (String grant : List.of("password", "sms", "email",
            "urn:macula:params:oauth:grant-type:external_code", "authorization_code", "refresh_token",
            "client_credentials")) {
            mvc.perform(post("/oauth2/token")
                    .with(httpBasic("jwt", "fixture-secret"))
                    .header(HttpHeaders.ORIGIN, "https://client.example")
                    .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                    .param("grant_type", grant))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://client.example"));
        }
    }

    @Test void tokenOriginOwnedByAuthenticatedClientReachesGrantProvider() throws Exception {
        SysOAuth2ClientService clientService = context.getBean(SysOAuth2ClientService.class);
        when(clientService.allowedOrigins()).thenReturn(Set.of("https://jwt.example"));
        when(clientService.isAllowedOrigin("jwt", "https://jwt.example")).thenReturn(true);

        mvc.perform(exchange("jwt", authorize("jwt", login(), "api.read"), verifier)
                .header(HttpHeaders.ORIGIN, "https://jwt.example"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").isNotEmpty())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://jwt.example"));
    }

    @Test void publicTokenGrantsAuthenticateClientWithoutRefreshAndKeepOwnership() throws Exception {
        String authorizationCode = authorize("public", login(), "api.read");
        mvc.perform(exchange("public-other", authorizationCode, verifier)).andExpect(status().isBadRequest());
        authorizationCode = authorize("public", login(), "api.read");
        mvc.perform(exchange("public", authorizationCode, verifier).header(HttpHeaders.ORIGIN, "https://client.example"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.refresh_token").doesNotExist())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://client.example"));

        for (MockHttpServletRequestBuilder request : List.of(
            post("/oauth2/token").param("grant_type", "sms").param("client_id", "public")
                .param("phone", "13800000000").param("code", "fixture-code").param("scope", "api.read"),
            post("/oauth2/token").param("grant_type", "email").param("client_id", "public")
                .param("email", "fixture@example.test").param("code", "fixture-code").param("scope", "api.read"),
            post("/oauth2/token").param("grant_type", ExternalCodeType.GRANT_TYPE).param("client_id", "public")
                .param("code", "fixture-code")
                .param("code_type", ExternalCodeType.WECHAT_MINIAPP_LOGIN_CODE.name())
                .param("scope", "api.read"))) {
            MvcResult result = mvc.perform(request.header(HttpHeaders.ORIGIN, "https://client.example")
                    .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isBadRequest()).andReturn();
            assertThat(result.getResponse().getContentAsString()).doesNotContain("invalid_client");
        }

        mvc.perform(post("/oauth2/token")
                .header(HttpHeaders.ORIGIN, "https://client.example")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials").param("client_id", "public"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.access_token").doesNotExist());
    }

    @Test void protocolCaptchaPreflightUsesOriginAndPostBindsClientHeaderToBody() throws Exception {
        selectClientProtocol("public", "OAUTH2_1");
        selectClientProtocol("compat", "OAUTH2_1");
        SysOAuth2ClientService clientService = context.getBean(SysOAuth2ClientService.class);
        LoginCaptchaService captchaService = context.getBean(LoginCaptchaService.class);
        when(clientService.allowedOrigins()).thenReturn(Set.of("https://client.example"));

        mvc.perform(options(ProtocolVerificationCodeController.CAPTCHA_PATH)
                .header(HttpHeaders.ORIGIN, "https://client.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                    HttpHeaders.CONTENT_TYPE + ", " + ProtocolVerificationCodeController.CLIENT_ID_HEADER))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://client.example"));

        when(captchaService.issue("public", "https://client.example", "127.0.0.1"))
            .thenReturn(new LoginCaptchaService.CaptchaChallenge("challenge-id", "1 + 1 = ?"));
        mvc.perform(post(ProtocolVerificationCodeController.CAPTCHA_PATH)
                .header(HttpHeaders.ORIGIN, "https://client.example")
                .header(ProtocolVerificationCodeController.CLIENT_ID_HEADER, "public")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"client_id\":\"public\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.challenge_id").value("challenge-id"));

        mvc.perform(post(ProtocolVerificationCodeController.CAPTCHA_PATH)
                .header(HttpHeaders.ORIGIN, "https://client.example")
                .header(ProtocolVerificationCodeController.CLIENT_ID_HEADER, "public")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"client_id\":\"other\"}"))
            .andExpect(status().isBadRequest());

        mvc.perform(post(ProtocolVerificationCodeController.CAPTCHA_PATH)
                .header(HttpHeaders.ORIGIN, "https://client.example")
                .header(ProtocolVerificationCodeController.CLIENT_ID_HEADER, "jwt")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"client_id\":\"jwt\"}"))
            .andExpect(status().isBadRequest());
    }

    /**
     * 协议集成测试使用的最小 Spring 应用。
     *
     * @since 6.1.0
     */
    @Configuration
    @EnableWebMvc
    @EnableConfigurationProperties
    @Import({ProtocolOAuth2Configuration.class, DefaultSecurityConfiguration.class, JwtConfiguration.class,
        PasswordEncoderConfig.class, LoginController.class, SmsCodeController.class,
        ProtocolVerificationCodeController.class,
        ThirdPartyBindingController.class, dev.macula.cloud.iam.controller.ProfileController.class,
        dev.macula.cloud.iam.service.profile.ProfileService.class,
        AuthorizationConsentController.class, GithubClient.class, WecomClient.class, FeishuClient.class,
        DingtalkClient.class, WechatClient.class,
        IdentityProviderConfigService.class, HttpSessionThirdPartyLoginStateRepository.class,
        BrowserLoginClientPolicyService.class, HostedLoginClientPolicyService.class,
        dev.macula.cloud.iam.authentication.inapp.InAppLoginPolicyService.class,
        ThirdPartyUserDetailsService.class, ThirdPartyIdentityResolutionService.class,
        PendingThirdPartyBindingRepository.class})
    public static class App implements WebMvcConfigurer {
        @Bean InMemoryRegisteredClientRepository fixtureDefinitions() {
            return new InMemoryRegisteredClientRepository(client("public", ClientAuthenticationMethod.NONE,
                OAuth2TokenFormat.REFERENCE), client("public-other", ClientAuthenticationMethod.NONE,
                OAuth2TokenFormat.REFERENCE), client("jwt", ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                OAuth2TokenFormat.SELF_CONTAINED), client("compat", ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                OAuth2TokenFormat.REFERENCE), client("reuse", ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                OAuth2TokenFormat.REFERENCE));
        }
        static RegisteredClient client(String id, ClientAuthenticationMethod method, OAuth2TokenFormat format) {
            RegisteredClient.Builder builder = RegisteredClient.withId(id).clientId(id)
                .clientAuthenticationMethod(method).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(new AuthorizationGrantType("sms"))
                .redirectUri("https://client.example/callback").scope("openid").scope("profile").scope("api.read");
            if (ClientAuthenticationMethod.NONE.equals(method)) {
                builder.authorizationGrantType(new AuthorizationGrantType("email"))
                    .authorizationGrantType(new AuthorizationGrantType(ExternalCodeType.GRANT_TYPE));
            } else {
                builder.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
                builder.clientSecret("{bcrypt}" + new BCryptPasswordEncoder().encode("fixture-secret"));
            }
            return builder.clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(true)
                    .setting(dev.macula.cloud.iam.protocol.oauth2.OAuth2ClientProtocolValidator.ENABLED_PROTOCOLS, Set.of("OIDC","OAUTH2_1"))
                    .setting(ExternalCodeClientSettings.ALLOWED_ORIGINS,
                        Set.of("jwt".equals(id) ? "https://jwt.example" : "https://client.example"))
                    .setting(ExternalCodeClientSettings.CODE_TYPES,
                        ClientAuthenticationMethod.NONE.equals(method)
                            ? Set.of(ExternalCodeType.WECHAT_MINIAPP_LOGIN_CODE.name()) : Set.of())
                    .setting(ExternalCodeClientSettings.PROVIDER_BINDINGS,
                        ClientAuthenticationMethod.NONE.equals(method)
                            ? java.util.Map.of(ExternalCodeType.WECHAT_MINIAPP_LOGIN_CODE.name(), 7L)
                            : java.util.Map.of())
                    .setting(ExternalCodeClientSettings.USER_TYPE, "employee")
                    .setting(ExternalCodeClientSettings.TENANT_ID, 1L).build())
                .tokenSettings(TokenSettings.builder().accessTokenFormat(format)
                    .reuseRefreshTokens("reuse".equals(id)).build()).build();
        }
        @Bean
        @Primary
        OAuth2AuthorizationConsentService fixtureConsents() {
            return new InMemoryOAuth2AuthorizationConsentService();
        }
        @Bean(destroyMethod = "close") RedisFixture redisFixture() throws Exception { return new RedisFixture(); }
        @Bean RedisTemplate<String, Object> redisTemplate(RedisFixture fixture) {
            RedisTemplate<String, Object> template = new RedisTemplate<>();
            template.setConnectionFactory(fixture.connection);
            template.setKeySerializer(new org.springframework.data.redis.serializer.StringRedisSerializer());
            template.afterPropertiesSet();
            return template;
        }
        @Bean dev.macula.cloud.iam.authentication.thirdparty.ExternalBindingTicketStore bindingTickets() {
            return mock(dev.macula.cloud.iam.authentication.thirdparty.ExternalBindingTicketStore.class);
        }
        @Bean dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceBindingPolicyService bindingPolicy() {
            return mock(dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceBindingPolicyService.class);
        }
        @Bean SysOAuth2ClientService clientService() { return mock(SysOAuth2ClientService.class); }
        @Bean ClientAwareAccountResolver accountResolver() { return mock(ClientAwareAccountResolver.class); }
        @Bean LoginCaptchaService captchaService() { return mock(LoginCaptchaService.class); }
        @Bean ProtocolLoginCodeDispatcher protocolLoginCodeDispatcher() {
            return mock(ProtocolLoginCodeDispatcher.class);
        }
        @Bean UserIdentifierNormalizer identifierNormalizer() { return new UserIdentifierNormalizer(); }
        @Bean ExternalCodeVerifierRegistry externalCodeVerifiers() {
            return mock(ExternalCodeVerifierRegistry.class);
        }
        @Bean dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeRateLimiter externalCodeRateLimiter() {
            return mock(dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeRateLimiter.class);
        }
        @Bean ExternalCodeReplayStore externalCodeReplayStore() { return mock(ExternalCodeReplayStore.class); }
        @Bean ExternalCodeAccountResolver externalCodeAccountResolver() {
            return mock(ExternalCodeAccountResolver.class);
        }
        @Bean SysIdentityProviderMapper identityProviderMapper() { return mock(SysIdentityProviderMapper.class); }
        @Bean SysOAuth2ClientExternalProviderMapper externalProviderMapper() {
            return mock(SysOAuth2ClientExternalProviderMapper.class);
        }
        @Bean SysUserIdentityBindingMapper identityBindingMapper() { return mock(SysUserIdentityBindingMapper.class); }
        @Bean @Primary SmsService fixtureSmsService() { return mock(SmsService.class); }
        @Bean EmailService fixtureEmailService() { return mock(EmailService.class); }
        @Bean ThirdPartyBindingService fixtureBindingService() { return mock(ThirdPartyBindingService.class); }
        @Bean SysUserService userService() {
            SysUserService service = mock(SysUserService.class);
            UserAuthInfo user = new UserAuthInfo();
            user.setUserId(42L);
            user.setUsername("fixture-user");
            user.setNickname("测试用户");
            user.setStatus(1);
            user.setDataScope(1);
            user.setRoles(Set.of("USER"));
            user.setPassword("{bcrypt}" + new BCryptPasswordEncoder().encode("fixture-password"));
            when(service.getUserAuthInfo(any(), eq("fixture-user"), isNull())).thenReturn(user);
            // Existing phone lookup uses an empty username placeholder; simulate its identity source only.
            when(service.getUserAuthInfo(any(), eq(""), isNull())).thenReturn(user);
            return service;
        }
        @Bean SpringResourceTemplateResolver templates() {
            SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
            resolver.setPrefix("classpath:/templates/");
            resolver.setSuffix(".html");
            resolver.setTemplateMode("HTML");
            resolver.setCharacterEncoding("UTF-8");
            return resolver;
        }
        @Bean SpringTemplateEngine engine(SpringResourceTemplateResolver resolver) {
            SpringTemplateEngine engine = new SpringTemplateEngine();
            engine.setTemplateResolver(resolver);
            return engine;
        }
        @Bean ThymeleafViewResolver views(SpringTemplateEngine engine) {
            ThymeleafViewResolver view = new ThymeleafViewResolver();
            view.setTemplateEngine(engine);
            view.setCharacterEncoding("UTF-8");
            return view;
        }
        @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
            registry.addResourceHandler("/iam/**").addResourceLocations("classpath:/static/iam/");
        }
    }

    /**
     * 为协议测试启动并关闭隔离 Redis 进程。
     *
     * @since 6.1.0
     */
    static class RedisFixture implements AutoCloseable {
        Process process;
        org.redisson.api.RedissonClient redisson;
        org.redisson.spring.data.connection.RedissonConnectionFactory connection;
        RedisFixture() throws Exception {
            int port;
            try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) { port = socket.getLocalPort(); }
            String binary = System.getenv().getOrDefault("TEST_REDIS_SERVER", "/opt/homebrew/bin/redis-server");
            process = new ProcessBuilder(binary, "--bind", "127.0.0.1", "--port", String.valueOf(port),
                "--save", "", "--appendonly", "no").redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try {
                org.redisson.config.Config config = new org.redisson.config.Config();
                config.useSingleServer().setAddress("redis://127.0.0.1:" + port)
                    .setConnectionMinimumIdleSize(1).setConnectionPoolSize(2);
                redisson = org.redisson.Redisson.create(config);
                connection = new org.redisson.spring.data.connection.RedissonConnectionFactory(redisson);
                connection.afterPropertiesSet();
            } catch (Exception ex) {
                close();
                throw ex;
            }
        }
        @Override
        public void close() throws Exception {
            try {
                if (connection != null) {
                    connection.destroy();
                }
                if (redisson != null) {
                    redisson.shutdown();
                }
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
        }
    }

    /** 测试明确注册不同身份域，禁止全局按厂商唯一查询。 */
    void configureDefaultSources() {
        SysIdentityProviderMapper providers = context.getBean(SysIdentityProviderMapper.class);
        String[] vendors = {"GITHUB","WECOM","FEISHU","DINGTALK","WECHAT"};
        String[] types = {"GITHUB_OAUTH","WECOM_WEB","FEISHU_WEB","DINGTALK_WEB","WECHAT_WEBSITE"};
        for (int i=0;i<vendors.length;i++) {
            SysIdentityProvider source = new SysIdentityProvider();
        source.setVersion(0L);
            source.setId((long)i+1);
            source.setProviderType(vendors[i]);
            source.setSourceType(types[i]);
            source.setAppId("app");
            source.setAppSecret("test-secret");
            source.setEnabled(true);
            source.setVersion(0L);
            if ("FEISHU_WEB".equals(source.getSourceType())) { source.setIdentityFlow("FEISHU_OAUTH_V3"); }
            source.setVisible(true);
            source.setConfigJson(switch(vendors[i]) {
                case "GITHUB" -> "{\"clientId\":\"app\"}";
                case "WECOM" -> "{\"corpId\":\"app\",\"agentId\":\"agent\"}";
                case "DINGTALK" -> "{\"appKey\":\"app\",\"corpId\":\"corp\"}";
                default -> "{\"appId\":\"app\"}";
            });
            when(providers.selectById((long)i+1)).thenReturn(source);
        }
    }

    /** 公众号独立registration，测试显式更新Client关联后才能发起端内网页。 */
    void useOfficialAccountSource() {
        SysIdentityProvider source = new SysIdentityProvider();
        source.setVersion(0L);
        source.setId(6L);
        source.setProviderType("WECHAT");
        source.setSourceType("WECHAT_OFFICIAL_ACCOUNT");
        source.setAppId("wechat-oa");
        source.setAppSecret("oa-secret");
        source.setConfigJson("{\"appId\":\"wechat-oa\"}");
        source.setEnabled(true);
            source.setVersion(0L);
            if ("FEISHU_WEB".equals(source.getSourceType())) { source.setIdentityFlow("FEISHU_OAUTH_V3"); }
        source.setVisible(true);
        when(context.getBean(SysIdentityProviderMapper.class).selectById(6L)).thenReturn(source);
        SysOAuth2Client client = context.getBean(SysOAuth2ClientService.class).getClientByClientId("public");
        java.util.Map<String,Long> fixed = new java.util.LinkedHashMap<>(json.readValue(client.getBrowserProviderBindings(),
            new tools.jackson.core.type.TypeReference<java.util.Map<String,Long>>() { }));
        fixed.put("WECHAT",6L);
        client.setBrowserProviderBindings(json.writeValueAsString(fixed));
    }

    @BeforeEach
    void setup() {
        SysOAuth2ClientService currentClients = context.getBean(SysOAuth2ClientService.class);
        org.mockito.Mockito.reset(currentClients);
        when(currentClients.getClientByClientId(org.mockito.ArgumentMatchers.anyString())).thenAnswer(call -> {
            String id = call.getArgument(0);
            RegisteredClient registered = context.getBean("fixtureDefinitions", InMemoryRegisteredClientRepository.class).findByClientId(id);
            if (registered == null) { return null; }
            SysOAuth2Client row = new SysOAuth2Client();
            row.setClientId(id);
            row.setClientName(id);
            row.setClientSecret(registered.getClientSecret());
            row.setClientAuthenticationMethods(registered.getClientAuthenticationMethods().stream().map(ClientAuthenticationMethod::getValue).collect(java.util.stream.Collectors.joining(",")));
            row.setRedirectUris(String.join(",", registered.getRedirectUris()));
            row.setRequireProofKey(true);
            row.setRequireAuthorizationConsent(true);
            row.setTokenFormat(registered.getTokenSettings().getAccessTokenFormat().getValue());
            row.setReuseRefreshTokens(registered.getTokenSettings().isReuseRefreshTokens());
            row.setAllowedOrigins("jwt".equals(id) ? "https://jwt.example" : "https://client.example");
            row.setExternalCodeTypes("WECHAT_MINIAPP_LOGIN_CODE");
            row.setEnabledProtocols("OIDC,OAUTH2_1");
        dev.macula.cloud.iam.protocol.oauth2.ProtocolTestFixtures.configure(row);
            row.setDefaultProtocol("OIDC");
            row.setConfigurationStatus("ENABLED");
            row.setAuthorizationGrantTypes(registered.getAuthorizationGrantTypes().stream().map(AuthorizationGrantType::getValue).collect(java.util.stream.Collectors.joining(",")));
            row.setScopes(String.join(",", registered.getScopes()));
            row.setTenantId(1L);
            row.setUserType(UserTypeEnum.EMPLOYEE);
            return dev.macula.cloud.iam.protocol.oauth2.ProtocolTestFixtures.configure(row);
        });
        configureDefaultSources();
        SysOAuth2Client browserClient = new SysOAuth2Client();
        browserClient.setClientId("public");
        browserClient.setAuthorizationGrantTypes("authorization_code,sms,email,urn:macula:params:oauth:grant-type:external_code");
        browserClient.setScopes("openid,profile,api.read");
        browserClient.setRedirectUris("https://client.example/callback");
        browserClient.setBrowserLoginProviderTypes("DINGTALK,FEISHU,GITHUB,WECOM,WECHAT");
        browserClient.setProtocolMode("OIDC");
        browserClient.setEnabledProtocols("OIDC,OAUTH2_1");
        browserClient.setDefaultProtocol("OIDC");
        browserClient.setConfigurationStatus("ENABLED");
        browserClient.setBrowserProviderBindings("{\"GITHUB\":1,\"WECOM\":2,\"FEISHU\":3,\"DINGTALK\":4,\"WECHAT\":5}");
        browserClient.setTenantId(1L);
        browserClient.setUserType(UserTypeEnum.EMPLOYEE);
        browserClient.setClientAuthenticationMethods("none");
        browserClient.setRequireAuthorizationConsent(true);
        browserClient.setAllowedOrigins("https://client.example");
        browserClient.setExternalCodeTypes("WECHAT_MINIAPP_LOGIN_CODE");
        dev.macula.cloud.iam.protocol.oauth2.ProtocolTestFixtures.configure(browserClient);
        when(context.getBean(SysOAuth2ClientService.class).getClientByClientId("public"))
            .thenReturn(browserClient);
        SysUser resolvedUser = new SysUser();
        resolvedUser.setUsername("fixture-user");
        when(context.getBean(ClientAwareAccountResolver.class)
            .resolveIdentifier(UserTypeEnum.EMPLOYEE, "fixture-user")).thenReturn(resolvedUser);
        SysOAuth2ClientService clientService = context.getBean(SysOAuth2ClientService.class);
        when(clientService.allowedOrigins()).thenReturn(Set.of("https://client.example", "https://jwt.example"));
        when(clientService.isAllowedOrigin("public", "https://client.example")).thenReturn(true);
        when(clientService.isAllowedOrigin("public-other", "https://client.example")).thenReturn(true);
        when(clientService.isAllowedOrigin("jwt", "https://jwt.example")).thenReturn(true);
        mvc = webAppContextSetup(context).apply(springSecurity()).alwaysDo(result -> {
            if (result.getResponse().getStatus() >= 400) {
                System.out.println("Rejected " + result.getRequest().getRequestURI() + ": "
                    + result.getResponse().getStatus() + " " + result.getResponse().getContentAsString());
            }
        }).build();
    }

    MockHttpSession browserAuthorizationSession() {
        return browserAuthorizationSession("");
    }

    MockHttpSession browserAuthorizationSession(String contextPath) {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
            contextPath + "/oauth2/authorize");
        request.setContextPath(contextPath);
        request.setSession(session);
        request.setScheme("https");
        request.setServerName("iam.example");
        request.setServerPort(443);
        request.setQueryString("response_type=code&client_id=public");
        request.addParameter(OAuth2ParameterNames.RESPONSE_TYPE, "code");
        request.addParameter(OAuth2ParameterNames.CLIENT_ID, "public");
        request.addParameter(OAuth2ParameterNames.SCOPE, "openid profile");
        request.addParameter(OAuth2ParameterNames.REDIRECT_URI, "https://client.example/callback");
        new org.springframework.security.web.savedrequest.HttpSessionRequestCache()
            .saveRequest(request, new org.springframework.mock.web.MockHttpServletResponse());
        return session;
    }

    @Test void loginPagesAndSessionWorkWithCsrf() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(
            org.hamcrest.Matchers.containsString("autocomplete=\"current-password\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("sms-login")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "data-send-code-url=\"api/v1/sms-code\"")));
        mvc.perform(get("/login").queryParam(OAuth2ParameterNames.ERROR,
                AuthenticationFailureReason.AUTHENTICATION_REJECTED.code()))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                AuthenticationFailureReason.AUTHENTICATION_REJECTED.message())));
        mvc.perform(get("/login").queryParam(OAuth2ParameterNames.ERROR, "forged-error"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                AuthenticationFailureReason.AUTHENTICATION_UNAVAILABLE.message())))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("forged-error"))));
        mvc.perform(get("/iam/auth.css")).andExpect(status().isOk());
        mvc.perform(post("/login").param("username", "fixture-user").param("password", "fixture-password"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/login").with(csrf()).param("username", "fixture-user").param("password", "wrong"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("https://iam.example/login?error=authentication_rejected"));
        MockHttpSession session = login();
        mvc.perform(get("/").session(session)).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("fixture-user")));
    }

    @Test void bindingPageRequiresPendingIdentityAndPostRequiresCsrf() throws Exception {
        mvc.perform(get("/login/bind")).andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?error=authentication_rejected"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        context.getBean(PendingThirdPartyBindingRepository.class).save(request,
            dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.bindingRequired("GITHUB", "123",
                dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.context("GITHUB", "", "test-client", Instant.now(), "/")));
        MockHttpSession session = (MockHttpSession) request.getSession();
        mvc.perform(get("/login/bind").session(session)).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "action=\"/login/bind/cancel\"")));
        mvc.perform(post("/login/bind").session(session).param("username", "fixture-user")
            .param("method", "password").param("password", "fixture-password"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/login/bind/sms-code").session(session).param("username", "fixture-user").param("phone", "13800000000"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/login/bind/cancel").session(session))
            .andExpect(status().isForbidden());
        mvc.perform(post("/login/bind/cancel").session(session).with(csrf()))
            .andExpect(status().isFound()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/login/bind").session(session)).andExpect(status().isFound())
            .andExpect(redirectedUrl("/login?error=authentication_rejected"));
    }

    @Test void thirdPartyButtonsNeedOnlyVisibleProvider() throws Exception {
        SysIdentityProviderMapper providers = context.getBean(SysIdentityProviderMapper.class);
        configureDefaultSources();

        mvc.perform(get("/login").session(browserAuthorizationSession()).with(anonymous()))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("使用 GitHub 登录")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("使用企业微信登录")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "href=\"/login/github\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "href=\"/login/wecom\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/login/feishu/browser\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/login/dingtalk/browser\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/login/wechat/browser\"")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("h5-js-sdk"))));

        mvc.perform(get("/iam/login").contextPath("/iam")
                .session(browserAuthorizationSession("/iam")).with(anonymous())
                .queryParam("client_id", "portal + 1"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "href=\"/iam/login/github\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "href=\"/iam/login/wecom\"")));

        when(providers.selectById(org.mockito.ArgumentMatchers.anyLong())).thenReturn(null);
        mvc.perform(get("/login").session(browserAuthorizationSession()).with(anonymous())
                .queryParam("client_id", "portal"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("使用 GitHub 登录"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("使用企业微信登录"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("使用飞书登录"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("使用钉钉登录"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("使用微信登录"))));

        configureDefaultSources();
        mvc.perform(get("/login").with(anonymous()).queryParam("client_id", "unknown"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("使用 GitHub 登录"))));
    }

    /** 手机页不展示桌面扫码，也不执行已配置的桌面直登；适用网页登录仍可用。 */
    @Test
    void mobileLoginFiltersDesktopProvidersAndRejectsExplicitStart() throws Exception {
        configureDefaultSources();
        mvc.perform(get("/login").session(browserAuthorizationSession()).with(anonymous())
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 iPhone Mobile Safari"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("使用 GitHub 登录")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("使用微信登录"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("使用企业微信登录"))))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("专用客户端")));
        mvc.perform(get("/login/wecom/browser").session(browserAuthorizationSession()).with(anonymous())
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 iPhone Mobile Safari"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("login.work.weixin.qq.com"))));
    }

    /** 标准Web在飞书容器仍展示IAM选择，错误后不循环拉起平台授权。 */
    @Test
    void standardWebDoesNotAutomaticallyRedirectInsideFeishu() throws Exception {
        configureDefaultSources();
        mvc.perform(get("/login").session(browserAuthorizationSession()).with(anonymous())
                .header(HttpHeaders.USER_AGENT, "Mozilla Mobile Lark/7.0"))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("使用飞书登录")));
        mvc.perform(get("/login").session(browserAuthorizationSession()).with(anonymous())
                .header(HttpHeaders.USER_AGENT, "Mozilla Mobile Lark/7.0")
                .queryParam("error", AuthenticationFailureReason.AUTHENTICATION_REJECTED.code()))
            .andExpect(status().isOk());
    }

    /** 小程序主动打开托管页时只提供内置登录，旧第三方直登引用不能生效。 */
    @Test
    void miniappHostedEntryDoesNotLoseLocalLoginInWechat() throws Exception {
        configureDefaultSources();
        useOfficialAccountSource();
        var row = context.getBean(SysOAuth2ClientService.class).getClientByClientId("public");
        row.setApplicationType("MINIAPP");
        row.setMiniappPlatform("WECHAT");
        row.setBrowserLoginProviderTypes("WECHAT");
        row.setBrowserProviderBindings("{\"WECHAT\":6}");
        row.setHostedLoginMethods(null);
        mvc.perform(get("/login").session(browserAuthorizationSession()).with(anonymous())
                .header(HttpHeaders.USER_AGENT, "MicroMessenger miniProgram"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"username\"")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("使用微信登录"))));
        var loginSession = browserAuthorizationSession();
        mvc.perform(post("/login").session(loginSession).with(csrf())
                .param("username", "fixture-user").param("password", "fixture-password"))
            .andExpect(status().isFound());
        String iamCode = authorize("public", loginSession, "openid profile");
        mvc.perform(exchange("public", iamCode, verifier)).andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").exists()).andExpect(jsonPath("$.id_token").exists());
        row.setDirectLoginProviderType("WECHAT");
        mvc.perform(get("/login").session(browserAuthorizationSession()).with(anonymous())
                .header(HttpHeaders.USER_AGENT, "MicroMessenger miniProgram"))
            .andExpect(status().isOk());
    }

    /** Web仅执行明确的通用网页直登，UA和错误参数不能开启端内自动分流。 */
    @Test void webLoginUsesOnlyExplicitDirectProviderAndStopsOnError() throws Exception {
        SysIdentityProviderMapper providers = context.getBean(SysIdentityProviderMapper.class);
        configureDefaultSources();
        SysIdentityProvider wecom = new SysIdentityProvider();
        wecom.setVersion(0L);
        wecom.setProviderType("WECOM");
        wecom.setId(2L);
        wecom.setSourceType("WECOM_WEB");
        wecom.setVisible(true);
        wecom.setId(2L);
        wecom.setSourceType("WECOM_WEB");
        wecom.setVisible(true);
        wecom.setAppId("corp");
        wecom.setAppSecret("secret");
        wecom.setConfigJson("{\"corpId\":\"corp\",\"agentId\":\"agent\"}");
        wecom.setEnabled(true);
        when(providers.selectById(2L)).thenReturn(wecom);

        mvc.perform(get("/login").session(browserAuthorizationSession()).with(anonymous())
                .header(HttpHeaders.USER_AGENT, "MicroMessenger wxwork"))
            .andExpect(status().isOk());

        SysIdentityProvider wechat = new SysIdentityProvider();
        wechat.setVersion(0L);
        wechat.setProviderType("WECHAT");
        wechat.setId(5L);
        wechat.setSourceType("WECHAT_WEBSITE");
        wechat.setVisible(true);
        wechat.setId(5L);
        wechat.setSourceType("WECHAT_WEBSITE");
        wechat.setVisible(true);
        wechat.setAppId("wechat-site");
        wechat.setAppSecret("site-secret");
        wechat.setConfigJson("{\"appId\":\"wechat-site\"}");
        wechat.setEnabled(true);
        when(providers.selectById(5L)).thenReturn(wechat);

        useOfficialAccountSource();
        mvc.perform(get("/login").session(browserAuthorizationSession()).with(anonymous())
                .header(HttpHeaders.USER_AGENT, "MicroMessenger"))
            .andExpect(status().isOk());
        mvc.perform(get("/login").session(browserAuthorizationSession()).with(anonymous())
                .header(HttpHeaders.USER_AGENT, "MicroMessenger")
                .queryParam(OAuth2ParameterNames.ERROR,
                    AuthenticationFailureReason.AUTHENTICATION_REJECTED.code()))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                AuthenticationFailureReason.AUTHENTICATION_REJECTED.message())));

        SysOAuth2Client directClient = new SysOAuth2Client();
        directClient.setClientId("public");
        directClient.setAuthorizationGrantTypes("authorization_code");
        directClient.setRedirectUris("https://client.example/callback");
        directClient.setBrowserLoginProviderTypes("GITHUB");
        directClient.setProtocolMode("OIDC");
        directClient.setEnabledProtocols("OIDC");
        dev.macula.cloud.iam.protocol.oauth2.ProtocolTestFixtures.configure(directClient);
        directClient.setDefaultProtocol("OIDC");
        directClient.setConfigurationStatus("ENABLED");
        directClient.setTenantId(1L);
        directClient.setUserType(UserTypeEnum.EMPLOYEE);
        directClient.setBrowserProviderBindings("{\"GITHUB\":1}");
        directClient.setDirectLoginProviderType("GITHUB");
        when(context.getBean(SysOAuth2ClientService.class).getClientByClientId("public"))
            .thenReturn(directClient);
        SysIdentityProvider github = new SysIdentityProvider();
        github.setVersion(0L);
        github.setProviderType("GITHUB");
        github.setId(1L);
        github.setSourceType("GITHUB_OAUTH");
        github.setVisible(true);
        github.setId(1L);
        github.setSourceType("GITHUB_OAUTH");
        github.setVisible(true);
        github.setId(1L);
        github.setSourceType("GITHUB_OAUTH");
        github.setVisible(true);
        github.setId(1L);
        github.setSourceType("GITHUB_OAUTH");
        github.setVisible(true);
        github.setAppId("github-client");
        github.setAppSecret("secret");
        github.setConfigJson("{\"clientId\":\"github-client\"}");
        github.setEnabled(true);
        when(providers.selectById(1L)).thenReturn(github);

        mvc.perform(get("/login").session(browserAuthorizationSession()).with(anonymous()))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("/login/github"));
    }

    @Test void proxiedLoginKeepsExternalIamPrefix() throws Exception {
        SysIdentityProviderMapper providers = context.getBean(SysIdentityProviderMapper.class);
        configureDefaultSources();
        MockMvc proxiedMvc = webAppContextSetup(context).addFilters(new ForwardedHeaderFilter())
            .apply(springSecurity()).build();

        proxiedMvc.perform(get("/login").session(browserAuthorizationSession("/iam")).with(anonymous())
                .header("Host", "localhost:8080")
                .header("X-Forwarded-Host", "127.0.0.1:5900")
                .header("X-Forwarded-Proto", "http")
                .header("X-Forwarded-Prefix", "/iam"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/iam/login/github\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/iam/login/wecom\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"/iam/login\"")));

        SysIdentityProvider github = new SysIdentityProvider();
        github.setVersion(0L);
        github.setProviderType("GITHUB");
        github.setId(1L);
        github.setSourceType("GITHUB_OAUTH");
        github.setVisible(true);
        github.setId(1L);
        github.setSourceType("GITHUB_OAUTH");
        github.setVisible(true);
        github.setId(1L);
        github.setSourceType("GITHUB_OAUTH");
        github.setVisible(true);
        github.setId(1L);
        github.setSourceType("GITHUB_OAUTH");
        github.setVisible(true);
        github.setAppId("github-client");
        github.setAppSecret("secret");
        github.setConfigJson("{\"clientId\":\"github-client\"}");
        github.setEnabled(true);
        when(providers.selectById(1L)).thenReturn(github);
        proxiedMvc.perform(get("/login/github").session(browserAuthorizationSession("/iam")).with(anonymous())
                .header("Host", "localhost:8080")
                .header("X-Forwarded-Host", "127.0.0.1:5900")
                .header("X-Forwarded-Proto", "http")
                .header("X-Forwarded-Prefix", "/iam"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(
                "redirect_uri=https://iam.example/login/github")));
    }

    @Test void thirdPartyLoginRejectsSavedRequestFromForwardedHost() throws Exception {
        SysIdentityProviderMapper providers = context.getBean(SysIdentityProviderMapper.class);
        SysIdentityProvider github = new SysIdentityProvider();
        github.setVersion(0L);
        github.setProviderType("GITHUB");
        github.setId(1L);
        github.setSourceType("GITHUB_OAUTH");
        github.setVisible(true);
        github.setId(1L);
        github.setSourceType("GITHUB_OAUTH");
        github.setVisible(true);
        github.setId(1L);
        github.setSourceType("GITHUB_OAUTH");
        github.setVisible(true);
        github.setId(1L);
        github.setSourceType("GITHUB_OAUTH");
        github.setVisible(true);
        github.setAppId("github-client");
        github.setAppSecret("secret");
        github.setConfigJson("{\"clientId\":\"github-client\"}");
        github.setEnabled(true);
        when(providers.selectById(1L)).thenReturn(github);
        MockMvc proxiedMvc = webAppContextSetup(context).addFilters(new ForwardedHeaderFilter())
            .apply(springSecurity()).build();

        MvcResult protectedRequest = proxiedMvc.perform(get("/private/profile").with(anonymous())
                .header("Host", "localhost:8080")
                .header("X-Forwarded-Host", "attacker.example")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Prefix", "/iam"))
            .andExpect(status().isFound()).andReturn();
        MockHttpSession session = (MockHttpSession) protectedRequest.getRequest().getSession(false);

        proxiedMvc.perform(get("/login/github").session(session).with(anonymous())
                .header("Host", "localhost:8080")
                .header("X-Forwarded-Host", "attacker.example")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Prefix", "/iam"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("attacker.example"))))
            .andExpect(redirectedUrl("https://iam.example/login?error=authentication_unavailable"));
    }

    /** 各入口固定可信issuer，端内模式只在对应IN_APP配置中开放。 */
    @Test void thirdPartyFiltersUseTrustedIssuerWithoutClientSnapshot() throws Exception {
        SysIdentityProviderMapper providers = context.getBean(SysIdentityProviderMapper.class);
        MockHttpSession browserSession = browserAuthorizationSession();

        SysIdentityProvider github = new SysIdentityProvider();
        github.setVersion(0L);
        github.setProviderType("GITHUB");
        github.setId(1L);
        github.setSourceType("GITHUB_OAUTH");
        github.setVisible(true);
        github.setId(1L);
        github.setSourceType("GITHUB_OAUTH");
        github.setVisible(true);
        github.setId(1L);
        github.setSourceType("GITHUB_OAUTH");
        github.setVisible(true);
        github.setId(1L);
        github.setSourceType("GITHUB_OAUTH");
        github.setVisible(true);
        github.setAppId("github-client");
        github.setAppSecret("secret");
        github.setConfigJson("{\"clientId\":\"github-client\"}");
        github.setEnabled(true);
        when(providers.selectById(1L)).thenReturn(github);

        MvcResult githubStart = mvc.perform(get("/login/github").session(browserSession).with(anonymous())
                .queryParam("client_id", "portal").header("Host", "attacker.example"))
            .andExpect(status().isFound()).andReturn();
        assertThat(githubStart.getResponse().getRedirectedUrl())
            .startsWith("https://github.com/login/oauth/authorize")
            .contains("redirect_uri=https://iam.example/login/github")
            .doesNotContain("attacker.example");

        SysIdentityProvider wecom = new SysIdentityProvider();
        wecom.setVersion(0L);
        wecom.setProviderType("WECOM");
        wecom.setId(2L);
        wecom.setSourceType("WECOM_WEB");
        wecom.setVisible(true);
        wecom.setId(2L);
        wecom.setSourceType("WECOM_WEB");
        wecom.setVisible(true);
        wecom.setAppId("corp");
        wecom.setAppSecret("secret");
        wecom.setConfigJson("{\"corpId\":\"corp\",\"agentId\":\"agent\"}");
        wecom.setEnabled(true);
        when(providers.selectById(2L)).thenReturn(wecom);

        MvcResult wecomStart = mvc.perform(get("/login/wecom").session(browserSession).with(anonymous())
                .header("User-Agent", "Mozilla/5.0 Chrome/120.0").header("Host", "attacker.example"))
            .andExpect(status().isFound()).andReturn();
        assertThat(wecomStart.getResponse().getRedirectedUrl())
            .startsWith("https://login.work.weixin.qq.com/wwlogin/sso/login")
            .contains("login_type=CorpApp")
            .contains("redirect_uri=https://iam.example/login/wecom")
            .doesNotContain("attacker.example");
        mvc.perform(get("/login/wecom/browser").session(browserSession).with(anonymous()))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(
                "https://login.work.weixin.qq.com/wwlogin/sso/login")));
        var containerClient = context.getBean(SysOAuth2ClientService.class).getClientByClientId("public");
        containerClient.setApplicationType("IN_APP");
        containerClient.setBrowserCapabilityBindings("{\"WECOM_WEBVIEW\":2}");
        mvc.perform(get("/login/wecom/webview").session(browserSession).with(anonymous())
                .header(HttpHeaders.USER_AGENT, "MicroMessenger wxwork"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("scope=snsapi_base")));
        containerClient.setApplicationType("WEB_SERVER");
        containerClient.setBrowserCapabilityBindings(null);
        mvc.perform(get("/login/wecom/browser").session(browserSession).with(anonymous())
                .queryParam("code", "forged").queryParam("state", "forged"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("https://iam.example/login?error=authentication_unavailable"));

        SysIdentityProvider feishu = new SysIdentityProvider();
        feishu.setVersion(0L);
        feishu.setProviderType("FEISHU");
        feishu.setId(3L);
        feishu.setSourceType("FEISHU_WEB");
        feishu.setIdentityFlow("FEISHU_OAUTH_V3");
        feishu.setVisible(true);
        feishu.setAppId("feishu-app");
        feishu.setAppSecret("secret");
        feishu.setConfigJson("{\"appId\":\"feishu-app\"}");
        feishu.setEnabled(true);
        when(providers.selectById(3L)).thenReturn(feishu);
        mvc.perform(get("/login/feishu/browser").session(browserSession).with(anonymous()))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.startsWith("https://accounts.feishu.cn/open-apis/authen/v1/authorize"),
                org.hamcrest.Matchers.containsString("redirect_uri=https://iam.example/login/feishu"))));
        SysIdentityProvider dingtalk = new SysIdentityProvider();
        dingtalk.setVersion(0L);
        dingtalk.setProviderType("DINGTALK");
        dingtalk.setId(4L);
        dingtalk.setSourceType("DINGTALK_WEB");
        dingtalk.setVisible(true);
        dingtalk.setAppId("ding-app");
        dingtalk.setAppSecret("secret");
        dingtalk.setConfigJson("{\"appKey\":\"ding-app\",\"corpId\":\"ding-corp\"}");
        dingtalk.setEnabled(true);
        when(providers.selectById(4L)).thenReturn(dingtalk);
        mvc.perform(get("/login/dingtalk/browser").session(browserSession).with(anonymous()))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.startsWith("https://login.dingtalk.com/oauth2/auth"),
                org.hamcrest.Matchers.containsString("redirect_uri=https://iam.example/login/dingtalk"))));
        SysIdentityProvider wechat = new SysIdentityProvider();
        wechat.setVersion(0L);
        wechat.setProviderType("WECHAT");
        wechat.setId(5L);
        wechat.setSourceType("WECHAT_WEBSITE");
        wechat.setVisible(true);
        wechat.setId(5L);
        wechat.setSourceType("WECHAT_WEBSITE");
        wechat.setVisible(true);
        wechat.setAppId("wechat-site");
        wechat.setAppSecret("site-secret");
        wechat.setConfigJson("{\"appId\":\"wechat-site\"}");
        wechat.setEnabled(true);
        when(providers.selectById(5L)).thenReturn(wechat);
        mvc.perform(get("/login/wechat/browser").session(browserSession).with(anonymous()))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("scope=snsapi_login")));
        useOfficialAccountSource();
        containerClient.setApplicationType("IN_APP");
        containerClient.setBrowserCapabilityBindings("{\"WECHAT_OFFICIAL_ACCOUNT\":6}");
        mvc.perform(get("/login/wechat/webview").session(browserSession).with(anonymous())
                .header(HttpHeaders.USER_AGENT, "MicroMessenger"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("scope=snsapi_base")));

        when(providers.selectById(org.mockito.ArgumentMatchers.anyLong())).thenReturn(null);
        mvc.perform(get("/login/github").session(browserSession).with(anonymous())
                .queryParam("client_id", "portal"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("https://iam.example/login?error=authentication_unavailable"));
    }

    @Test void smsCodeEndpointRequiresCsrfAndKeepsFailuresGeneric() throws Exception {
        SmsService smsService = context.getBean(SmsService.class);
        mvc.perform(post("/api/v1/sms-code").param("phone", "13800000000"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/sms-code").with(csrf()).header("Origin", "http://localhost:5900")
            .param("phone", "13800000000"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("submitted"));
        verify(smsService).sendCode("13800000000", "127.0.0.1");

        doThrow(new IllegalStateException("provider detail must not leak"))
            .when(smsService).sendCode("13900000000", "127.0.0.1");
        mvc.perform(post("/api/v1/sms-code").with(csrf()).param("phone", "13900000000"))
            .andExpect(status().isBadRequest())
            .andExpect(status().reason("验证码暂时无法发送"));
    }

    /** DevTools额外探测不能替换原授权请求；保持策略验证后正常认证。 */
    @Test
    void devtoolsProbePreservesAuthorizationLoginContext() throws Exception {
        var result = mvc.perform(get("/oauth2/authorize").queryParam("response_type", "code")
            .queryParam("client_id", "public").queryParam("redirect_uri", "https://client.example/callback")
            .queryParam("scope", "openid profile").queryParam("state", "fixture-state")
            .queryParam("code_challenge", challenge()).queryParam("code_challenge_method", "S256"))
            .andExpect(status().isFound()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        var cache = new org.springframework.security.web.savedrequest.HttpSessionRequestCache();
        String original = cache.getRequest(result.getRequest(), result.getResponse()).getRedirectUrl();
        var probe = mvc.perform(get("/.well-known/appspecific/com.chrome.devtools.json").session(session))
            .andExpect(status().isFound()).andReturn();
        assertThat(cache.getRequest(probe.getRequest(), probe.getResponse()).getRedirectUrl()).isEqualTo(original);
        mvc.perform(post("/login").session(session).with(csrf()).param("username", "fixture-user")
            .param("password", "fixture-password"))
            .andExpect(status().isFound()).andExpect(redirectedUrl(original));
    }

    MockHttpSession login() throws Exception {
        MvcResult result = mvc.perform(post("/login").with(csrf()).param("username", "fixture-user")
            .param("password", "fixture-password")).andExpect(status().isFound())
            .andExpect(redirectedUrl("/")).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    String challenge() throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    }

    @Test void discoveryAndJwksAdvertiseOidc() throws Exception {
        mvc.perform(get("/.well-known/openid-configuration")).andExpect(status().isOk())
            .andExpect(jsonPath("$.issuer").value("https://iam.example"))
            .andExpect(jsonPath("$.userinfo_endpoint").exists());
        mvc.perform(get("/oauth2/jwks")).andExpect(status().isOk()).andExpect(jsonPath("$.keys[0].kid").exists());
    }

    /** 浏览器读取 Discovery/JWKS 的真实安全链 CORS，不扩大凭据或方法范围。 */
    @Test
    void browserMetadataCorsUsesExactEnabledOrigins() throws Exception {
        for (String path : List.of("/.well-known/openid-configuration", "/.well-known/oauth-authorization-server", "/oauth2/jwks")) {
            mvc.perform(get(path).header("Origin", "https://client.example"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://client.example"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
            mvc.perform(get(path).header("Origin", "https://unregistered.example"))
                .andExpect(status().isForbidden());
            mvc.perform(options(path).header("Origin", "https://client.example")
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk());
            mvc.perform(options(path).header("Origin", "https://client.example")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
        }
    }

    /** 测试在独立场景中明确切换服务端配置，并推进授权版本。 */
    private void selectClientProtocol(String clientId, String protocol) {
        var clients = context.getBean(SysOAuth2ClientService.class);
        var row = clients.getClientByClientId(clientId);
        if (row.getProtocolConfigurations().size() != 1 || !protocol.equals(row.getProtocolConfigurations().get(0).protocol())) {
            row.setAuthorizationVersion(row.getAuthorizationVersion() + 1);
            row.setEnabledProtocols(protocol);
            row.setDefaultProtocol(protocol);
            dev.macula.cloud.iam.protocol.oauth2.ProtocolTestFixtures.configure(row);
        }
        when(clients.getClientByClientId(clientId)).thenReturn(row);
    }

    String authorize(String client, MockHttpSession session) throws Exception {
        return authorize(client, session, "openid profile");
    }

    String authorize(String client, MockHttpSession session, String scopes) throws Exception {
        // 每个场景显式更新服务端单协议配置，不能让请求自动启用另一协议。
        selectClientProtocol(client, scopes.contains("openid") ? "OIDC" : "OAUTH2_1");
        MvcResult result = mvc.perform(get("/oauth2/authorize").session(session).queryParam("response_type", "code")
            .queryParam("client_id", client).queryParam("redirect_uri", "https://client.example/callback")
            .queryParam("scope", scopes).queryParam("state", "fixture-state").queryParam("nonce", "fixture-nonce")
            .queryParam("code_challenge", challenge()).queryParam("code_challenge_method", "S256")).andReturn();
        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).isNotNull();
        if (location.contains("/oauth2/consent")) {
            var stored = context.getBean(OAuth2AuthorizationService.class).findByToken(query(location, "state"), new org.springframework.security.oauth2.server.authorization.OAuth2TokenType("state"));
            assertThat(stored).as("consent authorization restored from Redis").isNotNull();
            assertThat((Object) stored.getAttribute(dev.macula.cloud.iam.protocol.oauth2.ApplicationProtocolResolver.PROTOCOL_ATTRIBUTE)).isIn("OIDC", "OAUTH2_1");
            assertThat(dev.macula.cloud.iam.protocol.oauth2.ApplicationProtocolResolver.bound(stored, client)).isIn("OIDC", "OAUTH2_1");
            mvc.perform(get(URI.create(location)).session(session)).andExpect(consentResult -> {
                new dev.macula.cloud.iam.protocol.oauth2.ApplicationProtocolResolver(context.getBean(OAuth2AuthorizationService.class))
                    .resolve(context.getBean(SysOAuth2ClientService.class).getClientByClientId(client), consentResult.getRequest());
                assertThat(consentResult.getResponse().getStatus()).as("consent redirect %s, session security %s", consentResult.getResponse().getRedirectedUrl(), session.getAttribute("SPRING_SECURITY_CONTEXT") != null).isEqualTo(200);
            })
                .andExpect(content().string(org.hamcrest.Matchers.containsString("确认应用访问权限")));
            String state = query(location, "state");
            result = mvc.perform(post("/oauth2/authorize").session(session).param("client_id", client)
                .param("state", state).param("scope", Arrays.stream(scopes.split(" "))
                    .filter(scope -> !scope.equals("openid")).toArray(String[]::new))).andReturn();
            location = result.getResponse().getRedirectedUrl();
        }
        assertThat(location).contains("code=").doesNotContain("error=");
        return query(location, "code");
    }

    MockHttpServletRequestBuilder exchange(String client, String code, String proof) {
        var request = post("/oauth2/token").param("grant_type", "authorization_code").param("code", code)
            .param("redirect_uri", "https://client.example/callback").param("code_verifier", proof);
        return client.startsWith("public") ? request.param("client_id", client) :
            request.with(httpBasic(client, "fixture-secret"));
    }

    @Test void oidcCodeWithPkceSupportsOpaqueAndJwtUserInfo() throws Exception {
        for (String client : List.of("public", "jwt")) {
            String code = authorize(client, login());
            MvcResult response = mvc.perform(exchange(client, code, verifier)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id_token").exists()).andReturn();
            JsonNode tokens = json.readTree(response.getResponse().getContentAsString());
            var idToken = decoder.decode(tokens.get("id_token").asText());
            assertThat(idToken.getSubject()).isEqualTo("1:42");
            assertThat(idToken.getAudience()).contains(client);
            assertThat(idToken.getClaimAsString("nonce")).isEqualTo("fixture-nonce");
            String access = tokens.get("access_token").asText();
            mvc.perform(get("/userinfo").header("Authorization", "Bearer " + access)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").value("1:42")).andExpect(jsonPath("$.nickname").value("测试用户"));
            mvc.perform(get("/userinfo").header("Authorization", "Bearer " + tokens.get("id_token").asText()))
                .andExpect(status().isUnauthorized());
            mvc.perform(exchange(client, code, verifier)).andExpect(status().isBadRequest());
        }
    }

    @Test void pkceRejectsMissingChallengeAndWrongVerifier() throws Exception {
        mvc.perform(get("/oauth2/authorize").session(login()).queryParam("response_type", "code")
            .queryParam("client_id", "public").queryParam("redirect_uri", "https://client.example/callback")
            .queryParam("scope", "openid")).andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("error=invalid_request")));
        String code = authorize("public", login());
        mvc.perform(exchange("public", code, "wrong-proof")).andExpect(status().isBadRequest());
    }

    @Test void pkceRejectsMissingVerifierAndMismatchedRedirect() throws Exception {
        for (String client : List.of("public", "jwt")) {
            String code = authorize(client, login());
            var missing = exchange(client, code, verifier);
            missing.with(request -> {
                request.removeParameter("code_verifier");
                return request;
            });
            if (client.equals("public")) {
                mvc.perform(missing).andExpect(status().is3xxRedirection())
                    .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/login")))
                    .andExpect(content().string(""));
            } else {
                mvc.perform(missing).andExpect(status().isBadRequest());
            }
            code = authorize(client, login());
            var wrongRedirect = exchange(client, code, verifier);
            wrongRedirect.with(request -> {
                request.setParameter("redirect_uri", "https://other.example/callback");
                return request;
            });
            mvc.perform(wrongRedirect).andExpect(status().isBadRequest());
        }
    }

    @Test void codeFlowSeparatesOidcAndProfileScopesWithoutInventingEmail() throws Exception {
        for (String scopes : List.of("api.read", "openid", "openid profile")) {
            String code = authorize("jwt", login(), scopes);
            var response = mvc.perform(exchange("jwt", code, verifier)).andExpect(status().isOk()).andReturn();
            JsonNode tokens = json.readTree(response.getResponse().getContentAsString());
            assertThat(decoder.decode(tokens.get("access_token").asText()).getClaims()).doesNotContainKeys("nickname", "preferred_username");
            var info = get("/userinfo").header("Authorization", "Bearer " + tokens.get("access_token").asText());
            if (!scopes.contains("openid")) {
                assertThat(tokens.has("id_token")).isFalse();
                mvc.perform(info).andExpect(status().isForbidden());
            } else {
                var claims = decoder.decode(tokens.get("id_token").asText()).getClaims();
                assertThat(claims).doesNotContainKeys("email", "email_verified");
                var userInfo = mvc.perform(info).andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").doesNotExist())
                    .andExpect(jsonPath("$.email_verified").doesNotExist());
                if (scopes.contains("profile")) {
                    userInfo.andExpect(jsonPath("$.nickname").value("测试用户"));
                } else {
                    assertThat(claims).doesNotContainKeys("nickname", "preferred_username");
                    userInfo.andExpect(jsonPath("$.nickname").doesNotExist())
                        .andExpect(jsonPath("$.preferred_username").doesNotExist());
                }
            }
        }
    }

    @Test void removedPasswordGrantIsRejectedForPublicAndConfidentialClients() throws Exception {
        for (String client : List.of("public", "jwt")) {
            var request = post("/oauth2/token").param("grant_type", "password")
                .param("username", "fixture-user").param("password", "fixture-password");
            if (client.equals("public")) { request.param("client_id", client); }
            else { request.with(httpBasic(client,"fixture-secret")); }
            mvc.perform(request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.isIn(
                    List.of("unsupported_grant_type", "invalid_grant"))))
                .andExpect(jsonPath("$.id_token").doesNotExist());
        }
    }

    @Test void smsCompatibilityFailsClosedWithoutVerificationStore() throws Exception {
        selectClientProtocol("public", "OAUTH2_1");
        selectClientProtocol("compat", "OAUTH2_1");
        mvc.perform(post("/oauth2/token").with(httpBasic("compat", "fixture-secret"))
            .param("grant_type", "sms").param("phone", "13800000000").param("code", "fixture-code")
            .param("scope", "api.read")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.access_token").doesNotExist());
    }

    @Test void extensionGrantsKeepRequiredParametersAndPasswordIsUnsupported() throws Exception {
        selectClientProtocol("public", "OAUTH2_1");
        selectClientProtocol("compat", "OAUTH2_1");
        mvc.perform(post("/oauth2/token").with(httpBasic("compat", "fixture-secret"))
            .param("grant_type", "sms").param("phone", "13800000000").param("scope", "api.read"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_request"));
        mvc.perform(post("/oauth2/token").with(httpBasic("compat", "fixture-secret"))
            .param("grant_type", "sms").param("code", "fixture-code").param("scope", "api.read"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_request"));
        mvc.perform(post("/oauth2/token").with(httpBasic("compat", "fixture-secret"))
            .param("grant_type", "password").param("username", "fixture-user").param("password", "wrong")
            .param("scope", "api.read"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("unsupported_grant_type"));
    }

    static String query(String url, String key) {
        return Arrays.stream(URI.create(url).getRawQuery().split("&")).map(pair -> pair.split("=", 2))
            .filter(pair -> pair[0].equals(key)).map(pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8))
            .findFirst().orElseThrow();
    }

    @Test void confidentialOidcRefreshPreservesScopesAndTokenSemantics() throws Exception {
        for (String client : new String[] {"compat", "jwt", "reuse"}) {
            for (String grant : new String[] {"authorization_code"}) {
                MvcResult issued = mvc.perform(exchange(client, authorize(client, login()), verifier))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id_token").exists()).andReturn();
                String refresh = json.readTree(issued.getResponse().getContentAsString()).get("refresh_token").asText();
                String otherClient = client.equals("compat") ? "jwt" : "compat";
                mvc.perform(post("/oauth2/token").with(httpBasic(otherClient, "fixture-secret"))
                    .param("grant_type", "refresh_token").param("refresh_token", refresh))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
                mvc.perform(post("/oauth2/token").with(httpBasic(client, "fixture-secret"))
                    .param("grant_type", "refresh_token").param("refresh_token", refresh).param("scope", "email"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_scope"));
                MvcResult refreshed = mvc.perform(post("/oauth2/token").with(httpBasic(client, "fixture-secret"))
                    .param("grant_type", "refresh_token").param("refresh_token", refresh))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id_token").exists())
                    .andExpect(jsonPath("$.scope", org.hamcrest.Matchers.containsString("openid"))).andReturn();
                var tokens = json.readTree(refreshed.getResponse().getContentAsString());
                OAuth2Authorization stored = context.getBean(OAuth2AuthorizationService.class).findByToken(
                    tokens.get("access_token").asText(), OAuth2TokenType.ACCESS_TOKEN);
                assertEquals(Set.of("openid", "profile"), stored.getAuthorizedScopes());
                org.junit.jupiter.api.Assertions.assertNotNull(stored.getToken(OidcIdToken.class));
                if (client.equals("reuse")) {
                    assertEquals(refresh, tokens.get("refresh_token").asText());
                } else {
                    mvc.perform(post("/oauth2/token").with(httpBasic(client, "fixture-secret"))
                        .param("grant_type", "refresh_token").param("refresh_token", refresh))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
                }
                MvcResult narrowed = mvc.perform(post("/oauth2/token").with(httpBasic(client, "fixture-secret"))
                    .param("grant_type", "refresh_token").param("refresh_token", tokens.get("refresh_token").asText())
                    .param("scope", "profile"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.scope").value("profile"))
                    .andExpect(jsonPath("$.id_token").exists()).andReturn();
                String finalRefresh = json.readTree(narrowed.getResponse().getContentAsString())
                    .get("refresh_token").asText();
                mvc.perform(post("/oauth2/revoke").with(httpBasic(client, "fixture-secret"))
                    .param("token", finalRefresh).param("token_type_hint", "refresh_token")).andExpect(status().isOk());
                mvc.perform(post("/oauth2/token").with(httpBasic(client, "fixture-secret"))
                    .param("grant_type", "refresh_token").param("refresh_token", finalRefresh))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
            }
        }
    }

    @Test void refusesPlainPkceAndConsentDenial() throws Exception {
        MockHttpSession session = login();
        mvc.perform(get("/oauth2/authorize").session(session).queryParam("response_type", "code")
            .queryParam("client_id", "public").queryParam("redirect_uri", "https://client.example/callback")
            .queryParam("scope", "openid").queryParam("code_challenge", verifier)
            .queryParam("code_challenge_method", "plain")).andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("error=invalid_request")));
        // A new principal avoids previously saved consent from the other tests.
        MvcResult request = mvc.perform(get("/oauth2/authorize").with(user("consent-fixture"))
            .queryParam("response_type", "code").queryParam("client_id", "public")
            .queryParam("redirect_uri", "https://client.example/callback").queryParam("scope", "openid profile")
            .queryParam("code_challenge", challenge()).queryParam("code_challenge_method", "S256")).andReturn();
        String state = query(request.getResponse().getRedirectedUrl(), "state");
        mvc.perform(post("/oauth2/authorize").with(user("consent-fixture"))
            .param("client_id", "public").param("state", state))
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("error=access_denied")));
    }

    @Test void refreshAndRevocationWorkAndInvalidTokensAreInactive() throws Exception {
        var introspectionCaller = context.getBean(SysOAuth2ClientService.class).getClientByClientId("compat");
        introspectionCaller.setPurpose("RESOURCE_SERVER");
        introspectionCaller.setProtocolConfigurations(java.util.List.of());

        when(context.getBean(SysOAuth2ClientService.class).getClientByClientId("compat")).thenReturn(introspectionCaller);
        String code = authorize("jwt", login());
        JsonNode first = json.readTree(mvc.perform(exchange("jwt", code, verifier)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        String refresh = first.get("refresh_token").asText();
        JsonNode second = json.readTree(mvc.perform(post("/oauth2/token").with(httpBasic("jwt", "fixture-secret"))
            .param("grant_type", "refresh_token").param("refresh_token", refresh)).andExpect(status().isOk())
            .andExpect(jsonPath("$.id_token").exists())
            .andReturn().getResponse().getContentAsString());
        mvc.perform(post("/oauth2/token").with(httpBasic("jwt", "fixture-secret"))
            .param("grant_type", "refresh_token").param("refresh_token", refresh)).andExpect(status().isBadRequest());
        String access = second.get("access_token").asText();
        mvc.perform(post("/oauth2/revoke").with(httpBasic("jwt", "fixture-secret")).param("token", access))
            .andExpect(status().isOk());
        mvc.perform(get("/userinfo").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
        mvc.perform(post("/oauth2/introspect").with(httpBasic("compat", "fixture-secret")).param("token", "absent"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
    }

    @Test void publicTokenClientAuthenticationIsLimitedToTokenEndpoint() throws Exception {
        var caller = context.getBean(SysOAuth2ClientService.class).getClientByClientId("compat");
        caller.setPurpose("RESOURCE_SERVER");
        caller.setProtocolConfigurations(java.util.List.of());

        when(context.getBean(SysOAuth2ClientService.class).getClientByClientId("compat")).thenReturn(caller);
        MvcResult issued = mvc.perform(exchange("jwt", authorize("jwt", login(), "api.read"), verifier))
            .andExpect(status().isOk()).andReturn();
        String accessToken = json.readTree(issued.getResponse().getContentAsString())
            .get("access_token").asText();

        mvc.perform(post("/oauth2/introspect").param("client_id", "public")
                .param("grant_type", "password").param("token", accessToken))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/login")))
            .andExpect(content().string(""));
        mvc.perform(post("/oauth2/revoke").param("client_id", "public")
                .param("grant_type", "password").param("token", accessToken))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/login")))
            .andExpect(content().string(""));

        mvc.perform(post("/oauth2/introspect").with(httpBasic("compat", "fixture-secret"))
                .param("token", accessToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
    }

    @Test void exportsRenderedPagesForResponsiveReview() throws Exception {
        java.nio.file.Path preview = java.nio.file.Path.of("target", "iam-preview");
        java.nio.file.Files.createDirectories(preview.resolve("iam"));
        SysOAuth2Client previewClient = new SysOAuth2Client();
        previewClient.setTenantId(1L);
        previewClient.setUserType(dev.macula.cloud.iam.enums.UserTypeEnum.EMPLOYEE);
        when(context.getBean(SysOAuth2ClientService.class).getClientByClientId("preview"))
            .thenReturn(previewClient);
        when(context.getBean(SysIdentityProviderMapper.class).selectCount(any())).thenReturn(1L);
        String login = mvc.perform(get("/login").queryParam("client_id", "preview"))
            .andExpect(status().isOk()).andReturn().getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
        MvcResult consentRequest = mvc.perform(get("/oauth2/authorize").with(user("演示账号"))
            .queryParam("response_type", "code").queryParam("client_id", "public").queryParam("scope", "openid profile")
            .queryParam("redirect_uri", "https://client.example/callback").queryParam("code_challenge", challenge())
            .queryParam("code_challenge_method", "S256")).andReturn();
        String consent = mvc.perform(get(URI.create(consentRequest.getResponse().getRedirectedUrl())).with(user("演示账号")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        org.springframework.mock.web.MockHttpServletRequest bindingRequest =
            new org.springframework.mock.web.MockHttpServletRequest();
        context.getBean(PendingThirdPartyBindingRepository.class).save(bindingRequest,
            dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.bindingRequired("GITHUB", "preview-subject",
                dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.context("GITHUB", "", "test-client", Instant.now(), "/")));
        String binding = mvc.perform(get("/login/bind")
                .session((MockHttpSession) bindingRequest.getSession()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        java.nio.file.Files.writeString(preview.resolve("login.html"), login);
        java.nio.file.Files.writeString(preview.resolve("consent.html"), consent);
        java.nio.file.Files.writeString(preview.resolve("binding.html"), binding);
        for (String asset : List.of("auth.css", "auth.js")) {
            java.nio.file.Files.copy(java.nio.file.Path.of("src/main/resources/static/iam", asset),
                preview.resolve("iam").resolve(asset), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Test void oidcLogoutRejectsUnregisteredRedirectAndEndsSession() throws Exception {
        MockHttpSession session = login();
        String code = authorize("jwt", session);
        JsonNode tokens = json.readTree(mvc.perform(exchange("jwt", code, verifier)).andReturn()
            .getResponse().getContentAsString());
        String hint = tokens.get("id_token").asText();
        mvc.perform(get("/connect/logout").session(session).queryParam("id_token_hint", hint)
            .queryParam("post_logout_redirect_uri", "https://unregistered.example"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/connect/logout").session(session).param("id_token_hint", hint).param("client_id", "jwt"))
            .andExpect(status().is3xxRedirection());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test void explicitDenialDoesNotApproveOrEraseExistingConsent() throws Exception {
        OAuth2AuthorizationConsentService consents = context.getBean(OAuth2AuthorizationConsentService.class);
        consents.save(OAuth2AuthorizationConsent.withId("public", "existing-consent-user").scope("openid").build());
        MvcResult pending = mvc.perform(get("/oauth2/authorize").with(user("existing-consent-user"))
            .queryParam("response_type", "code").queryParam("client_id", "public")
            .queryParam("redirect_uri", "https://client.example/callback").queryParam("scope", "openid profile")
            .queryParam("code_challenge", challenge()).queryParam("code_challenge_method", "S256"))
            .andReturn();
        String state = query(pending.getResponse().getRedirectedUrl(), "state");
        mvc.perform(post("/oauth2/authorize").with(user("existing-consent-user"))
            .param("client_id", "public").param("state", state).param("scope", "openid", "profile")
            .param("decision", "deny"))
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("error=access_denied")));
        assertThat(consents.findById("public", "existing-consent-user").getScopes()).containsExactly("openid");
        assertThat(context.getBean(OAuth2AuthorizationService.class)
            .findByToken(state, new OAuth2TokenType("state"))).isNull();
    }

    /** refresh请求缩小Scope时仍须尊重原始OIDC授权，不能变成纯OAuth。 */
    @Test
    void nativeRefreshCannotConvertDisabledOidcIntoOauth() throws Exception {
        var clients = context.getBean(SysOAuth2ClientService.class);
        var current = clients.getClientByClientId("jwt");
        when(clients.getClientByClientId("jwt")).thenReturn(current);
        String code = authorize("jwt",login(),"openid profile api.read");
        MvcResult result = mvc.perform(exchange("jwt",code,verifier)).andExpect(status().isOk()).andReturn();
        String refresh = json.readTree(result.getResponse().getContentAsString()).get("refresh_token").asText();
        current.setEnabledProtocols("OAUTH2_1");
        dev.macula.cloud.iam.protocol.oauth2.ProtocolTestFixtures.configure(current);
        current.setDefaultProtocol("OAUTH2_1");
        mvc.perform(post("/oauth2/token").with(httpBasic("jwt","fixture-secret"))
            .param("grant_type","refresh_token").param("refresh_token",refresh).param("scope","api.read"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("unauthorized_client"));
    }

    /** 不带Scope的请求不得通过框架默认全Scope隐式获得openid。 */
    @Test
    void authorizeRequiresExplicitScope() throws Exception {
        mvc.perform(get("/oauth2/authorize").session(login()).queryParam("response_type","code").queryParam("client_id","public")
            .queryParam("redirect_uri","https://client.example/callback").queryParam("code_challenge",challenge()).queryParam("code_challenge_method","S256"))
            .andExpect(status().isBadRequest());
    }

    /** 已签发的授权码不能绕过随后禁用OIDC或停用应用。 */
    @Test
    void currentConfigurationBlocksOldAuthorizationCodes() throws Exception {
        String code = authorize("public",login());
        var row = context.getBean(SysOAuth2ClientService.class).getClientByClientId("public");
        row.setEnabledProtocols("OAUTH2_1");
        dev.macula.cloud.iam.protocol.oauth2.ProtocolTestFixtures.configure(row);
        row.setDefaultProtocol("OAUTH2_1");
        mvc.perform(exchange("public",code,verifier)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("unauthorized_client"));
        row.setEnabledProtocols("OIDC,OAUTH2_1");
        dev.macula.cloud.iam.protocol.oauth2.ProtocolTestFixtures.configure(row);
        code = authorize("public",login());
        row.setConfigurationStatus("DRAFT");
        mvc.perform(exchange("public",code,verifier)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("unauthorized_client"));
    }

    @Test void anonymousAuthorizeRestoresOriginalRequestAfterLogin() throws Exception {
        MvcResult pending = mvc.perform(get("/oauth2/authorize").queryParam("response_type", "code")
            .queryParam("client_id", "public").queryParam("redirect_uri", "https://client.example/callback")
            .queryParam("scope", "openid profile").queryParam("state", "saved-state").queryParam("nonce", "saved-nonce")
            .queryParam("code_challenge", challenge()).queryParam("code_challenge_method", "S256"))
            .andExpect(status().is3xxRedirection()).andReturn();
        MockHttpSession session = (MockHttpSession) pending.getRequest().getSession(false);
        MvcResult loggedIn = mvc.perform(post("/login").session(session).with(csrf()).param("username", "fixture-user")
            .param("password", "fixture-password")).andExpect(status().isFound()).andReturn();
        String target = loggedIn.getResponse().getRedirectedUrl();
        assertThat(query(target, "code_challenge")).isEqualTo(challenge());
        assertThat(query(target, "nonce")).isEqualTo("saved-nonce");
        mvc.perform(get(java.net.URI.create(target)).session(session)).andExpect(status().is3xxRedirection());
    }
    /** 协议切换使旧授权码失效，不能带入新协议策略。 */
    @Test
    void protocolSwitchInvalidatesOldAuthorizationCode() throws Exception {
        String code = authorize("jwt", login(), "openid profile");
        selectClientProtocol("jwt", "OAUTH2_1");
        mvc.perform(exchange("jwt", code, verifier)).andExpect(status().isBadRequest());
        String next = authorize("jwt", login(), "profile api.read");
        mvc.perform(exchange("jwt", next, verifier)).andExpect(status().isOk())
            .andExpect(jsonPath("$.id_token").doesNotExist());
    }

    /** 内省资源服务器使用唯一JWKS，不能拿业务Client策略试错。 */
    @Test
    void resourcePrivateKeyJwtUsesOnlyRegisteredJwks() throws Exception {
        String token = json.readTree(mvc.perform(exchange("public", authorize("public", login()), verifier))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("access_token").asText();
        var row = context.getBean(SysOAuth2ClientService.class).getClientByClientId("jwt");
        row.setPurpose("RESOURCE_SERVER");
        row.setProtocolConfigurations(java.util.List.of());
        row.setClientAuthenticationMethods("private_key_jwt");

        row.setSigningAlgorithm("RS256");
        when(context.getBean(SysOAuth2ClientService.class).getClientByClientId("jwt")).thenReturn(row);
        var correctKey = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).keyID("resource-key").generate();
        var wrongKey = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).keyID("wrong-key").generate();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", request -> {
            byte[] body = new com.nimbusds.jose.jwk.JWKSet(correctKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            request.getResponseHeaders().set("Content-Type", "application/json");
            request.sendResponseHeaders(200, body.length);
            try (var out = request.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            row.setJwkSetUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/jwks");
            mvc.perform(managementAssertion("/oauth2/introspect", token, wrongKey)).andExpect(status().isUnauthorized());
            mvc.perform(managementAssertion("/oauth2/introspect", token, correctKey)).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.nickname").doesNotExist());
            mvc.perform(managementAssertion("/oauth2/revoke", token, correctKey)).andExpect(status().isBadRequest());
        } finally { server.stop(0); }
    }

    /** 使用一次性测试私钥签名，签名校验和JWKS读取仍由原生provider完成。 */
    private MockHttpServletRequestBuilder managementAssertion(String endpoint, String token, com.nimbusds.jose.jwk.RSAKey key) throws Exception {
        var claims = new com.nimbusds.jwt.JWTClaimsSet.Builder().issuer("jwt").subject("jwt").audience("https://iam.example")
            .issueTime(java.util.Date.from(Instant.now())).expirationTime(java.util.Date.from(Instant.now().plusSeconds(60)))
            .jwtID(java.util.UUID.randomUUID().toString()).build();
        var assertion = new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256)
            .keyID(key.getKeyID()).build(), claims);
        assertion.sign(new com.nimbusds.jose.crypto.RSASSASigner(key));
        return post(endpoint).param("client_id", "jwt").param("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
            .param("client_assertion", assertion.serialize()).param("token", token);
    }

    /** 机密Gateway可内省公共且仅启用OIDC的Admin，目标身份不参与调用方认证选择。 */
    @Test
    void confidentialCallerIntrospectsPublicOidcOnlyTarget() throws Exception {
        var introspectionCaller = context.getBean(SysOAuth2ClientService.class).getClientByClientId("compat");
        introspectionCaller.setPurpose("RESOURCE_SERVER");
        introspectionCaller.setProtocolConfigurations(java.util.List.of());

        when(context.getBean(SysOAuth2ClientService.class).getClientByClientId("compat")).thenReturn(introspectionCaller);
        var target = context.getBean(SysOAuth2ClientService.class).getClientByClientId("public");
        target.setProtocolConfigurations(target.getProtocolConfigurations().stream()
            .filter(value -> "OIDC".equals(value.protocol())).toList());
        String code = authorize("public", login());
        var issued = mvc.perform(exchange("public", code, verifier)).andExpect(status().isOk()).andReturn();
        String token = json.readTree(issued.getResponse().getContentAsString()).get("access_token").asText();
        mvc.perform(post("/oauth2/introspect").with(httpBasic("compat", "fixture-secret")).param("token", token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.client_id").value("public"));
        mvc.perform(post("/oauth2/introspect").with(httpBasic("public", "not-a-secret")).param("token", token))
            .andExpect(status().isUnauthorized());
    }

    /** 纯OAuth的profile真实走安全链与Redis授权，不产生ID Token。 */
    @Test
    void oauthProfileIsRealAndRevocationRemovesAccess() throws Exception {
        String code = authorize("public", login(), "profile api.read");
        var response = mvc.perform(exchange("public", code, verifier)).andExpect(status().isOk())
            .andExpect(jsonPath("$.id_token").doesNotExist()).andReturn();
        String token = json.readTree(response.getResponse().getContentAsString()).get("access_token").asText();
        mvc.perform(get("/api/v1/profile").header("Authorization", "Bearer " + token)).andExpect(status().isOk())
            .andExpect(jsonPath("$.preferred_username").value("fixture-user"))
            .andExpect(jsonPath("$.nickname").value("测试用户"))
            .andExpect(jsonPath("$.authorities").doesNotExist()).andExpect(jsonPath("$.email").doesNotExist());
        var store = context.getBean(OAuth2AuthorizationService.class);
        store.remove(store.findByToken(token, OAuth2TokenType.ACCESS_TOKEN));
        mvc.perform(get("/api/v1/profile").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    /** SDK扩展经过真实Token端点和签名器，账号解析与厂商远端单独Mock。 */
    @Test
    void oidcSdkIssuesSignedIdentityAndUsableUserInfoWithoutBrowserSession() throws Exception {
        var clients = context.getBean(SysOAuth2ClientService.class);
        var row = clients.getClientByClientId("public");
        var sdkSource = new SysIdentityProvider();
        sdkSource.setId(7L);
        sdkSource.setSourceType("WECHAT_MINIAPP");
        sdkSource.setProviderType("WECHAT");
        sdkSource.setVersion(0L);
        sdkSource.setAppId("fixture-miniapp");
        sdkSource.setAppSecret("fixture-secret");
        sdkSource.setConfigJson("{\"appId\":\"fixture-miniapp\"}");
        sdkSource.setEnabled(true);
        when(context.getBean(SysIdentityProviderMapper.class).selectById(7L)).thenReturn(sdkSource);
        row.setApplicationType("MINIAPP");
        row.setMiniappPlatform("WECHAT");
        row.setBrowserLoginProviderTypes("");
        row.setBrowserProviderBindings("{}");
        var settings = dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration.settings(row.getProtocolConfigurations().get(0));
        settings.setAuthorizationGrantTypes(ExternalCodeType.GRANT_TYPE);
        settings.setRedirectUris("");
        var binding = new dev.macula.cloud.iam.pojo.entity.SysOAuth2ClientExternalProvider();
        binding.setCodeType("WECHAT_MINIAPP_LOGIN_CODE");
        binding.setIdentityProviderId(7L);
        row.setExternalProviderBindings(java.util.Map.of("WECHAT_MINIAPP_LOGIN_CODE",7L));
        when(context.getBean(SysOAuth2ClientExternalProviderMapper.class).selectList(any()))
            .thenReturn(java.util.List.of(binding));
        var type = ExternalCodeType.WECHAT_MINIAPP_LOGIN_CODE;
        var verifier = mock(dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeVerifier.class);
        var verification = new dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeVerification(
            new dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity(7L, "WECHAT", "WECHAT_MINIAPP", 0L, dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.flow("WECHAT_MINIAPP"), type.name(), "OPEN_ID", "sdk-member", java.util.Map.of()));
        when(context.getBean(ExternalCodeVerifierRegistry.class).require(type)).thenReturn(verifier);
        when(verifier.verify(eq(type), eq(7L), any())).thenReturn(verification);
        var user = new dev.macula.cloud.iam.service.userdetails.SysUserDetails();
        user.setUserId(42L);
        user.setTenantId(1L);
        user.setUsername("fixture-user");
        user.setNickname("测试用户");
        user.setEnabled(true);
        var principal = org.springframework.security.authentication.UsernamePasswordAuthenticationToken
            .authenticated(user, null, user.getAuthorities());
        principal.setDetails(new java.util.HashMap<>(java.util.Map.of(
            dev.macula.cloud.iam.authentication.mfa.MfaCompletionService.SOURCE_PROOF,
            dev.macula.cloud.iam.authentication.mfa.MfaCompletionService.verifiedProof(verification.identity()))));
        when(context.getBean(ExternalCodeAccountResolver.class).resolve(any(), eq(type), any())).thenReturn(principal);
        for (String format : java.util.List.of("reference", "self-contained")) {
            settings.setTokenFormat(format);
            var result = mvc.perform(post("/oauth2/token").param("client_id", "public")
                .param("grant_type", ExternalCodeType.GRANT_TYPE).param("code_type", type.name())
                .param("code", "sdk-" + format).param("scope", "openid profile api.read")
                .param("nonce", "sdk-nonce")).andExpect(status().isOk())
                .andExpect(jsonPath("$.refresh_token").doesNotExist()).andReturn();
            assertThat(result.getRequest().getSession(false)).isNull();
            var tokens = json.readTree(result.getResponse().getContentAsString());
            var identity = decoder.decode(tokens.get("id_token").asText());
            assertThat(identity.getIssuer().toString()).isEqualTo("https://iam.example");
            assertThat(identity.getSubject()).isEqualTo("1:42");
            assertThat(identity.getClaims()).doesNotContainKeys(
                dev.macula.cloud.iam.authentication.mfa.MfaCompletionService.SOURCE_PROOF, "registrationId", "sourceVersion");
            var storedSource = context.getBean(OAuth2AuthorizationService.class).findByToken(
                tokens.get("access_token").asText(), OAuth2TokenType.ACCESS_TOKEN);
            java.util.Map<String,Object> sourceProof = storedSource.getAttribute(
                dev.macula.cloud.iam.authentication.mfa.MfaCompletionService.SOURCE_PROOF);
            assertThat(((Number) sourceProof.get("registrationId")).longValue()).isEqualTo(7L);
            assertThat(identity.getAudience()).containsExactly("public");
            assertThat(identity.getClaimAsString("nonce")).isEqualTo("sdk-nonce");
            assertThat(identity.getExpiresAt()).isAfter(identity.getIssuedAt());
            assertThat(identity.getClaims()).doesNotContainKeys("roles", "authorities", "auth_time", "acr", "amr");
            assertThat(identity.getClaimAsString("nickname")).isEqualTo("测试用户");
            String access = tokens.get("access_token").asText();
            var saved = context.getBean(OAuth2AuthorizationService.class).findByToken(access, OAuth2TokenType.ACCESS_TOKEN);
            assertThat(saved.getToken(org.springframework.security.oauth2.core.oidc.OidcIdToken.class)).isNotNull();
            assertThat(saved.getAccessToken().getClaims().get("aud")).asList()
                .containsExactlyInAnyOrder("urn:macula:iam:profile", "urn:macula:system");
            assertThat(saved.<String>getAttribute("nonce")).isEqualTo("sdk-nonce");
            mvc.perform(get("/userinfo").header("Authorization", "Bearer " + access)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").value("1:42")).andExpect(jsonPath("$.nickname").value("测试用户"));
            mvc.perform(get("/userinfo").header("Authorization", "Bearer " + tokens.get("id_token").asText()))
                .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/oauth2/token").param("client_id", "public")
            .param("grant_type", ExternalCodeType.GRANT_TYPE).param("code_type", type.name())
            .param("code", "no-openid").param("scope", "profile"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_scope"));
        var noNonce = mvc.perform(post("/oauth2/token").param("client_id", "public")
            .param("grant_type", ExternalCodeType.GRANT_TYPE).param("code_type", type.name())
            .param("code", "no-nonce").param("scope", "openid profile"))
            .andExpect(status().isOk()).andReturn();
        var tokens = json.readTree(noNonce.getResponse().getContentAsString());
        assertThat(decoder.decode(tokens.get("id_token").asText()).getClaims()).doesNotContainKey("nonce");
        row.setAuthorizationVersion(row.getAuthorizationVersion() + 1);
        mvc.perform(get("/userinfo").header("Authorization", "Bearer " + tokens.get("access_token").asText()))
            .andExpect(status().isUnauthorized());
        settings.setScopes("profile,api.read");
        row.setProtocolConfigurations(java.util.List.of(new dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration(
            "OAUTH2_1", 1, true, settings)));
        mvc.perform(post("/oauth2/token").param("client_id", "public")
            .param("grant_type", ExternalCodeType.GRANT_TYPE).param("code_type", type.name())
            .param("code", "oauth-code").param("scope", "profile"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id_token").doesNotExist());

    }

    /** 原生服务授权得到注册租户和资源aud，不得申请用户profile或ID Token。 */
    @Test
    void machineGrantUsesTrustedTenantAndCannotRequestUserProfile() throws Exception {
        var clients = context.getBean(SysOAuth2ClientService.class);
        var row = clients.getClientByClientId("jwt");
        row.setEnabledProtocols("OAUTH2_1");
        row.setDefaultProtocol("OAUTH2_1");
        row.setApplicationType("SERVICE");
        row.setUserType(null);
        row.setAuthorizationGrantTypes("client_credentials");
        row.setScopes("api.read");
        row.setExternalCodeTypes("");
        dev.macula.cloud.iam.protocol.oauth2.ProtocolTestFixtures.configure(row);
        when(clients.getClientByClientId("jwt")).thenReturn(row);
        var result = mvc.perform(post("/oauth2/token").with(httpBasic("jwt", "fixture-secret"))
            .param("grant_type", "client_credentials").param("scope", "api.read")).andExpect(status().isOk())
            .andExpect(jsonPath("$.id_token").doesNotExist()).andReturn();
        String token = json.readTree(result.getResponse().getContentAsString()).get("access_token").asText();
        var claims = decoder.decode(token).getClaims();
        assertThat(claims.get("aud")).isEqualTo(java.util.List.of("urn:macula:system"));
        assertThat(((Number) claims.get("tenantId")).longValue()).isEqualTo(1L);
        row.setUserType(null);
        mvc.perform(post("/oauth2/token").with(httpBasic("jwt", "fixture-secret"))
            .param("grant_type", "client_credentials").param("scope", "profile"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_scope"));
    }

    /** 微信网站和服务号各自固定注册；撤销能力后旧state不能继续换码。 */
    @Test
    void browserCapabilitiesChooseIndependentWechatRegistrationsAndRejectRevokedCallback() throws Exception {
        configureDefaultSources();
         useOfficialAccountSource();
        var row = context.getBean(SysOAuth2ClientService.class).getClientByClientId("public");
        row.setBrowserLoginProviderTypes("WECHAT");
         row.setBrowserProviderBindings("{\"WECHAT\":5}");
        row.setBrowserCapabilityBindings("{\"WECHAT_WEBSITE\":5,\"WECHAT_OFFICIAL_ACCOUNT\":6}");
        var session = browserAuthorizationSession();
        mvc.perform(get("/login/wechat/browser").session(session).with(anonymous()))
            .andExpect(status().isFound()).andExpect(header().string("Location",org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.startsWith("https://open.weixin.qq.com/connect/qrconnect"), org.hamcrest.Matchers.containsString("appid=app"))));
        row.setApplicationType("IN_APP");
        row.setBrowserCapabilityBindings("{\"WECHAT_OFFICIAL_ACCOUNT\":6}");
        var redirected = mvc.perform(get("/login/wechat/webview").session(session).with(anonymous())
                .header(HttpHeaders.USER_AGENT,"MicroMessenger"))
            .andExpect(status().isFound()).andExpect(header().string("Location",org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.startsWith("https://open.weixin.qq.com/connect/oauth2/authorize"),org.hamcrest.Matchers.containsString("appid=wechat-oa"))))
            .andReturn().getResponse().getRedirectedUrl();
        var official = context.getBean(SysIdentityProviderMapper.class).selectById(6L);
        official.setCapabilitiesJson("{}");
         official.setVersion(1L);
        mvc.perform(get("/login/wechat").session(session).with(anonymous())
                .queryParam("code","must-not-exchange").queryParam("state",query(redirected,"state")))
            .andExpect(status().isFound()).andExpect(redirectedUrl("https://iam.example/login?error=authentication_unavailable"));
    }

}
