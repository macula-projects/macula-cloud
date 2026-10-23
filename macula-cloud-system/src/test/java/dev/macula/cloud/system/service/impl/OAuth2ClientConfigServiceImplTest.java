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

import dev.macula.boot.constants.CacheConstants;
import dev.macula.cloud.system.enums.BrowserLoginProviderType;
import dev.macula.cloud.system.enums.ExternalCodeType;
import dev.macula.cloud.system.form.OAuth2ClientForm;
import dev.macula.cloud.system.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.system.mapper.SysOAuth2ClientConfigMapper;
import dev.macula.cloud.system.mapper.SysOAuth2ClientExternalProviderMapper;
import dev.macula.cloud.system.mapper.SysTenantInfoMapper;
import dev.macula.cloud.system.pojo.entity.SysIdentityProvider;
import dev.macula.cloud.system.pojo.entity.SysOAuth2ClientConfig;
import dev.macula.cloud.system.pojo.entity.SysOAuth2ClientExternalProvider;
import dev.macula.cloud.system.pojo.entity.SysTenantInfo;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * IAM 客户端租户、公共客户端和提交后缓存失效约束测试。
 *
 * @author Rain
 * @since 2026-10-13
 */
class OAuth2ClientConfigServiceImplTest {
    private final SysOAuth2ClientConfigMapper mapper = mock(SysOAuth2ClientConfigMapper.class);
    private final SysOAuth2ClientExternalProviderMapper externalProviderMapper =
        mock(SysOAuth2ClientExternalProviderMapper.class);
    private final SysIdentityProviderMapper identityProviderMapper = mock(SysIdentityProviderMapper.class);
    private final SysTenantInfoMapper tenantMapper = mock(SysTenantInfoMapper.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final CacheManager cacheManager = mock(CacheManager.class);
    private final Cache cache = mock(Cache.class);
    private final OAuth2ClientConfigServiceImpl service =
        new OAuth2ClientConfigServiceImpl(mapper, externalProviderMapper, identityProviderMapper,
            tenantMapper, encoder, cacheManager);

    @Test
    void externalCodeCatalogExposesOnlyNonSensitiveProviderCapabilities() {
        SysIdentityProvider provider = new SysIdentityProvider();
        provider.setId(7L);
        provider.setName("微信测试应用");
        provider.setProviderType("WECHAT");
        provider.setConfigJson("{\"mobileAppId\":\"wx-app\",\"miniAppId\":\"wx-mini\"}");
        provider.setSecretConfigJson(
            "{\"mobileAppSecret\":\"mobile-secret\",\"miniAppSecret\":\"mini-secret\"}");
        when(identityProviderMapper.selectList(any())).thenReturn(List.of(provider));

        var registration = service.externalCodeCatalog().getProviderRegistrations().get(0);

        assertEquals(Set.of("DEFAULT", "WECHAT_MOBILE_APP", "WECHAT_MINIAPP"),
            registration.getCapabilities());
    }

    @Test
    void publicClientRequiresTenantAndPkce() {
        OAuth2ClientForm form = valid();
        assertThrows(ResponseStatusException.class, () -> service.save(null, form));
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        form.setRequireProofKey(false);
        assertThrows(ResponseStatusException.class, () -> service.save(null, form));
        verifyNoInteractions(encoder);
    }

    @Test
    void confidentialSecretIsEncodedAndCacheEvictedAfterCommit() {
        OAuth2ClientForm form = valid();
        form.setClientAuthenticationMethods("client_secret_basic");
        form.setClientSecret("test-secret-123456");
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        when(mapper.selectCount(any())).thenReturn(0L);
        when(encoder.encode("test-secret-123456")).thenReturn("{bcrypt}digest");
        when(cacheManager.getCache(CacheConstants.OAUTH2_CLIENT_CACHE_KEY)).thenReturn(cache);
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertTrue(service.save(null, form).isSecretConfigured());
            verify(mapper).insert(argThat((SysOAuth2ClientConfig entity) ->
                "{bcrypt}digest".equals(entity.getClientSecret()) && entity.getTenantId() == 1L));
            verify(cache, never()).evict(any());
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(cache).evict("test-client");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void machineClientDoesNotNeedRedirectOrOidcScope() {
        OAuth2ClientForm form = valid();
        form.setClientAuthenticationMethods("client_secret_basic");
        form.setClientSecret("test-secret-123456");
        form.setAuthorizationGrantTypes("client_credentials");
        form.setRedirectUris(null);
        form.setScopes("message.read");
        form.setRequireProofKey(false);
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        when(encoder.encode("test-secret-123456")).thenReturn("{bcrypt}digest");
        when(cacheManager.getCache(CacheConstants.OAUTH2_CLIENT_CACHE_KEY)).thenReturn(cache);
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertDoesNotThrow(() -> service.save(null, form));
            verify(mapper).insert(argThat((SysOAuth2ClientConfig entity) -> "".equals(entity.getRedirectUris())));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void protocolVerificationGrantRequiresPublicClientAndExactOrigins() {
        OAuth2ClientForm form = valid();
        form.setAuthorizationGrantTypes("sms");
        form.setRedirectUris(null);
        form.setScopes("profile");
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());

        assertThrows(ResponseStatusException.class, () -> service.save(null, form));

        form.setAllowedOrigins(Set.of("https://app.example.test"));
        when(cacheManager.getCache(CacheConstants.OAUTH2_CLIENT_CACHE_KEY)).thenReturn(cache);
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertDoesNotThrow(() -> service.save(null, form));
            verify(mapper).insert(argThat((SysOAuth2ClientConfig entity) ->
                "https://app.example.test".equals(entity.getAllowedOrigins())));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publicMiniappClientCanEnableAuthorizationCodeAndExternalCodeTogether() {
        OAuth2ClientForm form = valid();
        form.setAuthorizationGrantTypes(
            "authorization_code,refresh_token," + ExternalCodeType.GRANT_TYPE);
        form.setExternalCodeTypes(Set.of(ExternalCodeType.WECHAT_MINIAPP_LOGIN_CODE.name()));
        form.setExternalProviderBindings(Map.of(ExternalCodeType.WECHAT_MINIAPP_LOGIN_CODE.name(), 7L));
        SysIdentityProvider provider = new SysIdentityProvider();
        provider.setId(7L);
        provider.setProviderType("WECHAT");
        provider.setConfigJson("{\"miniAppId\":\"wx-mini\"}");
        provider.setSecretConfigJson("{\"miniAppSecret\":\"mini-secret\"}");
        provider.setEnabled(true);
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        when(identityProviderMapper.selectById(7L)).thenReturn(provider);
        when(cacheManager.getCache(CacheConstants.OAUTH2_CLIENT_CACHE_KEY)).thenReturn(cache);
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertDoesNotThrow(() -> service.save(null, form));
            verify(mapper).insert(argThat((SysOAuth2ClientConfig entity) ->
                entity.getAuthorizationGrantTypes().contains("authorization_code") &&
                    entity.getAuthorizationGrantTypes().contains(ExternalCodeType.GRANT_TYPE) &&
                    ExternalCodeType.WECHAT_MINIAPP_LOGIN_CODE.name().equals(entity.getExternalCodeTypes())));
            verify(externalProviderMapper).insert(argThat((SysOAuth2ClientExternalProvider mapping) ->
                "test-client".equals(mapping.getClientId()) &&
                    ExternalCodeType.WECHAT_MINIAPP_LOGIN_CODE.name().equals(mapping.getCodeType()) &&
                    Long.valueOf(7L).equals(mapping.getIdentityProviderId())));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void browserLoginProvidersAreNormalizedAndReturned() {
        OAuth2ClientForm form = valid();
        form.setBrowserLoginProviderTypes(Set.of(BrowserLoginProviderType.WECOM.name(),
            BrowserLoginProviderType.GITHUB.name()));
        form.setDirectLoginProviderType(BrowserLoginProviderType.WECOM.name());
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        when(identityProviderMapper.selectCount(any())).thenReturn(1L);
        when(cacheManager.getCache(CacheConstants.OAUTH2_CLIENT_CACHE_KEY)).thenReturn(cache);
        TransactionSynchronizationManager.initSynchronization();
        try {
            var result = service.save(null, form);

            assertEquals(Set.of(BrowserLoginProviderType.GITHUB.name(),
                BrowserLoginProviderType.WECOM.name()), result.getBrowserLoginProviderTypes());
            assertEquals(BrowserLoginProviderType.WECOM.name(), result.getDirectLoginProviderType());
            verify(mapper).insert(argThat((SysOAuth2ClientConfig entity) ->
                "GITHUB,WECOM".equals(entity.getBrowserLoginProviderTypes()) &&
                    BrowserLoginProviderType.WECOM.name().equals(entity.getDirectLoginProviderType())));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void browserLoginProvidersRequireValidClientAndUniqueEnabledRegistration() {
        OAuth2ClientForm form = valid();
        form.setBrowserLoginProviderTypes(Set.of(BrowserLoginProviderType.WECOM.name()));
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());

        form.setAuthorizationGrantTypes("refresh_token");
        assertThrows(ResponseStatusException.class, () -> service.save(null, form));

        form.setAuthorizationGrantTypes("authorization_code,refresh_token");
        form.setRedirectUris(null);
        assertThrows(ResponseStatusException.class, () -> service.save(null, form));

        form.setRedirectUris("https://example.test/callback");
        when(identityProviderMapper.selectCount(any())).thenReturn(0L, 2L);
        assertThrows(ResponseStatusException.class, () -> service.save(null, form));
        assertThrows(ResponseStatusException.class, () -> service.save(null, form));
    }

    @Test
    void directLoginProviderMustBelongToAllowlist() {
        OAuth2ClientForm form = valid();
        form.setBrowserLoginProviderTypes(Set.of(BrowserLoginProviderType.GITHUB.name()));
        form.setDirectLoginProviderType(BrowserLoginProviderType.WECOM.name());
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());

        assertThrows(ResponseStatusException.class, () -> service.save(null, form));

        form.setDirectLoginProviderType(null);
        form.setBrowserLoginProviderTypes(Set.of("UNKNOWN"));
        assertThrows(ResponseStatusException.class, () -> service.save(null, form));
    }

    @Test
    void failedInsertOrRolledBackTransactionNeverEvictsClientCache() {
        OAuth2ClientForm form = valid();
        form.setClientAuthenticationMethods("client_secret_basic");
        form.setClientSecret("test-secret-123456");
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        when(encoder.encode("test-secret-123456")).thenReturn("{bcrypt}digest");
        when(cacheManager.getCache(CacheConstants.OAUTH2_CLIENT_CACHE_KEY)).thenReturn(cache);
        doThrow(new IllegalStateException("fixture database failure")).when(mapper)
            .insert(any(SysOAuth2ClientConfig.class));
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThrows(IllegalStateException.class, () -> service.save(null, form));
            assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
            verify(cache, never()).evict(any());
            reset(mapper);
            service.save(null, form);
            verify(cache, never()).evict(any());
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            verify(cache, never()).evict(any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rejectsInvalidUserTypeRedirectAndTokenDuration() {
        OAuth2ClientForm form = valid();
        when(tenantMapper.selectById(1L)).thenReturn(new SysTenantInfo());
        form.setUserType("other");
        assertThrows(ResponseStatusException.class, () -> service.save(null, form));
        form.setUserType("supplier");
        form.setRedirectUris("http://example.test/callback");
        assertThrows(ResponseStatusException.class, () -> service.save(null, form));
        form.setRedirectUris("https://example.test/callback");
        form.setAccessTokenTimeToLive(30L);
        assertThrows(ResponseStatusException.class, () -> service.save(null, form));
        verify(mapper, never()).insert(any(SysOAuth2ClientConfig.class));
    }

    private OAuth2ClientForm valid() {
        OAuth2ClientForm form = new OAuth2ClientForm();
        form.setClientId("test-client");
        form.setClientName("Test Client");
        form.setClientAuthenticationMethods("none");
        form.setAuthorizationGrantTypes("authorization_code,refresh_token");
        form.setRedirectUris("https://example.test/callback");
        form.setScopes("openid,profile");
        form.setRequireProofKey(true);
        form.setRequireAuthorizationConsent(false);
        form.setReuseRefreshTokens(true);
        form.setUserType("employee");
        form.setTenantId(1L);
        return form;
    }
}
