package dev.macula.cloud.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import dev.macula.boot.result.Option;
import dev.macula.cloud.system.form.TenantForm;
import dev.macula.cloud.system.pojo.entity.SysTenantInfo;
import dev.macula.cloud.system.query.TenantPageQuery;
import dev.macula.cloud.system.vo.tenant.TenantPageVO;

import java.util.List;

public interface SysTenantService extends IService<SysTenantInfo> {

    /**
     * 租户分页列表
     *
     * @param tenantPageQuery 查询参数
     * @return 租户列表
     */
    IPage<TenantPageVO> listTenantPages(TenantPageQuery tenantPageQuery);

    /**
     * 新增租户
     *
     * @param tenantForm 租户表单
     * @return 新增是否成功
     */
    boolean saveTenant(TenantForm tenantForm);

    /**
     * 更新租户
     *
     * @param id 租户ID
     * @param tenantForm 租户表单
     * @return 更新是否成功
     */
    boolean updateTenant(Long id, TenantForm tenantForm);

    /**
     * 删除用户
     *
     * @param ids 租户IDS
     * @return boolean
     */
    boolean deleteTenants(String ids);

    /**
     * 根据filterMe属性获取租户下拉列表
     *
     * @param filterMe true: 获取我的租户下拉选项; false: 获取所有租户下拉选项
     * @return Option List
     */
    List<Option<Long>> listTenantOptions(boolean filterMe);

}
