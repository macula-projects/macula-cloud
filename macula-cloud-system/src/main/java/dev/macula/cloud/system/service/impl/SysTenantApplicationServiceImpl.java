package dev.macula.cloud.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.macula.cloud.system.mapper.SysTenantApplicationMapper;
import dev.macula.cloud.system.mapper.SysTenantMenuMapper;
import dev.macula.cloud.system.pojo.entity.SysTenantApplication;
import dev.macula.cloud.system.pojo.entity.SysTenantMenu;
import dev.macula.cloud.system.service.SysTenantApplicationService;
import dev.macula.cloud.system.service.SysTenantMenuService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SysTenantApplicationServiceImpl extends ServiceImpl<SysTenantApplicationMapper, SysTenantApplication> implements SysTenantApplicationService {

    @Override
    public List<Long> tenantApplications(Long tenantId) {
        return null;
    }

    @Override
    public boolean updateTenantApplications(Long tenantId, List<Long> ApplicationIds) {
        return false;
    }
}
