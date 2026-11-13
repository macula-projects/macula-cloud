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
package dev.macula.cloud.system.protocol;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import dev.macula.cloud.system.enums.ExternalCodeType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 中立策略分派、v1兼容、v2类型边界和资源管理身份的拒绝路径。
 * @author Rain
 * @since 6.1.0
 */
class IamApplicationProtocolRegistryTest {
    private static final Instant ISSUED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private final JsonMapper json = JsonMapper.builder().build();
    private final IamApplicationProtocolRegistry registry = OAuth2ProtocolDefinition.registry();

    /** 独立设置与策略证明通用层不依赖OAuth类型或固定产品目录。 */
    @Test
    void dispatchesIndependentDefinitionAndRejectsDuplicates() {
        var definition = new TestDefinition();
        var independent = new IamApplicationProtocolRegistry(List.of(definition));
        var registration = independent.read("TEST_ONLY", 3, true, "synthetic", ISSUED_AT, "allowed", "WEB_SERVER", "APPLICATION");
        assertEquals(new TestSettings("allowed"), registration.settings());
        assertEquals("allowed", independent.serialize(registration, "WEB_SERVER", "APPLICATION"));
        assertFalse(independent.requiresSecret(registration));
        assertThrows(IllegalArgumentException.class, () -> new IamApplicationProtocolRegistry(List.of(definition, definition)));
        assertThrows(IllegalArgumentException.class, () -> independent.read("TEST_ONLY", 2, false, null, null, "allowed", "WEB_SERVER", "APPLICATION"));
        assertThrows(IllegalArgumentException.class, () -> independent.read("TEST_ONLY", 3, true, null, null, "denied", "WEB_SERVER", "APPLICATION"));
    }

    /** 产品目录只发布已实现类型，未知协议和结构版本不得回退。 */
    @Test
    void productCatalogContainsOnlyImplementedProtocols() {
        assertEquals(List.of("OIDC", "OAUTH2_1"), registry.definitions().stream().map(ApplicationProtocolDefinition::protocol).toList());
        for (String protocol : List.of("SAML2", "CAS", "java.lang.Runtime", "oidc")) {
            assertThrows(IllegalArgumentException.class, () -> registry.require(protocol));
        }
        assertThrows(IllegalArgumentException.class, () -> registry.read("OIDC", 1, false, null, null, "{}", "SPA", "APPLICATION"));
    }

    /** 空协议保留标识时间，但历史槽永远不能执行、启用或重新作为Settings写入。 */
    @Test
    void unassignedLegacySlotIsNeverExecutable() {
        var registration = registry.read(null, 0, false, "OriginalClient", ISSUED_AT, "{}", "WEB_SERVER", "APPLICATION");
        assertFalse(registration.assigned());
        assertEquals("OriginalClient", registration.protocolIdentifier());
        assertEquals(ISSUED_AT, registration.identifierIssuedAt());
        assertEquals(null, registration.settings());
        assertThrows(IllegalArgumentException.class, () -> registry.read(null, 0, true, null, null, "{}", "WEB_SERVER", "APPLICATION"));
        assertThrows(IllegalArgumentException.class, () -> registry.read(null, 2, false, null, null, "{}", "WEB_SERVER", "APPLICATION"));
        assertThrows(IllegalArgumentException.class, () -> registry.serialize(registration, "WEB_SERVER", "APPLICATION"));
        assertThrows(IllegalArgumentException.class, () -> registry.requiresSecret(registration));
    }

    /** JSON解析在关闭草稿下也拒绝秘密字段、重复键、尾随值和多字节超限。 */
    @Test
    void rejectsAmbiguousAndOversizedPayloadsEvenWhenDisabled() {
        String valid = json.writeValueAsString(settings("none", "authorization_code"));
        String duplicate = valid.substring(0, valid.length() - 1) + ",\"externalCodeTypes\":[]}";
        String secret = valid.substring(0, valid.length() - 1) + ",\"credential_hash\":\"synthetic-only\"}";
        for (String payload : List.of(duplicate, secret, valid + "{}", "汉".repeat(12000), "null")) {
            assertThrows(RuntimeException.class, () -> registry.read("OIDC", 2, false, null, null, payload, "SPA", "APPLICATION"));
        }
        var wrong = new IamApplicationProtocolRegistration<>("OIDC", 2, false, null, null, new TestSettings("allowed"));
        assertThrows(ClassCastException.class, () -> registry.serialize(wrong, "SPA", "APPLICATION"));
    }

    /** external_code的授权许可归协议；来源路由不会自动带来授予权利。 */
    @Test
    void externalCodePermissionMustAgreeWithGrant() {
        var settings = settings("none", ExternalCodeType.GRANT_TYPE);
        settings.setExternalCodeTypes(Set.of(ExternalCodeType.FEISHU_WEBVIEW_CODE));
        assertDoesNotThrow(() -> roundTrip("OIDC", settings, true, "SPA", "APPLICATION"));
        settings.getOauth2().setAuthorizationGrantTypes("authorization_code");
        assertThrows(IllegalArgumentException.class, () -> roundTrip("OIDC", settings, false, "SPA", "APPLICATION"));
        settings.getOauth2().setAuthorizationGrantTypes(ExternalCodeType.GRANT_TYPE);
        settings.setExternalCodeTypes(Set.of());
        assertThrows(IllegalArgumentException.class, () -> roundTrip("OIDC", settings, true, "SPA", "APPLICATION"));
        settings.getOauth2().setAuthorizationGrantTypes("email");
        assertThrows(IllegalArgumentException.class, () -> roundTrip("OAUTH2_1", settings, false, "SPA", "APPLICATION"));
    }

