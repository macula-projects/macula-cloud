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
package dev.macula.cloud.iam.controller;

import dev.macula.boot.constants.SecurityConstants;
import dev.macula.cloud.iam.authentication.email.EmailCodeAuthenticationToken;
import dev.macula.cloud.iam.authentication.email.EmailCodeService;
import dev.macula.cloud.iam.authentication.mfa.AdditionalAuthenticationRequiredException;
import dev.macula.cloud.iam.authentication.mfa.AuthenticationCheckpoint;
import dev.macula.cloud.iam.authentication.mfa.AuthenticationMethod;
import dev.macula.cloud.iam.authentication.mfa.MfaCompletionPolicy;
import dev.macula.cloud.iam.config.IamProtocolTest;
import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.message.mail.LoginMailDispatcher;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import dev.macula.cloud.iam.protocol.oauth2.ProtocolTestFixtures;
import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2ClientRegistration;
import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2ClientRegistrationService;
import dev.macula.cloud.iam.service.account.ClientAwareAccountResolver;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * 真安全链、真Redis和模板验证邮箱认证、授权码/OIDC及关闭策略；只隔离邮件外发。
 *
 * @author Macula
 * @since 6.1.0
 */
@SpringJUnitConfig(IamProtocolTest.App.class)
@WebAppConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("local")
@TestPropertySource(properties = {"macula.cloud.iam.issuer-uri=https://iam.example",
        "macula.cloud.iam.sms-verification.digest-key=fixture-digest-key",
        "macula.cloud.iam.email-verification.digest-key=fixture-digest-key"})
class HostedEmailLoginFlowTest {
    private static final String EMAIL = "alice@example.test";
    private static final String VERIFIER = "e".repeat(43);
    @Autowired
    private WebApplicationContext context;
    @MockitoBean
    private MfaCompletionPolicy completionPolicy;
    private MockMvc mvc;
    private OAuth2ClientRegistration client;
    private LoginMailDispatcher mail;

    /**
     * 固定服务端Client与唯一账号，SMTP由Mock捕获，验证码状态走真实Redis。
     */
    @BeforeEach
    void setUp() {
        reset(completionPolicy);
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        mail = context.getBean(LoginMailDispatcher.class);
        clearInvocations(mail);
        client = client("public");
        when(context.getBean(OAuth2ClientRegistrationService.class).getClientByClientId("public")).thenReturn(client);
        when(context.getBean(OAuth2ClientRegistrationService.class).getClientByClientId("public-other")).thenReturn(
                client("public-other"));
        var user = new SysUser();
        user.setId(42L);
        user.setUsername("fixture-user");
        user.setEmail(EMAIL);
        when(context.getBean(ClientAwareAccountResolver.class).findByEmail(eq(UserTypeEnum.EMPLOYEE), anyString()))
                .thenReturn(Optional.of(user));
    }

