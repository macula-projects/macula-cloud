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

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 协议扩展白名单、类型化JSON和共享凭据的安全边界。
 * @author Rain
 * @since 6.1.0
 */
class ApplicationProtocolRegistryTest {
    /** 未实现协议、未知版本和未知JSON字段不能通过白名单。 */
    @Test
    void rejectsUnknownProtocolsVersionsAndPolymorphicTypes() {
        var settings = new OAuth2ProtocolSettings();
        assertThrows(IllegalArgumentException.class, () -> ApplicationProtocolRegistry.require(new ApplicationProtocolConfiguration("CAS", 1, true, settings)));
        assertThrows(IllegalArgumentException.class, () -> ApplicationProtocolRegistry.require(new ApplicationProtocolConfiguration("OIDC", 2, true, settings)));
        var json = JsonMapper.builder().build();
        assertThrows(RuntimeException.class, () -> json.readValue("{\"kind\":\"java.lang.Runtime\"}", ApplicationProtocolSettings.class));
        assertThrows(RuntimeException.class, () -> json.readValue("{\"kind\":\"oauth2\",\"unknownSecret\":true}", ApplicationProtocolSettings.class));
        assertEquals(settings, json.readValue(json.writeValueAsString(settings), ApplicationProtocolSettings.class));
    }

    /** 管理端点选择固定OAuth优先，与列表顺序和默认协议无关。 */
    @Test
    void managementSelectionAndSecretRetentionAreIndependent() {
        var oidc = profile("OIDC", true, "private_key_jwt");
        var oauth = profile("OAUTH2_1", true, "client_secret_basic");
        assertSame(oauth, ApplicationProtocolRegistry.management(List.of(oidc, oauth)));
        assertSame(oidc, ApplicationProtocolRegistry.management(List.of(oidc, profile("OAUTH2_1", false, "client_secret_basic"))));
        var disabled = profile("OAUTH2_1", false, "client_secret_post");
        assertTrue(ApplicationProtocolRegistry.requiresSecret(disabled));
        assertThrows(IllegalArgumentException.class, () -> ApplicationProtocolRegistry.management(List.of(profile("OIDC", true, "none"), disabled)));
    }

    /** 公共应用、OIDC身份Scope和纯OAuth业务Scope不能互相冒用。 */
    @Test
    void enforcesCapabilitiesEvenForSuppliedDisabledDraftValues() {
        var oidc = profile("OIDC", true, "none");
        var settings = OAuth2ProtocolConfiguration.settings(oidc);
        settings.setAuthorizationGrantTypes("authorization_code");
        settings.setScopes("openid,profile");
        settings.setRedirectUris("https://client.example/callback");
        settings.setRequireProofKey(true);
        settings.setAllowedOrigins(Set.of());
        assertDoesNotThrow(() -> OAuth2ProtocolConfiguration.validate(oidc, "SPA"));
        settings.setAuthorizationGrantTypes("authorization_code,refresh_token");
        assertThrows(IllegalArgumentException.class, () -> OAuth2ProtocolConfiguration.validate(oidc, "SPA"));
        var oauth = profile("OAUTH2_1", false, "none");
        OAuth2ProtocolConfiguration.settings(oauth).setScopes("openid");
        assertThrows(IllegalArgumentException.class, () -> OAuth2ProtocolConfiguration.validate(oauth, "SPA"));
        assertThrows(IllegalArgumentException.class, () -> OAuth2ProtocolConfiguration.csv("openid,,profile"));
        assertThrows(IllegalArgumentException.class, () -> OAuth2ProtocolConfiguration.csv("openid,openid"));
    }

    /** 当前OIDC允许SDK-only和小程序，历史迁移校验保持原合同。 */
    @Test
    void runtimeOidcSdkRequiresPublicClientAndOpenidWithoutCallback() {
        var oidc = profile("OIDC", true, "none");
        var settings = OAuth2ProtocolConfiguration.settings(oidc);
        settings.setAuthorizationGrantTypes(dev.macula.cloud.system.enums.ExternalCodeType.GRANT_TYPE);
        settings.setScopes("openid,profile");
        settings.setRequireProofKey(true);
        settings.setAllowedOrigins(Set.of());
        assertDoesNotThrow(() -> OAuth2ProtocolConfiguration.validateRuntime(oidc, "SPA"));
        assertDoesNotThrow(() -> OAuth2ProtocolConfiguration.validateRuntime(oidc, "MINIAPP"));
        assertThrows(IllegalArgumentException.class, () -> OAuth2ProtocolConfiguration.validate(oidc, "SPA"));
        assertThrows(IllegalArgumentException.class, () -> OAuth2ProtocolConfiguration.validateRuntime(oidc, "SERVICE"));
        settings.setScopes("profile");
        assertThrows(IllegalArgumentException.class, () -> OAuth2ProtocolConfiguration.validateRuntime(oidc, "SPA"));
        settings.setScopes("openid,profile");
        settings.setClientAuthenticationMethods("client_secret_basic");
        assertThrows(IllegalArgumentException.class, () -> OAuth2ProtocolConfiguration.validateRuntime(oidc, "WEB_SERVER"));
        settings.setClientAuthenticationMethods("none");
        for (String grant : List.of("sms", "email", "client_credentials")) {
            settings.setAuthorizationGrantTypes(grant);
            assertThrows(IllegalArgumentException.class, () -> OAuth2ProtocolConfiguration.validateRuntime(oidc, "SPA"));
        }
    }

    /** 每次创建独立的配置对象。 */
    private ApplicationProtocolConfiguration profile(String protocol, boolean enabled, String method) {
        var settings = new OAuth2ProtocolSettings();
        settings.setClientAuthenticationMethods(method);
        settings.setRequireAuthorizationConsent(false);
        settings.setReuseRefreshTokens(false);
        return new ApplicationProtocolConfiguration(protocol, 1, enabled, settings);
    }
}
