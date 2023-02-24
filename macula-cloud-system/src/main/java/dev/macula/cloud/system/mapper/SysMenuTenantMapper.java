package dev.macula.cloud.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.macula.cloud.system.pojo.entity.SysMenuTenant;
import org.apache.ibatis.annotations.Mapper;

import java.util.Set;

@Mapper
public interface SysMenuTenantMapper extends BaseMapper<SysMenuTenant> {
    Set<Long> listShowMenuParentIdByName(String keywords);
}