    /**
     * 邮箱成功建立Session，原PKCE和nonce贯穿到ID Token及UserInfo。
     */
    @Test
    void emailLoginCompletesPkceOidcAndRestoresPrincipal() throws Exception {
        MockHttpSession session = authorization("public");
        String code = send(session);
        var login = mvc.perform(
                        post("/login/email").session(session).with(csrf()).param("email", EMAIL).param("code", code))
                .andExpect(status().isFound()).andReturn();
        var security = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(security.getAuthentication()).isInstanceOf(EmailCodeAuthenticationToken.class);
        assertThat(security.getAuthentication().getCredentials()).isNull();
        assertThat(context.getBean("emailCodeService")).isInstanceOf(EmailCodeService.class);
        var authorized = mvc.perform(get(URI.create(login.getResponse().getRedirectedUrl())).session(session))
                .andExpect(status().is3xxRedirection()).andReturn();
        String location = authorized.getResponse().getRedirectedUrl();
        if (location != null && location.contains("/oauth2/consent")) {
            String state = UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("state");
            location = mvc.perform(post("/oauth2/authorize").session(session).with(csrf()).param("client_id", "public")
                            .param("state", state).param("scope", "openid", "profile"))
                    .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        }
        String authorizationCode = UriComponentsBuilder.fromUriString(Objects.requireNonNull(location))
                .build().getQueryParams().getFirst("code");
        assertThat(authorizationCode).isNotBlank();
        var token = mvc.perform(
                        post("/oauth2/token").param("grant_type", "authorization_code").param("client_id", "public")
                                .param("redirect_uri", "https://client.example/callback").param("code",
                                        authorizationCode)
                                .param("code_verifier", VERIFIER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id_token").isString()).andReturn();
        var json = JsonMapper.builder().build()
                .readTree(token.getResponse().getContentAsString());
        var jwt = context.getBean(JwtDecoder.class)
                .decode(json.get("id_token").asString());
        assertThat(jwt.getClaimAsString("nonce")).isEqualTo("email-nonce");
        assertThat(jwt.getClaimAsInstant("auth_time")).isNotNull();
        var stored = context.getBean(
                        OAuth2AuthorizationService.class)
                .findByToken(json.get("access_token").asString(),
                        OAuth2TokenType.ACCESS_TOKEN);
        // 当前授权只有openid/profile，不应发布业务权限或框架认证因子。
        assertThat(stored.getAccessToken().getClaims()).doesNotContainKey(
                SecurityConstants.AUTHORITIES_KEY);
        mvc.perform(get("/userinfo").header("Authorization", "Bearer " + json.get("access_token").asString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sub").isString());
        mvc.perform(post("/login/email").session(authorization("public")).with(csrf()).param("email", EMAIL)
                        .param("code", code))
                .andExpect(redirectedUrl("https://iam.example/login?error=authentication_rejected"));
    }

    /**
     * 邮箱一次认证仍需经过MFA发布门禁，拒绝时不建立会话。
     */
    @Test
    void emailDenialDoesNotEstablishSession() throws Exception {
        var session = authorization("public");
        String code = send(session);
        doThrow(new AdditionalAuthenticationRequiredException())
                .when(completionPolicy).check(any());
        var response = mvc.perform(
                        post("/login/email").session(session).with(csrf()).param("email", EMAIL).param("code", code))
                .andExpect(status().isServiceUnavailable()).andReturn();
        verify(completionPolicy).check(argThat(completion ->
                completion.method() == AuthenticationMethod.EMAIL
                        && completion.checkpoint() ==
                        AuthenticationCheckpoint.BROWSER_SESSION));
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
        assertThat(response.getResponse().getCookie("remember-me")).isNull();
    }

    /**
     * 关闭方式或缺少CSRF在投递前拒绝，已有授权请求不能绕过。
     */
    @Test
    void disabledAndCsrfCannotSendOrAuthenticate() throws Exception {
        var session = authorization("public");
        mvc.perform(post("/api/v1/email-code").session(session).param("email", EMAIL))
                .andExpect(status().isForbidden());
        client.setHostedLoginMethods("PASSWORD,SMS");
        mvc.perform(post("/api/v1/email-code").session(session).with(csrf()).param("email", EMAIL))
                .andExpect(status().isForbidden());
        mvc.perform(post("/login/email").session(session).with(csrf()).param("email", EMAIL).param("code", "123456"))
                .andExpect(redirectedUrl("https://iam.example/login?error=authentication_unavailable"));
        verifyNoInteractions(mail);
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
    }

    /**
     * Client B不能消费A的验证码，失败后A仍可完成合法认证。
     */
    @Test
    void isolatesCodeByClientAndRejectsDuplicateParameter() throws Exception {
        var first = authorization("public");
        String code = send(first);
        var second = authorization("public-other");
        mvc.perform(post("/login/email").session(second).with(csrf()).param("email", EMAIL).param("code", code))
                .andExpect(redirectedUrl("https://iam.example/login?error=authentication_rejected"));
        mvc.perform(
                        post("/login/email").session(first).with(csrf()).param("email", EMAIL).param("code", code,
                                "000000"))
                .andExpect(redirectedUrl("https://iam.example/login?error=authentication_rejected"));
        mvc.perform(post("/login/email").session(first).with(csrf()).param("email", EMAIL).param("code", code))
                .andExpect(status().isFound());
        assertThat(
                ((SecurityContext) first.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                        .getAuthentication().isAuthenticated()).isTrue();
    }

    /**
     * 从真实Thymeleaf模板导出合成预览，供独立浏览器验证布局和交互。
     */
    @Test
    void rendersAllFiveIconsAndEmailForBrowserVerification() throws Exception {
        var request = new MockHttpServletRequest(context.getServletContext());
        var response = new MockHttpServletResponse();
        var application = JakartaServletWebApplication.buildApplication(
                context.getServletContext());
        Map<String, Object> model = new HashMap<>();
        model.put("enablePasswordLogin", true);
        model.put("enableSmsLogin", true);
        model.put("enableEmailLogin", true);
        model.put("_csrf",
                new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "fixture-csrf"));
        for (String provider : List.of("github", "wecom", "feishu", "dingtalk", "wechat")) {
            model.put(provider + "LoginEnabled", true);
            model.put(provider + "LoginPath", "/login/" + provider);
        }
        var web = new WebContext(application.buildExchange(request, response),
                Locale.SIMPLIFIED_CHINESE, model);
        String html = context.getBean(SpringTemplateEngine.class).process("login", web);
        assertThat(html).contains("id=\"email-login\"", "aria-label=\"使用飞书登录\"", "/iam/icons/wecom.svg");
        Path output = Path.of("target", "iam-preview", "login.html");
        Files.createDirectories(output.getParent());
        Files.writeString(output, html);
        // 预览使用与线上相同的本地静态资源；所有生成物只写模块target。
        for (String asset : List.of("auth.css", "auth.js", "icons/github.svg", "icons/wecom.svg",
                "icons/feishu.svg", "icons/dingtalk.svg", "icons/wechat.svg")) {
            Path target = output.getParent().resolve("iam").resolve(asset);
            Files.createDirectories(target.getParent());
            try (var resource = new ClassPathResource("static/iam/" + asset).getInputStream()) {
                Files.copy(resource, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * 发码到合成地址，捕获本次随机验证码而不写入日志。
     */
    private String send(MockHttpSession session) throws Exception {
        // 各测试使用独立Redis状态键，避免发送窗口互相影响。
        context.getBean(StringRedisTemplate.class).delete(
                context.getBean(StringRedisTemplate.class)
                        .keys("iam:email-login:send:*"));
        mvc.perform(post("/api/v1/email-code").session(session).with(csrf()).param("email", EMAIL))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("submitted"));
        var value = ArgumentCaptor.forClass(String.class);
        verify(mail).send(eq(EMAIL), value.capture(), anyString(), any());
        return value.getValue();
    }

    /**
     * 请求由真实授权服务器保存，不能直接构造可信Client属性。
     */
    private MockHttpSession authorization(String clientId) throws Exception {
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(VERIFIER.getBytes(StandardCharsets.US_ASCII)));
        return (MockHttpSession) mvc.perform(
                        get("/oauth2/authorize").queryParam("response_type", "code").queryParam("client_id", clientId)
                                .queryParam("redirect_uri", "https://client.example/callback")
                                .queryParam("scope", "openid profile").queryParam("state", "email-state")
                                .queryParam("nonce", "email-nonce").queryParam("code_challenge", challenge)
                                .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection()).andReturn().getRequest().getSession(false);
    }

    /**
     * 只提供当前用例允许的EMAIl方式，不依赖生产Client或真实账号。
     */
    private OAuth2ClientRegistration client(String id) {
        var result = new OAuth2ClientRegistration();
        result.setClientId(id);
        result.setClientName("Email fixture");
        result.setApplicationType("SPA");
        result.setTenantId(1L);
        result.setUserType(UserTypeEnum.EMPLOYEE);
        result.setClientAuthenticationMethods("none");
        result.setAuthorizationGrantTypes("authorization_code");
        result.setScopes("openid,profile");
        result.setRedirectUris("https://client.example/callback");
        result.setHostedLoginMethods("EMAIL");
        result.setAllowedOrigins("https://client.example");
        return ProtocolTestFixtures.configure(result);
    }
}
