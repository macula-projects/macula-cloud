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
import dev.macula.cloud.iam.authentication.mfa.AdditionalAuthenticationRequiredException;
import dev.macula.cloud.iam.authentication.mfa.AuthenticationCheckpoint;
import dev.macula.cloud.iam.authentication.mfa.AuthenticationMethod;
import dev.macula.cloud.iam.authentication.mfa.MfaCompletionPolicy;
import dev.macula.cloud.iam.authentication.sms.SmsAuthenticationToken;
import dev.macula.cloud.iam.config.IamProtocolTest;
import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.message.sms.LoginSmsDispatcher;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import dev.macula.cloud.iam.protocol.oauth2.ProtocolTestFixtures;
import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2ClientRegistration;
import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2ClientRegistrationService;
import dev.macula.cloud.iam.service.account.ClientAwareAccountResolver;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;
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
 * 真安全链、真Redis和模板验证短信认证、授权码/OIDC及关闭策略；只隔离短信外发。
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
class HostedSmsLoginFlowTest {
    private static final String SMS = "13800000000";
    private static final String VERIFIER = "s".repeat(43);
    @Autowired
    private WebApplicationContext context;
    @MockitoBean
    private MfaCompletionPolicy completionPolicy;
    @MockitoBean
    private LoginSmsDispatcher fixtureSmsDispatcher;
    private MockMvc mvc;
    private OAuth2ClientRegistration client;
    private LoginSmsDispatcher dispatcher;

    /**
     * 固定服务端Client与唯一账号，SMS由Mock捕获，验证码状态走真实Redis。
     */
    @BeforeEach
    void setUp() {
        reset(completionPolicy);
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        dispatcher = context.getBean(LoginSmsDispatcher.class);
        clearInvocations(dispatcher);
        client = client("public");
        when(context.getBean(OAuth2ClientRegistrationService.class).getClientByClientId("public")).thenReturn(client);
        when(context.getBean(OAuth2ClientRegistrationService.class).getClientByClientId("public-other")).thenReturn(
                client("public-other"));
        var user = new SysUser();
        user.setId(42L);
        user.setUsername("fixture-user");
        user.setMobile(SMS);
        when(context.getBean(ClientAwareAccountResolver.class).findByMobile(eq(UserTypeEnum.EMPLOYEE), anyString()))
                .thenReturn(Optional.of(user));
    }

    /**
     * 短信成功建立Session，原PKCE和nonce贯穿到ID Token及UserInfo。
     */
    @Test
    void smsLoginCompletesPkceOidcAndRestoresPrincipal() throws Exception {
        MockHttpSession session = authorization("public");
        String code = send(session);
        var login = mvc.perform(
                        post("/login/sms").session(session).with(csrf()).param("phone", SMS).param("code", code))
                .andExpect(status().isFound()).andReturn();
        var security = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(security.getAuthentication()).isInstanceOf(SmsAuthenticationToken.class);
        assertThat(security.getAuthentication().getCredentials()).isNull();
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
        assertThat(jwt.getClaimAsString("nonce")).isEqualTo("sms-nonce");
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
        mvc.perform(post("/login/sms").session(authorization("public")).with(csrf()).param("phone", SMS)
                        .param("code", code))
                .andExpect(redirectedUrl("https://iam.example/login?error=authentication_rejected"));
    }

    /**
     * 短信一次认证仍需经过MFA发布门禁，拒绝时不建立会话。
     */
    @Test
    void smsDenialDoesNotEstablishSession() throws Exception {
        var session = authorization("public");
        String code = send(session);
        doThrow(new AdditionalAuthenticationRequiredException())
                .when(completionPolicy).check(any());
        var response = mvc.perform(
                        post("/login/sms").session(session).with(csrf()).param("phone", SMS).param("code", code))
                .andExpect(status().isServiceUnavailable()).andReturn();
        verify(completionPolicy).check(argThat(completion ->
                completion.method() == AuthenticationMethod.SMS
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
        mvc.perform(post("/api/v1/sms-code").session(session).param("phone", SMS))
                .andExpect(status().isForbidden());
        client.setHostedLoginMethods("PASSWORD,EMAIL");
        mvc.perform(post("/api/v1/sms-code").session(session).with(csrf()).param("phone", SMS))
                .andExpect(status().isForbidden());
        mvc.perform(post("/login/sms").session(session).with(csrf()).param("phone", SMS).param("code", "123456"))
                .andExpect(redirectedUrl("https://iam.example/login?error=authentication_unavailable"));
        verifyNoInteractions(dispatcher);
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
        mvc.perform(post("/login/sms").session(second).with(csrf()).param("phone", SMS).param("code", code))
                .andExpect(redirectedUrl("https://iam.example/login?error=authentication_rejected"));
        mvc.perform(
                        post("/login/sms").session(first).with(csrf()).param("phone", SMS).param("code", code,
                                "000000"))
                .andExpect(redirectedUrl("https://iam.example/login?error=authentication_rejected"));
        mvc.perform(post("/login/sms").session(first).with(csrf()).param("phone", SMS).param("code", code))
                .andExpect(status().isFound());
        assertThat(
                ((SecurityContext) first.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                        .getAuthentication().isAuthenticated()).isTrue();
    }

    /**
     * 发码到合成地址，捕获本次随机验证码而不写入日志。
     */
    private String send(MockHttpSession session) throws Exception {
        // 各测试使用独立Redis状态键，避免发送窗口互相影响。
        context.getBean(StringRedisTemplate.class).delete(
                context.getBean(StringRedisTemplate.class)
                        .keys("iam:sms:send:*"));
        mvc.perform(post("/api/v1/sms-code").session(session).with(csrf()).param("phone", SMS))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("submitted"));
        var value = ArgumentCaptor.forClass(String.class);
        verify(dispatcher).send(eq(SMS), value.capture());
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
                                .queryParam("scope", "openid profile").queryParam("state", "sms-state")
                                .queryParam("nonce", "sms-nonce").queryParam("code_challenge", challenge)
                                .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection()).andReturn().getRequest().getSession(false);
    }

    /**
     * 只提供当前用例允许的SMS方式，不依赖生产Client或真实账号。
     */
    private OAuth2ClientRegistration client(String id) {
        var result = new OAuth2ClientRegistration();
        result.setClientId(id);
        result.setClientName("SMS fixture");
        result.setApplicationType("SPA");
        result.setTenantId(1L);
        result.setUserType(UserTypeEnum.EMPLOYEE);
        result.setClientAuthenticationMethods("none");
        result.setAuthorizationGrantTypes("authorization_code");
        result.setScopes("openid,profile");
        result.setRedirectUris("https://client.example/callback");
        result.setHostedLoginMethods("SMS");
        result.setAllowedOrigins("https://client.example");
        return ProtocolTestFixtures.configure(result);
    }
}
