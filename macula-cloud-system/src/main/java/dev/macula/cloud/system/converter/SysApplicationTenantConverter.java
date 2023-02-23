package dev.macula.cloud.system.converter;

import dev.macula.cloud.system.pojo.entity.SysApplication;
import dev.macula.cloud.system.pojo.entity.SysApplicationTenant;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SysApplicationTenantConverter {
    @Mapping(source = "id", target = "systemApplicationId")
    @InheritInverseConfiguration(name = "tenant2Application")
    SysApplicationTenant application2Tenant(SysApplication sysApplication);
}
