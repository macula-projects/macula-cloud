package dev.macula.cloud.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.macula.cloud.system.pojo.entity.SysUserTenant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户租户持久层
 *
 */
@Mapper
public interface SysUserTenantMapper  extends BaseMapper<SysUserTenant> {
}
