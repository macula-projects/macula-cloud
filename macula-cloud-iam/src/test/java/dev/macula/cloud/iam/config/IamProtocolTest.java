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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.macula.cloud.iam.controller.LoginController;
import dev.macula.cloud.iam.protocol.oauth2.endpoint.AuthorizationConsentController;
import dev.macula.cloud.iam.pojo.dto.UserAuthInfo;
import dev.macula.cloud.iam.service.support.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.settings.*;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.*;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * 不启动 Nacos/MySQL 的真实 SecurityFilterChain、Thymeleaf 与 OAuth/OIDC 协议回归。
 * @author rain
 * @since 6.1.0
 */
@SpringJUnitConfig(IamProtocolTest.App.class)
@WebAppConfiguration
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("local")
@TestPropertySource(properties = "macula.cloud.iam.issuer-uri=https://iam.example")
class IamProtocolTest {
    @Autowired WebApplicationContext context;
    @Autowired JwtDecoder decoder;
    MockMvc mvc;
    ObjectMapper json = tools.jackson.databind.json.JsonMapper.builder().build();
    final String verifier = "01234567890123456789012345678901234567890123456789";

    @Configuration
    @EnableWebMvc
    @EnableConfigurationProperties
    @Import({ProtocolOAuth2Configuration.class, DefaultSecurityConfiguration.class, JwtConfiguration.class,
        PasswordEncoderConfig.class, LoginController.class, AuthorizationConsentController.class})
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
                .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(true).build())
                .tokenSettings(TokenSettings.builder().accessTokenFormat(format).reuseRefreshTokens("reuse".equals(id)).build()).build();
        }
        @Bean @Primary OAuth2AuthorizationService fixtureAuthorizations(RedisTemplate<String, Object> redisTemplate,
            RegisteredClientRepository clients) {
            return new dev.macula.cloud.iam.service.oauth2.MaculaOAuth2AuthorizationService(redisTemplate, clients);
        }
        @Bean @Primary OAuth2AuthorizationConsentService fixtureConsents() { return new InMemoryOAuth2AuthorizationConsentService(); }
        @Bean(destroyMethod = "close") RedisFixture redisFixture() throws Exception { return new RedisFixture(); }
        @Bean RedisTemplate<String, Object> redisTemplate(RedisFixture fixture) {
            RedisTemplate<String, Object> template = new RedisTemplate<>();
            template.setConnectionFactory(fixture.connection);
            template.setKeySerializer(new org.springframework.data.redis.serializer.StringRedisSerializer());
            template.afterPropertiesSet();
            return template;
        }
        @Bean SysOAuth2ClientService clientService() { return mock(SysOAuth2ClientService.class); }
        @Bean SysUserService userService() {
            SysUserService service = mock(SysUserService.class);
            UserAuthInfo user = new UserAuthInfo();
            user.setUserId(42L); user.setUsername("fixture-user"); user.setNickname("测试用户");
            user.setStatus(1); user.setDataScope(1); user.setRoles(Set.of("USER"));
            user.setPassword("{bcrypt}" + new BCryptPasswordEncoder().encode("fixture-password"));
            when(service.getUserAuthInfo(any(), eq("fixture-user"), isNull())).thenReturn(user);
            // Existing phone lookup uses an empty username placeholder; simulate its identity source only.
            when(service.getUserAuthInfo(any(), eq(""), isNull())).thenReturn(user);
            return service;
        }
        @Bean SpringResourceTemplateResolver templates() {
            SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
            resolver.setPrefix("classpath:/templates/"); resolver.setSuffix(".html");
            resolver.setTemplateMode("HTML"); resolver.setCharacterEncoding("UTF-8"); return resolver;
        }
        @Bean SpringTemplateEngine engine(SpringResourceTemplateResolver resolver) {
            SpringTemplateEngine engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver); return engine;
        }
        @Bean ThymeleafViewResolver views(SpringTemplateEngine engine) {
            ThymeleafViewResolver view = new ThymeleafViewResolver(); view.setTemplateEngine(engine);
            view.setCharacterEncoding("UTF-8"); return view;
        }
        @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
            registry.addResourceHandler("/iam/**").addResourceLocations("classpath:/static/iam/");
        }
    }

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
            } catch (Exception ex) { close(); throw ex; }
        }
        @Override public void close() throws Exception {
            try {
                if (connection != null) connection.destroy();
                if (redisson != null) redisson.shutdown();
            } finally { if (process != null) process.destroy(); }
        }
    }

    @BeforeEach void setup() {
        mvc = webAppContextSetup(context).apply(springSecurity()).alwaysDo(result -> {
            if (result.getResponse().getStatus() >= 400)
                System.out.println("Rejected " + result.getRequest().getRequestURI() + ": "
                    + result.getResponse().getStatus() + " " + result.getResponse().getContentAsString());
        }).build();
    }

    @Test void disabledPlaygroundRoutesAndAssetsReturn404ThroughRealSecurityChain() throws Exception {
        for (String path : new String[]{"/playground", "/playground/callback", "/playground/device",
            "/playground/assets/playground.js", "/api/v1/iam-playground/flows"}) {
            mvc.perform(get(path)).andExpect(status().isNotFound()).andExpect(header().string("Cache-Control", "no-store"));
            mvc.perform(post(path)).andExpect(status().isNotFound());
            mvc.perform(options(path)).andExpect(status().isNotFound());
        }
    }

    @Test void loginPagesAndSessionWorkWithCsrf() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(
            org.hamcrest.Matchers.containsString("autocomplete=\"current-password\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("captcha-login")));
        mvc.perform(get("/iam/auth.css")).andExpect(status().isOk());
        mvc.perform(post("/login").param("username", "fixture-user").param("password", "fixture-password"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/login").with(csrf()).param("username", "fixture-user").param("password", "wrong"))
            .andExpect(jsonPath("$.success").value(false));
        MockHttpSession session = login();
        mvc.perform(get("/").session(session)).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("fixture-user")));
    }

    MockHttpSession login() throws Exception {
        MvcResult result = mvc.perform(post("/login").with(csrf()).param("username", "fixture-user")
            .param("password", "fixture-password")).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true)).andReturn();
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

    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder exchange(String client, String code, String proof) {
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
            missing.with(request -> { request.removeParameter("code_verifier"); return request; });
            if (client.equals("public")) {
                mvc.perform(missing).andExpect(status().is3xxRedirection())
                    .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/login")))
                    .andExpect(content().string(""));
            } else {
                mvc.perform(missing).andExpect(status().isBadRequest());
            }
            code = authorize(client, login());
            var wrongRedirect = exchange(client, code, verifier);
            wrongRedirect.with(request -> { request.setParameter("redirect_uri", "https://other.example/callback"); return request; });
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
                    .andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.email_verified").doesNotExist());
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
            .andExpect(jsonPath("$.access_token").exists()).andExpect(jsonPath("$.id_token").doesNotExist()).andReturn();
        String access = json.readTree(result.getResponse().getContentAsString()).get("access_token").asText();
        mvc.perform(get("/userinfo").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
    }

    @Test void smsCompatibilityUsesOriginalParametersAndDefaultVerifierWithoutPkce() throws Exception {
        MvcResult result = mvc.perform(post("/oauth2/token").with(httpBasic("compat", "fixture-secret"))
            .param("grant_type", "sms").param("phone", "13800000000").param("captcha", "fixture-code")
            .param("scope", "profile")).andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").exists()).andExpect(jsonPath("$.refresh_token").exists())
            .andExpect(jsonPath("$.id_token").doesNotExist()).andReturn();
        String access = json.readTree(result.getResponse().getContentAsString()).get("access_token").asText();
        OAuth2Authorization authorization = context.getBean(OAuth2AuthorizationService.class)
            .findByToken(access, org.springframework.security.oauth2.server.authorization.OAuth2TokenType.ACCESS_TOKEN);
        org.junit.jupiter.api.Assertions.assertEquals(new AuthorizationGrantType("password"),
            authorization.getAuthorizationGrantType());
        mvc.perform(post("/oauth2/token").with(httpBasic("compat", "fixture-secret"))
            .param("grant_type", "refresh_token").param("refresh_token",
                json.readTree(result.getResponse().getContentAsString()).get("refresh_token").asText()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.access_token").exists());
    }

    @Test void compatibilityGrantsKeepRequiredParametersAndPasswordError() throws Exception {
        mvc.perform(post("/oauth2/token").with(httpBasic("compat", "fixture-secret"))
            .param("grant_type", "sms").param("phone", "13800000000").param("scope", "profile"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_request"));
        mvc.perform(post("/oauth2/token").with(httpBasic("compat", "fixture-secret"))
            .param("grant_type", "sms").param("captcha", "fixture-code").param("scope", "profile"))
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
            for (String grant : new String[] {"password", "sms"}) {
                MvcResult issued = mvc.perform(post("/oauth2/token").with(httpBasic(client, "fixture-secret"))
                    .param("grant_type", grant).param("username", "fixture-user").param("password", "fixture-password")
                    .param("phone", "13800000000").param("captcha", "fixture-code").param("scope", "openid profile"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id_token").doesNotExist()).andReturn();
                String refresh = json.readTree(issued.getResponse().getContentAsString()).get("refresh_token").asText();
                mvc.perform(post("/oauth2/token").with(httpBasic(client.equals("compat") ? "jwt" : "compat", "fixture-secret"))
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
                    tokens.get("access_token").asText(), org.springframework.security.oauth2.server.authorization.OAuth2TokenType.ACCESS_TOKEN);
                org.junit.jupiter.api.Assertions.assertEquals(Set.of("openid", "profile"), stored.getAuthorizedScopes());
                org.junit.jupiter.api.Assertions.assertNull(stored.getToken(org.springframework.security.oauth2.core.oidc.OidcIdToken.class));
                if (client.equals("reuse")) {
                    org.junit.jupiter.api.Assertions.assertEquals(refresh, tokens.get("refresh_token").asText());
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
                String finalRefresh = json.readTree(narrowed.getResponse().getContentAsString()).get("refresh_token").asText();
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
        String login = mvc.perform(get("/login")).andExpect(status().isOk()).andReturn().getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
        String consent = mvc.perform(get("/oauth2/consent").with(user("演示账号"))
            .param("client_id", "public").param("scope", "openid profile").param("state", "preview"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        java.nio.file.Files.writeString(preview.resolve("login.html"), login);
        java.nio.file.Files.writeString(preview.resolve("consent.html"), consent);
        for (String asset : List.of("auth.css", "auth.js"))
            java.nio.file.Files.copy(java.nio.file.Path.of("src/main/resources/static/iam", asset),
                preview.resolve("iam").resolve(asset), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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
        assertThat(context.getBean(OAuth2AuthorizationService.class).findByToken(state, new OAuth2TokenType("state"))).isNull();
    }

    @Test void anonymousAuthorizeRestoresOriginalRequestAfterLogin() throws Exception {
        MvcResult pending = mvc.perform(get("/oauth2/authorize").queryParam("response_type", "code")
            .queryParam("client_id", "public").queryParam("redirect_uri", "https://client.example/callback")
            .queryParam("scope", "openid profile").queryParam("state", "saved-state").queryParam("nonce", "saved-nonce")
            .queryParam("code_challenge", challenge()).queryParam("code_challenge_method", "S256"))
            .andExpect(status().is3xxRedirection()).andReturn();
        MockHttpSession session = (MockHttpSession) pending.getRequest().getSession(false);
        MvcResult loggedIn = mvc.perform(post("/login").session(session).with(csrf()).param("username", "fixture-user")
            .param("password", "fixture-password")).andExpect(jsonPath("$.success").value(true)).andReturn();
        String target = json.readTree(loggedIn.getResponse().getContentAsString()).at("/data/targetUrl").asText();
        assertThat(query(target, "code_challenge")).isEqualTo(challenge());
        assertThat(query(target, "nonce")).isEqualTo("saved-nonce");
        mvc.perform(get(target).session(session)).andExpect(status().is3xxRedirection());
    }
}
