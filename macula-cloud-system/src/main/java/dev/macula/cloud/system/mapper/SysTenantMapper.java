package dev.macula.cloud.system.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.macula.cloud.system.pojo.bo.UserBO;
import dev.macula.cloud.system.pojo.entity.SysTenant;
import dev.macula.cloud.system.query.TenantPageQuery;
import dev.macula.cloud.system.vo.tenant.TenantPageVO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysTenantMapper extends BaseMapper<SysTenant> {

    /**
     * 租户分页列表
     * @param page
     * @param queryParams
     * @return
     */
    Page<TenantPageVO> listTenantpages(Page<TenantPageVO> page, TenantPageQuery queryParams);
}
