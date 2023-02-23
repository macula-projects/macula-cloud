package dev.macula.cloud.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.macula.cloud.system.converter.SysApplicationTenantConverter;
import dev.macula.cloud.system.mapper.SysTenantApplicationMapper;
import dev.macula.cloud.system.mapper.SysTenantMenuMapper;
import dev.macula.cloud.system.pojo.entity.SysApplication;
import dev.macula.cloud.system.pojo.entity.SysApplicationTenant;
import dev.macula.cloud.system.pojo.entity.SysTenantApplication;
import dev.macula.cloud.system.pojo.entity.SysTenantMenu;
import dev.macula.cloud.system.service.SysApplicationService;
import dev.macula.cloud.system.service.SysApplicationTenantService;
import dev.macula.cloud.system.service.SysTenantApplicationService;
import dev.macula.cloud.system.service.SysTenantMenuService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class SysTenantApplicationServiceImpl extends ServiceImpl<SysTenantApplicationMapper, SysTenantApplication> implements SysTenantApplicationService {

    private final SysApplicationTenantConverter applicationTenantConverter;

    private final SysApplicationTenantService applicationTenantService;

    private final SysApplicationService applicationService;

    @Override
    public List<Long> tenantApplications(Long tenantId) {
        return list(new LambdaQueryWrapper<SysTenantApplication>().eq(SysTenantApplication::getTenantId, tenantId))
                .stream()
                .map(SysTenantApplication::getApplicationId)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public boolean updateTenantApplications(Long tenantId, List<Long> applicationIds) {
        remove(new LambdaQueryWrapper<SysTenantApplication>().eq(SysTenantApplication::getTenantId, tenantId));
        List<SysApplication> sysApplications = applicationService.list();
        Set<Long> systemApplicationIds = sysApplications.stream().map(SysApplication::getId).collect(Collectors.toSet());
        applicationTenantService.remove(new LambdaQueryWrapper<SysApplicationTenant>().in(SysApplicationTenant::getSystemApplicationId, systemApplicationIds));

        if(applicationIds.isEmpty()){
            return true;
        }
        Set<Long> addApplicationIds = new HashSet<>(applicationIds);
        List<SysTenantApplication> sysTenantApplications = applicationIds.stream().map(item->new SysTenantApplication(item, tenantId)).collect(Collectors.toList());
        saveBatch(sysTenantApplications);
        Set<SysApplicationTenant> sysApplicationTenants = sysApplications.stream().filter(item->addApplicationIds.contains(item.getId()))
                .map(item->{
                    SysApplicationTenant sysApplicationTenant =applicationTenantConverter.application2Tenant(item);
                    sysApplicationTenant.setId(null);
                    return sysApplicationTenant;
                }).collect(Collectors.toSet());
        return applicationTenantService.saveBatch(sysApplicationTenants);
    }
}
