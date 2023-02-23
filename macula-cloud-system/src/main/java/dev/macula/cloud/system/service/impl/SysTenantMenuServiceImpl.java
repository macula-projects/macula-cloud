package dev.macula.cloud.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.macula.cloud.system.converter.SysMenuTenantConverter;
import dev.macula.cloud.system.mapper.SysTenantMenuMapper;
import dev.macula.cloud.system.pojo.entity.SysMenu;
import dev.macula.cloud.system.pojo.entity.SysMenuTenant;
import dev.macula.cloud.system.pojo.entity.SysTenantMenu;
import dev.macula.cloud.system.service.SysMenuService;
import dev.macula.cloud.system.service.SysMenuTenantService;
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
public class SysTenantMenuServiceImpl extends ServiceImpl<SysTenantMenuMapper, SysTenantMenu> implements SysTenantMenuService {

    private final SysMenuTenantService sysMenuTenantService;

    private final SysMenuService sysMenuService;

    private final SysMenuTenantConverter menuTenantConverter;

    @Override
    public List<Long> tenantMenus(Long tenantId) {
        return list(new LambdaQueryWrapper<SysTenantMenu>().eq(SysTenantMenu::getTenantId, tenantId)).stream().map(SysTenantMenu::getMenuId).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public boolean updateTenantMenus(Long tenantId, List<Long> menuIds) {
        remove(new LambdaQueryWrapper<SysTenantMenu>().eq(SysTenantMenu::getTenantId, tenantId));
        List<SysMenu> sysMenus = sysMenuService.list();
        Set<Long> systemMenuIds = sysMenus.stream().map(SysMenu::getId).collect(Collectors.toSet());
        sysMenuTenantService.remove(new LambdaQueryWrapper<SysMenuTenant>().in(SysMenuTenant::getSystemMenuId,systemMenuIds));

        if(menuIds.isEmpty()){
            return true;
        }
        Set<Long> addMenuIds = new HashSet<>(menuIds);
        List<SysTenantMenu> sysTenantMenus = menuIds.stream().map(item->new SysTenantMenu(item, tenantId)).collect(Collectors.toList());
        saveBatch(sysTenantMenus);
        Set<SysMenuTenant> sysMenuTenants = sysMenus.stream().filter(item->addMenuIds.contains(item.getId()))
                .map(item->{
                    SysMenuTenant sysMenuTenant =menuTenantConverter.menu2Tenant(item);
                    sysMenuTenant.setId(null);
                    return sysMenuTenant;
                }).collect(Collectors.toSet());
        return sysMenuTenantService.saveBatch(sysMenuTenants);
    }


}
