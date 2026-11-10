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

import cn.hutool.core.util.StrUtil;
import dev.macula.boot.security.AccessRuleUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.macula.boot.security.ResourceAccessException;
import dev.macula.cloud.system.service.MenuRouteTenantContext;
import dev.macula.boot.starter.security.utils.SecurityUtils;
import dev.macula.cloud.system.form.ApplicationApiKeyForm;
import dev.macula.cloud.system.mapper.SysApplicationApiKeyMapper;
import dev.macula.cloud.system.mapper.SysApplicationMapper;
import dev.macula.cloud.system.pojo.entity.SysApplication;
import dev.macula.cloud.system.pojo.entity.SysApplicationApiKey;
import dev.macula.cloud.system.service.ApplicationCredentialCacheService;
import dev.macula.cloud.system.service.SysApplicationApiKeyService;
import dev.macula.cloud.system.vo.app.ApplicationApiKeySecretVO;
import dev.macula.cloud.system.vo.app.ApplicationApiKeyVO;
import dev.macula.boot.mapstruct.DateTimeConverter;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 校验应用管理权限并维护独立 API Key，凭据修改与应用删除共用行锁。
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class SysApplicationApiKeyServiceImpl implements SysApplicationApiKeyService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int KEY_BYTES = 32;
    private static final int MAX_PAGE_SIZE = 100;
    private final SysApplicationApiKeyMapper mapper;
    private final SysApplicationMapper applications;
    private final ApplicationCredentialCacheService cache;
    private final MenuRouteTenantContext tenantContext;
    private final DateTimeConverter dateTimeConverter;

    /** 校验管理权限及持久化应用归属，不能仅依赖网关权限缓存。 */
    private SysApplication application(Long appId, boolean lock) {
        Long tenantId;
        try {
            // 请求租户需先与认证租户或成员关系核对，不能直接信任 Header。
            tenantId = tenantContext.resolve().tenantId();
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问当前租户应用");
        }
        Set<String> roles = SecurityUtils.getRoles();
        if (!SecurityUtils.isRoot() && (roles == null || roles.isEmpty()
            || mapper.countManagers(tenantId, roles) == 0)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无应用管理权限");
        }
        SysApplication app = lock ? mapper.lockApplication(appId, tenantId)
            : applications.selectOne(new LambdaQueryWrapper<SysApplication>()
                .eq(SysApplication::getId, appId).eq(SysApplication::getTenantId, tenantId));
        if (app == null || !Objects.equals(tenantId, app.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "应用不存在或无权访问");
        }
        return app;
    }

    /** 按应用及归属租户共同限定 Key，隐藏跨应用记录是否存在。 */
    private SysApplicationApiKey key(SysApplication app, Long keyId) {
        SysApplicationApiKey key = mapper.selectOne(scope(app).eq(SysApplicationApiKey::getId, keyId));
        if (key == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "API Key 不存在或无权访问");
        }
        return key;
    }

    /** 非 HTTP 调用也执行共享规则校验，错误不回显原始配置内容。 */
    private void normalizeRules(ApplicationApiKeyForm form) {
        try {
            form.normalizeAccessRules();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL/IP 规则不合法或超过 2000 字符");
        }
    }

    /** 生成仅包含当前应用有效凭据的查询条件。 */
    private LambdaQueryWrapper<SysApplicationApiKey> scope(SysApplication app) {
        return new LambdaQueryWrapper<SysApplicationApiKey>().eq(SysApplicationApiKey::getTenantId, app.getTenantId())
            .eq(SysApplicationApiKey::getApplicationId, app.getId()).eq(SysApplicationApiKey::getDeleted, false);
    }

    @Override
    public Page<ApplicationApiKeyVO> page(Long appId, int pageNum, int pageSize) {
        if (pageNum < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数不合法");
        }
        SysApplication app = application(appId, false);
        Page<SysApplicationApiKey> result = mapper.selectPage(new Page<>(pageNum, pageSize),
            scope(app).orderByDesc(SysApplicationApiKey::getId));
        Page<ApplicationApiKeyVO> page = new Page<>(pageNum, pageSize, result.getTotal());
        page.setRecords(result.getRecords().stream().map(this::toView).toList());
        return page;
    }

    /** 列表仅返回凭据掩码、多行规则，并按配置的存储时区转换创建时间。 */
    private ApplicationApiKeyVO toView(SysApplicationApiKey key) {
        ApplicationApiKeyVO view = new ApplicationApiKeyVO();
        view.setId(key.getId().toString());
        String value = key.getApiKey();
        view.setMaskedKey(value.substring(0, 7) + "****" + value.substring(value.length() - 4));
        view.setRemark(key.getRemark());
        view.setAccessPath(AccessRuleUtils.normalizeUrls(key.getAccessPath()));
        view.setAllowIps(key.getAllowIps());
        // 审计时间按公共存储时区解释，避免绕过外部时区配置。
        view.setCreateTime(dateTimeConverter.toDate(key.getCreateTime()));
        return view;
    }

    /** 校验 URL/IP 后创建服务端生成的 Key；提交后按各自继承规则发布缓存。 */
    @Override
    @Transactional
    public ApplicationApiKeySecretVO create(Long appId, ApplicationApiKeyForm form) {
        SysApplication app = application(appId, true);
        normalizeRules(form);
        byte[] bytes = new byte[KEY_BYTES];
        RANDOM.nextBytes(bytes);
        SysApplicationApiKey key = new SysApplicationApiKey();
        key.setTenantId(app.getTenantId());
        key.setApplicationId(appId);
        key.setApiKey("sk-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        key.setRemark(StrUtil.trimToNull(form.getRemark()));
        key.setAccessPath(StrUtil.trimToNull(form.getAccessPath()));
        key.setAllowIps(form.getAllowIps());
        key.setDeleted(false);
        try {
            mapper.insert(key);
        } catch (DataAccessException exception) {
            // 数据库唯一键异常可能包含凭据值，不将原异常传给日志或审计。
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "创建 API Key 失败，请重试");
        }
        cache.afterCommit(app.getTenantId(), appId, null);
        return new ApplicationApiKeySecretVO(key.getId().toString(), key.getApiKey());
    }

    /** 在应用行锁内更新独立规则，省略 IP 保留，显式空白恢复应用继承。 */
    @Override
    @Transactional
    public void update(Long appId, Long keyId, ApplicationApiKeyForm form) {
        SysApplication app = application(appId, true);
        key(app, keyId);
        normalizeRules(form);
        mapper.update(null, new LambdaUpdateWrapper<SysApplicationApiKey>()
            .eq(SysApplicationApiKey::getId, keyId).eq(SysApplicationApiKey::getTenantId, app.getTenantId())
            .eq(SysApplicationApiKey::getApplicationId, appId).eq(SysApplicationApiKey::getDeleted, false)
            .set(SysApplicationApiKey::getRemark, StrUtil.trimToNull(form.getRemark()))
            .set(SysApplicationApiKey::getAccessPath, StrUtil.trimToNull(form.getAccessPath()))
            .set(form.isAllowIpsProvided(), SysApplicationApiKey::getAllowIps, form.getAllowIps()));
        cache.afterCommit(app.getTenantId(), appId, null);
    }

    @Override
    @Transactional
    public void delete(Long appId, Long keyId) {
        SysApplication app = application(appId, true);
        key(app, keyId);
        mapper.update(null, new LambdaUpdateWrapper<SysApplicationApiKey>()
            .eq(SysApplicationApiKey::getId, keyId).eq(SysApplicationApiKey::getTenantId, app.getTenantId())
            .eq(SysApplicationApiKey::getApplicationId, appId).set(SysApplicationApiKey::getDeleted, true));
        cache.afterCommit(app.getTenantId(), appId, null);
    }

    @Override
    public ApplicationApiKeySecretVO secret(Long appId, Long keyId) {
        SysApplicationApiKey key = key(application(appId, false), keyId);
        return new ApplicationApiKeySecretVO(key.getId().toString(), key.getApiKey());
    }
}
