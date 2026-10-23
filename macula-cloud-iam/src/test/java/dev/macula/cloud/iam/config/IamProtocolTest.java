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
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyLoginContext;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyUserDetailsService;
import dev.macula.cloud.iam.authentication.wecom.WecomClient;
import dev.macula.cloud.iam.authentication.wechat.WechatClient;
import dev.macula.cloud.iam.controller.LoginController;
import dev.macula.cloud.iam.controller.ProtocolVerificationCodeController;
import dev.macula.cloud.iam.controller.SmsCodeController;
import dev.macula.cloud.iam.controller.ThirdPartyBindingController;
import dev.macula.cloud.iam.handler.AuthenticationFailureReason;
import dev.macula.cloud.iam.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.iam.mapper.SysOAuth2ClientExternalProviderMapper;
import dev.macula.cloud.iam.mapper.SysUserIdentityBindingMapper;
import dev.macula.cloud.iam.message.ProtocolLoginCodeDispatcher;
import dev.macula.cloud.iam.pojo.dto.UserAuthInfo;
import dev.macula.cloud.iam.pojo.entity.SysIdentityProvider;
import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.protocol.oauth2.endpoint.AuthorizationConsentController;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeAccountResolver;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeClientSettings;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeReplayStore;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeVerifierRegistry;
import dev.macula.cloud.iam.service.support.SysOAuth2ClientService;
import dev.macula.cloud.iam.service.support.SysUserService;
import dev.macula.cloud.iam.service.userdetails.ClientAwareAccountResolver;
import dev.macula.cloud.system.security.UserIdentifierNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
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
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
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

import java.net.ServerSocket;
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
import static org.junit.jupiter.api.Assertions.assertNull;
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
    "macula.cloud.iam.token-cors-allowed-origins=https://admin.example",
    "macula.cloud.iam.sms-verification.digest-key=fixture-digest-key",
    "macula.cloud.iam.email-verification.digest-key=fixture-digest-key",
    "macula.cloud.iam.login-captcha.digest-key=fixture-digest-key"
})
class IamProtocolTest {
    @Autowired WebApplicationContext context;
    @Autowired JwtDecoder decoder;
    MockMvc mvc;
    ObjectMapper json = tools.jackson.databind.json.JsonMapper.builder().build();
    final String verifier = "01234567890123456789012345678901234567890123456789";

