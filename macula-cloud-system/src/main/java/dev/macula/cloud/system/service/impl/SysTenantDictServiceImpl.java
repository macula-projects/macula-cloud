package dev.macula.cloud.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.macula.cloud.system.mapper.SysTenantDictMapper;
import dev.macula.cloud.system.mapper.SysTenantMenuMapper;
import dev.macula.cloud.system.pojo.entity.SysTenantDict;
import dev.macula.cloud.system.pojo.entity.SysTenantMenu;
import dev.macula.cloud.system.service.SysTenantDictService;
import dev.macula.cloud.system.service.SysTenantMenuService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SysTenantDictServiceImpl extends ServiceImpl<SysTenantDictMapper, SysTenantDict> implements SysTenantDictService {
    @Override
    public List<Long> tenantDicts(Long tenantId) {
        return null;
    }

    @Override
    public boolean updateTenantDicts(Long tenantId, List<Long> DictIds) {
        return false;
    }
}
