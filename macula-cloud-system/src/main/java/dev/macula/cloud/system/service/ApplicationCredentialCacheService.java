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

import cn.hutool.core.util.StrUtil;
import dev.macula.boot.security.AccessRuleUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.macula.boot.constants.CacheConstants;
import dev.macula.boot.constants.GlobalConstants;
import dev.macula.boot.context.TenantContextHolder;
import dev.macula.cloud.system.mapper.SysApplicationApiKeyMapper;
import dev.macula.cloud.system.mapper.SysApplicationMapper;
import dev.macula.cloud.system.pojo.entity.SysApplication;
import dev.macula.cloud.system.pojo.entity.SysApplicationApiKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * 提交后发布应用 HMAC 和 API Key 缓存，重建与撤销共用刷新锁。
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class ApplicationCredentialCacheService {
    private static final String REFRESH_LOCK = CacheConstants.SECURITY_SYSTEM_APPS + ":credentials:refresh";
    private final SysApplicationMapper applications;
    private final SysApplicationApiKeyMapper keys;
    private final SysTenantService tenants;
    private final RedisTemplate<String, Object> redis;
    private final RedissonClient redisson;
    private final PlatformTransactionManager transactionManager;

    /** 提交成功后刷新，回调显式保留租户。 */
    public void afterCommit(Long tenantId, Long appId, String previousCode) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    refresh(tenantId, appId, previousCode);
                }
            });
        } else {
            refresh(tenantId, appId, previousCode);
        }
    }

    /** 获取刷新锁后读取新的已提交状态，避免旧快照恢复撤销凭据。 */
    public void refresh(Long tenantId, Long appId, String previousCode) {
        locked(() -> {
            Long previous = TenantContextHolder.getCurrentTenantId();
            try {
                TenantContextHolder.setCurrentTenantId(tenantId);
                if (previousCode != null) {
                    redis.delete(CacheConstants.SECURITY_SYSTEM_APPS + previousCode);
                }
                readLatest(() -> publish(tenantId, appId));
            } finally {
                restoreTenant(previous);
            }
        });
    }

    /** 启动恢复有效缓存，也清理已撤销及已删除应用的 Key Hash。 */
    public void rebuild() {
        locked(() -> {
            Long previous = TenantContextHolder.getCurrentTenantId();
            try {
                for (var tenant : tenants.list()) {
                    TenantContextHolder.setCurrentTenantId(tenant.getId());
                    readLatest(() -> {
                        List<SysApplication> apps = applications.selectList(new LambdaQueryWrapper<SysApplication>()
                            .eq(SysApplication::getTenantId, tenant.getId()));
                        for (SysApplication app : apps) {
                            publish(tenant.getId(), app.getId());
                        }
                        for (SysApplicationApiKey key : keys.selectList(new LambdaQueryWrapper<SysApplicationApiKey>()
                            .eq(SysApplicationApiKey::getTenantId, tenant.getId()))) {
                            if (Boolean.TRUE.equals(key.getDeleted())
                                || apps.stream().noneMatch(app -> app.getId().equals(key.getApplicationId()))) {
                                redis.delete(CacheConstants.APIKEY_CACHE_PREFIX + key.getApiKey());
                            }
                        }
                    });
                }
            } finally {
                restoreTenant(previous);
            }
        });
    }

    /**
     * 无外层事务时开启只读事务；提交回调复用已提交的连接，避免池耗尽时再申请连接。
     * 缓存专用 Mapper 强制清除查询缓存，数据库事务已提交，不复用原事务快照。
     */
    private void readLatest(Runnable work) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setReadOnly(true);
        transaction.executeWithoutResult(status -> work.run());
    }

    /** 在缓存锁内从数据库重读，已撤销凭据只删除不发布。 */
    private void publish(Long tenantId, Long appId) {
        SysApplication app = keys.cacheApplication(appId, tenantId);
        if (app != null) {
            Map<String, String> fields = tenantFields(app);
            fields.put(CacheConstants.SECURITY_SYSTEM_APPS_SECRIT_KEY, app.getSk());
            fields.put(CacheConstants.SECURITY_SYSTEM_APPS_PERMIT_URLS, AccessRuleUtils.normalizeUrls(app.getAccessPath()));
            fields.put(CacheConstants.SECURITY_SYSTEM_APPS_PERMIT_IPS, AccessRuleUtils.normalizeIps(app.getAllowIps()));
            redis.opsForHash().putAll(CacheConstants.SECURITY_SYSTEM_APPS + app.getCode(), fields);
        }
        for (SysApplicationApiKey key : keys.cacheKeys(appId, tenantId)) {
            String cacheKey = CacheConstants.APIKEY_CACHE_PREFIX + key.getApiKey();
            if (app == null || Boolean.TRUE.equals(key.getDeleted())) {
                redis.delete(cacheKey);
            } else {
                redis.opsForHash().putAll(cacheKey, apiKeyFields(app, key));
            }
        }
    }

    /** 新增、更新和重建共用 URL/IP 各自 Key 优先、应用兜底规则。 */
    public static Map<String, String> apiKeyFields(SysApplication app, SysApplicationApiKey key) {
        Map<String, String> fields = tenantFields(app);
        fields.put(CacheConstants.APIKEY_APPLICATION_CODE_ATTRIBUTE, app.getCode());
        fields.put(CacheConstants.APIKEY_APPLICATION_ID_ATTRIBUTE, app.getId().toString());
        fields.put(CacheConstants.SECURITY_SYSTEM_APPS_PERMIT_URLS,
            AccessRuleUtils.normalizeUrls(StrUtil.isNotBlank(key.getAccessPath()) ? key.getAccessPath() : app.getAccessPath()));
        // 两个维度独立继承；显式发布空字符串，清空后不能残留旧缓存字段。
        fields.put(CacheConstants.SECURITY_SYSTEM_APPS_PERMIT_IPS,
            AccessRuleUtils.normalizeIps(StrUtil.isNotBlank(key.getAllowIps()) ? key.getAllowIps() : app.getAllowIps()));
        return fields;
    }

    /** 分开传递归属租户和业务认证租户，禁止从请求参数推断。 */
    private static Map<String, String> tenantFields(SysApplication app) {
        if (app.getTenantId() == null || app.getId() == null || StrUtil.isBlank(app.getCode())) {
            throw new IllegalStateException("应用身份配置不完整");
        }
        Map<String, String> fields = new HashMap<>();
        fields.put(GlobalConstants.TENANT_ID_NAME, app.getTenantId().toString());
        fields.put(CacheConstants.SECURITY_SYSTEM_APPS_AUTH_TENANT_ID,
            String.valueOf(app.getAuthTenantId() == null ? app.getTenantId() : app.getAuthTenantId()));
        return fields;
    }

    /** 统一串行化缓存发布，向外报告错误时不携带可能含 Key 的 Redis 异常。 */
    private void locked(Runnable work) {
        RLock lock = null;
        boolean acquired = false;
        try {
            lock = redisson.getLock(REFRESH_LOCK);
            lock.lock();
            acquired = true;
            work.run();
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "凭据缓存同步失败，请重试或重建缓存");
        } finally {
            if (acquired) {
                lock.unlock();
            }
        }
    }

    /** 恢复原上下文，包含原来未设置租户的情况。 */
    private void restoreTenant(Long tenantId) {
        if (tenantId == null) {
            TenantContextHolder.clearCurrentTenantId();
        } else {
            TenantContextHolder.setCurrentTenantId(tenantId);
        }
    }
}
