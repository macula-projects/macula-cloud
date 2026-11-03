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

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import dev.macula.boot.starter.security.utils.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Objects;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import dev.macula.boot.constants.CacheConstants;
import dev.macula.boot.constants.GlobalConstants;
import dev.macula.boot.context.TenantContextHolder;
import dev.macula.cloud.system.converter.ApplicationConverter;
import dev.macula.cloud.system.form.ApplicationForm;
import dev.macula.cloud.system.mapper.SysApplicationMapper;
import dev.macula.cloud.system.pojo.bo.ApplicationBO;
import dev.macula.cloud.system.pojo.entity.SysApplication;
import dev.macula.cloud.system.pojo.entity.SysTenantInfo;
import dev.macula.cloud.system.query.ApplicationPageQuery;
import dev.macula.cloud.system.service.SysApplicationService;
import dev.macula.cloud.system.service.SysTenantService;
import dev.macula.cloud.system.vo.app.ApplicationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 维护第三方应用、认证租户授权以及网关应用凭据缓存。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class SysApplicationServiceImpl extends ServiceImpl<SysApplicationMapper, SysApplication>
    implements SysApplicationService {

    private final ApplicationConverter applicationConverter;
    private final SysTenantService tenantService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Page<ApplicationVO> listApplicationPages(ApplicationPageQuery queryParams) {
        Page<SysApplication> page = new Page<>(queryParams.getPageNum(), queryParams.getPageSize());
        Page<ApplicationBO> bo = this.baseMapper.listApplicationPages(page, queryParams);
        return applicationConverter.bo2Vo(bo);
    }

    @Override
    public boolean saveApplication(ApplicationForm appForm) {
        Long ownerTenantId = currentTenantId();
        validateAuthTenant(appForm, ownerTenantId);
        SysApplication sysApplication = applicationConverter.form2Entity(appForm);
        return this.save(sysApplication);
    }

    @Override
    public boolean updateApplication(Long appId, ApplicationForm appForm) {
        SysApplication existing = this.getById(appId);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "应用不存在或无权访问");
        }
        validateAuthTenant(appForm, existing.getTenantId());
        SysApplication sysApplication = applicationConverter.form2Entity(appForm);
        sysApplication.setId(appId);
        LambdaUpdateWrapper<SysApplication> update = new LambdaUpdateWrapper<SysApplication>()
            .eq(SysApplication::getId, appId);
        if (appForm.isAuthTenantIdProvided()) {
            update.set(SysApplication::getAuthTenantId, appForm.getAuthTenantId());
        }
        return this.update(sysApplication, update);
    }

    @Override
    @Transactional
    public boolean deleteApplications(String idsStr) {
        Assert.isTrue(StrUtil.isNotBlank(idsStr), "删除的应用数据为空");
        // 逻辑删除
        List<Long> ids = Arrays.stream(idsStr.split(",")).map(Long::parseLong).collect(Collectors.toList());
        return this.removeByIds(ids);
    }

    @Override
    public boolean addMaintainer(Long appId, ApplicationForm appForm) {
        SysApplication application = this.getById(appId);
        application.setMaintainer(appForm.getMaintainer());
        return this.updateById(application);
    }

    @Override
    @Async
    public void refreshApplicationCache() {
        List<SysTenantInfo> tenantInfos = tenantService.list();
        // 获取当前上下文默认的租户id
        Long contextTenantId = TenantContextHolder.getCurrentTenantId();
        try {
            tenantInfos.forEach(tenant -> {
                TenantContextHolder.setCurrentTenantId(tenant.getId());
                List<SysApplication> applications = this.list();
                applications.forEach(application -> {
                    Map<String, String> apps = new HashMap<>();
                    apps.put(CacheConstants.SECURITY_SYSTEM_APPS_SECRIT_KEY, application.getSk());
                    apps.put(GlobalConstants.TENANT_ID_NAME, String.valueOf(tenant.getId()));
                    Long authTenantId = application.getAuthTenantId() == null
                        ? tenant.getId() : application.getAuthTenantId();
                    apps.put(CacheConstants.SECURITY_SYSTEM_APPS_AUTH_TENANT_ID, String.valueOf(authTenantId));
                    apps.put(CacheConstants.SECURITY_SYSTEM_APPS_PERMIT_URLS,
                        StrUtil.nullToEmpty(application.getAccessPath()));
                    String key = CacheConstants.SECURITY_SYSTEM_APPS + application.getCode();
                    redisTemplate.delete(key);
                    redisTemplate.opsForHash().putAll(key, apps);
                });
            });
        } finally {
            restoreTenant(contextTenantId);
        }
    }

    @Override
    public boolean validatorAppCode(Long appId, String appCode) {
        List<SysTenantInfo> tenantInfos = tenantService.list();
        // 获取当前上下文默认的租户id
        Long contextTenantId = TenantContextHolder.getCurrentTenantId();
        try {
            if (tenantInfos != null && !tenantInfos.isEmpty()) {
                for (SysTenantInfo tenant : tenantInfos) {
                    TenantContextHolder.setCurrentTenantId(tenant.getId());
                    Wrapper<SysApplication> queryWrapper =
                        new LambdaQueryWrapper<SysApplication>().eq(SysApplication::getCode, appCode)
                            .and(appId != null, wrapper -> wrapper.ne(SysApplication::getId, appId));
                    if (getBaseMapper().selectCount(queryWrapper) != 0) {
                        return false;
                    }
                }
            }
            return true;
        } finally {
            restoreTenant(contextTenantId);
        }
    }

    private void validateAuthTenant(ApplicationForm form, Long ownerTenantId) {
        if (!form.isAuthTenantIdProvided() || form.getAuthTenantId() == null) {
            return;
        }
        Long authTenantId = form.getAuthTenantId();
        if (ownerTenantId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无法确认应用归属租户");
        }
        if (!Objects.equals(authTenantId, ownerTenantId) && !SecurityUtils.isRoot()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅 ROOT 可设置跨租户认证");
        }
        if (tenantService.getById(authTenantId) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "认证租户不存在或无权访问");
        }
    }

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getCurrentTenantId();
        return tenantId == null ? SecurityUtils.getTenantId() : tenantId;
    }

    private void restoreTenant(Long tenantId) {
        if (tenantId == null) {
            TenantContextHolder.clearCurrentTenantId();
        } else {
            TenantContextHolder.setCurrentTenantId(tenantId);
        }
    }
}
