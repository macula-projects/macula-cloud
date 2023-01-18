package dev.macula.cloud.system.converter;

import dev.macula.cloud.system.dto.PermDTO;
import dev.macula.cloud.system.pojo.entity.SysPermission;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

/**
 * 权限对象转换器
 *
 * @author qiuyuhao
 * @date 2023.01.17
 */
@Mapper(componentModel = "spring")
public interface PermissionConverter {

  @Mappings({
    @Mapping(source = "code", target = "name"),
    @Mapping(source = "url", target = "urlPerm"),
    @Mapping(source = "id", target = "id")
  })
  SysPermission permDTO2Entity(PermDTO permDTO);
}
