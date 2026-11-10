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
package dev.macula.cloud.iam.config;

import dev.macula.cloud.iam.authentication.mfa.AdditionalAuthenticationRequiredException;
import dev.macula.cloud.iam.authentication.mfa.AuthenticationCheckpoint;
import dev.macula.cloud.iam.authentication.mfa.AuthenticationCompletionContext;
import dev.macula.cloud.iam.authentication.mfa.MfaCompletionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * 真实安全过滤链、真实签名和专用Redis验证MFA拒绝时不会发布认证成果。
 * 账号及平台适配仍为既有fixture，不代表完成平台或MFA因子联调。
 * @author Rain
 * @since 6.1.0
 */
@SpringJUnitConfig(IamProtocolTest.App.class)
@WebAppConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("local")
@TestPropertySource(properties = {"macula.cloud.iam.issuer-uri=https://iam.example",
    "macula.cloud.iam.sms-verification.digest-key=fixture-digest-key",
    "macula.cloud.iam.email-verification.digest-key=fixture-digest-key",
    "macula.cloud.iam.login-captcha.digest-key=fixture-digest-key"})
class MfaProtocolIT {
    @Autowired private WebApplicationContext context;
    @MockitoBean private MfaCompletionPolicy policy;
    @MockitoBean private dev.macula.cloud.iam.authentication.github.GithubClient github;
    @MockitoBean private dev.macula.cloud.iam.authentication.wecom.WecomClient wecom;
    @MockitoBean private dev.macula.cloud.iam.authentication.feishu.FeishuClient feishu;
    @MockitoBean private dev.macula.cloud.iam.authentication.dingtalk.DingtalkClient dingtalk;
    @MockitoBean private dev.macula.cloud.iam.authentication.wechat.WechatClient wechat;
    @MockitoBean private dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyIdentityResolutionService identities;

    private IamProtocolTest fixture;
    private MockMvc mvc;

    /** 复用已存在的用户、Client和隔离Redis素材，避免建立另一套认证服务。 */
    @BeforeEach
    void setup() {
        reset(policy);
        fixture = new IamProtocolTest();
        fixture.context = context;
        fixture.setup();
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }
    /** 密码已验证后拒绝，不能留下完整SecurityContext或Remember-Me。 */
    @Test
    void passwordDenialDoesNotEstablishSession() throws Exception {
        doThrow(new AdditionalAuthenticationRequiredException()).when(policy).check(any());
        var response = mvc.perform(post("/login").with(csrf()).param("username", "fixture-user")
            .param("password", "fixture-password")).andExpect(status().isServiceUnavailable()).andReturn();
        var session = response.getRequest().getSession(false);
        assertTrue(session == null || session.getAttribute("SPRING_SECURITY_CONTEXT") == null);
        assertNull(response.getResponse().getCookie("remember-me"));
    }
    /** opaque/JWT与ID Token均在生成之前被拦截，原授权不能留下半份Token。 */
    @Test
    void codeExchangeDenialLeavesNoTokens() throws Exception {
        for (String client : new String[] {"public", "jwt"}) {
            reset(policy);
            String code = fixture.authorize(client, fixture.login());
            doThrow(new IllegalStateException("private-policy-detail")).when(policy).check(any());
            mvc.perform(fixture.exchange(client, code, fixture.verifier)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("server_error"))
                .andExpect(jsonPath("$.access_token").doesNotExist())
                .andExpect(jsonPath("$.id_token").doesNotExist())
                .andExpect(jsonPath("$.refresh_token").doesNotExist());
            var stored = context.getBean(OAuth2AuthorizationService.class).findByToken(code, new OAuth2TokenType("code"));
            assertTrue(stored != null);
            assertNull(stored.getAccessToken());
            assertNull(stored.getRefreshToken());
        }
    }
    /** 即使已在内存生成Access Token，后续ID Token门禁拒绝也不能落库半份授权。 */
    @Test
    void lateTokenDenialDoesNotPersistPartialAuthorization() throws Exception {
        for (String client : new String[] {"public", "jwt"}) {
            reset(policy);
            String code = fixture.authorize(client, fixture.login());
            var checks = new java.util.concurrent.atomic.AtomicInteger();
            doAnswer(call -> {
                if (checks.incrementAndGet() == 2) { throw new AdditionalAuthenticationRequiredException(); }
                return null;
            }).when(policy).check(any());
            mvc.perform(fixture.exchange(client, code, fixture.verifier)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("server_error"))
                .andExpect(jsonPath("$.access_token").doesNotExist())
                .andExpect(jsonPath("$.id_token").doesNotExist());
            assertTrue(checks.get() >= 2);
            var stored = context.getBean(OAuth2AuthorizationService.class).findByToken(code, new OAuth2TokenType("code"));
            assertTrue(stored != null);
            assertNull(stored.getAccessToken());
            assertNull(stored.getRefreshToken());
        }
    }

