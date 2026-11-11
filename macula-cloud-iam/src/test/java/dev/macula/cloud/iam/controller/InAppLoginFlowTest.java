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

import java.time.Instant;
import java.util.List;

import dev.macula.cloud.iam.authentication.inapp.InAppAuthorizationRequestFilter;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyAuthenticationToken;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyLoginContext;
import dev.macula.cloud.iam.config.IamProtocolTest;
import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.iam.pojo.entity.SysIdentityProvider;
import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.service.client.SysOAuth2ClientService;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.server.authorization.web.OAuth2AuthorizationEndpointFilter;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * 使用真实SAS安全链证明普通会话不绕过端内平台认证，保留回调校验。
 *
 * @author Rain
 * @since 6.1.0
 */
@SpringJUnitConfig(IamProtocolTest.App.class)
@WebAppConfiguration
@ActiveProfiles("local")
@TestPropertySource(properties = {"macula.cloud.iam.issuer-uri=https://iam.example",
        "macula.cloud.iam.sms-verification.digest-key=fixture-digest-key",
        "macula.cloud.iam.email-verification.digest-key=fixture-digest-key",
        "macula.cloud.iam.login-captcha.digest-key=fixture-digest-key"})
class InAppLoginFlowTest {
    @Autowired
    private WebApplicationContext context;
    private MockMvc mvc;
    private SysOAuth2Client client;

