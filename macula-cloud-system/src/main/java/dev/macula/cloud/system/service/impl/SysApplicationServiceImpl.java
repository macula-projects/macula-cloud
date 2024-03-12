package dev.macula.cloud.system.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
        SysApplication sysApplication = applicationConverter.form2Entity(appForm);
        return this.save(sysApplication);
    }

    @Override
    public boolean updateApplication(Long appId, ApplicationForm appForm) {
        SysApplication sysApplication = applicationConverter.form2Entity(appForm);
        sysApplication.setId(appId);
        return this.updateById(sysApplication);
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
        tenantInfos.forEach(tenant -> {
            TenantContextHolder.setCurrentTenantId(tenant.getId());
            List<SysApplication> applications = this.list();
            applications.forEach(application -> {
                Map<String, String> apps = new HashMap<>();
                apps.put(CacheConstants.SECURITY_SYSTEM_APPS_SECRIT_KEY, application.getSk());
                apps.put(GlobalConstants.TENANT_ID_NAME, String.valueOf(tenant.getId()));
                apps.put(CacheConstants.SECURITY_SYSTEM_APPS_PERMIT_URLS, application.getAccessPath());
                String key = CacheConstants.SECURITY_SYSTEM_APPS + application.getCode();
                redisTemplate.delete(key);
                redisTemplate.opsForHash().putAll(key, apps);
            });
        });
        // 回退上下文默认租户id
        TenantContextHolder.setCurrentTenantId(contextTenantId);
    }

    @Override
    public boolean validatorAppCode(Long appId, String appCode) {
        List<SysTenantInfo> tenantInfos = tenantService.list();
        // 获取当前上下文默认的租户id
        Long contextTenantId = TenantContextHolder.getCurrentTenantId();
        boolean pass = true;
        if (tenantInfos != null && !tenantInfos.isEmpty()) {
            for (SysTenantInfo tenant : tenantInfos) {
                TenantContextHolder.setCurrentTenantId(tenant.getId());
                Wrapper<SysApplication> queryWrapper =
                    new LambdaQueryWrapper<SysApplication>().eq(SysApplication::getCode, appCode)
                        .and(appId != null, wrapper -> wrapper.ne(SysApplication::getId, appId));
                pass = getBaseMapper().selectCount(queryWrapper) == 0;
                if (!pass) {
                    break;
                }
            }
        }
        // 回退上下文默认租户id
        TenantContextHolder.setCurrentTenantId(contextTenantId);
        return pass;
    }
}
