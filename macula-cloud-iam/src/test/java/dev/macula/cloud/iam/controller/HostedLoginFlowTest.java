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

import dev.macula.cloud.iam.authentication.hosted.HostedLoginPolicyFilter;
import dev.macula.cloud.iam.authentication.sms.SmsAuthenticationFilter;
import dev.macula.cloud.iam.authentication.sms.SmsService;
import dev.macula.cloud.iam.config.IamProtocolTest;
import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.service.support.SysOAuth2ClientService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

/**
 * 复用真实SecurityFilterChain与Thymeleaf验证托管页、认证、发码同策。
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
class HostedLoginFlowTest {
    @Autowired private WebApplicationContext context;
    private MockMvc mvc;
    private SysOAuth2Client client;
    private SmsService sms;

    /** 每例重设Client策略和短信副作用记录。 */
    @BeforeEach
    void setup() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        client = new SysOAuth2Client();
        client.setClientId("public");
        client.setTenantId(1L);
        client.setUserType(UserTypeEnum.EMPLOYEE);
        client.setAuthorizationGrantTypes("authorization_code,refresh_token");
        client.setRedirectUris("https://client.example/callback");
        dev.macula.cloud.iam.protocol.oauth2.ProtocolTestFixtures.configure(client);
        when(context.getBean(SysOAuth2ClientService.class).getClientByClientId("public")).thenReturn(client);
        sms = context.getBean(SmsService.class);
        clearInvocations(sms);
    }

    /** CSRF在策略前，策略在两类表单认证前。 */
    @Test
    void realChainOrdersPolicyBeforeBothAuthenticators() {
        List<jakarta.servlet.Filter> filters = context.getBean(FilterChainProxy.class).getFilters("/login");
        int policy = index(filters, HostedLoginPolicyFilter.class);
        assertTrue(index(filters, CsrfFilter.class) < policy);
        assertTrue(policy < index(filters, UsernamePasswordAuthenticationFilter.class));
        assertTrue(policy < index(filters, SmsAuthenticationFilter.class));
    }

    /** 密码only/SMSonly/全部禁用在呈现和POST上使用相同策略。 */
    @Test
    void pageAndSubmissionShareClientPolicy() throws Exception {
        client.setHostedLoginMethods("PASSWORD");
        mvc.perform(get("/login").session(session())).andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"password-login\"")))
            .andExpect(content().string(not(containsString("id=\"sms-login\""))));
        mvc.perform(post("/api/v1/sms-code").session(session()).with(csrf()).param("phone", "13800000000"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(sms);
        client.setHostedLoginMethods("SMS");
        mvc.perform(get("/login").session(session())).andExpect(status().isOk())
            .andExpect(content().string(not(containsString("id=\"password-login\""))))
            .andExpect(content().string(containsString("id=\"sms-login\"")));
        mvc.perform(post("/login").session(session()).with(csrf()).param("username", "fixture-user").param("password", "fixture-password"))
            .andExpect(status().is3xxRedirection());
        mvc.perform(post("/api/v1/sms-code").session(session()).param("phone", "13800000000"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(sms);
        mvc.perform(post("/api/v1/sms-code").session(session()).with(csrf())
                .param("client_id", "attacker").param("phone", "13800000000"))
            .andExpect(status().isOk());
        verify(sms).sendCode("13800000000", "127.0.0.1", "public");
        client.setHostedLoginMethods("");
        mvc.perform(get("/login").session(session())).andExpect(status().isOk())
            .andExpect(content().string(not(containsString("id=\"password-login\""))))
            .andExpect(content().string(not(containsString("id=\"sms-login\""))));
        mvc.perform(post("/api/v1/sms-code").session(session()).with(csrf()).param("phone", "13800000000"))
            .andExpect(status().isForbidden());
    }

    /** 短信消费把受控Client传给校验器，伪造参数和cookie不改变该边界。 */
    @Test
    void smsConsumptionUsesTheSameClientAsIssuance() throws Exception {
        client.setHostedLoginMethods("SMS");
        mvc.perform(post("/login/sms").session(session()).with(csrf()).param("phone", "13800000000")
                .param("code", "fixture-code").param("client_id", "attacker"))
            .andExpect(status().is3xxRedirection());
        verify(sms).verifyCode("13800000000", "fixture-code", "public");
    }

    /** 只保存合法授权请求；其他Client声明无法创建受信任上下文。 */
    private MockHttpSession session() {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        request.setSession(session);
        request.addParameter("client_id", "public");
        request.addParameter("response_type", "code");
        request.addParameter("scope", "openid");
        request.addParameter("redirect_uri", "https://client.example/callback");
        new HttpSessionRequestCache().saveRequest(request, new MockHttpServletResponse());
        return session;
    }

    /** 以真实过滤器类型定位顺序，找不到时返回负值使断言失败。 */
    private int index(List<jakarta.servlet.Filter> filters, Class<?> type) {
        for (int i = 0; i < filters.size(); i++) {
            if (type.isInstance(filters.get(i))) {
                return i;
            }
        }
        throw new AssertionError("Missing filter " + type.getSimpleName());
    }
}
