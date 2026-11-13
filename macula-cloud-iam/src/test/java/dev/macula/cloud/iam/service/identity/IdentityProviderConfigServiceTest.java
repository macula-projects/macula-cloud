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
package dev.macula.cloud.iam.service.identity;

import java.util.List;

import dev.macula.cloud.iam.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.iam.pojo.entity.SysIdentityProvider;
import dev.macula.cloud.system.enums.ExternalCodeType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.AuthenticationServiceException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证第三方配置与全局账号绑定在缺失、重复或损坏时失败关闭。
 *
 * @author Rain
 * @since 6.1.0
 */
class IdentityProviderConfigServiceTest {
    private final SysIdentityProviderMapper providers = mock(SysIdentityProviderMapper.class);
    private final IdentityProviderConfigService service = new IdentityProviderConfigService(providers);

    @Test
    void loadsExactlyOneEnabledProviderWithoutExposingFallbacks() {
        SysIdentityProvider provider = new SysIdentityProvider();
        provider.setId(1L);
        provider.setEnabled(true);
        provider.setVersion(0L);
        provider.setSourceType("GITHUB_OAUTH");
        provider.setProviderType("GITHUB");
        provider.setAppId("client-id");
        provider.setAppSecret("decrypted-secret");
        provider.setConfigJson("{\"clientId\":\"client-id\"}");
        when(providers.selectById(1L)).thenReturn(provider);

        IdentityProviderConfigService.ProviderConfig config = service.requireEnabledById(1L, "GITHUB");

        assertEquals("client-id", config.require("clientId"));
        assertEquals("decrypted-secret", config.appSecret());
    }

    @Test
    void miniappDoesNotBorrowWebsiteOrOfficialCredentials() {
        SysIdentityProvider provider = provider("mini-secret", "{\"appId\":\"mini-id\"}");
        provider.setProviderType("WECHAT");
        provider.setSourceType("WECHAT_MINIAPP");
        when(providers.selectById(1L)).thenReturn(provider);
        var config = service.requireEnabledById(1L, "WECHAT");
        assertEquals("mini-id", config.require("appId"));
        assertEquals("mini-secret", config.appSecret());
        assertFalse(config.values().containsKey("appSecret"));
        assertThrows(AuthenticationServiceException.class, () -> config.requireSecret("officialAccountAppSecret"));
    }

    @Test
    void rejectsMissingDisabledAndMalformedFixedSources() {
        when(providers.selectById(1L)).thenReturn(null);
        assertThrows(AuthenticationServiceException.class, () -> service.requireEnabledById(1L, "GITHUB"));
        SysIdentityProvider disabled = provider("secret", "{\"clientId\":\"one\"}");
        disabled.setEnabled(false);
        SysIdentityProvider malformed = provider("secret", "not-json");
        when(providers.selectById(1L)).thenReturn(disabled, malformed);
        assertThrows(AuthenticationServiceException.class, () -> service.requireEnabledById(1L, "GITHUB"));
        assertThrows(AuthenticationServiceException.class, () -> service.requireEnabledById(1L, "GITHUB"));
    }

    /**
     * 兼容无附加凭据的历史加密null及新空对象，不影响独立来源主密钥。
     */
    @Test
    void acceptsEmptyAdditionalSecretsWithoutBorrowingCredentials() {
        SysIdentityProvider source = provider("secret", "{\"clientId\":\"one\"}");
        when(providers.selectById(1L)).thenReturn(source);
        for (String empty : new String[]{null, "null", "{}"}) {
            source.setSecretConfigJson(empty);
            assertEquals("secret", service.requireEnabledById(1L, "GITHUB").appSecret());
        }
    }

    @Test
    void rejectsMalformedAdditionalSecrets() {
        for (String secretJson : List.of("not-json", "[]", "{\"unknownSecret\":\"value\"}")) {
            SysIdentityProvider provider = provider("secret", "{\"appId\":\"id\"}");
            provider.setProviderType("WECHAT");
            provider.setSourceType("WECHAT_WEBSITE");
            provider.setSecretConfigJson(secretJson);
            when(providers.selectById(1L)).thenReturn(provider);
            assertThrows(AuthenticationServiceException.class, () -> service.requireEnabledById(1L, "WECHAT"));
        }
    }

    @Test
    void rejectsEnabledProviderWithIncompleteModeConfiguration() {
        SysIdentityProvider legacyWechat = new SysIdentityProvider();
        legacyWechat.setProviderType("WECHAT");
        legacyWechat.setAppId("website-id");
        legacyWechat.setAppSecret("website-secret");
        legacyWechat.setConfigJson("{\"appId\":\"website-id\"}");
        when(providers.selectById(1L)).thenReturn(legacyWechat);

        assertThrows(AuthenticationServiceException.class, () -> service.requireEnabledById(1L, "WECHAT"));
    }

    @Test
    void visibilityFailuresAreClosed() {
        SysIdentityProvider source = provider("secret", "{\"clientId\":\"one\"}");
        source.setVisible(true);
        when(providers.selectById(1L)).thenReturn(source, null).thenThrow(new IllegalStateException("db unavailable"));
        assertTrue(service.requireEnabledById(1L, "GITHUB").visible());
        assertThrows(AuthenticationServiceException.class, () -> service.requireEnabledById(1L, "GITHUB"));
        assertThrows(IllegalStateException.class, () -> service.requireEnabledById(1L, "GITHUB"));
    }

