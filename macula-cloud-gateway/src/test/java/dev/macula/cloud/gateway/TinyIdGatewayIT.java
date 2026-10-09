/*
 * Copyright (c) 2026 Macula
 * macula.dev, China
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
package dev.macula.cloud.gateway;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import dev.macula.boot.constants.CacheConstants;
import dev.macula.boot.starter.cloud.gateway.filter.AddJwtGlobalFilter;
import dev.macula.boot.starter.cloud.gateway.filter.KongApiGlobalFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.filter.factory.StripPrefixGatewayFilterFactory;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 使用真实路由谓词和签名/JWT 过滤器，隔离 Redis 与下游服务验证号段链路。
 * @author Rain
 * @since 6.1.0
 */
class TinyIdGatewayIT {

    private static final String PATH = "/tinyid/api/v1/id/nextSegmentIdSimple";
    private static final String SERVICE_PATH = "/api/v1/id/nextSegmentIdSimple";
    private static final String SECRET = "test-only-app-secret";

    @Test
    void configuredRouteStripsPrefixAndIncludesExactlyFourIssuingApis() throws Exception {
        var source = new YamlPropertySourceLoader().load("gateway", new ClassPathResource("application.yml")).get(0);
        String predicate = (String) source.getProperty(
            "spring.cloud.gateway.server.webflux.routes[1].predicates[0]");
        assertThat(predicate).isEqualTo("Path=/tinyid/api/v1/admin/**,/tinyid/api/v1/id/nextId,"
            + "/tinyid/api/v1/id/nextIdSimple,/tinyid/api/v1/id/nextSegmentId," + PATH);
        assertThat(source.getProperty("spring.cloud.gateway.server.webflux.routes[1].filters[0]"))
            .isEqualTo("StripPrefix=1");
        var config = new PathRoutePredicateFactory.Config().setPatterns(
            Arrays.asList(predicate.substring("Path=".length()).split(",")));
        var matches = new PathRoutePredicateFactory(
            new org.springframework.boot.webflux.autoconfigure.WebFluxProperties()).apply(config);
        assertThat(matches.test(MockServerWebExchange.from(MockServerHttpRequest.post(PATH)))).isTrue();
        assertThat(stripPrefix(MockServerWebExchange.from(MockServerHttpRequest.post(PATH)))
            .getRequest().getURI().getPath()).isEqualTo(SERVICE_PATH);
        assertThat(stripPrefix(MockServerWebExchange.from(MockServerHttpRequest.get("/tinyid/api/v1/admin/businesses")))
            .getRequest().getURI().getPath()).isEqualTo("/api/v1/admin/businesses");
        for (String endpoint : Arrays.asList("nextId", "nextIdSimple", "nextSegmentId", "nextSegmentIdSimple")) {
            String path = "/tinyid/api/v1/id/" + endpoint;
            assertThat(matches.test(MockServerWebExchange.from(MockServerHttpRequest.post(path)))).isTrue();
            assertThat(stripPrefix(MockServerWebExchange.from(MockServerHttpRequest.post(path)))
                .getRequest().getURI().getPath()).isEqualTo("/api/v1/id/" + endpoint);
        }
        assertThat(matches.test(MockServerWebExchange.from(MockServerHttpRequest.post("/tinyid/api/v1/id/unknown"))))
            .isFalse();
    }

