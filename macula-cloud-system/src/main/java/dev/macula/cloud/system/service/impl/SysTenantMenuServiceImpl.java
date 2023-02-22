package dev.macula.cloud.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.macula.cloud.system.mapper.SysTenantMenuMapper;
import dev.macula.cloud.system.pojo.entity.SysTenantMenu;
import dev.macula.cloud.system.service.SysTenantMenuService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SysTenantMenuServiceImpl extends ServiceImpl<SysTenantMenuMapper, SysTenantMenu> implements SysTenantMenuService {

    @Override
    public List<Long> tenantMenus(Long tenantId) {
        return list(new LambdaQueryWrapper<SysTenantMenu>().eq(SysTenantMenu::getTenantId, tenantId)).stream().map(SysTenantMenu::getMenuId).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public boolean updateTenantMenus(Long tenantId, List<Long> menuIds) {
        remove(new LambdaQueryWrapper<SysTenantMenu>().eq(SysTenantMenu::getTenantId, tenantId));

        if(menuIds.isEmpty()){
            return true;
        }
        List<SysTenantMenu> sysTenantMenus = menuIds.stream().map(item->new SysTenantMenu(item, tenantId)).collect(Collectors.toList());

        return saveBatch(sysTenantMenus);
    }


}
