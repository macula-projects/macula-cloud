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

import dev.macula.cloud.iam.playground.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.servlet.DispatcherServlet;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

/**
 * 随机端口 Tomcat + 真 IAM 过滤链 + 独立 Redis；仅身份/业务客户端服务使用测试替身。
 * @author Rain
 * @since 6.1.0
 */
class PlaygroundHttpIntegrationTest {
    /** Manual, isolated browser fixture. Stop the JVM to close Tomcat and its disposable Redis. */
    public static void main(String[] args) throws Exception {
        startServer();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { stopServer(); } catch (Exception ignored) { }
        }));
        System.out.println("PLAYGROUND_TEST_URL=" + issuer);
        new java.util.concurrent.CountDownLatch(1).await();
    }

    static Tomcat tomcat;
    static AnnotationConfigWebApplicationContext application;
    static String issuer;
    static Path directory;
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String VERIFIER = "a".repeat(64);
    HttpClient browser;
    String csrf;

    @Configuration
    @Import(dev.macula.boot.starter.web.advice.ControllerExceptionAdvice.class)
    static class App extends IamProtocolTest.App {
        @org.springframework.beans.factory.annotation.Autowired PlaygroundProperties properties;
        @org.springframework.beans.factory.annotation.Autowired PlaygroundAccessPolicy policy;
        @Override @Bean @Primary RegisteredClientRepository fixtureClients() {
            return new PlaygroundRegisteredClientRepository(super.fixtureClients(),
                policy, properties, issuer,
                PasswordEncoderFactories.createDelegatingPasswordEncoder());
        }
    }

    @BeforeAll static void startServer() throws Exception {
        int port;
        try (var socket = new java.net.ServerSocket(0)) { port = socket.getLocalPort(); }
        issuer = "http://127.0.0.1:" + port;
        directory = Files.createTempDirectory("iam-playground-http-");
        tomcat = new Tomcat();
        tomcat.setBaseDir(directory.toString());
        tomcat.setPort(port);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        var context = tomcat.addContext("", directory.toString());
        context.addApplicationListener(org.springframework.web.context.request.RequestContextListener.class.getName());
        application = new AnnotationConfigWebApplicationContext();
        application.setServletContext(context.getServletContext());
        application.getEnvironment().setActiveProfiles("local");
        application.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
            "fixture", Map.of("macula.cloud.iam.issuer-uri", issuer, "macula.cloud.iam.playground.enabled", "true",
                "macula.cloud.iam.playground.allowed-profiles", "local", "macula.cloud.iam.playground.allow-loopback-http", "true",
                "macula.cloud.iam.playground.client-secret", "fixture-playground-secret")));
        application.register(App.class);
        var servlet = Tomcat.addServlet(context, "dispatcher", new DispatcherServlet(application));
        servlet.setLoadOnStartup(1);
        context.addServletMappingDecoded("/", "dispatcher");
        var filter = new FilterDef();
        filter.setFilterName("security");
        filter.setFilter(new DelegatingFilterProxy("springSecurityFilterChain", application));
        context.addFilterDef(filter);
        var mapping = new FilterMap();
        mapping.setFilterName("security"); mapping.addURLPattern("/*");
        context.addFilterMap(mapping);
        tomcat.start();
    }

    @AfterAll static void stopServer() throws Exception {
        if (tomcat != null) { tomcat.stop(); tomcat.destroy(); }
        if (application != null) application.close();
        if (directory != null) {
            try (var paths = Files.walk(directory)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    @BeforeEach void freshBrowser() throws Exception {
        browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(3)).build();
        csrf = body(get("/api/v1/iam-playground/configuration")).get("csrfToken").asText();
    }

    @Test void realClientCredentialsIntrospectionAndRevocationStaySessionBound() throws Exception {
        var started = body(api("/flows", Map.of("scenario", "CLIENT_CREDENTIALS")));
        assertThat(started.at("/transcript/status").asInt()).isEqualTo(200);
        assertThat(started.at("/transcript/body/access_token").asText()).isEqualTo("[REDACTED]");
        String id = started.get("flowId").asText();
        assertThat(body(action(id, "introspect")).at("/transcript/body/active").asBoolean()).isTrue();
        assertThat(body(action(id, "reveal")).at("/data/tokens/access_token").asText()).isNotBlank();
        action(id, "revoke-access");
        assertThat(body(action(id, "introspect")).at("/transcript/body/active").asBoolean()).isFalse();
        freshBrowser();
        assertThat(action(id, "reveal").statusCode()).isEqualTo(400);
    }

    @Test void publicPkceOidcBindsStateVerifierAndSessionAcrossLoginRotation() throws Exception {
        String id = codeFlow("PUBLIC_CODE");
        var state = body(get("/api/v1/iam-playground/flows/" + id));
        assertThat(state.get("verification").asText()).isEqualTo("verified");
        assertThat(state.at("/data/hasRefreshToken").asBoolean()).isFalse();
        assertThat(body(action(id, "userinfo")).at("/data/userInfo/sub").asText()).isNotBlank();
        assertThat(action(id, "refresh").statusCode()).isEqualTo(400);
    }

    @Test void expiredAuthorizationCodeCannotBeExchangedOverHttp() throws Exception {
        codeFlow("PUBLIC_CODE", true);
    }

    @Test void confidentialCodeRefreshAndUserInfoUseVerifiedOidc() throws Exception {
        String id = codeFlow("CONFIDENTIAL_CODE");
        assertThat(body(action(id, "refresh")).at("/transcript/status").asInt()).isEqualTo(200);
        assertThat(body(action(id, "userinfo")).at("/transcript/status").asInt()).isEqualTo(200);
    }

    @Test void oidcLogoutClearsFlowAndUsesRegisteredRedirect() throws Exception {
        String id = codeFlow("PUBLIC_CODE");
        var logout = body(action(id, "logout")).get("data");
        var response = form("/connect/logout", Map.of("id_token_hint", logout.get("idTokenHint").asText(),
            "post_logout_redirect_uri", logout.get("postLogoutRedirectUri").asText()));
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElseThrow()).isEqualTo(issuer + "/playground");
        assertThat(get("/api/v1/iam-playground/flows/" + id).statusCode()).isEqualTo(400);
    }

    @Test void passwordAndSmsUseOriginalParametersWithoutIdToken() throws Exception {
        for (var input : List.of(
            Map.of("scenario", "PASSWORD", "username", "fixture-user", "password", "fixture-password"),
            Map.of("scenario", "SMS", "phone", "13000000000", "captcha", "fixture-code"))) {
            var result = body(api("/flows", input));
            assertThat(result.at("/transcript/status").asInt()).isEqualTo(200);
            assertThat(result.at("/data/hasIdToken").asBoolean()).isFalse();
            assertThat(result.toString()).doesNotContain("fixture-password", "fixture-code", "13000000000");
            String id = result.get("flowId").asText();
            assertThat(body(action(id, "refresh")).at("/transcript/status").asInt()).isEqualTo(200);
        }
    }

    @Test void csrfOriginAndUnknownFlowsFailBeforeAnyProtocolAction() throws Exception {
        var request = HttpRequest.newBuilder(URI.create(issuer + "/api/v1/iam-playground/flows"))
            .header("Content-Type", "application/json").header("Origin", issuer)
            .POST(HttpRequest.BodyPublishers.ofString("{\"scenario\":\"CLIENT_CREDENTIALS\"}")).build();
        assertThat(browser.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
        request = HttpRequest.newBuilder(URI.create(issuer + "/api/v1/iam-playground/flows"))
            .header("Content-Type", "application/json").header("Origin", "https://evil.example").header("X-CSRF-TOKEN", csrf)
            .POST(HttpRequest.BodyPublishers.ofString("{\"scenario\":\"CLIENT_CREDENTIALS\"}")).build();
        assertThat(browser.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
        assertThat(action("unknown", "reveal").statusCode()).isEqualTo(400);
        assertThat(api("/flows", Map.of("scenario", "CLIENT_CREDENTIALS", "issuer", "https://evil.example")).statusCode()).isEqualTo(400);
        var started = body(api("/flows", Map.of("scenario", "CLIENT_CREDENTIALS")));
        assertThat(api("/flows/" + started.get("flowId").asText() + "/actions/introspect",
            Map.of("token", "arbitrary-input")).statusCode()).isEqualTo(400);
    }

    @Test void deviceUsesRealApprovalCsrfAndClientBinding() throws Exception {
        var issuedResponse = form("/oauth2/device_authorization", Map.of("client_id", PlaygroundRegisteredClientRepository.DEVICE,
            "scope", "playground.read"));
        assertThat(issuedResponse.statusCode()).as("device authorization HTTP status").isEqualTo(200);
        var issued = body(issuedResponse);
        String deviceCode = issued.get("device_code").asText();
        String userCode = issued.get("user_code").asText();
        var poll = Map.of("grant_type", "urn:ietf:params:oauth:grant-type:device_code",
            "client_id", PlaygroundRegisteredClientRepository.DEVICE, "device_code", deviceCode);
        assertThat(body(form("/oauth2/token", poll)).get("error").asText()).isEqualTo("authorization_pending");
        assertThat(form("/oauth2/token", Map.of("grant_type", "urn:ietf:params:oauth:grant-type:device_code",
            "client_id", PlaygroundRegisteredClientRepository.PUBLIC, "device_code", deviceCode)).statusCode())
            .isEqualTo(302);
        freshBrowser(); // independent user agent, not the device's cookie jar
        assertThat(get("/playground/device?user_code=" + userCode).statusCode()).isEqualTo(302);
        var login = get("/login");
        assertThat(body(form("/login", Map.of("username", "fixture-user", "password", "fixture-password",
            "_csrf", input(login.body(), "_csrf")))).get("success").asBoolean()).isTrue();
        var verification = get("/oauth2/device_verification?user_code=" + userCode);
        assertThat(verification.statusCode()).isEqualTo(302);
        String consentUri = verification.headers().firstValue("Location").orElseThrow();
        assertThat(consentUri).contains("/playground/device");
        String html = get(consentUri).body();
        var approval = new LinkedHashMap<String, String>();
        approval.put("client_id", PlaygroundRegisteredClientRepository.DEVICE);
        approval.put("user_code", userCode);
        approval.put("state", input(html, "state"));
        approval.put("scope", "playground.read");
        approval.put("decision", "allow");
        assertThat(form("/oauth2/device_verification", approval).statusCode()).isEqualTo(403);
        approval.put("_csrf", input(html, "_csrf"));
        assertThat(form("/oauth2/device_verification", approval).headers().firstValue("Location").orElseThrow())
            .endsWith("/playground/device?result=approved");
        var result = body(form("/oauth2/token", poll));
        assertThat(result.has("access_token")).isTrue();
        assertThat(result.has("id_token")).isFalse();
        assertThat(result.has("refresh_token")).isFalse();
        assertThat(body(form("/oauth2/token", poll)).get("error").asText()).isEqualTo("access_denied");

        // Previous consent must not silently approve the next device, and explicit denial must win.
        issued = body(form("/oauth2/device_authorization", Map.of("client_id", PlaygroundRegisteredClientRepository.DEVICE,
            "scope", "playground.read")));
        userCode = issued.get("user_code").asText();
        verification = get("/oauth2/device_verification?user_code=" + userCode);
        html = get(verification.headers().firstValue("Location").orElseThrow()).body();
        approval.put("user_code", userCode);
        approval.put("state", input(html, "state"));
        approval.put("_csrf", input(html, "_csrf"));
        approval.put("decision", "deny");
        assertThat(form("/oauth2/device_verification", approval).headers().firstValue("Location").orElseThrow())
            .endsWith("/playground/device?result=denied");
        var denied = body(form("/oauth2/token", Map.of("grant_type", "urn:ietf:params:oauth:grant-type:device_code",
            "client_id", PlaygroundRegisteredClientRepository.DEVICE, "device_code", issued.get("device_code").asText())));
        assertThat(denied.get("error").asText()).isEqualTo("access_denied");
    }

    @Test void disabledAndProductionProfilesRejectRoutesAndPreviouslyIssuedTokensOverHttp() throws Exception {
        String id = codeFlow("CONFIDENTIAL_CODE");
        var tokens = body(action(id, "reveal")).at("/data/tokens");
        String access = tokens.get("access_token").asText();
        String refresh = tokens.get("refresh_token").asText();
        var properties = application.getBean(PlaygroundProperties.class);
        try {
            for (String mode : List.of("disabled", "prd", "production", "mixed-prd", "mixed-production")) {
                properties.setEnabled(!mode.equals("disabled"));
                application.getEnvironment().setActiveProfiles(mode.startsWith("mixed-")
                    ? new String[]{"local", mode.substring(6)} : new String[]{mode.equals("disabled") ? "local" : mode});
                for (String path : List.of("/playground", "/playground/callback", "/playground/device",
                    "/playground/assets/playground.js", "/playground/assets/playground.css", "/api/v1/iam-playground/configuration")) {
                    assertThat(get(path).statusCode()).as("%s %s", mode, path).isEqualTo(404);
                }
                assertThat(action(id, "reveal").statusCode()).isEqualTo(404);
                assertThat(browser.send(HttpRequest.newBuilder(URI.create(issuer + "/userinfo"))
                    .header("Authorization", "Bearer " + access).GET().build(), HttpResponse.BodyHandlers.ofString())
                    .statusCode()).isEqualTo(401);
                var response = browser.send(HttpRequest.newBuilder(URI.create(issuer + "/oauth2/token"))
                    .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                        (PlaygroundRegisteredClientRepository.CONFIDENTIAL + ":fixture-playground-secret").getBytes(StandardCharsets.UTF_8)))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("grant_type=refresh_token&refresh_token=" + encode(refresh)))
                    .build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(401);
                assertThat(HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(issuer + "/login"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
            }
        } finally {
            properties.setEnabled(true);
            application.getEnvironment().setActiveProfiles("local");
        }
    }

    @Test void expiredDeviceCodeAndUnknownUserCodeCannotAuthorize() throws Exception {
        var issued = body(form("/oauth2/device_authorization", Map.of("client_id", PlaygroundRegisteredClientRepository.DEVICE,
            "scope", "playground.read")));
        String deviceCode = issued.get("device_code").asText();
        var store = application.getBean(org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService.class);
        var authorization = store.findByToken(deviceCode,
            new org.springframework.security.oauth2.server.authorization.OAuth2TokenType("device_code"));
        // Simulate elapsed lifetime in isolated Redis, then exercise the actual token endpoint.
        var now = java.time.Instant.now();
        store.save(org.springframework.security.oauth2.server.authorization.OAuth2Authorization.from(authorization)
            .token(new org.springframework.security.oauth2.core.OAuth2DeviceCode(deviceCode, now.minusSeconds(310), now.minusSeconds(10)))
            .build());
        var expired = form("/oauth2/token", Map.of("grant_type", "urn:ietf:params:oauth:grant-type:device_code",
            "client_id", PlaygroundRegisteredClientRepository.DEVICE, "device_code", deviceCode));
        assertThat(expired.statusCode()).isEqualTo(400);
        assertThat(body(expired).get("error").asText()).isEqualTo("invalid_grant");
        var login = get("/login");
        form("/login", Map.of("username", "fixture-user", "password", "fixture-password", "_csrf", input(login.body(), "_csrf")));
        assertThat(get("/oauth2/device_verification?user_code=ZZZZ-ZZZZ").statusCode()).isEqualTo(400);
    }

    String codeFlow(String scenario) throws Exception {
        return codeFlow(scenario, false);
    }

    String codeFlow(String scenario, boolean expireCode) throws Exception {
        String state = UUID.randomUUID().toString().replace("-", "") + "a".repeat(16);
        String nonce = UUID.randomUUID().toString().replace("-", "") + "b".repeat(16);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(VERIFIER.getBytes(StandardCharsets.US_ASCII)));
        var start = body(api("/flows", Map.of("scenario", scenario, "state", state, "nonce", nonce, "challenge", challenge)));
        String id = start.get("flowId").asText();
        String authorize = start.get("authorizeUrl").asText();
        assertThat(get(authorize).statusCode()).isIn(302, 401);
        String login = get("/login").body();
        String loginCsrf = input(login, "_csrf");
        var loggedIn = form("/login", Map.of("username", "fixture-user", "password", "fixture-password", "_csrf", loginCsrf));
        assertThat(body(loggedIn).get("success").asBoolean()).isTrue();
        csrf = body(get("/api/v1/iam-playground/configuration")).get("csrfToken").asText();
        var authorization = get(authorize);
        String location = authorization.headers().firstValue("Location").orElseThrow();
        if (location.contains("/oauth2/consent")) {
            var parameters = query(location);
            // Actual framework consent POST; scope values are repeated, not a mock authorization write.
            var consent = formRaw("/oauth2/authorize", "client_id=" + encode(parameters.get("client_id"))
                + "&state=" + encode(parameters.get("state")) + "&scope=openid&scope=profile&scope=playground.read&decision=allow");
            location = consent.headers().firstValue("Location").orElseThrow();
        }
        var callback = query(location);
        assertThat(callback.get("state")).isEqualTo(state);
        if (expireCode) {
            var store = application.getBean(org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService.class);
            var storedAuthorization = store.findByToken(callback.get("code"),
                new org.springframework.security.oauth2.server.authorization.OAuth2TokenType("code"));
            var now = java.time.Instant.now();
            store.save(org.springframework.security.oauth2.server.authorization.OAuth2Authorization.from(storedAuthorization)
                .token(new org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode(
                    callback.get("code"), now.minusSeconds(130), now.minusSeconds(10))).build());
            var rejected = form("/oauth2/token", Map.of("grant_type", "authorization_code",
                "client_id", PlaygroundRegisteredClientRepository.PUBLIC, "code", callback.get("code"),
                "redirect_uri", issuer + "/playground/callback", "code_verifier", VERIFIER));
            assertThat(rejected.statusCode()).isBetween(300, 499);
            assertThat(rejected.body()).doesNotContain("access_token");
            return id;
        }
        assertThat(api("/flows/" + id + "/callback",
            Map.of("state", "wrong", "code", callback.get("code"), "verifier", VERIFIER)).statusCode()).isEqualTo(400);
        assertThat(api("/flows/" + id + "/callback",
            Map.of("state", state, "code", callback.get("code"), "verifier", "b".repeat(64))).statusCode()).isEqualTo(400);
        var exchanged = body(api("/flows/" + id + "/callback",
            Map.of("state", state, "code", callback.get("code"), "verifier", VERIFIER)));
        assertThat(exchanged.at("/transcript/status").asInt()).isEqualTo(200);
        assertThat(exchanged.get("verification").asText()).isEqualTo("verified");
        assertThat(api("/flows/" + id + "/callback",
            Map.of("state", state, "code", callback.get("code"), "verifier", VERIFIER)).statusCode()).isEqualTo(400);
        return id;
    }

    HttpResponse<String> action(String id, String action) throws Exception { return api("/flows/" + id + "/actions/" + action, Map.of()); }
    HttpResponse<String> api(String path, Map<String, String> input) throws Exception {
        return browser.send(HttpRequest.newBuilder(URI.create(issuer + "/api/v1/iam-playground" + path))
            .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
            .header("Origin", issuer).header("X-CSRF-TOKEN", csrf)
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(input))).build(), HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> get(String path) throws Exception {
        return browser.send(HttpRequest.newBuilder(URI.create(path.startsWith("http") ? path : issuer + path))
            .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> form(String path, Map<String, String> values) throws Exception {
        return formRaw(path, values.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
            .collect(java.util.stream.Collectors.joining("&")));
    }
    HttpResponse<String> formRaw(String path, String value) throws Exception {
        return browser.send(HttpRequest.newBuilder(URI.create(issuer + path)).timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/x-www-form-urlencoded").header("Origin", issuer)
            .POST(HttpRequest.BodyPublishers.ofString(value)).build(), HttpResponse.BodyHandlers.ofString());
    }
    static tools.jackson.databind.JsonNode body(HttpResponse<String> response) { return JSON.readTree(response.body()); }
    static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    static Map<String, String> query(String uri) {
        var result = new HashMap<String, String>();
        for (String pair : URI.create(uri).getRawQuery().split("&")) {
            String[] pieces = pair.split("=", 2);
            result.put(URLDecoder.decode(pieces[0], StandardCharsets.UTF_8), URLDecoder.decode(pieces[1], StandardCharsets.UTF_8));
        }
        return result;
    }
    static String input(String html, String name) {
        var matcher = java.util.regex.Pattern.compile("name=\"" + name + "\" value=\"([^\"]+)\"").matcher(html);
        if (!matcher.find()) throw new AssertionError("Missing form binding");
        return matcher.group(1);
    }
}
