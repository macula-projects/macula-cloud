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

package dev.macula.cloud.system.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import dev.macula.boot.constants.CacheConstants;
import dev.macula.boot.context.TenantContextHolder;
import dev.macula.cloud.system.mapper.SysApplicationApiKeyMapper;
import dev.macula.cloud.system.mapper.SysApplicationMapper;
import dev.macula.cloud.system.pojo.entity.SysApplication;
import dev.macula.cloud.system.pojo.entity.SysApplicationApiKey;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 网关缓存协议、URL 优先级、提交/回滚及撤销重建行为。
 * @author Rain
 * @since 6.1.0
 */
class ApplicationCredentialCacheServiceTest {
    private final SysApplicationMapper apps = mock(SysApplicationMapper.class);
    private final SysApplicationApiKeyMapper keys = mock(SysApplicationApiKeyMapper.class);
    private final SysTenantService tenants = mock(SysTenantService.class);
    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
    private final RedissonClient redisson = mock(RedissonClient.class);
    private final RLock lock = mock(RLock.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final ApplicationCredentialCacheService cache =
        new ApplicationCredentialCacheService(apps, keys, tenants, redis, redisson, transactions);
    private SysApplication app;
    private SysApplicationApiKey key;

    @BeforeEach
    void setup() {
        when(redis.opsForHash()).thenReturn(hashes);
        when(redisson.getLock(org.mockito.ArgumentMatchers.anyString())).thenReturn(lock);
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        app = new SysApplication();
        app.setId(34L);
        app.setTenantId(12L);
        app.setCode("sample");
        app.setSk("synthetic-signing-secret");
        app.setAccessPath("GET:/app/**");
        key = new SysApplicationApiKey();
        key.setApplicationId(34L);
        key.setTenantId(12L);
        key.setApiKey("sk-synthetic-cache-fixture");
        key.setDeleted(false);
        when(keys.cacheApplication(any(), any())).thenReturn(app);
        when(keys.selectList(any(Wrapper.class))).thenReturn(List.of(key));
        when(keys.cacheKeys(any(), any())).thenReturn(List.of(key));
        TenantContextHolder.setCurrentTenantId(99L);
    }

    @AfterEach
    void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TenantContextHolder.clearCurrentTenantId();
    }

    @Test
    void independentUrlOverridesApplicationAndEmptyFallsBackForAllWrites() {
        key.setAccessPath("POST:/key/**");
        Map<String, String> values = ApplicationCredentialCacheService.apiKeyFields(app, key);
        assertThat(values).containsEntry("permitUrls", "POST:/key/**")
            .containsEntry("applicationCode", "sample").containsEntry("applicationId", "34")
            .containsEntry("tenantId", "12").containsEntry("authTenantId", "12");
        app.setAuthTenantId(7L);
        for (String empty : new String[] {null, "", "  "}) {
            key.setAccessPath(empty);
            assertThat(ApplicationCredentialCacheService.apiKeyFields(app, key))
                .containsEntry("permitUrls", "GET:/app/**").containsEntry("authTenantId", "7");
        }
        app.setAccessPath(null);
        assertThat(ApplicationCredentialCacheService.apiKeyFields(app, key)).containsEntry("permitUrls", "");
    }

    @Test
    void callbackDoesNotPublishBeforeCommitAndRollbackNeverPublishes() {
        TransactionSynchronizationManager.initSynchronization();
        cache.afterCommit(12L, 34L, null);
        verify(redisson, never()).getLock(org.mockito.ArgumentMatchers.anyString());
        var callbacks = TransactionSynchronizationManager.getSynchronizations();
        callbacks.forEach(callback -> callback.afterCompletion(1));
        verify(redis, never()).opsForHash();
    }

    @Test
    void commitPublishesBothTypesFromFreshDatabaseAndRestoresContext() {
        TransactionSynchronizationManager.initSynchronization();
        cache.afterCommit(12L, 34L, null);
        TransactionSynchronizationManager.getSynchronizations().forEach(callback -> callback.afterCommit());
        verify(hashes).putAll(eq(CacheConstants.APIKEY_CACHE_PREFIX + key.getApiKey()),
            org.mockito.ArgumentMatchers.<Map<String, String>>argThat(fields -> "GET:/app/**".equals(fields.get("permitUrls"))));
        verify(hashes).putAll(eq(CacheConstants.SECURITY_SYSTEM_APPS + "sample"),
            org.mockito.ArgumentMatchers.<Map<String, String>>argThat(fields -> "synthetic-signing-secret".equals(fields.get("secretKey"))));
        verify(transactions).getTransaction(org.mockito.ArgumentMatchers.argThat(definition ->
            definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED
                && definition.getIsolationLevel() == TransactionDefinition.ISOLATION_READ_COMMITTED));
        verify(lock).unlock();
        assertThat(TenantContextHolder.getCurrentTenantId()).isEqualTo(99L);
    }

    @Test
    void refreshReadsDeletionAfterLockInsteadOfReusingStaleKeySnapshot() {
        // 模拟锁竞争期间撤销已经提交，取得锁后只读取最终状态。
        org.mockito.Mockito.doAnswer(call -> {
            key.setDeleted(true);
            return null;
        }).when(lock).lock();
        cache.refresh(12L, 34L, null);
        verify(redis).delete(CacheConstants.APIKEY_CACHE_PREFIX + key.getApiKey());
        verify(hashes, never()).putAll(eq(CacheConstants.APIKEY_CACHE_PREFIX + key.getApiKey()), any());
    }

    @Test
    void deletedApplicationRemovesAllItsKeyHashesAndPreviousHmacCode() {
        when(keys.cacheApplication(any(), any())).thenReturn(null);
        cache.refresh(12L, 34L, "old-app");
        verify(redis).delete(CacheConstants.SECURITY_SYSTEM_APPS + "old-app");
        verify(redis).delete(CacheConstants.APIKEY_CACHE_PREFIX + key.getApiKey());
        verify(redis, never()).opsForHash();
    }

    @Test
    void startupRebuildPublishesActiveKeysAndClearsPersistedRevocations() {
        var tenant = new dev.macula.cloud.system.pojo.entity.SysTenantInfo();
        tenant.setId(12L);
        when(tenants.list()).thenReturn(List.of(tenant));
        when(apps.selectList(any(Wrapper.class))).thenReturn(List.of(app));
        key.setDeleted(true);
        cache.rebuild();
        verify(hashes, never()).putAll(eq(CacheConstants.APIKEY_CACHE_PREFIX + key.getApiKey()), any());
        verify(redis, org.mockito.Mockito.atLeastOnce()).delete(CacheConstants.APIKEY_CACHE_PREFIX + key.getApiKey());
        assertThat(TenantContextHolder.getCurrentTenantId()).isEqualTo(99L);
    }

    @Test
    void cacheFailureIsReportedWithoutLeakingCredentialAndReleasesContextAndLock() {
        doThrow(new IllegalStateException(key.getApiKey())).when(hashes).putAll(any(), any());
        assertThatThrownBy(() -> cache.refresh(12L, 34L, null)).isInstanceOf(ResponseStatusException.class)
            .hasMessageNotContaining(key.getApiKey()).hasCause(null);
        verify(lock).unlock();
        assertThat(TenantContextHolder.getCurrentTenantId()).isEqualTo(99L);
    }
}