    @Test
    void validatesSignatureThenForwardsVerifiableJwt() throws Exception {
        RedisTemplate<String, Object> redis = redis();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        RSAKey key = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
            .privateKey((RSAPrivateKey) pair.getPrivate()).keyID("test-key").build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        var jwtFilter = new AddJwtGlobalFilter(encoder, claims -> { }, redis);
        ReflectionTestUtils.setField(jwtFilter, "issuerUri", "https://test.invalid");
        var signatureFilter = new KongApiGlobalFilter(redis);
        AtomicReference<String> forwarded = new AtomicReference<>();
        var exchange = signed(SECRET, Instant.now());
        signatureFilter.filter(exchange, verified -> jwtFilter.filter(verified, downstream -> {
            assertThat(downstream.getRequest().getURI().getPath()).isEqualTo(SERVICE_PATH);
            forwarded.set(downstream.getRequest().getHeaders().getFirst("Authorization"));
            return Mono.empty();
        })).block();
        assertThat(forwarded.get()).startsWith("Bearer ");
        var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) pair.getPublic()).build();
        assertThat(decoder.decode(forwarded.get().substring(7)).getSubject()).isEqualTo("test-app");
    }

    @Test
    void rejectsInvalidExpiredAndForbiddenRequests() throws Exception {
        RedisTemplate<String, Object> redis = redis();
        var filter = new KongApiGlobalFilter(redis);
        for (var exchange : List.of(signed("wrong-secret", Instant.now()),
            signed(SECRET, Instant.now().minusSeconds(600)))) {
            filter.filter(exchange, next -> {
                throw new AssertionError("Rejected request reached downstream");
            }).block();
            assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(403);
        }
        when(redis.<String, String>opsForHash().entries(CacheConstants.SECURITY_SYSTEM_APPS + "test-app"))
            .thenReturn(Map.of(CacheConstants.SECURITY_SYSTEM_APPS_SECRIT_KEY, SECRET,
                CacheConstants.SECURITY_SYSTEM_APPS_PERMIT_URLS, "POST:/other/**"));
        var forbidden = signed(SECRET, Instant.now());
        filter.filter(forbidden, next -> { throw new AssertionError("Forbidden URL reached downstream"); }).block();
        assertThat(forbidden.getResponse().getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void propagatesDownstreamFailure() throws Exception {
        var filter = new KongApiGlobalFilter(redis());
        var exchange = signed(SECRET, Instant.now());
        IllegalStateException unavailable = new IllegalStateException("downstream unavailable");
        assertThatThrownBy(() -> filter.filter(exchange, next -> Mono.error(unavailable)).block())
            .isSameAs(unavailable);
    }

    @SuppressWarnings("unchecked")
    private static RedisTemplate<String, Object> redis() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class, RETURNS_DEEP_STUBS);
        when(redis.<String, String>opsForHash().entries(CacheConstants.SECURITY_SYSTEM_APPS + "test-app"))
            .thenReturn(Map.of(CacheConstants.SECURITY_SYSTEM_APPS_SECRIT_KEY, SECRET,
                CacheConstants.SECURITY_SYSTEM_APPS_PERMIT_URLS, "POST:" + SERVICE_PATH));
        when(redis.opsForValue().get(anyString())).thenReturn(null);
        return redis;
    }

    private static ServerWebExchange signed(String secret, Instant instant) throws Exception {
        String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(instant.atZone(ZoneOffset.UTC));
        String uri = PATH + "?bizType=order";
        String digest = "SHA-256=" + Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(new byte[0]));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getEncoder().encodeToString(mac.doFinal(
            ("date: " + date + "\nPOST " + SERVICE_PATH + "?bizType=order HTTP/1.1\ndigest: " + digest)
                .getBytes(StandardCharsets.UTF_8)));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post(uri).header("Date", date)
            .header("Digest", digest).header("Authorization",
                "hmac username=\"test-app\", algorithm=\"hmac-sha256\", headers=\"date request-line digest\", signature=\""
                    + signature + "\""));
        exchange.getAttributes().put(
            dev.macula.boot.starter.cloud.gateway.constants.GatewayConstants.CACHED_REQUEST_BODY_OBJECT_KEY,
            new byte[0]);
        return stripPrefix(exchange);
    }

    private static ServerWebExchange stripPrefix(ServerWebExchange exchange) {
        var config = new StripPrefixGatewayFilterFactory.Config();
        config.setParts(1);
        AtomicReference<ServerWebExchange> stripped = new AtomicReference<>();
        new StripPrefixGatewayFilterFactory().apply(config).filter(exchange, next -> {
            stripped.set(next);
            return Mono.empty();
        }).block();
        return stripped.get();
    }
}
