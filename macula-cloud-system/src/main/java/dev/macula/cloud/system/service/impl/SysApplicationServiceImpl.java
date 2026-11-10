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
import dev.macula.boot.context.TenantContextHolder;
import dev.macula.boot.security.ResourceAccessException;
import dev.macula.cloud.system.service.MenuRouteTenantContext;
import dev.macula.cloud.system.converter.ApplicationConverter;
import dev.macula.cloud.system.form.ApplicationForm;
import dev.macula.cloud.system.mapper.SysApplicationMapper;
import dev.macula.cloud.system.mapper.SysApplicationApiKeyMapper;
import dev.macula.cloud.system.pojo.entity.SysApplicationApiKey;
import dev.macula.cloud.system.service.ApplicationCredentialCacheService;
import dev.macula.cloud.system.pojo.bo.ApplicationBO;
import dev.macula.cloud.system.pojo.entity.SysApplication;
import dev.macula.cloud.system.pojo.entity.SysTenantInfo;
import dev.macula.cloud.system.query.ApplicationPageQuery;
import dev.macula.cloud.system.service.SysApplicationService;
import dev.macula.cloud.system.service.SysTenantService;
import dev.macula.cloud.system.vo.app.ApplicationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;


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
    private final ApplicationCredentialCacheService credentialCache;
    private final SysApplicationApiKeyMapper apiKeys;
    private final MenuRouteTenantContext tenantContext;

    @Override
    public Page<ApplicationVO> listApplicationPages(ApplicationPageQuery queryParams) {
        Page<SysApplication> page = new Page<>(queryParams.getPageNum(), queryParams.getPageSize());
        Page<ApplicationBO> bo = this.baseMapper.listApplicationPages(page, queryParams);
        return applicationConverter.bo2Vo(bo);
    }

    /** 校验并规范化应用访问规则，提交成功后发布应用及关联 Key 缓存。 */
    @Override
    @Transactional
    public boolean saveApplication(ApplicationForm appForm) {
        Long ownerTenantId = currentTenantId();
        validateAuthTenant(appForm, ownerTenantId);
        try {
            appForm.normalizeAccessRules();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL/IP 规则不合法或超过 2000 字符");
        }
        SysApplication sysApplication = applicationConverter.form2Entity(appForm);
        boolean saved = this.save(sysApplication);
        if (saved) {
            credentialCache.afterCommit(ownerTenantId, sysApplication.getId(), null);
        }
        return saved;
    }

    /** 在应用行锁内更新规则，IP 省略保留、显式空值清空，提交后统一刷新凭据。 */
    @Override
    @Transactional
    public boolean updateApplication(Long appId, ApplicationForm appForm) {
        SysApplication existing = apiKeys.lockApplication(appId, currentTenantId());
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "应用不存在或无权访问");
        }
        validateAuthTenant(appForm, existing.getTenantId());
        try {
            appForm.normalizeAccessRules();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL/IP 规则不合法或超过 2000 字符");
        }
        SysApplication sysApplication = applicationConverter.form2Entity(appForm);
        sysApplication.setId(appId);
        LambdaUpdateWrapper<SysApplication> update = new LambdaUpdateWrapper<SysApplication>()
            .eq(SysApplication::getId, appId);
        if (appForm.isAuthTenantIdProvided()) {
            update.set(SysApplication::getAuthTenantId, appForm.getAuthTenantId());
        }
        update.set(SysApplication::getAccessPath, appForm.getAccessPath());
        // 不能让旧客户端省略字段时清空配置；显式空值必须覆盖旧限制。
        update.set(appForm.isAllowIpsProvided(), SysApplication::getAllowIps, appForm.getAllowIps());
        boolean updated = this.update(sysApplication, update);
        if (updated) {
            credentialCache.afterCommit(existing.getTenantId(), appId, existing.getCode());
        }
        return updated;
    }

    @Override
    @Transactional
    public boolean deleteApplications(String idsStr) {
        Assert.isTrue(StrUtil.isNotBlank(idsStr), "删除的应用数据为空");
        Long tenantId = currentTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无法确认当前租户");
        }
        List<Long> ids = Arrays.stream(idsStr.split(",")).map(Long::parseLong).distinct().sorted().toList();
        // 与创建 Key 先锁同一应用，防止删除捕获集合后出现新的有效凭据。
        for (Long id : ids) {
            SysApplication app = apiKeys.lockApplication(id, tenantId);
            if (app == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "应用不存在或无权访问");
            }
            apiKeys.update(null, new LambdaUpdateWrapper<SysApplicationApiKey>()
                .eq(SysApplicationApiKey::getTenantId, tenantId).eq(SysApplicationApiKey::getApplicationId, id)
                .set(SysApplicationApiKey::getDeleted, true));
            credentialCache.afterCommit(tenantId, id, app.getCode());
        }
        if (!this.removeByIds(ids)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "删除应用失败");
        }
        return true;
    }

    @Override
    public boolean addMaintainer(Long appId, ApplicationForm appForm) {
        SysApplication application = this.getById(appId);
        application.setMaintainer(appForm.getMaintainer());
        return this.updateById(application);
    }

    @Override
    public void refreshApplicationCache() {
        credentialCache.rebuild();
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
        try {
            return tenantContext.resolve().tenantId();
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问当前租户应用");
        }
    }

    private void restoreTenant(Long tenantId) {
        if (tenantId == null) {
            TenantContextHolder.clearCurrentTenantId();
        } else {
            TenantContextHolder.setCurrentTenantId(tenantId);
        }
    }
}
