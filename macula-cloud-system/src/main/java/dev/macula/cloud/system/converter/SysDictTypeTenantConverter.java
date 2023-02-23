package dev.macula.cloud.system.converter;

import dev.macula.cloud.system.pojo.entity.SysDictType;
import dev.macula.cloud.system.pojo.entity.SysDictTypeTenant;
import dev.macula.cloud.system.pojo.entity.SysMenu;
import dev.macula.cloud.system.pojo.entity.SysMenuTenant;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SysDictTypeTenantConverter {
    @Mapping(source = "id", target = "systemDictTypeId")
    @InheritInverseConfiguration(name = "tenant2DictType")
    SysDictTypeTenant dictType2Tenant(SysDictType sysDictType);
}
