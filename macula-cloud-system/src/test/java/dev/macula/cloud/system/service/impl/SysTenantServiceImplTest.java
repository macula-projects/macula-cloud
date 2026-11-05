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

package dev.macula.cloud.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import dev.macula.cloud.system.pojo.entity.SysTenantUser;
import dev.macula.boot.constants.CacheConstants;
import dev.macula.cloud.system.converter.TenantConverter;
import dev.macula.cloud.system.form.TenantForm;
import dev.macula.cloud.system.listener.ApplicationListener;
import dev.macula.cloud.system.pojo.entity.SysTenantInfo;
import dev.macula.cloud.system.service.SysPermissionService;
import dev.macula.cloud.system.service.SysTenantUserService;
import org.redisson.api.RedissonClient;
import org.redisson.api.RLock;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 租户映射随租户生命周期更新，事务回滚时不得发布未提交映射。
 *
 * @author Rain
 * @since 6.1.0
 */
class SysTenantServiceImplTest {
    private final TenantConverter converter = mock(TenantConverter.class);
    private final SysTenantUserService users = mock(SysTenantUserService.class);
    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
    private final RedissonClient redisson = mock(RedissonClient.class);
    private final RLock lock = mock(RLock.class);
    private final SysTenantServiceImpl service = spy(new SysTenantServiceImpl(converter, users, redis, redisson));

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysTenantUser.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysTenantInfo.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(redisson.getLock(anyString())).thenReturn(lock);
        doReturn(0L).when(service).count(any(Wrapper.class));
        doReturn(true).when(service).save(any(SysTenantInfo.class));
        doReturn(true).when(service).updateById(any(SysTenantInfo.class));
        doReturn(true).when(service).removeByIds(anyCollection());
        when(users.saveBatch(anyCollection())).thenReturn(true);
        when(users.remove(any(Wrapper.class))).thenReturn(true);
    }

    @AfterEach
    void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void startupInitializesTenantCacheWithoutApplicationEdits() {
        doReturn(List.of(tenant("macula-cloud", 1L))).when(service).list();
        SysPermissionService permissions = mock(SysPermissionService.class);
        var credentials = mock(dev.macula.cloud.system.service.ApplicationCredentialCacheService.class);
        new ApplicationListener(permissions, service, credentials).onStarted(null);
        verify(credentials).rebuild();
        verify(hashes).putAll(CacheConstants.SECURITY_SYSTEM_TENANTS, Map.of("macula-cloud", "1"));
        verify(permissions).refreshPermRolesRules();
    }

    @Test
    void creatingTenantRefreshesOnlyAfterCommit() {
        SysTenantInfo created = tenant("macula-samples", 12L);
        when(converter.form2Entity(any())).thenReturn(created);
        doReturn(List.of(created)).when(service).list();
        TransactionSynchronizationManager.initSynchronization();
        assertThat(service.saveTenant(form("macula-samples"))).isTrue();
        verifyNoInteractions(redis);
        commitCallbacks();
        verify(hashes).putAll(CacheConstants.SECURITY_SYSTEM_TENANTS, Map.of("macula-samples", "12"));
    }

    @Test
    void renamingTenantReplacesOldMappingAfterCommit() {
        SysTenantInfo updated = tenant("new-code", 12L);
        when(converter.form2Entity(any())).thenReturn(updated);
        doReturn(List.of(updated)).when(service).list();
        TransactionSynchronizationManager.initSynchronization();
        assertThat(service.updateTenant(12L, form("new-code"))).isTrue();
        verifyNoInteractions(redis);
        commitCallbacks();
        verify(redis).delete(CacheConstants.SECURITY_SYSTEM_TENANTS);
        verify(hashes).putAll(CacheConstants.SECURITY_SYSTEM_TENANTS, Map.of("new-code", "12"));
    }

    @Test
    void deletingLastTenantRemovesOldMappingAfterCommit() {
        doReturn(List.of()).when(service).list();
        TransactionSynchronizationManager.initSynchronization();
        assertThat(service.deleteTenants("12")).isTrue();
        verifyNoInteractions(redis);
        commitCallbacks();
        verify(redis).delete(CacheConstants.SECURITY_SYSTEM_TENANTS);
        verify(redis, never()).opsForHash();
    }

    @Test
    void rollbackDoesNotRefreshTenantCache() {
        when(converter.form2Entity(any())).thenReturn(tenant("new-code", 12L));
        TransactionSynchronizationManager.initSynchronization();
        service.updateTenant(12L, form("new-code"));
        for (TransactionSynchronization callback : TransactionSynchronizationManager.getSynchronizations()) {
            callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        verifyNoInteractions(redis);
    }

    @Test
    void failedTenantWriteDoesNotScheduleRefresh() {
        when(converter.form2Entity(any())).thenReturn(tenant("new-code", 12L));
        doReturn(false).when(service).updateById(any(SysTenantInfo.class));
        TransactionSynchronizationManager.initSynchronization();
        assertThatThrownBy(() -> service.updateTenant(12L, form("new-code")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verifyNoInteractions(redis);
    }

    @Test
    void directInvocationWithoutTransactionRefreshesImmediately() {
        when(converter.form2Entity(any())).thenReturn(tenant("new-code", 12L));
        doReturn(List.of(tenant("new-code", 12L))).when(service).list();
        service.updateTenant(12L, form("new-code"));
        verify(hashes).putAll(CacheConstants.SECURITY_SYSTEM_TENANTS, Map.of("new-code", "12"));
    }

    @Test
    void caseInsensitiveDuplicateCodesCannotPublishAmbiguousMappings() {
        doReturn(List.of(tenant("macula-cloud", 1L), tenant("MACULA-CLOUD", 12L))).when(service).list();
        assertThatThrownBy(service::refreshTenantCache).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(redis);
    }

    @Test
    @SuppressWarnings("unchecked")
    void concurrentRefreshCannotPublishOldSnapshotAfterNewSnapshot() throws Exception {
        ReentrantLock sharedLock = new ReentrantLock();
        CountDownLatch firstRead = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondLockAttempt = new CountDownLatch(1);
        AtomicInteger lockAttempts = new AtomicInteger();
        AtomicInteger reads = new AtomicInteger();
        doAnswer(invocation -> {
            if (lockAttempts.incrementAndGet() == 2) {
                secondLockAttempt.countDown();
            }
            sharedLock.lock();
            return null;
        }).when(lock).lock();
        doAnswer(invocation -> {
            sharedLock.unlock();
            return null;
        }).when(lock).unlock();
        doAnswer(invocation -> {
            if (reads.incrementAndGet() == 1) {
                firstRead.countDown();
                assertThat(releaseFirst.await(5, TimeUnit.SECONDS)).isTrue();
                return List.of(tenant("old-code", 12L));
            }
            return List.of(tenant("new-code", 12L));
        }).when(service).list();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(service::refreshTenantCache);
            assertThat(firstRead.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(service::refreshTenantCache);
            assertThat(secondLockAttempt.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(reads.get()).isEqualTo(1);
            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
        ArgumentCaptor<Map<String, String>> published = ArgumentCaptor.forClass(Map.class);
        verify(hashes, times(2)).putAll(eq(CacheConstants.SECURITY_SYSTEM_TENANTS), published.capture());
        assertThat(published.getAllValues().get(1)).containsExactlyEntriesOf(Map.of("new-code", "12"));
    }

    @Test
    void databaseFailureReleasesSharedRefreshLock() {
        doThrow(new IllegalStateException("test failure")).when(service).list();
        assertThatThrownBy(service::refreshTenantCache).isInstanceOf(IllegalStateException.class);
        verify(lock).unlock();
    }

    private static void commitCallbacks() {
        for (TransactionSynchronization callback : TransactionSynchronizationManager.getSynchronizations()) {
            callback.afterCommit();
        }
    }

    private static SysTenantInfo tenant(String code, Long id) {
        SysTenantInfo tenant = new SysTenantInfo();
        tenant.setCode(code);
        tenant.setId(id);
        return tenant;
    }

    private static TenantForm form(String code) {
        TenantForm form = new TenantForm();
        form.setCode(code);
        form.setName("测试租户");
        form.setSupervisor(List.of(1L));
        return form;
    }
}
