package dev.macula.cloud.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.macula.cloud.system.pojo.entity.SysTenantApplication;
import dev.macula.cloud.system.pojo.entity.SysTenantDict;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysTenantDictMapper extends BaseMapper<SysTenantDict> {

}
