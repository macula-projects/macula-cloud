package dev.macula.cloud.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import dev.macula.cloud.system.pojo.entity.SysTenantMenu;

import java.util.List;

public interface SysTenantMenuService extends IService<SysTenantMenu> {
    List<Long> tenantMenus(Long tenantId);

    boolean updateTenantMenus(Long tenantId, List<Long> menuIds);

}
