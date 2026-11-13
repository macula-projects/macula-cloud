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

import dev.macula.cloud.system.protocol.OAuth2ProtocolDefinition;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import dev.macula.cloud.system.config.OAuth2ScopeCatalogProperties;
import dev.macula.cloud.system.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.system.pojo.entity.SysIdentityProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证目录只声明实际协议资料与显式登记资源Scope，来源元数据不暴露凭据。
 * @author Rain
 * @since 6.1.0
 */
class IamApplicationCatalogServiceImplTest {
    private final OAuth2ScopeCatalogProperties properties = new OAuth2ScopeCatalogProperties();
    private final SysIdentityProviderMapper sources = mock(SysIdentityProviderMapper.class);
    private final IamApplicationCatalogServiceImpl service = new IamApplicationCatalogServiceImpl(properties, sources, OAuth2ProtocolDefinition.registry());

    /** 默认只有已实现的openid/profile，不从样例或旧Client推断业务权限。 */
    @Test
    void builtinsMatchActualClaimsAndPureOauthExcludesThem() {
        service.validate();
        assertEquals(Set.of("openid","profile","system:access"),service.allowedScopes("OIDC","SPA",Set.of("authorization_code")));
        assertEquals(Set.of("system:access"),service.allowedScopes("OAUTH2_1","SERVICE",Set.of("client_credentials")));
        var scopes = service.catalog().scopes();
        assertEquals(3,scopes.size());
        assertTrue(scopes.stream().noneMatch(scope -> Set.of("email","phone","address","offline_access","message.read").contains(scope.value())));
    }

    /** 业务Scope必须明确登记，并同时满足应用架构和授权方式限制。 */
    @Test
    void businessScopeRespectsDeclaredApplicability() {
        var scope = scope("orders.read");
        scope.setApplicationTypes(Set.of("SERVICE"));
        scope.setGrants(Set.of("client_credentials"));
        properties.setBusiness(List.of(scope));
        service.validate();
        assertEquals(Set.of("orders.read","system:access"),service.allowedScopes("OAUTH2_1","SERVICE",Set.of("client_credentials")));
        assertEquals(Set.of("profile","system:access"),service.allowedScopes("OAUTH2_1","SPA",Set.of("authorization_code")));
    }

    /** 重复或伪装成未实现标准资料Scope的声明不能启用。 */
    @Test
    void invalidDuplicateAndReservedDeclarationsAreRejected() {
        for (String value : List.of("email","phone","openid","bad scope")) {
            properties.setBusiness(List.of(scope(value)));
            assertThrows(IllegalStateException.class,service::validate);
        }
        properties.setBusiness(List.of(scope("orders.read"),scope("orders.read")));
        assertThrows(IllegalStateException.class,service::validate);
    }

    /** 未明确协议不能降级成历史OAuth模式。 */
    @Test
    void missingProtocolIsRejected() {
        assertThrows(IllegalArgumentException.class,()->service.allowedScopes(null,"SPA",Set.of("authorization_code")));
    }

    /** 小程序显示语义能力，目录不能含原文或可恢复凭据。 */
    @Test
    void concreteSourcesExposeCapabilitiesWithoutCredentials() {
        SysIdentityProvider source = new SysIdentityProvider();
        source.setId(7L);
        source.setVersion(0L);
        source.setName("小程序");
        source.setProviderType("WECHAT");
        source.setSourceType("WECHAT_MINIAPP");
        source.setAppId("mini");
        source.setConfigJson("{\"appId\":\"mini\"}");
        source.setAppSecret("secret-marker");
        source.setEnabled(true);
        when(sources.selectList(any())).thenReturn(List.of(source));
        var result = service.catalog();
        assertEquals(2,result.sources().get(0).capabilities().size());
        assertEquals(Set.of("MINIAPP"), result.sources().get(0).capabilities().get(0).applicationTypes());
        assertTrue(result.sources().get(0).browserApplicationTypes().isEmpty());
        assertFalse(result.sources().get(0).browser());
        assertTrue(result.sources().get(0).capabilities().stream().anyMatch(capability -> "WECHAT_MINIAPP_PHONE".equals(capability.loginCapability())));
        assertFalse(result.toString().contains("secret-marker"));
    }