    /** 已登录SSO授权也必须经过发布栅栏，不能只拦登录页。 */
    @Test
    void existingSsoCannotPublishNewAuthorizationCode() throws Exception {
        MockHttpSession session = fixture.login();
        fixture.authorize("public", session);
        org.mockito.Mockito.clearInvocations(policy);
        doAnswer(call -> {
            AuthenticationCompletionContext completion = call.getArgument(0);
            if (completion.checkpoint() == AuthenticationCheckpoint.AUTHORIZATION_CODE) {
                throw new AdditionalAuthenticationRequiredException();
            }
            return null;
        }).when(policy).check(any());
        var result = mvc.perform(get("/oauth2/authorize").session(session).queryParam("response_type", "code")
            .queryParam("client_id", "public").queryParam("redirect_uri", "https://client.example/callback")
            .queryParam("scope", "openid profile").queryParam("state", "mfa-test-state").queryParam("nonce", "fixture-nonce")
            .queryParam("code_challenge", fixture.challenge()).queryParam("code_challenge_method", "S256")).andReturn();
        String location = result.getResponse().getRedirectedUrl();
        // Spring 7授权端点对无可续转上下文的发布失败使用sendError，不返回授权码。
        org.mockito.Mockito.verify(policy).check(org.mockito.ArgumentMatchers.argThat(
            completion -> completion.checkpoint() == AuthenticationCheckpoint.AUTHORIZATION_CODE));
        assertEquals(400, result.getResponse().getStatus());
        assertTrue(result.getResponse().getErrorMessage() != null
            && result.getResponse().getErrorMessage().contains("server_error"), result.getResponse().getErrorMessage());
        assertFalse(location != null && location.contains("code="));
        org.mockito.Mockito.verify(policy).check(org.mockito.ArgumentMatchers.argThat(
            completion -> completion.checkpoint() == AuthenticationCheckpoint.AUTHORIZATION_CODE));
        assertTrue(session.getAttribute("SPRING_SECURITY_CONTEXT") != null);
    }
    /** MFA拒绝刷新签发时仍允许撤销原有令牌。 */
    @Test
    void refreshDeniedButRevocationRemainsAvailable() throws Exception {
        String code = fixture.authorize("compat", fixture.login());
        var response = mvc.perform(fixture.exchange("compat", code, fixture.verifier))
            .andExpect(status().isOk()).andReturn();
        var tokens = JsonMapper.builder().build().readTree(response.getResponse().getContentAsString());
        doThrow(new AdditionalAuthenticationRequiredException()).when(policy).check(any());
        mvc.perform(post("/oauth2/token").with(httpBasic("compat", "fixture-secret"))
            .param("grant_type", "refresh_token").param("refresh_token", tokens.path("refresh_token").asText()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("server_error"))
            .andExpect(jsonPath("$.access_token").doesNotExist());
        reset(policy);
        doThrow(new AdditionalAuthenticationRequiredException()).when(policy).check(any());
        mvc.perform(post("/oauth2/revoke").with(httpBasic("compat", "fixture-secret"))
            .param("token", tokens.path("access_token").asText())).andExpect(status().isOk());
        verifyNoInteractions(policy);
    }
    /** 短信完成核验后仍经过相同会话门禁，不需要开启短信Token grant。 */
    @Test
    void hostedSmsDenialDoesNotCreateSso() throws Exception {
        var client = context.getBean(dev.macula.cloud.iam.service.support.SysOAuth2ClientService.class)
            .getClientByClientId("public");
        client.setHostedLoginMethods("SMS");
        org.mockito.Mockito.when(context.getBean(dev.macula.cloud.iam.authentication.sms.SmsService.class)
            .verifyCode("13800000000", "fixture-code", "public")).thenReturn(true);
        var account = new dev.macula.cloud.iam.pojo.entity.SysUser();
        account.setUsername("fixture-user");
        org.mockito.Mockito.when(context.getBean(dev.macula.cloud.iam.service.userdetails.ClientAwareAccountResolver.class)
            .findByMobile(dev.macula.cloud.iam.enums.UserTypeEnum.EMPLOYEE, "13800000000")).thenReturn(java.util.Optional.of(account));
        doThrow(new AdditionalAuthenticationRequiredException()).when(policy).check(any());
        var result = mvc.perform(post("/login/sms").session(fixture.browserAuthorizationSession()).with(csrf())
            .param("phone", "13800000000").param("code", "fixture-code"))
            .andExpect(status().isServiceUnavailable()).andReturn();
        assertNull(result.getRequest().getSession().getAttribute("SPRING_SECURITY_CONTEXT"));
        org.mockito.Mockito.verify(policy).check(org.mockito.ArgumentMatchers.argThat(completion ->
            completion.checkpoint() == AuthenticationCheckpoint.BROWSER_SESSION
                && completion.method() == dev.macula.cloud.iam.authentication.mfa.AuthenticationMethod.SMS));
    }

    /** 五个平台网页回调都先执行MFA，再建立完整会话；上游Code交换仍是Mock。 */
    @Test
    void everyBrowserProviderIsCheckedBeforeSessionPublication() throws Exception {
        var user = user();
        org.mockito.Mockito.when(identities.resolve(any(), any())).thenReturn(user);
        org.mockito.Mockito.when(github.authorizationUri(any(), any(), any())).thenAnswer(call -> authorization(call.getArgument(1)));
        org.mockito.Mockito.when(wecom.authorizationUri(any(), any(), any(), any())).thenAnswer(call -> authorization(call.getArgument(1)));
        org.mockito.Mockito.when(feishu.authorizationUri(any(), any(), any(), any())).thenAnswer(call -> authorization(call.getArgument(1)));
        org.mockito.Mockito.when(dingtalk.authorizationUri(any(), any(), any(), any())).thenAnswer(call -> authorization(call.getArgument(1)));
        org.mockito.Mockito.when(wechat.authorizationUri(any(), any(), any(), any())).thenAnswer(call -> authorization(call.getArgument(1)));
        org.mockito.Mockito.when(github.fetchSubject(any(), any(), any())).thenReturn("fixture-subject");
        org.mockito.Mockito.when(wecom.fetchUserId(any(), any())).thenReturn("fixture-member");
        org.mockito.Mockito.when(dingtalk.fetchUserId(any(), any(), any())).thenReturn("fixture-member");
        org.mockito.Mockito.when(wechat.fetchOpenId(any(), any(), any())).thenReturn("fixture-open-id");
        org.mockito.Mockito.when(feishu.fetchIdentity(any(), any(), any(), any(), any())).thenAnswer(call ->
            dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity.of(call.getArgument(0),
                dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity.BROWSER,
                dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity.OPEN_ID, "fixture-open-id", java.util.Map.of()));
        doThrow(new AdditionalAuthenticationRequiredException()).when(policy).check(any());
        for (String provider : new String[] {"github", "wecom", "feishu", "dingtalk", "wechat"}) {
            var session = fixture.browserAuthorizationSession();
            String start = "/login/" + provider + ("github".equals(provider) ? "" : "/browser");
            var begun = mvc.perform(get(start).session(session)).andExpect(status().isFound()).andReturn();
            String state = query(begun.getResponse().getRedirectedUrl(), "state");
            var result = mvc.perform(get("/login/" + provider).session(session).queryParam("state", state)
                .queryParam("code", "fixture-provider-code")).andExpect(status().isServiceUnavailable()).andReturn();
            assertNull(session.getAttribute("SPRING_SECURITY_CONTEXT"));
            assertNull(result.getResponse().getCookie("remember-me"));
        }
        org.mockito.Mockito.verify(policy, org.mockito.Mockito.times(5)).check(org.mockito.ArgumentMatchers.argThat(completion ->
            completion.checkpoint() == AuthenticationCheckpoint.BROWSER_SESSION
                && completion.method() == dev.macula.cloud.iam.authentication.mfa.AuthenticationMethod.EXTERNAL
                && completion.registrationId() != null));
    }

    /** 浏览器绑定已完成但MFA拒绝时仍不能保存SSO；SDK绑定不建立SSO或伪造MFA完成。 */
    @Test
    void bindingSessionIsGuardedAndSdkBindingDoesNotCreateSso() throws Exception {
        var binding = context.getBean(dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyBindingService.class);
        var external = new dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyLoginContext("GITHUB", "BROWSER",
            "public", java.time.Instant.now(), "/", 1L, "GITHUB_OAUTH", 1L,
            dev.macula.cloud.iam.enums.UserTypeEnum.EMPLOYEE, 0L, "GITHUB_OAUTH_V1", "https://iam.example/login/github");
        org.mockito.Mockito.when(binding.bind(any(), any(), any(), any(), any(), any(), any())).thenReturn(
            new dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyBindingService.BoundAccount(user(), "/", false,
                external, "password"));
        doThrow(new AdditionalAuthenticationRequiredException()).when(policy).check(any());
        var blocked = mvc.perform(post("/login/bind").with(csrf()).param("username", "fixture-user")
            .param("method", "password").param("password", "fixture-password"))
            .andExpect(status().isServiceUnavailable()).andReturn();
        assertNull(blocked.getRequest().getSession().getAttribute("SPRING_SECURITY_CONTEXT"));
        org.mockito.Mockito.verify(policy).check(org.mockito.ArgumentMatchers.argThat(completion ->
            completion.checkpoint() == AuthenticationCheckpoint.BINDING_SESSION
                && completion.method() == dev.macula.cloud.iam.authentication.mfa.AuthenticationMethod.PASSWORD));
        reset(policy);
        org.mockito.Mockito.when(binding.bind(any(), any(), any(), any(), any(), any(), any())).thenReturn(
            new dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyBindingService.BoundAccount(user(), "/", true,
                external, "password"));
        var sdk = mvc.perform(post("/login/bind").with(csrf()).param("username", "fixture-user")
            .param("method", "password").param("password", "fixture-password")).andExpect(status().isOk()).andReturn();
        assertNull(sdk.getRequest().getSession().getAttribute("SPRING_SECURITY_CONTEXT"));
        verifyNoInteractions(policy);
    }

    /** Consent确认后的新授权码同样受持久化发布栅栏约束。 */
    @Test
    void consentCannotPublishAuthorizationCodeWhenMfaDenies() throws Exception {
        var session = fixture.login();
        var consents = context.getBean(org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService.class);
        var previous = consents.findById("public", "fixture-user");
        if (previous != null) { consents.remove(previous); }
        var begun = mvc.perform(get("/oauth2/authorize").session(session).queryParam("response_type", "code")
            .queryParam("client_id", "public").queryParam("redirect_uri", "https://client.example/callback")
            .queryParam("scope", "openid profile").queryParam("state", "consent-state").queryParam("nonce", "fixture-nonce")
            .queryParam("code_challenge", fixture.challenge()).queryParam("code_challenge_method", "S256")).andReturn();
        String location = begun.getResponse().getRedirectedUrl();
        assertTrue(location != null && location.contains("/oauth2/consent"));
        String state = query(location, "state");
        doThrow(new AdditionalAuthenticationRequiredException()).when(policy).check(any());
        var denied = mvc.perform(post("/oauth2/authorize").session(session).param("client_id", "public")
            .param("state", state).param("scope", "profile")).andExpect(status().isBadRequest()).andReturn();
        assertTrue(denied.getResponse().getErrorMessage().contains("server_error"));
        var stored = context.getBean(OAuth2AuthorizationService.class).findByToken(state, new OAuth2TokenType("state"));
        assertTrue(stored != null);
        assertNull(stored.getToken(org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode.class));
        assertNull(stored.getAccessToken());
    }


    /** SDK直接grant也不能绕过用户签发门禁，不返回302、ID Token或半份授权。 */
    @Test
    void sdkDirectGrantDenialIsTokenErrorWithoutSso() throws Exception {
        var type = dev.macula.cloud.system.enums.ExternalCodeType.WECHAT_MINIAPP_LOGIN_CODE;
        var source = new dev.macula.cloud.iam.pojo.entity.SysIdentityProvider();
        source.setId(7L);
        source.setProviderType("WECHAT");
        source.setSourceType("WECHAT_MINIAPP");
        source.setVersion(0L);
        source.setEnabled(true);
        source.setAppId("fixture-miniapp");
        source.setAppSecret("fixture-secret");
        source.setConfigJson("{\"appId\":\"fixture-miniapp\"}");
        org.mockito.Mockito.when(context.getBean(dev.macula.cloud.iam.mapper.SysIdentityProviderMapper.class).selectById(7L))
            .thenReturn(source);
        var client = context.getBean(dev.macula.cloud.iam.service.support.SysOAuth2ClientService.class).getClientByClientId("public");
        var settings = dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration.settings(client.getProtocolConfigurations().get(0));
        settings.setAuthorizationGrantTypes(dev.macula.cloud.system.enums.ExternalCodeType.GRANT_TYPE);
        settings.setRedirectUris("");
        client.setBrowserLoginProviderTypes("");
        client.setBrowserProviderBindings("{}");
        var binding = new dev.macula.cloud.iam.pojo.entity.SysOAuth2ClientExternalProvider();
        binding.setCodeType(type.name());
        binding.setIdentityProviderId(7L);
        org.mockito.Mockito.when(context.getBean(dev.macula.cloud.iam.mapper.SysOAuth2ClientExternalProviderMapper.class)
            .selectList(any())).thenReturn(java.util.List.of(binding));
        var cfg = context.getBean(dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService.class)
            .requireEnabledById(7L, "WECHAT");
        var verification = new dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeVerification(
            dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity.of(cfg, type.name(),
                dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity.OPEN_ID, "fixture-open-id", java.util.Map.of()));
        var verifier = org.mockito.Mockito.mock(dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeVerifier.class);
        org.mockito.Mockito.when(context.getBean(dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeVerifierRegistry.class)
            .require(type)).thenReturn(verifier);
        org.mockito.Mockito.when(verifier.verify(type, 7L, "fixture-code")).thenReturn(verification);
        var user = user();
        var principal = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
            user, null, user.getAuthorities());
        principal.setDetails(new java.util.HashMap<>(java.util.Map.of(
            dev.macula.cloud.iam.authentication.mfa.MfaCompletionService.SOURCE_PROOF,
            dev.macula.cloud.iam.authentication.mfa.MfaCompletionService.verifiedProof(verification.identity()))));
        org.mockito.Mockito.when(context.getBean(dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeAccountResolver.class)
            .resolve(any(), org.mockito.ArgumentMatchers.eq(type), any())).thenReturn(principal);
        doThrow(new AdditionalAuthenticationRequiredException()).when(policy).check(any());
        for (String format : new String[] {"reference", "self-contained"}) {
            settings.setTokenFormat(format);
            var result = mvc.perform(post("/oauth2/token").param("client_id", "public").param("grant_type", dev.macula.cloud.system.enums.ExternalCodeType.GRANT_TYPE)
                .param("code_type", type.name()).param("code", "fixture-code").param("scope", "openid profile")
                .param("nonce", "fixture-nonce")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("server_error"))
                .andExpect(jsonPath("$.access_token").doesNotExist()).andExpect(jsonPath("$.id_token").doesNotExist())
                .andExpect(jsonPath("$.refresh_token").doesNotExist()).andReturn();
            assertNull(result.getResponse().getRedirectedUrl());
            assertNull(result.getRequest().getSession(false));
        }
    }

