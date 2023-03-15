package dev.macula.cloud.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.macula.cloud.system.pojo.bo.TenantBO;
import dev.macula.cloud.system.pojo.entity.SysTenantInfo;
import dev.macula.cloud.system.query.TenantPageQuery;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysTenantInfoMapper extends BaseMapper<SysTenantInfo> {

    /**
     * 租户分页列表
     *
     * @param page
     * @param queryParams
     * @return
     */
    Page<TenantBO> listTenantpages(Page<TenantBO> page, TenantPageQuery queryParams);
}
