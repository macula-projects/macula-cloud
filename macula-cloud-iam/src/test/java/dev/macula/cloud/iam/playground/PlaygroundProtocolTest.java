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

package dev.macula.cloud.iam.playground;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.authentication.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.token.*;
import dev.macula.cloud.iam.protocol.oauth2.CustomOAuth2TokenCustomizer;
import static org.assertj.core.api.Assertions.*;

/**
 * 用真实框架 introspection provider 验证隔离矩阵，不将其冒充 HTTP 集成测试。
 * @author Rain
 * @since 6.1.0
 */
class PlaygroundProtocolTest {
    @Test void clientBacksOffOnActualHttpSlowDownResponseWithoutOverlappingPolls() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        byte[] error = "{\"error\":\"slow_down\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        server.createContext("/oauth2/token", exchange -> {
            calls.incrementAndGet(); exchange.sendResponseHeaders(400, error.length);
            exchange.getResponseBody().write(error); exchange.close();
        });
        server.start();
        try {
            var http = httpClient(server);
            var sessions = new PlaygroundSessionStore();
            var session = new org.springframework.mock.web.MockHttpSession();
            var flow = sessions.create(session, PlaygroundFlow.Scenario.DEVICE);
            flow.device("fixture-device", 5, 300, Instant.now().minusSeconds(6));
            var service = new PlaygroundFlowService(http, new PlaygroundOidcVerifier(http), sessions,
                new InMemoryRegisteredClientRepository(client(PlaygroundRegisteredClientRepository.DEVICE)), new PlaygroundProperties());
            var response = service.action(session, flow.id(), "poll");
            assertThat(response.transcript().body()).containsEntry("error", "slow_down");
            assertThat(flow.pollIntervalSeconds()).isEqualTo(10);
            assertThat(response.pollAfterSeconds()).isBetween(9L, 11L);
            assertThatThrownBy(() -> service.action(session, flow.id(), "poll")).isInstanceOf(IllegalStateException.class);
            assertThat(calls.get()).isEqualTo(1);
        } finally { server.stop(0); }
    }

    @Test void httpClientNeverFollowsRedirectsAndCapsResponseSize() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        var destinationCalls = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/oauth2/token", exchange -> {
            exchange.getResponseHeaders().set("Location", "/destination");
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.createContext("/destination", exchange -> { destinationCalls.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close(); });
        server.createContext("/oauth2/jwks", exchange -> {
            byte[] bytes = new byte[300000];
            exchange.sendResponseHeaders(200, bytes.length);
            try { exchange.getResponseBody().write(bytes); } finally { exchange.close(); }
        });
        server.start();
        try {
            var http = httpClient(server);
            var result = http.call(PlaygroundProtocolClient.Endpoint.TOKEN, Map.of("password", "fixture-sensitive"), null, null, null);
            assertThat(result.status()).isEqualTo(302);
            assertThat(result.successful()).isFalse();
            assertThat(destinationCalls.get()).isZero();
            assertThat(result.toString()).doesNotContain("fixture-sensitive");
            assertThat(http.call(PlaygroundProtocolClient.Endpoint.JWKS, Map.of(), null, null, null).status()).isZero();
        } finally { server.stop(0); }
    }

    @Test void oidcRejectsWrongSignatureIssuerAudienceNonceAndMissingOrExpiredClaims() throws Exception {
        var key = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).keyID("fixture-signing").generate();
        var wrongKey = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).keyID("fixture-signing").generate();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        byte[] jwks = new com.nimbusds.jose.jwk.JWKSet(key.toPublicJWK()).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        server.createContext("/oauth2/jwks", exchange -> {
            exchange.sendResponseHeaders(200, jwks.length); exchange.getResponseBody().write(jwks); exchange.close();
        });
        server.start();
        try {
            var http = httpClient(server);
            var verifier = new PlaygroundOidcVerifier(http);
            for (String variant : List.of("valid", "signature", "issuer", "audience", "nonce", "expired", "missing-exp", "refresh")) {
                Instant now = Instant.now();
                var claims = new com.nimbusds.jwt.JWTClaimsSet.Builder().subject("fixture-user")
                    .issuer("issuer".equals(variant) ? "https://other.example" : http.issuer())
                    .audience("audience".equals(variant) ? "other-client" : "fixture-client")
                    .issueTime(java.util.Date.from(now.minusSeconds(300)));
                if (!"missing-exp".equals(variant)) claims.expirationTime(java.util.Date.from(now.plusSeconds("expired".equals(variant) ? -120 : 60)));
                if (!"refresh".equals(variant)) claims.claim("nonce", "nonce".equals(variant) ? "wrong" : "fixture-nonce");
                var signed = new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256)
                    .keyID(key.getKeyID()).build(), claims.build());
                signed.sign(new com.nimbusds.jose.crypto.RSASSASigner("signature".equals(variant) ? wrongKey : key));
                String token = signed.serialize();
                if ("valid".equals(variant)) {
                    assertThat(verifier.verify(token, "fixture-client", "fixture-nonce").getSubject()).isEqualTo("fixture-user");
                } else {
                    assertThatThrownBy(() -> verifier.verify(token, "fixture-client", "fixture-nonce"))
                        .as(variant).isInstanceOf(IllegalArgumentException.class);
                    if ("refresh".equals(variant)) assertThat(verifier.verify(token, "fixture-client", "fixture-nonce", true)
                        .getSubject()).isEqualTo("fixture-user");
                }
            }
        } finally { server.stop(0); }
    }

    private PlaygroundProtocolClient httpClient(com.sun.net.httpserver.HttpServer server) {
        var properties = new PlaygroundProperties(); properties.setEnabled(true); properties.setAllowedProfiles(Set.of("local"));
        properties.setAllowLoopbackHttp(true);
        var environment = new org.springframework.mock.env.MockEnvironment(); environment.setActiveProfiles("local");
        return new PlaygroundProtocolClient(new PlaygroundAccessPolicy(properties, environment),
            "http://127.0.0.1:" + server.getAddress().getPort());
    }

    private RegisteredClient client(String id) {
        return RegisteredClient.withId(id).clientId(id)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).scope("playground.read").build();
    }

    @Test void introspectionIsInactiveAcrossDemoBoundaryAndNormalBetweenBusinessClients() {
        var demo = client(PlaygroundRegisteredClientRepository.CONFIDENTIAL);
        var otherDemo = client(PlaygroundRegisteredClientRepository.PUBLIC);
        var business = client("business");
        var otherBusiness = client("gateway");
        var repository = new InMemoryRegisteredClientRepository(demo, otherDemo, business, otherBusiness);
        var authorizations = new InMemoryOAuth2AuthorizationService();
        var provider = new PlaygroundTokenIntrospectionAuthenticationProvider(
            new OAuth2TokenIntrospectionAuthenticationProvider(repository, authorizations), authorizations);
        for (var owner : List.of(demo, otherDemo, business, otherBusiness)) {
            String token = "fixture-" + owner.getId();
            Instant now = Instant.now();
            authorizations.save(OAuth2Authorization.withRegisteredClient(owner).principalName("fixture")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .accessToken(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, token, now, now.plusSeconds(60),
                    Set.of("playground.read"))).build());
            for (var caller : List.of(demo, otherDemo, business, otherBusiness)) {
                var principal = new OAuth2ClientAuthenticationToken(caller, ClientAuthenticationMethod.CLIENT_SECRET_BASIC, null);
                var request = new OAuth2TokenIntrospectionAuthenticationToken(token, principal, null, null);
                var response = (OAuth2TokenIntrospectionAuthenticationToken) provider.authenticate(request);
                boolean expected = owner.getId().equals(caller.getId())
                    || (!PlaygroundRegisteredClientRepository.isPlayground(owner.getId())
                        && !PlaygroundRegisteredClientRepository.isPlayground(caller.getId()));
                assertThat(response.getTokenClaims().isActive()).as("%s queries %s", caller.getId(), owner.getId())
                    .isEqualTo(expected);
                if (!expected) assertThat(response.getTokenClaims().getClaims()).containsOnlyKeys("active");
            }
        }
    }

    @Test void unauthenticatedIntrospectionStillFailsClientAuthentication() {
        var client = client(PlaygroundRegisteredClientRepository.CONFIDENTIAL);
        var repository = new InMemoryRegisteredClientRepository(client);
        var store = new InMemoryOAuth2AuthorizationService();
        var provider = new PlaygroundTokenIntrospectionAuthenticationProvider(
            new OAuth2TokenIntrospectionAuthenticationProvider(repository, store), store);
        var principal = new OAuth2ClientAuthenticationToken(client.getClientId(), ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
            "invalid-fixture", Map.of());
        assertThatThrownBy(() -> provider.authenticate(
            new OAuth2TokenIntrospectionAuthenticationToken("fixture", principal, null, null)))
            .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test void privilegedPrincipalDoesNotAddBusinessClaimsToDemoAccessToken() {
        var principal = UsernamePasswordAuthenticationToken.authenticated("fixture-admin", "unused",
            List.of(new SimpleGrantedAuthority("ROOT")));
        for (String id : List.of(PlaygroundRegisteredClientRepository.CONFIDENTIAL, "normal")) {
            var claims = OAuth2TokenClaimsSet.builder();
            var context = OAuth2TokenClaimsContext.with(claims).registeredClient(client(id)).principal(principal)
                .tokenType(OAuth2TokenType.ACCESS_TOKEN).build();
            new CustomOAuth2TokenCustomizer().customize(context);
            if (PlaygroundRegisteredClientRepository.isPlayground(id)) {
                assertThat(claims.build().getClaims()).containsExactlyInAnyOrderEntriesOf(Map.of("client_id", id));
            } else {
                assertThat(claims.build().getClaims().values()).contains(List.of("ROOT"));
            }
        }
    }
}
