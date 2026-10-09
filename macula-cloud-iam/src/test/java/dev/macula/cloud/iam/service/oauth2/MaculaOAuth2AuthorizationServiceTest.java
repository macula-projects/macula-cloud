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

package dev.macula.cloud.iam.service.oauth2;

import dev.macula.cloud.iam.pojo.dto.Authorization;
import dev.macula.boot.constants.CacheConstants;
import org.junit.jupiter.api.*;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import java.net.ServerSocket;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/**
 * 用独立临时 Redis 验证真实缓存 TTL、索引和并发更新，不连接业务 Redis。
 * @author rain
 * @since 6.1.0
 */
class MaculaOAuth2AuthorizationServiceTest {
    static Process redis;
    static RedissonConnectionFactory connection;
    static RedissonClient redisson;
    static StringRedisTemplate strings;
    static RedisTemplate<String, Object> legacy;
    static RegisteredClient client;
    MaculaOAuth2AuthorizationService service;

    @BeforeAll
    static void startRedis() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        String executable = System.getenv().getOrDefault("TEST_REDIS_SERVER", "/opt/homebrew/bin/redis-server");
        Assumptions.assumeTrue(new java.io.File(executable).canExecute(),
            "设置 TEST_REDIS_SERVER 指向 redis-server 才能执行隔离 Redis 测试");
        redis = new ProcessBuilder(executable, "--bind", "127.0.0.1", "--port", String.valueOf(port),
            "--save", "", "--appendonly", "no").redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:" + port).setConnectionMinimumIdleSize(1)
            .setConnectionPoolSize(2);
        redisson = Redisson.create(config);
        connection = new RedissonConnectionFactory(redisson);
        connection.afterPropertiesSet();
        strings = new StringRedisTemplate(connection);
        legacy = new RedisTemplate<>();
        legacy.setConnectionFactory(connection);
        legacy.setKeySerializer(new StringRedisSerializer());
        legacy.afterPropertiesSet();
        Exception failure = null;
        for (int i = 0; i < 50; i++) {
            try { strings.getConnectionFactory().getConnection().ping(); failure = null; break; }
            catch (Exception ex) { failure = ex; Thread.sleep(50); }
        }
        if (failure != null) throw failure;
        client = RegisteredClient.withId("test-client").clientId("test-client")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("https://client.example/callback").scope("openid").build();
    }

    @AfterAll
    static void stopRedis() throws Exception {
        if (connection != null) connection.destroy();
        if (redisson != null) redisson.shutdown();
        if (redis != null) redis.destroy();
    }

    @BeforeEach
    void setup() {
        service = new MaculaOAuth2AuthorizationService(legacy, new InMemoryRegisteredClientRepository(client));
    }

    OAuth2Authorization authorization() {
        Instant now = Instant.now();
        String suffix = UUID.randomUUID().toString();
        return OAuth2Authorization.withRegisteredClient(client).id(suffix).principalName("fixture-user")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizedScopes(Set.of("openid")).attribute("state", "state-" + suffix)
            .token(new OAuth2AuthorizationCode("code-" + suffix, now, now.plusSeconds(30)))
            .accessToken(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-" + suffix, now,
                now.plusSeconds(60), Set.of("openid")))
            .refreshToken(new OAuth2RefreshToken("refresh-" + suffix, now, now.plusSeconds(120)))
            .token(new OidcIdToken("id-" + suffix, now, now.plusSeconds(60), Map.of("sub", "fixture-user")))
            .token(new OAuth2DeviceCode("device-" + suffix, now, now.plusSeconds(60)))
            .token(new OAuth2UserCode("user-" + suffix, now, now.plusSeconds(60))).build();
    }

    @Test void redisConnectionFailureIsNotReportedAsMissingToken() {
        var factory = org.mockito.Mockito.mock(org.springframework.data.redis.connection.RedisConnectionFactory.class);
        org.mockito.Mockito.when(factory.getConnection()).thenThrow(
            new org.springframework.data.redis.RedisConnectionFailureException("fixture unavailable"));
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.afterPropertiesSet();
        var unavailable = new MaculaOAuth2AuthorizationService(template, new InMemoryRegisteredClientRepository(client));
        assertThatThrownBy(() -> unavailable.findByToken("fixture", null))
            .isInstanceOf(org.springframework.data.redis.RedisConnectionFailureException.class);
    }

    @Test void disabledPlaygroundRejectsEveryPersistedTokenTypeWithoutBreakingBusiness() {
        var demo = RegisteredClient.from(client).id("iam-playground-public").clientId("iam-playground-public").build();
        var repository = org.mockito.Mockito.mock(RegisteredClientRepository.class);
        org.mockito.Mockito.when(repository.findById(demo.getId())).thenReturn(demo);
        var scoped = new MaculaOAuth2AuthorizationService(legacy, repository);
        var original = authorization();
        var authorization = OAuth2Authorization.withRegisteredClient(demo).id(original.getId())
            .principalName(original.getPrincipalName()).authorizationGrantType(original.getAuthorizationGrantType())
            .authorizedScopes(original.getAuthorizedScopes()).attributes(values -> values.putAll(original.getAttributes()))
            .token(original.getToken(OAuth2AuthorizationCode.class).getToken())
            .accessToken(original.getAccessToken().getToken()).refreshToken(original.getRefreshToken().getToken())
            .token(original.getToken(OidcIdToken.class).getToken())
            .token(original.getToken(OAuth2DeviceCode.class).getToken())
            .token(original.getToken(OAuth2UserCode.class).getToken()).build();
        scoped.save(authorization);
        assertThat(scoped.findById(authorization.getId())).isNotNull();
        org.mockito.Mockito.when(repository.findById(demo.getId())).thenReturn(null);
        assertThat(scoped.findById(authorization.getId())).isNull();
        Map<String, String> tokens = Map.of("code", authorization.getToken(OAuth2AuthorizationCode.class).getToken().getTokenValue(),
            "access_token", authorization.getAccessToken().getToken().getTokenValue(),
            "refresh_token", authorization.getRefreshToken().getToken().getTokenValue(),
            "id_token", authorization.getToken(OidcIdToken.class).getToken().getTokenValue(),
            "device_code", authorization.getToken(OAuth2DeviceCode.class).getToken().getTokenValue(),
            "user_code", authorization.getToken(OAuth2UserCode.class).getToken().getTokenValue(),
            "state", authorization.getAttribute("state"));
        tokens.forEach((type, value) -> {
            assertThat(scoped.findByToken(value, new OAuth2TokenType(type))).as(type).isNull();
            assertThat(scoped.findByToken(value, null)).as("unspecified " + type).isNull();
        });
        assertThatThrownBy(() -> scoped.save(authorization)).isInstanceOf(OAuth2AuthenticationException.class);
        var business = authorization();
        service.save(business);
        assertThat(service.findById(business.getId())).isNotNull();
    }

    @Test void readsHistoricalJdkSerializedDtoAndMigratesIt() throws Exception {
        byte[] fixture;
        try (var input = getClass().getResourceAsStream("/fixtures/legacy-authorization.base64")) {
            assertThat(input).isNotNull();
            fixture = Base64.getMimeDecoder().decode(input.readAllBytes());
        }
        byte[] key = (CacheConstants.OAUTH2_TOKEN_KEY + ":access_token:legacy-binary-access")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try (var redisConnection = connection.getConnection()) {
            redisConnection.stringCommands().setEx(key, 60, fixture);
        }
        var restored = service.findByToken("legacy-binary-access", OAuth2TokenType.ACCESS_TOKEN);
        assertThat(restored.getPrincipalName()).isEqualTo("fixture-user");
        assertThat(restored.getAuthorizationGrantType().getValue()).isEqualTo("password");
        assertThat(restored.getAccessToken().getToken().getScopes()).containsExactly("openid");
        service.save(restored);
        assertThat(service.findById("legacy-binary-fixture")).isNotNull();
        service.remove(service.findById("legacy-binary-fixture"));
        assertThat(service.findByToken("legacy-binary-access", null)).isNull();
    }

    @Test void malformedStoredRecordIsNotReportedAsMissingToken() throws Exception {
        OAuth2Authorization original = authorization();
        service.save(original);
        String hash = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
            .digest(original.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        strings.opsForValue().set(CacheConstants.OAUTH2_TOKEN_KEY + ":{iam-oauth2}:v2:authorization:" + hash,
            "not-json", java.time.Duration.ofSeconds(60));
        assertThatThrownBy(() -> service.findById(original.getId())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.findByToken(original.getAccessToken().getToken().getTokenValue(), null))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test void findsEveryTypeAndDoesNotConfuseExplicitTypes() {
        OAuth2Authorization original = authorization();
        service.save(original);
        Map<String, String> values = Map.of(
            "state", original.getAttribute("state"),
            "code", original.getToken(OAuth2AuthorizationCode.class).getToken().getTokenValue(),
            "access_token", original.getAccessToken().getToken().getTokenValue(),
            "refresh_token", original.getRefreshToken().getToken().getTokenValue(),
            "id_token", original.getToken(OidcIdToken.class).getToken().getTokenValue(),
            "device_code", original.getToken(OAuth2DeviceCode.class).getToken().getTokenValue(),
            "user_code", original.getToken(OAuth2UserCode.class).getToken().getTokenValue());
        values.forEach((type, value) -> {
            assertThat(service.findByToken(value, null).getId()).isEqualTo(original.getId());
            assertThat(service.findByToken(value, new OAuth2TokenType(type)).getId()).isEqualTo(original.getId());
        });
        assertThat(service.findById(original.getId()).getToken(OidcIdToken.class).getToken().getSubject())
            .isEqualTo("fixture-user");
        assertThat(service.findByToken(original.getRefreshToken().getToken().getTokenValue(),
            OAuth2TokenType.ACCESS_TOKEN)).isNull();
        assertThat(service.findByToken("absent", null)).isNull();
        assertThat(service.findByToken("absent", new OAuth2TokenType("unknown"))).isNull();
    }

    @Test void rotationRevocationAndDeletionUseCurrentState() {
        OAuth2Authorization original = authorization();
        service.save(original);
        OAuth2Authorization current = service.findById(original.getId());
        String oldRefresh = current.getRefreshToken().getToken().getTokenValue();
        OAuth2RefreshToken replacement = new OAuth2RefreshToken("replacement-" + UUID.randomUUID(),
            Instant.now(), Instant.now().plusSeconds(100));
        service.save(OAuth2Authorization.from(current).refreshToken(replacement).build());
        assertThat(service.findByToken(oldRefresh, null)).isNull();
        current = service.findByToken(replacement.getTokenValue(), null);
        service.save(OAuth2Authorization.from(current).invalidate(current.getAccessToken().getToken()).build());
        current = service.findById(original.getId());
        assertThat(current.getAccessToken().isInvalidated()).isTrue();
        service.remove(current);
        assertThat(service.findById(original.getId())).isNull();
        assertThat(service.findByToken(replacement.getTokenValue(), null)).isNull();
    }

    @Test void expiredTokensAreNotExtendedAndSubMinuteCodesWork() throws Exception {
        OAuth2Authorization base = authorization();
        Instant now = Instant.now();
        OAuth2Authorization value = OAuth2Authorization.from(base)
            .token(new OAuth2AuthorizationCode("short-" + base.getId(), now.minusSeconds(30), now.plusSeconds(2)))
            .accessToken(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "expired-" + base.getId(),
                now.minusSeconds(60), now.minusSeconds(1))).build();
        service.save(value);
        assertThat(service.findByToken("short-" + base.getId(), null)).isNotNull();
        String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
            .digest(("short-" + base.getId()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThat(strings.getExpire(CacheConstants.OAUTH2_TOKEN_KEY + ":{iam-oauth2}:v2:code:" + digest,
            TimeUnit.MILLISECONDS)).isBetween(1L, 2000L);
        assertThat(service.findByToken("expired-" + base.getId(), null)).isNull();
    }

    @Test void concurrentUpdatesCannotBothConsumeTheSameRefreshToken() throws Exception {
        OAuth2Authorization original = authorization();
        service.save(original);
        OAuth2Authorization first = service.findById(original.getId());
        OAuth2Authorization second = service.findById(original.getId());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (OAuth2Authorization snapshot : List.of(first, second)) {
                results.add(executor.submit(() -> {
                    start.await();
                    try {
                        service.save(OAuth2Authorization.from(snapshot).refreshToken(new OAuth2RefreshToken(
                            UUID.randomUUID().toString(), Instant.now(), Instant.now().plusSeconds(60))).build());
                        return true;
                    } catch (OAuth2AuthenticationException ex) {
                        assertThat(ex.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_GRANT);
                        return false;
                    }
                }));
            }
            start.countDown();
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(true, false);
        } finally { executor.shutdownNow(); }
    }

    @Test void readsJackson2RecordsAndCustomAuthenticationWithJackson3() throws Exception {
        // Test-only Jackson 2 writer reproduces the previous wire format; production has no fallback mapper.
        var oldMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        oldMapper.registerModules(org.springframework.security.jackson2.SecurityJackson2Modules
            .getModules(getClass().getClassLoader()));
        oldMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        oldMapper.addMixIn(dev.macula.cloud.iam.authentication.captcha.CaptchaAuthenticationToken.class,
            LegacyAuthenticationMixin.class);
        oldMapper.addMixIn(dev.macula.cloud.iam.authentication.weapp.WeappAuthenticationToken.class,
            LegacyAuthenticationMixin.class);
        var oldRecordMapper = new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        var user = new dev.macula.cloud.iam.service.userdetails.SysUserDetails();
        user.setUsername("fixture-user"); user.setEnabled(true); user.setUserId(42L); user.setTenantId(1L);
        user.setAuthorities(List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("USER")));
        var tokens = List.of(
            new dev.macula.cloud.iam.authentication.captcha.CaptchaAuthenticationToken(user, null, user.getAuthorities()),
            new dev.macula.cloud.iam.authentication.captcha.CaptchaAuthenticationToken("fixture-user", null),
            new dev.macula.cloud.iam.authentication.weapp.WeappAuthenticationToken(user, user.getAuthorities()),
            new dev.macula.cloud.iam.authentication.weapp.WeappAuthenticationToken("fixture-user"));
        for (var token : tokens) {
            token.setDetails(new org.springframework.security.web.authentication.WebAuthenticationDetails("127.0.0.1", "fixture"));
            OAuth2Authorization original = OAuth2Authorization.from(authorization())
                .attribute(java.security.Principal.class.getName(), token).attribute("longValue", 42L).build();
            var conversion = MaculaOAuth2AuthorizationService.class.getDeclaredMethod("toEntity", OAuth2Authorization.class);
            conversion.setAccessible(true);
            Authorization record = (Authorization) conversion.invoke(service, original);
            record.setAttributes(oldMapper.writeValueAsString(original.getAttributes()));
            record.setAccessTokenMetadata(oldMapper.writeValueAsString(original.getAccessToken().getMetadata()));
            String key = CacheConstants.OAUTH2_TOKEN_KEY + ":{iam-oauth2}:v2:authorization:" +
                HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(original.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            strings.opsForValue().set(key, oldRecordMapper.writeValueAsString(record), java.time.Duration.ofSeconds(60));
            var restored = service.findById(original.getId());
            assertThat(restored.getAccessToken().getToken().getIssuedAt()).isEqualTo(original.getAccessToken().getToken().getIssuedAt());
            org.springframework.security.core.Authentication principal = restored.getAttribute(java.security.Principal.class.getName());
            assertThat(principal.getClass()).isEqualTo(token.getClass());
            assertThat(principal.isAuthenticated()).isEqualTo(token.isAuthenticated());
            assertThat(principal.getAuthorities()).isEqualTo(token.getAuthorities());
            assertThat(principal.getDetails()).isEqualTo(token.getDetails());
            assertThat((Object) restored.getAttribute("longValue")).isEqualTo(42L);
            if (token.isAuthenticated()) {
                assertThat(((dev.macula.cloud.iam.service.userdetails.SysUserDetails) principal.getPrincipal()).getUserId()).isEqualTo(42L);
            }
            service.save(restored);
            assertThat(service.findById(original.getId()).getAccessToken().getToken()).isEqualTo(restored.getAccessToken().getToken());
        }
    }

    @com.fasterxml.jackson.annotation.JsonTypeInfo(use = com.fasterxml.jackson.annotation.JsonTypeInfo.Id.CLASS, property = "@class")
    @com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY,
        getterVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE)
    abstract static class LegacyAuthenticationMixin { }

    @Test void rejectsUnapprovedPolymorphicType() throws Exception {
        var field = MaculaOAuth2AuthorizationService.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        var mapper = (tools.jackson.databind.ObjectMapper) field.get(service);
        assertThatThrownBy(() -> mapper.readValue("{\"@class\":\"java.util.HashMap\",\"unexpected\":{\"@class\":\"java.io.File\",\"path\":\"fixture\"}}",
            new tools.jackson.core.type.TypeReference<Map<String, Object>>() { }))
            .isInstanceOf(tools.jackson.databind.exc.InvalidTypeIdException.class);
    }

    @Test void serializesAuthenticatedPrincipalAndAuthorizationRequest() {
        var user = new dev.macula.cloud.iam.service.userdetails.SysUserDetails();
        user.setUsername("fixture-user"); user.setNickname("测试用户"); user.setEnabled(true);
        user.setTenantId(1L); user.setUserId(42L); user.setAuthorities(List.of(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("USER")));
        var principal = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
            user, null, user.getAuthorities());
        var request = org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri("https://iam.example/oauth2/authorize").clientId(client.getClientId())
            .redirectUri("https://client.example/callback").scopes(Set.of("openid"))
            .additionalParameters(Map.of("code_challenge", "fixture-challenge", "code_challenge_method", "S256"))
            .state("fixture-state").build();
        OAuth2Authorization original = OAuth2Authorization.from(authorization())
            .attribute(java.security.Principal.class.getName(), principal)
            .attribute(org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest.class.getName(), request)
            .build();
        service.save(original);
        OAuth2Authorization result = service.findById(original.getId());
        org.springframework.security.core.Authentication restored = result.getAttribute(java.security.Principal.class.getName());
        assertThat(restored.getPrincipal()).isInstanceOf(dev.macula.cloud.iam.service.userdetails.SysUserDetails.class);
        assertThat(((dev.macula.cloud.iam.service.userdetails.SysUserDetails) restored.getPrincipal()).getUserId()).isEqualTo(42);
        org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest restoredRequest = result.getAttribute(
            org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest.class.getName());
        assertThat(restoredRequest.getAdditionalParameters()).containsEntry("code_challenge_method", "S256");
    }

    @Test void legacySnapshotCannotResurrectRotatedOrDeletedAuthorization() throws Exception {
        OAuth2Authorization original = authorization();
        var conversion = MaculaOAuth2AuthorizationService.class.getDeclaredMethod("toEntity", OAuth2Authorization.class);
        conversion.setAccessible(true);
        Authorization old = (Authorization) conversion.invoke(service, original);
        String oldRefresh = original.getRefreshToken().getToken().getTokenValue();
        legacy.opsForValue().set(CacheConstants.OAUTH2_TOKEN_KEY + ":refresh_token:" + oldRefresh, old, 120, TimeUnit.SECONDS);
        OAuth2Authorization restored = service.findByToken(oldRefresh, null);
        assertThat(restored).isNotNull();
        OAuth2RefreshToken replacement = new OAuth2RefreshToken(UUID.randomUUID().toString(), Instant.now(), Instant.now().plusSeconds(80));
        service.save(OAuth2Authorization.from(restored).refreshToken(replacement).build());
        assertThat(service.findByToken(oldRefresh, null)).isNull();
        service.remove(service.findById(original.getId()));
        assertThat(service.findByToken(oldRefresh, null)).isNull();
        assertThat(service.findByToken(replacement.getTokenValue(), null)).isNull();
    }

    @Test void resavingDoesNotExtendStateOrTokenExpiry() throws Exception {
        OAuth2Authorization original = authorization();
        service.save(original);
        var before = service.findById(original.getId());
        Object stateExpiry = before.getAttribute("macula.authorization.stateExpiresAt");
        service.save(before);
        var after = service.findById(original.getId());
        assertThat((Object) after.getAttribute("macula.authorization.stateExpiresAt")).isEqualTo(stateExpiry);
        assertThat(after.getAccessToken().getToken().getExpiresAt()).isEqualTo(original.getAccessToken().getToken().getExpiresAt());
    }
}