    /**
     * 当前端内注册与协议均由服务端权威测试快照提供，厂商无外部调用。
     */
    @BeforeEach
    void setup() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        client = new SysOAuth2Client();
        client.setClientId("public");
        client.setApplicationType("IN_APP");
        client.setAuthorizationVersion(7L);
        client.setClientAuthenticationMethods("none");
        client.setAuthorizationGrantTypes("authorization_code");
        client.setScopes("openid,profile");
        client.setRedirectUris("https://client.example/callback");
        client.setTenantId(1L);
        client.setUserType(UserTypeEnum.EMPLOYEE);
        client.setBrowserLoginProviderTypes("WECOM");
        client.setBrowserProviderBindings("{\"WECOM\":2}");
        client.setBrowserCapabilityBindings("{\"WECOM_WEBVIEW\":2}");
        client.setHostedLoginMethods("");
        dev.macula.cloud.iam.protocol.oauth2.ProtocolTestFixtures.configure(client);
        when(context.getBean(SysOAuth2ClientService.class).getClientByClientId("public")).thenReturn(client);
        var source = new SysIdentityProvider();
        source.setId(2L);
        source.setProviderType("WECOM");
        source.setSourceType("WECOM_WEB");
        source.setEnabled(true);
        source.setVisible(true);
        source.setVersion(0L);
        source.setAppId("corp");
        source.setAppSecret("fixture-secret");
        source.setConfigJson("{\"corpId\":\"corp\",\"agentId\":\"agent\"}");
        when(context.getBean(SysIdentityProviderMapper.class).selectById(2L)).thenReturn(source);
    }

    /**
     * 验证实际过滤链，不能只由单测构造顺序推断。
     */
    @Test
    void filterRunsAfterContextAndBeforeSasAuthorization() {
        List<jakarta.servlet.Filter> filters = context.getBean(FilterChainProxy.class).getFilters("/oauth2/authorize");
        assertTrue(index(filters, SecurityContextHolderFilter.class) <
                index(filters, InAppAuthorizationRequestFilter.class));
        assertTrue(index(filters, InAppAuthorizationRequestFilter.class) <
                index(filters, OAuth2AuthorizationEndpointFilter.class));
    }

    /**
     * 无会话及普通密码SSO都需要固定平台授权，错误容器只展示专用错误。
     */
    @Test
    void ordinarySsoCannotSkipPlatformAndIsNotErased() throws Exception {
        var withoutSso = mvc.perform(authorize()).andExpect(status().is3xxRedirection()).andReturn();
        mvc.perform(get("/login").session((MockHttpSession) withoutSso.getRequest().getSession(false))
                        .header("User-Agent", "MicroMessenger wxwork"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login/wecom/webview"));
        var authentication = UsernamePasswordAuthenticationToken.authenticated(user(), "", List.of());
        var session = session(authentication);
        var original = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        mvc.perform(authorize().session(session)).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/login")));
        assertSame(original, session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
        mvc.perform(get("/login").session(session).header("User-Agent", "MicroMessenger wxwork"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login/wecom/webview"));
        mvc.perform(get("/login").session(session).header("User-Agent", "Chrome"))
                .andExpect(status().isOk()).andExpect(view().name("in-app-login-error"));
        mvc.perform(
                        get("/login").session(session).header("User-Agent", "MicroMessenger wxwork").param("error",
                                "rejected"))
                .andExpect(status().isOk()).andExpect(view().name("in-app-login-error"));
    }

    /**
     * 即使带有效平台证明，非法回调仍交给框架拒绝，不能向攻击者跳转。
     */
    @Test
    void validProofUsesSasAndInvalidRedirectNeverLeavesIam() throws Exception {
        // 使用来源实际Flow，防止测试凭空假设平台合同。
        var source = context.getBean(dev.macula.cloud.iam.service.identity.IdentityProviderConfigService.class)
                .requireEnabledById(2L, "WECOM").forCapability("WECOM_WEBVIEW");
        var proof = new ThirdPartyLoginContext("WECOM", "WEBVIEW", "public", Instant.now(),
                "https://iam.example/oauth2/authorize",
                2L, "WECOM_WEB", 1L, UserTypeEnum.EMPLOYEE, 0L, source.flow().value(), null, "WECOM_WEBVIEW", 7L);
        var authentication = ThirdPartyAuthenticationToken.authenticated("WECOM", user(), proof);
        var valid = mvc.perform(authorize().session(session(authentication))).andExpect(status().is3xxRedirection())
                .andReturn();
        assertTrue(valid.getResponse().getRedirectedUrl().startsWith("https://client.example/callback?code="));
        var invalid = authorize("https://attacker.example/callback");
        mvc.perform(invalid.session(session(authentication))).andExpect(status().isBadRequest());
        client.setAuthorizationVersion(8L);
        mvc.perform(authorize().session(session(authentication))).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/login")));
    }

    /**
     * 平台成果经原授权码端点换Token；客户端或来源撤销会使在途Code失效。
     */
    @Test
    void tokenExchangeRechecksClientAndSourceVersions() throws Exception {
        client.setClientSecret("{noop}retained-fixture");
        String first = platformCode();
        mvc.perform(exchange(first).with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic(
                                "public", "retained-fixture")))
                .andExpect(status().isUnauthorized());
        mvc.perform(exchange(first)).andExpect(status().isOk()).andExpect(jsonPath("$.access_token").exists());
        String staleClient = platformCode();
        client.setAuthorizationVersion(8L);
        mvc.perform(exchange(staleClient)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.access_token").doesNotExist());
        client.setAuthorizationVersion(7L);
        String staleSource = platformCode();
        context.getBean(SysIdentityProviderMapper.class).selectById(2L).setVersion(1L);
        mvc.perform(exchange(staleSource)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.access_token").doesNotExist());
    }

    /**
     * 同意页打开之后客户端变更，POST也不能沿普通会话发布授权码。
     */
    @Test
    void consentSubmissionRejectsChangedClientProof() throws Exception {
        dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration.settings(client.getProtocolConfigurations().get(0))
                .setRequireAuthorizationConsent(true);
        var source = context.getBean(dev.macula.cloud.iam.service.identity.IdentityProviderConfigService.class)
                .requireEnabledById(2L, "WECOM").forCapability("WECOM_WEBVIEW");
        var proof = new ThirdPartyLoginContext("WECOM", "WEBVIEW", "public", Instant.now(),
                "https://iam.example/oauth2/authorize",
                2L, "WECOM_WEB", 1L, UserTypeEnum.EMPLOYEE, 0L, source.flow().value(), null, "WECOM_WEBVIEW", 7L);
        var user = user();
        user.setUsername("consent-user");
        var session = session(ThirdPartyAuthenticationToken.authenticated("WECOM", user, proof));
        var consent = mvc.perform(authorize().session(session)).andExpect(status().is3xxRedirection()).andReturn();
        var location = consent.getResponse().getRedirectedUrl();
        assertTrue(location.contains("/oauth2/consent"));
        String state = org.springframework.web.util.UriComponentsBuilder.fromUriString(location).build()
                .getQueryParams().getFirst("state");
        client.setAuthorizationVersion(8L);
        mvc.perform(post("/oauth2/authorize").session(session).param("client_id", "public").param("state", state)
                        .param("scope", "profile"))
                .andExpect(result -> {
                    var redirect = result.getResponse().getRedirectedUrl();
                    assertFalse(redirect != null && redirect.startsWith("https://client.example/callback?code="));
                    assertTrue(
                            result.getResponse().getStatus() == 400 || redirect != null && redirect.contains("/login"));
                });
    }

    /**
     * 端内SavedRequest不能成为密码/短信标准页和发码的入口。
     */
    @Test
    void forbidsHostedPasswordAndSmsEvenWithOrdinarySso() throws Exception {
        var session = session(UsernamePasswordAuthenticationToken.authenticated(user(), "", List.of()));
        mvc.perform(authorize().session(session)).andExpect(status().is3xxRedirection());
        mvc.perform(post("/api/v1/sms-code").session(session)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .param("phone", "13800000000")).andExpect(status().isForbidden());
        mvc.perform(post("/login").session(session)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .param("username", "fixture-user").param("password", "fixture-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("error=")));
    }

    /**
     * 测试平台证明只由服务端认证器构造；实际第三方认证由另组Mock适配器测试覆盖。
     */
    private String platformCode() throws Exception {
        var source = context.getBean(dev.macula.cloud.iam.service.identity.IdentityProviderConfigService.class)
                .requireEnabledById(2L, "WECOM").forCapability("WECOM_WEBVIEW");
        var proof = new ThirdPartyLoginContext("WECOM", "WEBVIEW", "public", Instant.now(),
                "https://iam.example/oauth2/authorize",
                2L, "WECOM_WEB", 1L, UserTypeEnum.EMPLOYEE, 0L, source.flow().value(), null, "WECOM_WEBVIEW", 7L);
        var result = mvc.perform(
                        authorize().session(session(ThirdPartyAuthenticationToken.authenticated("WECOM", user(),
                                proof))))
                .andExpect(status().is3xxRedirection()).andReturn();
        var code = org.springframework.web.util.UriComponentsBuilder.fromUriString(
                result.getResponse().getRedirectedUrl()).build().getQueryParams().getFirst("code");
        assertNotNull(code);
        return code;
    }

    /**
     * 原生授权码PKCE换码，不增加端内专用Token接口。
     */
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder exchange(String code) {
        return post("/oauth2/token").param("grant_type", "authorization_code").param("client_id", "public")
                .param("redirect_uri", "https://client.example/callback").param("code", code)
                .param("code_verifier", "v".repeat(43));
    }

    /**
     * 平台证明绑定真实本地用户，不使用普通字符串principal。
     */
    private SysUserDetails user() {
        var user = new SysUserDetails();
        user.setUserId(9L);
        user.setTenantId(1L);
        user.setUsername("fixture-user");
        user.setEnabled(true);
        return user;
    }

    /**
     * 通过Session仓库存储认证，检查请求隔离不会更改该对象。
     */
    private MockHttpSession session(Authentication authentication) {
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(authentication));
        return session;
    }

    /**
     * 使用固定合法回调和S256挑战，框架负责全部OAuth请求校验。
     */
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authorize() throws Exception {
        return authorize("https://client.example/callback");
    }

    /**
     * 单一回调参数用于区分非法URI与重复参数，不绕过SAS原生校验。
     */
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authorize(String redirect) throws
            Exception {
        return get("/oauth2/authorize").queryParam("client_id", "public").queryParam("response_type", "code")
                .queryParam("scope", "openid profile")
                .queryParam("redirect_uri", redirect).queryParam("state", "fixture-state").queryParam("code_challenge",
                        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                                java.security.MessageDigest.getInstance("SHA-256")
                                        .digest("v".repeat(43).getBytes(java.nio.charset.StandardCharsets.US_ASCII))))
                .queryParam("code_challenge_method", "S256");
    }

    /**
     * 缺失过滤器让测试明确失败。
     */
    private int index(List<jakarta.servlet.Filter> filters, Class<?> type) {
        for (int i = 0; i < filters.size(); i++) {
            if (type.isInstance(filters.get(i))) {
                return i;
            }
        }
        throw new AssertionError(type.getName());
    }
}
