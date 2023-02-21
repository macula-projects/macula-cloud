package dev.macula.cloud.system.converter;


import dev.macula.cloud.system.form.TenantForm;
import dev.macula.cloud.system.pojo.entity.SysTenantInfo;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TenantConverter {

    @InheritInverseConfiguration(name = "entity2Form")
    SysTenantInfo form2Entity(TenantForm entity);
}
