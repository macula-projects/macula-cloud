package dev.macula.cloud.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import dev.macula.cloud.system.pojo.entity.SysTenantApplication;
import dev.macula.cloud.system.pojo.entity.SysTenantMenu;

import java.util.List;

public interface SysTenantApplicationService extends IService<SysTenantApplication> {
    List<Long> tenantApplications(Long tenantId);

    boolean updateTenantApplications(Long tenantId, List<Long> ApplicationIds);

}