    @Test void tokenCorsAllowsOnlyConfiguredOriginAndEndpoint() throws Exception {
        mvc.perform(options("/oauth2/token")
                .header(HttpHeaders.ORIGIN, "https://admin.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                    HttpHeaders.AUTHORIZATION + ", " + HttpHeaders.CONTENT_TYPE + ", tenantId"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://admin.example"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                org.hamcrest.Matchers.containsString(HttpMethod.POST.name())))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                org.hamcrest.Matchers.containsStringIgnoringCase(HttpHeaders.AUTHORIZATION)))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));

        mvc.perform(post("/oauth2/token")
                .header(HttpHeaders.ORIGIN, "https://admin.example")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://admin.example"))
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

    @Test void protocolCaptchaPreflightUsesOriginAndPostBindsClientHeaderToBody() throws Exception {
        SysOAuth2ClientService clientService = context.getBean(SysOAuth2ClientService.class);
        LoginCaptchaService captchaService = context.getBean(LoginCaptchaService.class);
        when(clientService.hasAllowedOrigin("https://client.example")).thenReturn(true);

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
        ThirdPartyBindingController.class,
        AuthorizationConsentController.class, GithubClient.class, WecomClient.class, FeishuClient.class,
        DingtalkClient.class, WechatClient.class,
        IdentityProviderConfigService.class, HttpSessionThirdPartyLoginStateRepository.class,
        BrowserLoginClientPolicyService.class,
        ThirdPartyUserDetailsService.class, ThirdPartyIdentityResolutionService.class,
        PendingThirdPartyBindingRepository.class})
    static class App implements WebMvcConfigurer {
        @Bean @Primary RegisteredClientRepository fixtureClients() {
            return new InMemoryRegisteredClientRepository(client("public", ClientAuthenticationMethod.NONE,
                OAuth2TokenFormat.REFERENCE), client("jwt", ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                OAuth2TokenFormat.SELF_CONTAINED), client("compat", ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                OAuth2TokenFormat.REFERENCE), client("reuse", ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                OAuth2TokenFormat.REFERENCE));
        }
        static RegisteredClient client(String id, ClientAuthenticationMethod method, OAuth2TokenFormat format) {
            return RegisteredClient.withId(id).clientId(id)
                .clientSecret("{bcrypt}" + new BCryptPasswordEncoder().encode("fixture-secret"))
                .clientAuthenticationMethod(method).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrantType(new AuthorizationGrantType("password"))
                .authorizationGrantType(new AuthorizationGrantType("sms"))
                .redirectUri("https://client.example/callback").scope("openid").scope("profile").scope("email")
                .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(true)
                    .setting(ExternalCodeClientSettings.ALLOWED_ORIGINS,
                        Set.of("https://client.example"))
                    .setting(ExternalCodeClientSettings.USER_TYPE, "employee")
                    .setting(ExternalCodeClientSettings.TENANT_ID, 1L).build())
                .tokenSettings(TokenSettings.builder().accessTokenFormat(format)
                    .reuseRefreshTokens("reuse".equals(id)).build()).build();
        }
        @Bean @Primary OAuth2AuthorizationService fixtureAuthorizations(RedisTemplate<String, Object> redisTemplate,
            RegisteredClientRepository clients) {
            return new dev.macula.cloud.iam.service.oauth2.MaculaOAuth2AuthorizationService(redisTemplate, clients);
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

    @BeforeEach
    void setup() {
        SysOAuth2Client browserClient = new SysOAuth2Client();
        browserClient.setClientId("public");
        browserClient.setAuthorizationGrantTypes("authorization_code,refresh_token");
        browserClient.setRedirectUris("https://client.example/callback");
        browserClient.setBrowserLoginProviderTypes("DINGTALK,FEISHU,GITHUB,WECOM,WECHAT");
        when(context.getBean(SysOAuth2ClientService.class).getClientByClientId("public"))
            .thenReturn(browserClient);
        mvc = webAppContextSetup(context).apply(springSecurity()).alwaysDo(result -> {
            if (result.getResponse().getStatus() >= 400) {
                System.out.println("Rejected " + result.getRequest().getRequestURI() + ": "
                    + result.getResponse().getStatus() + " " + result.getResponse().getContentAsString());
            }
        }).build();
    }

    private MockHttpSession browserAuthorizationSession() {
        return browserAuthorizationSession("");
    }

    private MockHttpSession browserAuthorizationSession(String contextPath) {
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
            new ThirdPartyIdentityResolutionService.BindingRequiredException("GITHUB", "123",
                new ThirdPartyLoginContext("GITHUB", "", "test-client", Instant.now(), "/")));
        MockHttpSession session = (MockHttpSession) request.getSession();
        mvc.perform(get("/login/bind").session(session)).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "action=\"/login/bind/cancel\"")));
        mvc.perform(post("/login/bind").session(session).param("username", "fixture-user")
            .param("method", "password").param("password", "fixture-password"))
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
        when(providers.selectCount(any())).thenReturn(1L);

        mvc.perform(get("/login").session(browserAuthorizationSession()).with(anonymous()))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("使用 GitHub 登录")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("使用企业微信登录")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "href=\"/login/github\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "href=\"/login/wecom\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/login/feishu\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/login/dingtalk\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/login/wechat\"")))
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

        when(providers.selectCount(any())).thenReturn(0L);
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

        when(providers.selectCount(any())).thenReturn(1L);
        mvc.perform(get("/login").with(anonymous()).queryParam("client_id", "unknown"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("使用 GitHub 登录"))));
    }

    @Test void loginAutomaticallyUsesClientPolicyAndStopsOnError() throws Exception {
        SysIdentityProviderMapper providers = context.getBean(SysIdentityProviderMapper.class);
        when(providers.selectCount(any())).thenReturn(1L);
        SysIdentityProvider wecom = new SysIdentityProvider();
        wecom.setProviderType("WECOM");
        wecom.setAppId("corp");
        wecom.setAppSecret("secret");
        wecom.setConfigJson("{\"corpId\":\"corp\",\"agentId\":\"agent\"}");
        wecom.setEnabled(true);
        when(providers.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
            .thenReturn(List.of(wecom));

        mvc.perform(get("/login").session(browserAuthorizationSession()).with(anonymous())
                .header(HttpHeaders.USER_AGENT, "MicroMessenger wxwork"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("/login/wecom/webview"));

        SysIdentityProvider wechat = new SysIdentityProvider();
        wechat.setProviderType("WECHAT");
        wechat.setAppId("wechat-site");
        wechat.setAppSecret("site-secret");
        wechat.setConfigJson("{\"appId\":\"wechat-site\",\"officialAccountAppId\":\"wechat-oa\"}");
        wechat.setSecretConfigJson("{\"officialAccountAppSecret\":\"oa-secret\"}");
        wechat.setEnabled(true);
        when(providers.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
            .thenReturn(List.of(wechat));

        mvc.perform(get("/login").session(browserAuthorizationSession()).with(anonymous())
                .header(HttpHeaders.USER_AGENT, "MicroMessenger"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("/login/wechat/webview"));
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
        directClient.setDirectLoginProviderType("GITHUB");
        when(context.getBean(SysOAuth2ClientService.class).getClientByClientId("public"))
            .thenReturn(directClient);
        SysIdentityProvider github = new SysIdentityProvider();
        github.setProviderType("GITHUB");
        github.setAppId("github-client");
        github.setAppSecret("secret");
        github.setConfigJson("{\"clientId\":\"github-client\"}");
        github.setEnabled(true);
        when(providers.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
            .thenReturn(List.of(github));

        mvc.perform(get("/login").session(browserAuthorizationSession()).with(anonymous()))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("/login/github"));
    }

    @Test void proxiedLoginKeepsExternalIamPrefix() throws Exception {
        SysIdentityProviderMapper providers = context.getBean(SysIdentityProviderMapper.class);
        when(providers.selectCount(any())).thenReturn(1L);
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
        github.setProviderType("GITHUB");
        github.setAppId("github-client");
        github.setAppSecret("secret");
        github.setConfigJson("{\"clientId\":\"github-client\"}");
        github.setEnabled(true);
        when(providers.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
            .thenReturn(List.of(github));
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
        github.setProviderType("GITHUB");
        github.setAppId("github-client");
        github.setAppSecret("secret");
        github.setConfigJson("{\"clientId\":\"github-client\"}");
        github.setEnabled(true);
        when(providers.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
            .thenReturn(List.of(github));
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

    @Test void thirdPartyFiltersUseTrustedIssuerWithoutClientSnapshot() throws Exception {
        SysIdentityProviderMapper providers = context.getBean(SysIdentityProviderMapper.class);
        MockHttpSession browserSession = browserAuthorizationSession();

        SysIdentityProvider github = new SysIdentityProvider();
        github.setProviderType("GITHUB");
        github.setAppId("github-client");
        github.setAppSecret("secret");
        github.setConfigJson("{\"clientId\":\"github-client\"}");
        github.setEnabled(true);
        when(providers.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
            .thenReturn(List.of(github));

        MvcResult githubStart = mvc.perform(get("/login/github").session(browserSession).with(anonymous())
                .queryParam("client_id", "portal").header("Host", "attacker.example"))
            .andExpect(status().isFound()).andReturn();
        assertThat(githubStart.getResponse().getRedirectedUrl())
            .startsWith("https://github.com/login/oauth/authorize")
            .contains("redirect_uri=https://iam.example/login/github")
            .doesNotContain("attacker.example");

        SysIdentityProvider wecom = new SysIdentityProvider();
        wecom.setProviderType("WECOM");
        wecom.setAppId("corp");
        wecom.setAppSecret("secret");
        wecom.setConfigJson("{\"corpId\":\"corp\",\"agentId\":\"agent\"}");
        wecom.setEnabled(true);
        when(providers.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
            .thenReturn(List.of(wecom));

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
        mvc.perform(get("/login/wecom/webview").session(browserSession).with(anonymous()))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("scope=snsapi_base")));
        mvc.perform(get("/login/wecom/browser").session(browserSession).with(anonymous())
                .queryParam("code", "forged").queryParam("state", "forged"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("https://iam.example/login?error=authentication_unavailable"));

        SysIdentityProvider feishu = new SysIdentityProvider();
        feishu.setProviderType("FEISHU");
        feishu.setAppId("feishu-app");
        feishu.setAppSecret("secret");
        feishu.setConfigJson("{\"appId\":\"feishu-app\"}");
        feishu.setEnabled(true);
        when(providers.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
            .thenReturn(List.of(feishu));
        mvc.perform(get("/login/feishu/browser").session(browserSession).with(anonymous()))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.startsWith("https://accounts.feishu.cn/open-apis/authen/v1/authorize"),
                org.hamcrest.Matchers.containsString("redirect_uri=https://iam.example/login/feishu"))));
        SysIdentityProvider dingtalk = new SysIdentityProvider();
        dingtalk.setProviderType("DINGTALK");
        dingtalk.setAppId("ding-app");
        dingtalk.setAppSecret("secret");
        dingtalk.setConfigJson("{\"appKey\":\"ding-app\",\"corpId\":\"ding-corp\"}");
        dingtalk.setEnabled(true);
        when(providers.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
            .thenReturn(List.of(dingtalk));
        mvc.perform(get("/login/dingtalk/browser").session(browserSession).with(anonymous()))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.startsWith("https://login.dingtalk.com/oauth2/auth"),
                org.hamcrest.Matchers.containsString("redirect_uri=https://iam.example/login/dingtalk"))));
        SysIdentityProvider wechat = new SysIdentityProvider();
        wechat.setProviderType("WECHAT");
        wechat.setAppId("wechat-site");
        wechat.setAppSecret("site-secret");
        wechat.setConfigJson("{\"appId\":\"wechat-site\",\"officialAccountAppId\":\"wechat-oa\"}");
        wechat.setSecretConfigJson("{\"officialAccountAppSecret\":\"oa-secret\"}");
        wechat.setEnabled(true);
        when(providers.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
            .thenReturn(List.of(wechat));
        mvc.perform(get("/login/wechat/browser").session(browserSession).with(anonymous()))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("scope=snsapi_login")));
        mvc.perform(get("/login/wechat/webview").session(browserSession).with(anonymous()))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("scope=snsapi_base")));

        when(providers.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
            .thenReturn(List.of());
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

    String authorize(String client, MockHttpSession session) throws Exception {
        return authorize(client, session, "openid profile");
    }

    String authorize(String client, MockHttpSession session, String scopes) throws Exception {
        MvcResult result = mvc.perform(get("/oauth2/authorize").session(session).queryParam("response_type", "code")
            .queryParam("client_id", client).queryParam("redirect_uri", "https://client.example/callback")
            .queryParam("scope", scopes).queryParam("state", "fixture-state").queryParam("nonce", "fixture-nonce")
            .queryParam("code_challenge", challenge()).queryParam("code_challenge_method", "S256")).andReturn();
        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).isNotNull();
        if (location.contains("/oauth2/consent")) {
            mvc.perform(get(location).session(session)).andExpect(status().isOk())
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
        return "public".equals(client) ? request.param("client_id", client) :
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
        for (String scopes : List.of("profile", "openid", "openid email", "openid profile")) {
            String code = authorize("jwt", login(), scopes);
            var response = mvc.perform(exchange("jwt", code, verifier)).andExpect(status().isOk()).andReturn();
            JsonNode tokens = json.readTree(response.getResponse().getContentAsString());
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

    @Test void passwordCompatibilityIsPreservedWithoutPkceOrIdToken() throws Exception {
        MvcResult result = mvc.perform(post("/oauth2/token").with(httpBasic("compat", "fixture-secret"))
            .param("grant_type", "password").param("username", "fixture-user").param("password", "fixture-password")
            .param("scope", "openid profile")).andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").exists())
            .andExpect(jsonPath("$.id_token").doesNotExist()).andReturn();
        String access = json.readTree(result.getResponse().getContentAsString()).get("access_token").asText();
        mvc.perform(get("/userinfo").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
    }

    @Test void smsCompatibilityFailsClosedWithoutVerificationStore() throws Exception {
        mvc.perform(post("/oauth2/token").with(httpBasic("compat", "fixture-secret"))
            .param("grant_type", "sms").param("phone", "13800000000").param("code", "fixture-code")
            .param("scope", "profile")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.access_token").doesNotExist());
    }

    @Test void compatibilityGrantsKeepRequiredParametersAndPasswordError() throws Exception {
        mvc.perform(post("/oauth2/token").with(httpBasic("compat", "fixture-secret"))
            .param("grant_type", "sms").param("phone", "13800000000").param("scope", "profile"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_request"));
        mvc.perform(post("/oauth2/token").with(httpBasic("compat", "fixture-secret"))
            .param("grant_type", "sms").param("code", "fixture-code").param("scope", "profile"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_request"));
        mvc.perform(post("/oauth2/token").with(httpBasic("compat", "fixture-secret"))
            .param("grant_type", "password").param("username", "fixture-user").param("password", "wrong")
            .param("scope", "profile"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("bad_credentials"));
    }

    static String query(String url, String key) {
        return Arrays.stream(URI.create(url).getRawQuery().split("&")).map(pair -> pair.split("=", 2))
            .filter(pair -> pair[0].equals(key)).map(pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8))
            .findFirst().orElseThrow();
    }

    @Test void compatibilityOpenidRefreshPreservesScopesWithoutIssuingIdToken() throws Exception {
        for (String client : new String[] {"compat", "jwt", "reuse"}) {
            for (String grant : new String[] {"password"}) {
                MvcResult issued = mvc.perform(post("/oauth2/token").with(httpBasic(client, "fixture-secret"))
                    .param("grant_type", grant).param("username", "fixture-user").param("password", "fixture-password")
                    .param("phone", "13800000000").param("code", "fixture-code").param("scope", "openid profile"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id_token").doesNotExist()).andReturn();
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
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id_token").doesNotExist())
                    .andExpect(jsonPath("$.scope", org.hamcrest.Matchers.containsString("openid"))).andReturn();
                var tokens = json.readTree(refreshed.getResponse().getContentAsString());
                OAuth2Authorization stored = context.getBean(OAuth2AuthorizationService.class).findByToken(
                    tokens.get("access_token").asText(), OAuth2TokenType.ACCESS_TOKEN);
                assertEquals(Set.of("openid", "profile"), stored.getAuthorizedScopes());
                assertNull(stored.getToken(OidcIdToken.class));
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
                    .andExpect(jsonPath("$.id_token").doesNotExist()).andReturn();
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
        mvc.perform(post("/oauth2/introspect").with(httpBasic("jwt", "fixture-secret")).param("token", "absent"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
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
        String consent = mvc.perform(get("/oauth2/consent").with(user("演示账号"))
            .param("client_id", "public").param("scope", "openid profile").param("state", "preview"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        org.springframework.mock.web.MockHttpServletRequest bindingRequest =
            new org.springframework.mock.web.MockHttpServletRequest();
        context.getBean(PendingThirdPartyBindingRepository.class).save(bindingRequest,
            new ThirdPartyIdentityResolutionService.BindingRequiredException("GITHUB", "preview-subject",
                new ThirdPartyLoginContext("GITHUB", "", "test-client", Instant.now(), "/")));
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
        mvc.perform(get(target).session(session)).andExpect(status().is3xxRedirection());
    }
}