    /** 资源身份保留Secret或JWKS认证能力，但拒绝所有业务授予、回调和Scope。 */
    @Test
    void resourceManagementIdentityCannotBecomeBusinessClient() {
        var settings = settings("client_secret_basic", "");
        settings.getOauth2().setScopes("");
        settings.getOauth2().setRedirectUris("");
        var resource = roundTrip("OAUTH2_1", settings, true, "SERVICE", "RESOURCE_SERVER");
        assertTrue(registry.requiresSecret(resource));
        settings.getOauth2().setClientAuthenticationMethods("private_key_jwt");
        settings.getOauth2().setJwkSetUrl("https://synthetic.example/jwks");
        settings.getOauth2().setSigningAlgorithm("RS256");
        assertFalse(registry.requiresSecret(roundTrip("OAUTH2_1", settings, true, "SERVICE", "RESOURCE_SERVER")));
        for (String jwks : List.of("http://synthetic.example/jwks", "https://user@synthetic.example/jwks", "https://synthetic.example/jwks#fragment")) {
            settings.getOauth2().setJwkSetUrl(jwks);
            assertThrows(IllegalArgumentException.class, () -> roundTrip("OAUTH2_1", settings, false, "SERVICE", "RESOURCE_SERVER"));
        }
        settings.getOauth2().setJwkSetUrl("https://synthetic.example/jwks");
        for (String grant : List.of("authorization_code", "client_credentials", "refresh_token", ExternalCodeType.GRANT_TYPE)) {
            settings.getOauth2().setAuthorizationGrantTypes(grant);
            assertThrows(IllegalArgumentException.class, () -> roundTrip("OAUTH2_1", settings, false, "SERVICE", "RESOURCE_SERVER"));
        }
        settings.getOauth2().setAuthorizationGrantTypes("");
        assertThrows(IllegalArgumentException.class, () -> roundTrip("OIDC", settings, true, "SERVICE", "RESOURCE_SERVER"));
        settings.getOauth2().setScopes("system:access");
        assertThrows(IllegalArgumentException.class, () -> roundTrip("OAUTH2_1", settings, false, "SERVICE", "RESOURCE_SERVER"));
    }

    /** v1没有externalCodeTypes默认字段，历史版本和序列化合同均不被v2改变。 */
    @Test
    void preservesVersionOneContractAndSecretFreeView() {
        var old = new OAuth2ProtocolSettings();
        String original = json.writeValueAsString(old);
        assertFalse(original.contains("externalCodeTypes"));
        assertEquals(1, ApplicationProtocolRegistry.SCHEMA_VERSION);
        assertEquals(old, json.readValue(original, OAuth2ProtocolSettings.class));
        var current = settings("none", "authorization_code");
        String value = json.writeValueAsString(roundTrip("OIDC", current, true, "SPA", "APPLICATION"));
        assertFalse(value.contains("credential_hash"));
        assertFalse(value.contains("clientSecret"));
        assertFalse(value.contains("credentialHash"));
    }

    /** 使用真实产品注册表验证读写结果相同。 */
    private IamApplicationProtocolRegistration<?> roundTrip(String protocol, OAuth2ApplicationProtocolSettings settings,
        boolean enabled, String type, String purpose) {
        var registration = registry.read(protocol, 2, enabled, "OriginalClient", ISSUED_AT,
            json.writeValueAsString(settings), type, purpose);
        assertEquals(settings, registration.settings());
        assertEquals(json.writeValueAsString(settings), registry.serialize(registration, type, purpose));
        return registration;
    }

    /** 构造合成OAuth配置，不包含真实应用数据。 */
    private OAuth2ApplicationProtocolSettings settings(String auth, String grants) {
        var oauth = new OAuth2ProtocolSettings();
        oauth.setClientAuthenticationMethods(auth);
        oauth.setAuthorizationGrantTypes(grants);
        oauth.setScopes("openid,profile");
        oauth.setRedirectUris("https://synthetic.example/callback");
        oauth.setRequireProofKey(true);
        oauth.setRequireAuthorizationConsent(false);
        var result = new OAuth2ApplicationProtocolSettings();
        result.setOauth2(oauth);
        return result;
    }

    /**
     * 与OAuth无关的合成测试配置。
     * @since 6.1.0
     */
    private record TestSettings(String policy) { }

    /**
     * 仅用于验证策略端口，不进入实际产品目录。
     * @since 6.1.0
     */
    private static final class TestDefinition implements ApplicationProtocolDefinition<TestSettings> {
        /** 返回测试专用代码。 */
        @Override
        public String protocol() { return "TEST_ONLY"; }
        /** 返回测试目录名称。 */
        @Override
        public String label() { return "独立测试协议"; }
        /** 使用独立版本验证分派。 */
        @Override
        public int schemaVersion() { return 3; }
        /** 返回非OAuth设置类型。 */
        @Override
        public Class<TestSettings> settingsType() { return TestSettings.class; }
        /** 将合成载荷解析为测试策略。 */
        @Override
        public TestSettings parse(String configuration) { return new TestSettings(configuration); }
        /** 输出合成测试策略。 */
        @Override
        public String serialize(TestSettings settings) { return settings.policy(); }
        /** 证明通用注册表实际调用具体策略。 */
        @Override
        public void validate(TestSettings settings, String type, String purpose, boolean enabled) {
            if (!"allowed".equals(settings.policy())) {
                throw new IllegalArgumentException("测试策略拒绝");
            }
        }
        /** 测试策略无需Secret。 */
        @Override
        public boolean requiresSecret(TestSettings settings) { return false; }
    }
}
