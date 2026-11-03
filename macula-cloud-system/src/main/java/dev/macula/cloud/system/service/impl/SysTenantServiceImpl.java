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

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import dev.macula.boot.constants.GlobalConstants;
import dev.macula.boot.constants.CacheConstants;
import dev.macula.boot.result.Option;
import dev.macula.boot.starter.security.utils.SecurityUtils;
import dev.macula.cloud.system.converter.TenantConverter;
import dev.macula.cloud.system.form.TenantForm;
import dev.macula.cloud.system.mapper.SysTenantInfoMapper;
import dev.macula.cloud.system.pojo.bo.TenantBO;
import dev.macula.cloud.system.pojo.entity.SysTenantInfo;
import dev.macula.cloud.system.pojo.entity.SysTenantUser;
import dev.macula.cloud.system.query.TenantPageQuery;
import dev.macula.cloud.system.service.SysTenantService;
import dev.macula.cloud.system.service.SysTenantUserService;
import dev.macula.cloud.system.vo.tenant.TenantPageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.redisson.api.RedissonClient;
import org.redisson.api.RLock;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 维护租户及负责人，事务提交后同步网关租户编码映射。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class SysTenantServiceImpl extends ServiceImpl<SysTenantInfoMapper, SysTenantInfo> implements SysTenantService {

    private final TenantConverter tenantConverter;
    private final SysTenantUserService tenantUserService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;

    @Override
    public void refreshTenantCache() {
        RLock lock = redissonClient.getLock(CacheConstants.SECURITY_SYSTEM_TENANTS + ":refresh");
        lock.lock();
        try {
            Map<String, String> tenantIds = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (SysTenantInfo tenant : this.list()) {
                if (StrUtil.isNotBlank(tenant.getCode())) {
                    String previous = tenantIds.putIfAbsent(tenant.getCode(), String.valueOf(tenant.getId()));
                    Assert.isTrue(previous == null, "租户编码重复，无法生成网关归属缓存");
                }
            }
            redisTemplate.delete(CacheConstants.SECURITY_SYSTEM_TENANTS);
            if (!tenantIds.isEmpty()) {
                redisTemplate.opsForHash().putAll(CacheConstants.SECURITY_SYSTEM_TENANTS, tenantIds);
            }
        } finally {
            lock.unlock();
        }
    }

    private void refreshTenantCacheAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    refreshTenantCache();
                }
            });
        } else {
            refreshTenantCache();
        }
    }

    @Override
    public IPage<TenantPageVO> listTenantPages(TenantPageQuery queryParams) {
        // 查询数据
        Page<TenantBO> tenantPages =
            this.baseMapper.listTenantPages(new Page<>(queryParams.getPageNum(), queryParams.getPageSize()),
                queryParams);
        return tenantConverter.bo2Page(tenantPages);
    }

    @Override
    @Transactional
    public boolean saveTenant(TenantForm tenantForm) {
        long count = this.count(
            new LambdaQueryWrapper<SysTenantInfo>().eq(SysTenantInfo::getCode, tenantForm.getCode()).or()
                .eq(SysTenantInfo::getName, tenantForm.getName()));
        Assert.isTrue(count == 0, "租户已存在");

        SysTenantInfo sysTenant = tenantConverter.form2Entity(tenantForm);
        boolean saveFlag = save(sysTenant);
        Assert.isTrue(saveFlag, "保存租户失败");
        refreshTenantCacheAfterCommit();
        List<SysTenantUser> tenantUserList =
            tenantForm.getSupervisor().stream().map(userId -> new SysTenantUser(userId, sysTenant.getId()))
                .collect(Collectors.toList());
        return tenantUserService.saveBatch(tenantUserList);
    }

    @Override
    @Transactional
    public boolean updateTenant(Long id, TenantForm tenantForm) {
        String tenantName = tenantForm.getName();
        long count = this.count(new LambdaQueryWrapper<SysTenantInfo>().ne(SysTenantInfo::getId, id).and(wrapper -> {
            wrapper.eq(SysTenantInfo::getName, tenantName).or().eq(SysTenantInfo::getCode, tenantForm.getCode());
        }));
        Assert.isTrue(count == 0, "租户已存在");

        SysTenantInfo sysTenant = tenantConverter.form2Entity(tenantForm);
        sysTenant.setId(id);
        boolean saveFlag = updateById(sysTenant);
        Assert.isTrue(saveFlag, "更新租户失败");
        refreshTenantCacheAfterCommit();
        tenantUserService.remove(
            new LambdaQueryWrapper<SysTenantUser>().eq(SysTenantUser::getTenantId, sysTenant.getId()));
        List<SysTenantUser> tenantUserList =
            tenantForm.getSupervisor().stream().map(userId -> new SysTenantUser(userId, sysTenant.getId()))
                .collect(Collectors.toList());
        return tenantUserService.saveBatch(tenantUserList);
    }

    @Override
    @Transactional
    public boolean deleteTenants(String idsStr) {
        Assert.isTrue(StrUtil.isNotBlank(idsStr), "删除的租户数据为空");
        // 逻辑删除
        List<Long> ids = Arrays.stream(idsStr.split(",")).map(Long::parseLong).collect(Collectors.toList());
        tenantUserService.remove(new LambdaQueryWrapper<SysTenantUser>().in(SysTenantUser::getTenantId, ids));
        boolean deleted = this.removeByIds(ids);
        if (deleted) {
            refreshTenantCacheAfterCommit();
        }
        return deleted;
    }

    @Override
    public List<Option<Long>> listTenantOptions(boolean filterMe) {
        boolean filterMeFlag = filterMe && !SecurityUtils.isRoot();
        Set<Long> ids = tenantUserService.getMeTenantIds();
        if (CollectionUtil.isEmpty(ids)) {
            // 默认租户
            ids.add(GlobalConstants.DEFAULT_TENANT_ID);
        }
        List<SysTenantInfo> sysTenantInfoList =
            list(new LambdaQueryWrapper<SysTenantInfo>().in(filterMeFlag, SysTenantInfo::getId, ids));
        return sysTenantInfoList.stream().map(tenantInfo -> new Option<>(tenantInfo.getId(), tenantInfo.getName()))
            .collect(Collectors.toList());
    }

}
