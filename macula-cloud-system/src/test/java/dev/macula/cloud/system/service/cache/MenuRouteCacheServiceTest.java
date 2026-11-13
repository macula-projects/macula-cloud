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

package dev.macula.cloud.system.service.cache;

import dev.macula.boot.context.TenantContextHolder;
import dev.macula.cloud.system.mapper.SysTenantInfoMapper;
import dev.macula.cloud.system.mapper.SysTenantUserMapper;
import dev.macula.cloud.system.mapper.SysUserMapper;
import dev.macula.cloud.system.service.cache.MenuRouteTenantContext.TrustedTenant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 菜单路由缓存键和事务提交后失效测试。
 *
 * @author Rain
 * @since 6.1.0
 */
class MenuRouteCacheServiceTest {

    private final CacheManager cacheManager = mock(CacheManager.class);

    private final Cache cache = mock(Cache.class);

    private final MenuRouteCacheService service = new MenuRouteCacheService(cacheManager);

    @AfterEach
    void clearContext() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
        SecurityContextHolder.clearContext();
        TenantContextHolder.clearCurrentTenantId();
    }

    @Test
    void keyIsVersionedAndTenantSpecific() {
        assertEquals("routes:v2:1", service.key(trusted(1L)));
        assertEquals("routes:v2:12", service.key(trusted(12L)));
    }

    @Test
    void nonTransactionalEvictionIsImmediateAndPrecise() {
        when(cacheManager.getCache("system")).thenReturn(cache);

        service.evictAfterCommit(trusted(12L));

        verify(cache).evict("routes:v2:12");
        verify(cache, never()).evict("routes:v2:1");
    }

    @Test
    void transactionalEvictionRunsOnlyAfterCommit() {
        when(cacheManager.getCache("system")).thenReturn(cache);
        beginTransaction();

        service.evictAfterCommit(trusted(12L));
        verify(cache, never()).evict("routes:v2:12");

        TransactionSynchronization synchronization =
            TransactionSynchronizationManager.getSynchronizations().get(0);
        synchronization.afterCommit();
        verify(cache).evict("routes:v2:12");
    }

    @Test
    void rolledBackTransactionDoesNotEvict() {
        when(cacheManager.getCache("system")).thenReturn(cache);
        beginTransaction();

        service.evictAfterCommit(trusted(12L));
        TransactionSynchronization synchronization =
            TransactionSynchronizationManager.getSynchronizations().get(0);
        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(cache, never()).evict("routes:v2:12");
    }

    @Test
    void missingCacheFailsExplicitly() {
        when(cacheManager.getCache("system")).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> service.evictAfterCommit(trusted(1L)));
    }

    private void beginTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private TrustedTenant trusted(Long tenantId) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject("service-client")
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
            .claim("tenantId", tenantId).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
            List.of(new SimpleGrantedAuthority("SCOPE_system.read")), "service-client"));
        return new MenuRouteTenantContext(mock(SysUserMapper.class), mock(SysTenantUserMapper.class),
            mock(SysTenantInfoMapper.class)).resolve();
    }
}
