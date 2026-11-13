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

package dev.macula.cloud.iam.protocol.oauth2.persistence;

import java.io.File;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.macula.boot.constants.CacheConstants;
import dev.macula.cloud.iam.authentication.email.EmailCodeAuthenticationToken;
import dev.macula.cloud.iam.authentication.sms.SmsAuthenticationToken;
import dev.macula.cloud.iam.pojo.dto.Authorization;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2DeviceCode;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2UserCode;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.exc.InvalidTypeIdException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 用独立临时 Redis 验证真实缓存 TTL、索引和并发更新，不连接业务 Redis。
 *
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
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        String executable = System.getenv().getOrDefault("TEST_REDIS_SERVER", "/opt/homebrew/bin/redis-server");
        Assumptions.assumeTrue(new File(executable).canExecute(),
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
            try {
                strings.getConnectionFactory().getConnection().ping();
                failure = null;
                break;
            } catch (Exception ex) {
                failure = ex;
                Thread.sleep(50);
            }
        }
        if (failure != null) {
            throw failure;
        }
        client = RegisteredClient.withId("test-client").clientId("test-client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("https://client.example/callback").scope("openid").build();
    }

    @AfterAll
    static void stopRedis() throws Exception {
        if (connection != null) {
            connection.destroy();
        }
        if (redisson != null) {
            redisson.shutdown();
        }
        if (redis != null) {
            redis.destroy();
        }
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

    @Test
    void redisConnectionFailureIsNotReportedAsMissingToken() {
        var factory = Mockito.mock(RedisConnectionFactory.class);
        Mockito.when(factory.getConnection()).thenThrow(
                new RedisConnectionFailureException("fixture unavailable"));
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.afterPropertiesSet();
        var unavailable = new MaculaOAuth2AuthorizationService(template,
                new InMemoryRegisteredClientRepository(client));
        assertThatThrownBy(() -> unavailable.findByToken("fixture", null))
                .isInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    void readsHistoricalJdkSerializedDtoAndMigratesIt() throws Exception {
        byte[] fixture;
        try (var input = getClass().getResourceAsStream("/fixtures/legacy-authorization.base64")) {
            assertThat(input).isNotNull();
            fixture = Base64.getMimeDecoder().decode(input.readAllBytes());
        }
        byte[] key = (CacheConstants.OAUTH2_TOKEN_KEY + ":access_token:legacy-binary-access")
                .getBytes(StandardCharsets.UTF_8);
        try (var redisConnection = connection.getConnection()) {
            redisConnection.stringCommands().setEx(key, 60, fixture);
        }
        assertThat(service.findByToken("legacy-binary-access", OAuth2TokenType.ACCESS_TOKEN)).isNull();
        assertThat(service.findById("legacy-binary-fixture")).isNull();
    }

    @Test
    void malformedStoredRecordIsNotReportedAsMissingToken() throws Exception {
        OAuth2Authorization original = authorization();
        service.save(original);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(original.getId().getBytes(StandardCharsets.UTF_8)));
        strings.opsForValue()
                .set(CacheConstants.OAUTH2_TOKEN_KEY + ":resource-v3:{iam-oauth2}:v3:authorization:" + hash,
                        "not-json", Duration.ofSeconds(60));
        assertThatThrownBy(() -> service.findById(original.getId())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.findByToken(original.getAccessToken().getToken().getTokenValue(), null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void findsEveryTypeAndDoesNotConfuseExplicitTypes() {
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

    @Test
    void rotationRevocationAndDeletionUseCurrentState() {
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

    @Test
    void expiredTokensAreNotExtendedAndSubMinuteCodesWork() throws Exception {
        OAuth2Authorization base = authorization();
        Instant now = Instant.now();
        OAuth2Authorization value = OAuth2Authorization.from(base)
                .token(new OAuth2AuthorizationCode("short-" + base.getId(), now.minusSeconds(30), now.plusSeconds(2)))
                .accessToken(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "expired-" + base.getId(),
                        now.minusSeconds(60), now.minusSeconds(1))).build();
        service.save(value);
        assertThat(service.findByToken("short-" + base.getId(), null)).isNotNull();
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(("short-" + base.getId()).getBytes(StandardCharsets.UTF_8)));
        assertThat(strings.getExpire(CacheConstants.OAUTH2_TOKEN_KEY + ":resource-v3:{iam-oauth2}:v3:code:" + digest,
                TimeUnit.MILLISECONDS)).isBetween(1L, 2000L);
        assertThat(service.findByToken("expired-" + base.getId(), null)).isNull();
    }

    @Test
    void concurrentUpdatesCannotBothConsumeTheSameRefreshToken() throws Exception {
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
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 用真实旧邮箱attributes恢复完整授权；读取不写回缓存，撤销保持有效。
     */
    @Test
    void restoresAndRevokesLegacyEmailAccessWithoutRewritingRecord() throws Exception {
        for (String version : List.of("2", "3")) {
            OAuth2Authorization original = OAuth2Authorization.from(authorization())
                    .authorizationGrantType(new AuthorizationGrantType("email")).build();
            service.save(original);
            var read = MaculaOAuth2AuthorizationService.class.getDeclaredMethod("readRecord", String.class);
            read.setAccessible(true);
            Authorization record = (Authorization) read.invoke(service, original.getId());
            try (var input = getClass().getResourceAsStream(
                    "/authorization/legacy-email-jackson" + version + "-attributes.json")) {
                record.setAttributes(new String(Objects.requireNonNull(input).readAllBytes(),
                        StandardCharsets.UTF_8));
            }
            var write = MaculaOAuth2AuthorizationService.class.getDeclaredMethod("writeRecord", Authorization.class);
            write.setAccessible(true);
            String json = (String) write.invoke(service, record);
            String key = CacheConstants.OAUTH2_TOKEN_KEY + ":resource-v3:{iam-oauth2}:v3:authorization:" +
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(original.getId().getBytes(StandardCharsets.UTF_8)));
            strings.opsForValue().set(key, json, Duration.ofSeconds(60));
            var restored = service.findByToken(original.getAccessToken().getToken().getTokenValue(),
                    OAuth2TokenType.ACCESS_TOKEN);
            assertThat(restored).isNotNull();
            assertThat(restored.getAccessToken().isActive()).isTrue();
            assertThat(restored.getAuthorizationGrantType().getValue()).isEqualTo("email");
            Authentication principal = restored.getAttribute(
                    Principal.class.getName());
            assertThat(principal.getName()).isEqualTo("fixture-user");
            assertThat(strings.opsForValue().get(key)).isEqualTo(json);
            service.remove(restored);
            assertThat(service.findByToken(original.getAccessToken().getToken().getTokenValue(),
                    OAuth2TokenType.ACCESS_TOKEN)).isNull();
        }
    }

    @Test
    void readsJackson2RecordsAndCustomAuthenticationWithJackson3() throws Exception {
        // Test-only Jackson 2 writer reproduces the previous wire format; production has no fallback mapper.
        var oldMapper = new ObjectMapper();
        oldMapper.registerModules(SecurityJackson2Modules
                .getModules(getClass().getClassLoader()));
        oldMapper.registerModule(new JavaTimeModule());
        oldMapper.addMixIn(SmsAuthenticationToken.class,
                LegacyAuthenticationMixin.class);
        var oldRecordMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        var user = new SysUserDetails();
        user.setUsername("fixture-user");
        user.setEnabled(true);
        user.setUserId(42L);
        user.setTenantId(1L);
        user.setAuthorities(List.of(new SimpleGrantedAuthority("USER")));
        var tokens = List.of(
                new SmsAuthenticationToken(user, null, user.getAuthorities()),
                new SmsAuthenticationToken("fixture-user", null));
        for (var token : tokens) {
            token.setDetails(new WebAuthenticationDetails("127.0.0.1",
                    "fixture"));
            OAuth2Authorization original = OAuth2Authorization.from(authorization())
                    .attribute(Principal.class.getName(), token).attribute("longValue", 42L).build();
            var conversion = MaculaOAuth2AuthorizationService.class.getDeclaredMethod("toEntity",
                    OAuth2Authorization.class);
            conversion.setAccessible(true);
            Authorization record = (Authorization) conversion.invoke(service, original);
            record.setAttributes(oldMapper.writeValueAsString(original.getAttributes()));
            record.setAccessTokenMetadata(oldMapper.writeValueAsString(original.getAccessToken().getMetadata()));
            String key = CacheConstants.OAUTH2_TOKEN_KEY + ":resource-v3:{iam-oauth2}:v3:authorization:" +
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(original.getId().getBytes(StandardCharsets.UTF_8)));
            strings.opsForValue()
                    .set(key, oldRecordMapper.writeValueAsString(record), Duration.ofSeconds(60));
            var restored = service.findById(original.getId());
            assertThat(restored.getAccessToken().getToken().getIssuedAt()).isEqualTo(
                    original.getAccessToken().getToken().getIssuedAt());
            Authentication principal = restored.getAttribute(
                    Principal.class.getName());
            assertThat(principal.getClass()).isEqualTo(token.getClass());
            assertThat(principal.isAuthenticated()).isEqualTo(token.isAuthenticated());
            assertThat(principal.getAuthorities()).isEqualTo(token.getAuthorities());
            assertThat(principal.getDetails()).isEqualTo(token.getDetails());
            assertThat((Object) restored.getAttribute("longValue")).isEqualTo(42L);
            if (token.isAuthenticated()) {
                assertThat(
                        ((SysUserDetails) principal.getPrincipal()).getUserId()).isEqualTo(
                        42L);
            }
            service.save(restored);
            assertThat(service.findById(original.getId()).getAccessToken().getToken()).isEqualTo(
                    restored.getAccessToken().getToken());
        }
    }

    /** 两种当前验证码主体通过生产授权Mapper恢复角色、details与真实认证时间。 */
    @Test
    void persistsCurrentVerificationCodePrincipalsAndFactors() {
        var user = new SysUserDetails();
        user.setUsername("fixture-user");
        user.setEnabled(true);
        user.setUserId(42L);
        user.setTenantId(1L);
        var authorities = List.of(new SimpleGrantedAuthority("USER"),
                FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.OTT_AUTHORITY));
        List<AbstractAuthenticationToken> tokens = List.of(new SmsAuthenticationToken(user, null, authorities),
                new EmailCodeAuthenticationToken(user, null, authorities));
        for (var token : tokens) {
            token.setDetails(new WebAuthenticationDetails("127.0.0.1", "fixture"));
            var original = OAuth2Authorization.from(authorization())
                    .attribute(Principal.class.getName(), token).build();
            service.save(original);
            Authentication restored = service.findById(original.getId()).getAttribute(Principal.class.getName());
            assertThat(restored).isInstanceOf(token.getClass());
            assertThat(restored.isAuthenticated()).isTrue();
            assertThat(restored.getCredentials()).isNull();
            assertThat(restored.getAuthorities()).isEqualTo(token.getAuthorities());
            assertThat(restored.getDetails()).isEqualTo(token.getDetails());
            assertThat(restored.getName()).isEqualTo("fixture-user");
            service.remove(service.findById(original.getId()));
            assertThat(service.findById(original.getId())).isNull();
        }
    }

    @Test
    void rejectsUnapprovedPolymorphicType() throws Exception {
        var field = MaculaOAuth2AuthorizationService.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        var mapper = (tools.jackson.databind.ObjectMapper) field.get(service);
        assertThatThrownBy(() -> mapper.readValue(
                "{\"@class\":\"java.util.HashMap\",\"unexpected\":{\"@class\":\"java.io.File\",\"path\":\"fixture\"}}",
                new TypeReference<Map<String, Object>>() {
                }))
                .isInstanceOf(InvalidTypeIdException.class);
    }

    @Test
    void serializesAuthenticatedPrincipalAndAuthorizationRequest() {
        var user = new SysUserDetails();
        user.setUsername("fixture-user");
        user.setNickname("测试用户");
        user.setEnabled(true);
        user.setTenantId(1L);
        user.setUserId(42L);
        user.setAuthorities(List.of(
                new SimpleGrantedAuthority("USER")));
        var principal = UsernamePasswordAuthenticationToken.authenticated(
                user, null, user.getAuthorities());
        var request = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://iam.example/oauth2/authorize").clientId(client.getClientId())
                .redirectUri("https://client.example/callback").scopes(Set.of("openid"))
                .additionalParameters(Map.of("code_challenge", "fixture-challenge", "code_challenge_method", "S256"))
                .state("fixture-state").build();
        OAuth2Authorization original = OAuth2Authorization.from(authorization())
                .attribute(Principal.class.getName(), principal)
                .attribute(OAuth2AuthorizationRequest.class.getName(),
                        request)
                .build();
        service.save(original);
        OAuth2Authorization result = service.findById(original.getId());
        Authentication restored = result.getAttribute(
                Principal.class.getName());
        assertThat(restored.getPrincipal()).isInstanceOf(SysUserDetails.class);
        assertThat(
                ((SysUserDetails) restored.getPrincipal()).getUserId()).isEqualTo(
                42);
        OAuth2AuthorizationRequest restoredRequest =
                result.getAttribute(
                OAuth2AuthorizationRequest.class.getName());
        assertThat(restoredRequest.getAdditionalParameters()).containsEntry("code_challenge_method", "S256");
    }

    @Test
    void legacySnapshotCannotResurrectRotatedOrDeletedAuthorization() throws Exception {
        OAuth2Authorization original = authorization();
        var conversion = MaculaOAuth2AuthorizationService.class.getDeclaredMethod("toEntity",
                OAuth2Authorization.class);
        conversion.setAccessible(true);
        Authorization old = (Authorization) conversion.invoke(service, original);
        String oldRefresh = original.getRefreshToken().getToken().getTokenValue();
        legacy.opsForValue()
                .set(CacheConstants.OAUTH2_TOKEN_KEY + ":refresh_token:" + oldRefresh, old, 120, TimeUnit.SECONDS);
        assertThat(service.findByToken(oldRefresh, null)).isNull();
        service.save(original);
        OAuth2Authorization restored = service.findByToken(oldRefresh, null);
        assertThat(restored).isNotNull();
        OAuth2RefreshToken replacement = new OAuth2RefreshToken(UUID.randomUUID().toString(), Instant.now(),
                Instant.now().plusSeconds(80));
        service.save(OAuth2Authorization.from(restored).refreshToken(replacement).build());
        assertThat(service.findByToken(oldRefresh, null)).isNull();
        service.remove(service.findById(original.getId()));
        assertThat(service.findByToken(oldRefresh, null)).isNull();
        assertThat(service.findByToken(replacement.getTokenValue(), null)).isNull();
    }

    @Test
    void resavingDoesNotExtendStateOrTokenExpiry() throws Exception {
        OAuth2Authorization original = authorization();
        service.save(original);
        var before = service.findById(original.getId());
        Object stateExpiry = before.getAttribute("macula.authorization.stateExpiresAt");
        service.save(before);
        var after = service.findById(original.getId());
        assertThat((Object) after.getAttribute("macula.authorization.stateExpiresAt")).isEqualTo(stateExpiry);
        assertThat(after.getAccessToken().getToken().getExpiresAt()).isEqualTo(
                original.getAccessToken().getToken().getExpiresAt());
    }

    /**
     * 旧Jackson认证序列化结构，仅用于授权记录恢复回归。
     *
     * @since 6.1.0
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS,
            property = "@class")
    @JsonAutoDetect(fieldVisibility =
            JsonAutoDetect.Visibility.ANY,
            getterVisibility = JsonAutoDetect.Visibility.NONE,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    abstract static class LegacyAuthenticationMixin {
    }
}