    /** 共用企微注册仍按能力区分浏览器/端内，目录不得生成独立IN_APP架构。 */
    @Test
    void catalogSeparatesWebArchitectureFromLoginMode() {
        var source = new SysIdentityProvider();
        source.setId(8L);
        source.setVersion(1L);
        source.setName("企微");
        source.setProviderType("WECOM");
        source.setSourceType("WECOM_WEB");
        source.setCapabilitiesJson("{\"WECOM_BROWSER\":\"WECOM_WEB_V1\",\"WECOM_WEBVIEW\":\"WECOM_WEB_V1\"}");
        source.setAppId("corp");
        source.setAppSecret("fixture-secret");
        source.setEnabled(true);
        when(sources.selectList(any())).thenReturn(List.of(source));
        var capabilities = service.catalog().sources().get(0).capabilities();
        for (var capability : capabilities) {
            assertEquals(Set.of("WEB_SERVER", "SPA"), capability.applicationTypes());
            assertEquals(Set.of("WECOM_WEBVIEW".equals(capability.loginCapability()) ? "IN_APP" : "BROWSER"), capability.webLoginModes());
        }
    }

    /** 历史加密空值解密后的null和空对象不影响来源可用性，真实附加凭据仍拒绝。 */
    @Test
    void emptyAdditionalSecretsRemainAvailableWithoutAcceptingMixedCredentials() {
        var source = new SysIdentityProvider();
        source.setId(8L);
        source.setVersion(1L);
        source.setName("GitHub");
        source.setProviderType("GITHUB");
        source.setSourceType("GITHUB_OAUTH");
        source.setIdentityFlow("GITHUB_OAUTH_V1");
        source.setDirectoryPolicy("IDENTITY_ONLY");
        source.setAppId("github-app");
        source.setAppSecret("fixture-secret");
        source.setConfigJson("{\"clientId\":\"github-app\"}");
        source.setEnabled(true);
        when(sources.selectList(any())).thenReturn(List.of(source));
        for (String empty : Arrays.asList(null, "", "null", "{}")) {
            source.setSecretConfigJson(empty);
            assertTrue(service.catalog().sources().get(0).available(), "empty additional secrets: " + empty);
        }
        for (String invalid : List.of("{\"otherSecret\":\"fixture-value\"}", "[]", "\"text\"", "broken")) {
            source.setSecretConfigJson(invalid);
            assertFalse(service.catalog().sources().get(0).available());
        }
    }

    /** 构造资源方明确声明的目录项。 */
    private OAuth2ScopeCatalogProperties.Scope scope(String id) {
        var scope = new OAuth2ScopeCatalogProperties.Scope();
        scope.setId(id);
        scope.setResource("urn:macula:orders");
        scope.setName("资源权限");
        return scope;
    }
    /** 目录按完整集合判定能力，不再从历史来源回调声明接入合同。 */
    @Test
    void appLinkCatalogDoesNotRequireOrExposeSourceCallback() {
        var row = new SysIdentityProvider();
        row.setId(7L);
        row.setName("fixture-app");
        row.setProviderType("FEISHU");
        row.setSourceType("FEISHU_WEB");
        row.setAppId("fixture-app");
        row.setAppSecret("fixture-secret");
        row.setVersion(1L);
        row.setEnabled(true);
        row.setOrganizationId("tenant-fixed");
        row.setOauthRedirectUri(" invalid historical value");
        row.setConfigJson("{\"appId\":\"fixture-app\"}");
        row.setCapabilitiesJson("{\"FEISHU_APPLINK\":\"FEISHU_APPLINK_V3\"}");
        row.setAutoBindEnabled(true);
        row.setBindingMappingsJson("[{\"sourceField\":\"mobile\",\"targetField\":\"mobile\"},{\"sourceField\":\"email\",\"targetField\":\"email\"}]");
        when(sources.selectList(any())).thenReturn(List.of(row));
        var capability = service.catalog().sources().get(0).capabilities().get(0);
        assertTrue(capability.available());
        Assertions.assertNull(capability.providerRedirectUri());
    }

}
