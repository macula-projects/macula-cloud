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
package dev.macula.cloud.system.service.impl;

import com.sun.net.httpserver.HttpServer;
import dev.macula.cloud.system.config.OAuth2IssuerMetadataConfiguration;
import dev.macula.cloud.system.config.OAuth2IssuerMetadataProperties;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通过隔离HTTP服务器验证元数据缓存、大小、重定向与Issuer边界。
 *
 * @author Rain
 * @since 6.1.0
 */
class OAuth2IssuerMetadataServiceImplTest {
    /** 只缓存验证成功的白名单数据，不携带认证、不跟随重定向。 */
    @Test
    void validatesAndCachesOnlyTrustedMetadata() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        String issuer = "https://iam.example.test";
        server.createContext("/discovery", exchange -> {
            requests.incrementAndGet();
            assertEquals(null, exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = ("{\"issuer\":\"" + issuer + "\",\"jwks_uri\":\"" + issuer
                + "/keys\",\"secret\":\"must-not-expose\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "/discovery");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/huge", exchange -> {
            byte[] body = new byte[256 * 1024 + 1];
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/invalid-uri", exchange -> {
            byte[] body = ("{\"issuer\":\"" + issuer + "\",\"jwks_uri\":\"javascript:alert(1)\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();
        try {
            OAuth2IssuerMetadataProperties properties = new OAuth2IssuerMetadataProperties();
            properties.setAllowHttp(true);
            properties.setExpectedIssuer(issuer);
            String root = "http://127.0.0.1:" + server.getAddress().getPort();
            properties.setDiscoveryUrl(root + "/discovery");
            var rest = new OAuth2IssuerMetadataConfiguration().issuerMetadataClient(properties);
            var service = new OAuth2IssuerMetadataServiceImpl(properties, rest);
            assertTrue(service.get().available());
            assertFalse(service.get().endpoints().containsKey("secret"));
            assertEquals(1, requests.get());
            properties.setDiscoveryUrl(root + "/redirect");
            assertFalse(new OAuth2IssuerMetadataServiceImpl(properties, rest).get().available());
            assertEquals(1, requests.get());
            properties.setDiscoveryUrl(root + "/huge");
            assertFalse(new OAuth2IssuerMetadataServiceImpl(properties, rest).get().available());
            properties.setDiscoveryUrl(root + "/discovery");
            properties.setExpectedIssuer("https://wrong.example.test");
            assertFalse(new OAuth2IssuerMetadataServiceImpl(properties, rest).get().available());
            properties.setAllowHttp(false);
            assertFalse(new OAuth2IssuerMetadataServiceImpl(properties, rest).get().available());
            properties.setAllowHttp(true);
            properties.setExpectedIssuer(issuer);
            properties.setCacheSeconds(0);
            var expired = new OAuth2IssuerMetadataServiceImpl(properties, rest);
            assertTrue(expired.get().available());
            assertTrue(expired.get().available());
            assertEquals(4, requests.get());
            properties.setDiscoveryUrl(root + "/invalid-uri");
            assertFalse(new OAuth2IssuerMetadataServiceImpl(properties, rest).get().available());
            properties.setDiscoveryUrl(root + "/slow");
            properties.setReadTimeoutMillis(30);
            var bounded = new OAuth2IssuerMetadataConfiguration().issuerMetadataClient(properties);
            assertFalse(new OAuth2IssuerMetadataServiceImpl(properties, bounded).get().available());
        } finally {
            server.stop(0);
        }
    }
    /** 带路径issuer按两个标准形成独立发现地址，OAuth失败不能借用OIDC响应。 */
    @Test
    void protocolDiscoveryPathsAndCachesAreIndependent() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String issuer = "https://iam.example.test/tenant";
        AtomicInteger oidc = new AtomicInteger(), oauth = new AtomicInteger();
        server.createContext("/tenant/.well-known/openid-configuration", exchange -> {
            oidc.incrementAndGet();
             byte[] body = ("{\"issuer\":\"" + issuer + "\",\"userinfo_endpoint\":\"" + issuer + "/userinfo\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
             exchange.getResponseBody().write(body);
             exchange.close();
        });
        server.createContext("/.well-known/oauth-authorization-server/tenant", exchange -> {
            oauth.incrementAndGet();
             exchange.sendResponseHeaders(503, -1);
             exchange.close();
        });
        server.start();
        try {
            var properties = new OAuth2IssuerMetadataProperties();
             properties.setAllowHttp(true);
             properties.setExpectedIssuer(issuer);
            properties.setDiscoveryUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/tenant/.well-known/openid-configuration");
            var service = new OAuth2IssuerMetadataServiceImpl(properties, new OAuth2IssuerMetadataConfiguration().issuerMetadataClient(properties));
            assertEquals(issuer + "/.well-known/openid-configuration", service.get("OIDC").endpoints().get("discovery"));
            assertFalse(service.get("OAUTH2_1").available());
             assertTrue(service.get("OIDC").available());
            assertEquals(1, oidc.get());
             assertEquals(1, oauth.get());
        } finally { server.stop(0); }
    }

}
