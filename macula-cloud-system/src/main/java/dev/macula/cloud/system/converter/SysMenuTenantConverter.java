package dev.macula.cloud.system.converter;

import dev.macula.cloud.system.pojo.entity.SysMenu;
import dev.macula.cloud.system.pojo.entity.SysMenuTenant;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SysMenuTenantConverter {
    @Mapping(source = "id", target = "systemMenuId")
    @InheritInverseConfiguration(name = "tenant2Menu")
    SysMenuTenant menu2Tenant(SysMenu sysMenu);
}
