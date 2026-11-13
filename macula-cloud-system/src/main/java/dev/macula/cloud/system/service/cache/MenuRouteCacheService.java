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

import dev.macula.cloud.system.service.cache.MenuRouteTenantContext.TrustedTenant;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 生成租户级菜单路由缓存键并在事务提交后精确失效缓存。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class MenuRouteCacheService {

    static final String CACHE_NAME = "system";

    static final String KEY_PREFIX = "routes:v2:";

    private final CacheManager cacheManager;

    /**
     * 生成已校验租户的路由缓存键。
     *
     * @param tenant 已校验租户
     * @return 版本化租户缓存键
     */
    public String key(TrustedTenant tenant) {
        return KEY_PREFIX + tenant.tenantId();
    }

    /**
     * 在当前事务提交后精确失效租户路由缓存；无事务时立即失效。
     *
     * @param tenant 已校验租户
     */
    public void evictAfterCommit(TrustedTenant tenant) {
        Cache cache = requireCache();
        String key = key(tenant);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                throw new IllegalStateException("菜单路由缓存无法注册事务提交回调");
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cache.evict(key);
                }
            });
            return;
        }
        cache.evict(key);
    }

    private Cache requireCache() {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) {
            throw new IllegalStateException("System 菜单路由缓存不可用");
        }
        return cache;
    }
}
