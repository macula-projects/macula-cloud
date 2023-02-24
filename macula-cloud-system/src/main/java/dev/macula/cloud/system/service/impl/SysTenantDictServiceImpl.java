package dev.macula.cloud.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.macula.cloud.system.converter.SysDictItemTenantConverter;
import dev.macula.cloud.system.converter.SysDictTypeTenantConverter;
import dev.macula.cloud.system.mapper.SysTenantDictMapper;
import dev.macula.cloud.system.pojo.entity.*;
import dev.macula.cloud.system.service.*;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@AllArgsConstructor
public class SysTenantDictServiceImpl extends ServiceImpl<SysTenantDictMapper, SysTenantDict> implements SysTenantDictService {
    private final SysDictTypeService sysDictTypeService;

    private final SysDictItemService sysDictItemService;

    private final SysDictItemTenantService dictItemTenantService;

    private final SysDictTypeTenantService dictTypeTenantService;

    private final SysDictTypeTenantConverter dictTypeTenantConverter;

    private final SysDictItemTenantConverter dictItemTenantConverter;

    @Override
    public List<Long> tenantDicts(Long tenantId, Integer type) {
        if(!SysTenantDict.DICT_ITEM_TYPE_INT.equals(type) && !SysTenantDict.DICT_TYPE_TYPE_INT.equals(type)){
            throw new RuntimeException("type类型错误只允许0：字典类型；1：字典类型项");
        }
        return list(new LambdaQueryWrapper<SysTenantDict>().eq(SysTenantDict::getTenantId, tenantId).eq(SysTenantDict::getType, type))
                .stream()
                .map(SysTenantDict::getDictItemId).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public boolean updateTenantDicts(Long tenantId, Map<String, List<Long>> dictIdsMap) {
        remove(new LambdaQueryWrapper<SysTenantDict>().eq(SysTenantDict::getTenantId, tenantId));
        List<Long> dictTypeIds = dictIdsMap.get("dictTypeIds");
        List<Long> dictItemIds = dictIdsMap.get("dictItemIds");
        List<SysDictType> sysDictTypes = sysDictTypeService.list();
        Set<Long> sysDictTypeIds = sysDictTypes.stream().map(SysDictType::getId).collect(Collectors.toSet());
        dictTypeTenantService.remove(new LambdaQueryWrapper<SysDictTypeTenant>().in(SysDictTypeTenant::getSystemDictTypeId, sysDictTypeIds));
        List<SysDictItem> sysDictItems = sysDictItemService.list();
        Set<Long> sysDictItemIds = sysDictItems.stream().map(SysDictItem::getId).collect(Collectors.toSet());
        dictItemTenantService.remove(new LambdaQueryWrapper<SysDictItemTenant>().in(SysDictItemTenant::getSystemDictItemId, sysDictItemIds));

        if(dictTypeIds.isEmpty() && dictItemIds.isEmpty()){
            return true;
        }
        Set<Long> addDictTypeIds = new HashSet<>(dictTypeIds);
        Set<Long> addDictItemIds = new HashSet<>(dictItemIds);
        List<SysTenantDict> SysTenantDicts = dictTypeIds.stream()
                .map(item->new SysTenantDict(item, SysTenantDict.DICT_TYPE_TYPE_INT, tenantId))
                .collect(Collectors.toList());
        SysTenantDicts.addAll(dictItemIds.stream()
                .map(item->new SysTenantDict(item, SysTenantDict.DICT_ITEM_TYPE_INT, tenantId))
                .collect(Collectors.toList()));
        saveBatch(SysTenantDicts);
        Set<SysDictTypeTenant> sysDictTypeTenants = sysDictTypes.stream().filter(item->addDictTypeIds.contains(item.getId()))
                .map(item->{
                    SysDictTypeTenant sysDictTypeTenant =dictTypeTenantConverter.dictType2Tenant(item);
                    sysDictTypeTenant.setId(null);
                    return sysDictTypeTenant;
                }).collect(Collectors.toSet());
        dictTypeTenantService.saveBatch(sysDictTypeTenants);
        Set<SysDictItemTenant> sysDictItemTenants = sysDictItems.stream().filter(item->addDictItemIds.contains(item.getId()))
                .map(item->{
                    SysDictItemTenant sysDictItemTenant =dictItemTenantConverter.dictItem2Tenant(item);
                    sysDictItemTenant.setId(null);
                    return sysDictItemTenant;
                }).collect(Collectors.toSet());
        return dictItemTenantService.saveBatch(sysDictItemTenants);
    }
}
