package dev.macula.cloud.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import dev.macula.cloud.system.pojo.entity.SysTenantDict;

import java.util.List;

public interface SysTenantDictService extends IService<SysTenantDict> {
    List<Long> tenantDicts(Long tenantId);

    boolean updateTenantDicts(Long tenantId, List<Long> DictIds);

}