    /** 真实第三方会话可规范化写入授权，私有来源证明不进入ID Token公开claims。 */
    @Test
    void browserSourceProofSurvivesAuthorizationWithoutLeakingClaims() throws Exception {
        org.mockito.Mockito.when(github.authorizationUri(any(), any(), any())).thenAnswer(call -> authorization(call.getArgument(1)));
        org.mockito.Mockito.when(github.fetchSubject(any(), any(), any())).thenReturn("fixture-subject");
        org.mockito.Mockito.when(identities.resolve(any(), any())).thenReturn(user());
        var session = fixture.browserAuthorizationSession();
        var start = mvc.perform(get("/login/github").session(session)).andExpect(status().isFound()).andReturn();
        mvc.perform(get("/login/github").session(session).queryParam("state", query(start.getResponse().getRedirectedUrl(), "state"))
            .queryParam("code", "fixture-provider-code")).andExpect(status().isFound());
        assertTrue(session.getAttribute("SPRING_SECURITY_CONTEXT") != null);
        String code = fixture.authorize("public", session);
        var result = mvc.perform(fixture.exchange("public", code, fixture.verifier)).andExpect(status().isOk()).andReturn();
        var response = JsonMapper.builder().build().readTree(result.getResponse().getContentAsString());
        var identity = context.getBean(org.springframework.security.oauth2.jwt.JwtDecoder.class).decode(response.path("id_token").asText());
        assertFalse(identity.getClaims().containsKey(dev.macula.cloud.iam.authentication.mfa.MfaCompletionService.SOURCE_PROOF));
        var stored = context.getBean(OAuth2AuthorizationService.class).findByToken(response.path("access_token").asText(), OAuth2TokenType.ACCESS_TOKEN);
        java.util.Map<String,Object> proof = stored.getAttribute(dev.macula.cloud.iam.authentication.mfa.MfaCompletionService.SOURCE_PROOF);
        assertEquals(1L, ((Number) proof.get("registrationId")).longValue());
    }

