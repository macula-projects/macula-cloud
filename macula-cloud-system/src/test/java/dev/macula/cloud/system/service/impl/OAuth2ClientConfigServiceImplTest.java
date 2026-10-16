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
import dev.macula.cloud.system.form.OAuth2ClientForm;
import dev.macula.cloud.system.mapper.SysOAuth2ClientConfigMapper;
import dev.macula.cloud.system.mapper.SysTenantInfoMapper;
import dev.macula.cloud.system.pojo.entity.SysOAuth2ClientConfig;
import dev.macula.cloud.system.pojo.entity.SysTenantInfo;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IAM 客户端租户、公共客户端和提交后缓存失效约束测试。
 *
 * @author Rain
 * @since 2026-10-13
 */
class OAuth2ClientConfigServiceImplTest {
    private final SysOAuth2ClientConfigMapper mapper = mock(SysOAuth2ClientConfigMapper.class);
    private final SysTenantInfoMapper tenantMapper = mock(SysTenantInfoMapper.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final CacheManager cacheManager = mock(CacheManager.class);
    private final Cache cache = mock(Cache.class);
    private final OAuth2ClientConfigServiceImpl service =
        new OAuth2ClientConfigServiceImpl(mapper, tenantMapper, encoder, cacheManager);

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
