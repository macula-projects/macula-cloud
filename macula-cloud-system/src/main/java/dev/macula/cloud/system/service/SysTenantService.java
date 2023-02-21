package dev.macula.cloud.system.service;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import dev.macula.cloud.system.form.TenantForm;
import dev.macula.cloud.system.pojo.entity.SysTenant;
import dev.macula.cloud.system.query.TenantPageQuery;
import dev.macula.cloud.system.vo.tenant.TenantPageVO;

public interface SysTenantService extends IService<SysTenant> {

    /**
     *  租户分页列表
     * @param tenantPageQuery
     * @return
     */
    Page<TenantPageVO> listTenantpages(TenantPageQuery tenantPageQuery);

    /**
     *  新增租户
     * @param tenantForm
     * @return
     */
    boolean saveTenant(TenantForm tenantForm);

    /**
     *  更新租户
     * @param id
     * @param tenantForm
     * @return
     */
    boolean updateTenant(Long id, TenantForm tenantForm);

    /**
     *  删除用户
     * @param ids
     * @return
     */
    boolean deleteTenants(String ids);
}
