package dev.macula.cloud.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import dev.macula.cloud.system.pojo.entity.SysTenantDict;

import java.util.List;
import java.util.Map;

public interface SysTenantDictService extends IService<SysTenantDict> {
    List<Long> tenantDicts(Long tenantId, Integer type);

    boolean updateTenantDicts(Long tenantId, Map<String, List<Long>> dictIdsMap);

}
