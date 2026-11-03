/*
 * Copyright (c) 2026 Macula
 * macula.dev, China
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.macula.cloud.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import dev.macula.boot.result.Option;
import dev.macula.cloud.system.form.TenantForm;
import dev.macula.cloud.system.pojo.entity.SysTenantInfo;
import dev.macula.cloud.system.query.TenantPageQuery;
import dev.macula.cloud.system.vo.tenant.TenantPageVO;

import java.util.List;

/**
 * 租户管理及网关归属租户映射缓存维护契约。
 *
 * @author Rain
 * @since 6.1.0
 */
public interface SysTenantService extends IService<SysTenantInfo> {

    /**
     * 根据当前租户记录重建租户编码到 ID 的缓存映射。
     */
    void refreshTenantCache();

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