    @Test
    void doesNotReadGlobalUsernameBindingOrSelectByProvider() {
        SysIdentityProvider source = provider("secret", "{\"clientId\":\"one\"}");
        when(providers.selectById(1L)).thenReturn(source);
        assertEquals(1L, service.requireEnabledById(1L, "GITHUB").registrationId());
        Mockito.verify(providers, Mockito.never()).selectList(any());
    }

    private SysIdentityProvider provider(String secret, String json) {
        SysIdentityProvider provider = new SysIdentityProvider();
        provider.setId(1L);
        provider.setProviderType("GITHUB");
        provider.setSourceType("GITHUB_OAUTH");
        provider.setAppId("id");
        if (json.contains("clientId")) {
            provider.setAppId(
                    JsonMapper.builder().build().readTree(json).path("clientId").asText());
        } else if (json.contains("appId")) {
            provider.setAppId(
                    JsonMapper.builder().build().readTree(json).path("appId").asText());
        }
        provider.setEnabled(true);
        provider.setVersion(0L);
        provider.setAppSecret(secret);
        provider.setConfigJson(json);
        return provider;
    }

    /**
     * 新映射可在无旧规则投影时加载，不能被旧枚举判定为来源不可用。
     */
    @Test
    void loadsExplicitEnterpriseEmailMappingWithoutLegacyRule() {
        var source = provider("secret", "{\"appId\":\"id\"}");
        source.setProviderType("FEISHU");
        source.setSourceType("FEISHU_WEB");
        source.setIdentityFlow("FEISHU_OAUTH_V3");
        source.setOrganizationId("fixed-tenant");
        source.setAutoBindEnabled(true);
        source.setBindingSourceField("email");
        source.setBindingTargetField("username");
        when(providers.selectById(1L)).thenReturn(source);
        var result = service.requireEnabledById(1L, "FEISHU");
        assertEquals("email", result.bindingMapping().source().value());
        assertEquals("username", result.bindingMapping().target().value());
        Assertions.assertNull(result.bindingRule());
    }

    /**
     * 非旧锚点的小程序能力复用同一企微应用，停用后不得继续换码。
     */
    @Test
    void resolvesIndependentWecomCapabilitiesAndRejectsRevokedCapability() {
        var row = new SysIdentityProvider();
        row.setId(8L);
        row.setEnabled(true);
        row.setVersion(2L);
        row.setProviderType("WECOM");
        row.setSourceType("WECOM_WEB");
        row.setCredentialType("WECOM_APPLICATION");
        row.setIdentityFlow("WECOM_WEB_V1");
        row.setAppId("corp");
        row.setAppSecret("fixture-secret");
        row.setConfigJson("{\"corpId\":\"corp\",\"agentId\":\"1001\"}");
        row.setCapabilitiesJson("{\"WECOM_BROWSER\":\"WECOM_WEB_V1\",\"WECOM_MINIAPP\":\"WECOM_MINIAPP_V1\"}");
        when(providers.selectById(8L)).thenReturn(row);
        var mini = service.requireEnabledById(8L, "WECOM")
                .forCode(ExternalCodeType.WECOM_MINIAPP_CODE);
        assertEquals("WECOM_MINIAPP", mini.sourceType().value());
        assertEquals("WECOM_MINIAPP_V1", mini.flow().value());
        assertEquals(8L, mini.registrationId());
        assertEquals("fixture-secret", mini.appSecret());
        assertThrows(AuthenticationServiceException.class, () -> mini.forCapability("WECHAT_MINIAPP_LOGIN"));
        row.setCapabilitiesJson("{\"WECOM_BROWSER\":\"WECOM_WEB_V1\"}");
        row.setVersion(3L);
        assertThrows(AuthenticationServiceException.class,
                () -> service.requireEnabledById(8L, "WECOM").forCapability(mini.loginCapability()));
    }

    /**
     * IAM读取System稳定JSON并在网页/AppLink/JSAPI视图中保留完整集合，无来源回调要求。
     */
    @Test
    void preservesMultipleMappingsAcrossCapabilityViewsWithoutSourceCallback() {
        var row = new SysIdentityProvider();
        row.setId(8L);
        row.setEnabled(true);
        row.setVersion(2L);
        row.setProviderType("FEISHU");
        row.setSourceType("FEISHU_WEB");
        row.setAppId("fixture-app");
        row.setAppSecret("fixture-secret");
        row.setOrganizationId("tenant-fixed");
        row.setConfigJson("{\"appId\":\"fixture-app\"}");
        row.setCapabilitiesJson(
                "{\"FEISHU_WEB\":\"FEISHU_OAUTH_V3\",\"FEISHU_APPLINK\":\"FEISHU_APPLINK_V3\"," +
                        "\"FEISHU_WEBVIEW\":\"FEISHU_WEBVIEW_V3\"}");
        row.setAutoBindEnabled(true);
        row.setBindingMappingsJson(
                "[{\"sourceField\":\"mobile\",\"targetField\":\"mobile\"},{\"sourceField\":\"email\"," +
                        "\"targetField\":\"email\"}]");
        when(providers.selectById(8L)).thenReturn(row);
        var config = service.requireEnabledById(8L, "FEISHU");
        assertEquals(2, config.bindingMappings().size());
        assertEquals(config.bindingMappings(), config.forCapability("FEISHU_APPLINK").bindingMappings());
        assertEquals(config.bindingMappings(), config.forCapability("FEISHU_WEBVIEW").bindingMappings());
        Assertions.assertNull(config.bindingMapping());
        assertThrows(UnsupportedOperationException.class, () -> config.bindingMappings().clear());
        row.setBindingMappingsJson("[]");
        assertThrows(AuthenticationServiceException.class, () -> service.requireEnabledById(8L, "FEISHU"));
    }

}
