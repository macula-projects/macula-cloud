package dev.macula.cloud.system.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.macula.cloud.system.form.TenantForm;
import dev.macula.cloud.system.pojo.bo.TenantBO;
import dev.macula.cloud.system.pojo.entity.SysTenantInfo;
import dev.macula.cloud.system.vo.tenant.TenantPageVO;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TenantConverter {

    @InheritInverseConfiguration(name = "entity2Form")
    SysTenantInfo form2Entity(TenantForm entity);

    Page<TenantPageVO> bo2Page(Page<TenantBO> page);
}