    /** 机器Client的标准client_credentials仍按资源Scope签发，不被用户MFA拦截。 */
    @Test
    void clientCredentialsDoesNotRunUserMfa() throws Exception {
        var row = new dev.macula.cloud.iam.pojo.entity.SysOAuth2Client();
        row.setClientId("machine");
        row.setClientName("machine");
        row.setPurpose("APPLICATION");
        row.setApplicationType("SERVICE");
        row.setClientSecret("{bcrypt}" + new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("fixture-secret"));
        row.setClientAuthenticationMethods("client_secret_basic");
        row.setAuthorizationGrantTypes("client_credentials");
        row.setScopes("api.read");
        row.setEnabledProtocols("OAUTH2_1");
        row.setDefaultProtocol("OAUTH2_1");
        row.setConfigurationStatus("ENABLED");
        row.setTokenFormat("reference");
        dev.macula.cloud.iam.protocol.oauth2.ProtocolTestFixtures.configure(row);
        org.mockito.Mockito.when(context.getBean(dev.macula.cloud.iam.service.support.SysOAuth2ClientService.class)
            .getClientByClientId("machine")).thenReturn(row);
        doThrow(new AdditionalAuthenticationRequiredException()).when(policy).check(any());
        mvc.perform(post("/oauth2/token").with(httpBasic("machine", "fixture-secret"))
            .param("grant_type", "client_credentials").param("scope", "api.read"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.access_token").exists())
            .andExpect(jsonPath("$.id_token").doesNotExist());
        verifyNoInteractions(policy);
    }

    /** 全部认证方式共用的稳定测试账号，不含真实账号或平台凭证。 */
    private dev.macula.cloud.iam.service.userdetails.SysUserDetails user() {
        var user = new dev.macula.cloud.iam.service.userdetails.SysUserDetails();
        user.setUserId(42L);
        user.setTenantId(1L);
        user.setUsername("fixture-user");
        user.setEnabled(true);
        return user;
    }
    /** 上游返回仅用于把真实state带回测试，不模拟平台Token。 */
    private String authorization(String state) { return "https://provider.example/authorize?state=" + state; }
    /** 只读取可信测试响应中的查询值，不输出Code或票据。 */
    private String query(String uri, String name) {
        String encoded = org.springframework.web.util.UriComponentsBuilder.fromUriString(uri).build().getQueryParams().getFirst(name);
        return encoded == null ? null : java.net.URLDecoder.decode(encoded, java.nio.charset.StandardCharsets.UTF_8);
    }

}
