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

import dev.macula.cloud.system.config.PlatformOAuthClientProperties;
import dev.macula.cloud.system.mapper.SysOAuth2ClientConfigMapper;
import dev.macula.cloud.system.pojo.entity.SysOAuth2ClientConfig;
import dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration;
import dev.macula.cloud.system.protocol.OAuth2ProtocolSettings;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 平台身份认领的幂等和提交边界；真实事务并发由数据库IT覆盖。
 * @author Rain
 * @since 6.1.0
 */
class PlatformOAuthClientProvisioningServiceTest {
    /** 保留已有身份，提交前不发布缓存，重复启动不撤销正常会话。 */
    @Test
    void preservesIdentityAndOnlyInvalidatesAfterFirstCommit() {
        var clients = mock(SysOAuth2ClientConfigMapper.class);
        var protocols = mock(ApplicationProtocolConfigurationService.class);
        String clientCache = dev.macula.boot.constants.CacheConstants.OAUTH2_CLIENT_CACHE_KEY;
        String originCache = dev.macula.cloud.system.security.OAuth2ClientCacheNames.TOKEN_ORIGIN_INDEX;
        var caches = new ConcurrentMapCacheManager(clientCache, originCache);
        var gateway = new SysOAuth2ClientConfig();
        gateway.setId(7L);
        gateway.setClientId("fixture-gateway");
        gateway.setClientSecret("synthetic-encoded-secret");
        gateway.setClientAuthenticationMethods("client_secret_basic");
        gateway.setAuthorizationVersion(1L);
        var admin = new SysOAuth2ClientConfig();
        admin.setId(8L);
        admin.setClientId("fixture-admin");
        admin.setApplicationType("SPA");
        admin.setTenantId(1L);
        admin.setUserType("employee");
        admin.setAuthorizationVersion(1L);
        admin.setResourcePermissions("{}");
        var settings = new OAuth2ProtocolSettings();
        settings.setClientAuthenticationMethods("none");
        settings.setAuthorizationGrantTypes("authorization_code," + dev.macula.cloud.system.enums.ExternalCodeType.GRANT_TYPE);
        settings.setScopes("openid,profile");
        settings.setRequireProofKey(true);
        settings.setRequireAuthorizationConsent(false);
        settings.setRedirectUris("https://admin.example.test/callback");
        var configurations = new ArrayList<>(List.of(new ApplicationProtocolConfiguration("OIDC", 1, true, settings)));
        when(protocols.list(8L)).thenAnswer(call -> configurations);
        when(protocols.list(7L)).thenReturn(List.of());
        when(clients.selectOne(any())).thenReturn(gateway, admin, gateway, admin);
        when(clients.updateById(any(SysOAuth2ClientConfig.class))).thenReturn(1);
        var properties = new PlatformOAuthClientProperties();
        properties.setEnabled(true);
        properties.setAdminClientId(admin.getClientId());
        properties.setGatewayClientId(gateway.getClientId());
        var external = mock(dev.macula.cloud.system.mapper.SysOAuth2ClientExternalProviderMapper.class);
        var catalog = mock(dev.macula.cloud.system.service.OAuth2ClientConfigurationCatalogService.class);
        admin.setExternalCodeTypes("WECOM_APP_CODE");
        var mapping = new dev.macula.cloud.system.pojo.entity.SysOAuth2ClientExternalProvider();
        mapping.setCodeType("WECOM_APP_CODE");
        mapping.setIdentityProviderId(17L);
        when(external.selectList(any())).thenReturn(List.of(mapping));
        var source = new dev.macula.cloud.system.vo.OAuth2ClientConfigurationCatalogVO.Source(
            17L, "fixture", "WECOM", "WECOM_SDK", "企微SDK", "SDK", false,
            List.of(new dev.macula.cloud.system.vo.OAuth2ClientConfigurationCatalogVO.Capability("WECOM_APP_CODE", "Code", "SDK", "MEMBER_ID")), true, null, "WECOM_SDK_V1", false, null);
        when(catalog.catalog()).thenReturn(new dev.macula.cloud.system.vo.OAuth2ClientConfigurationCatalogVO(List.of(), List.of(), List.of(source)));
        var service = new PlatformOAuthClientProvisioningService(clients, protocols, caches, external, catalog);
        caches.getCache(clientCache).put("fixture", "old");
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.initialize(properties);
            assertEquals("old", caches.getCache(clientCache).get("fixture", String.class));
            assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
            assertNull(caches.getCache(clientCache).get("fixture"));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        assertEquals("synthetic-encoded-secret", gateway.getClientSecret());
        assertEquals("fixture-gateway", gateway.getClientId());
        assertEquals(2L, gateway.getAuthorizationVersion());
        assertEquals(2L, admin.getAuthorizationVersion());
        // 重复运行不安排缓存操作，所以在事务外执行也应无需注册同步回调。
        service.initialize(properties);
        assertEquals(2L, gateway.getAuthorizationVersion());
        assertEquals(2L, admin.getAuthorizationVersion());
        assertEquals("authorization_code," + dev.macula.cloud.system.enums.ExternalCodeType.GRANT_TYPE, settings.getAuthorizationGrantTypes());
        verify(clients, times(2)).updateById(any(SysOAuth2ClientConfig.class));
        when(external.selectList(any())).thenReturn(List.of());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> service.initialize(properties));
        verify(clients, times(2)).updateById(any(SysOAuth2ClientConfig.class));
    }
}
