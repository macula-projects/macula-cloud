package dev.macula.cloud.system.converter;

import dev.macula.cloud.system.pojo.entity.SysDictItem;
import dev.macula.cloud.system.pojo.entity.SysDictItemTenant;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SysDictItemTenantConverter {
    @Mapping(source = "id", target = "systemDictItemId")
    @InheritInverseConfiguration(name = "tenant2DictItem")
    SysDictItemTenant dictItem2Tenant(SysDictItem sysDictItem);
}
