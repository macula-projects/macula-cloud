/*
 * Copyright (c) 2022 Macula
 *   macula.dev, China
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


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import dev.macula.boot.result.Option;
import dev.macula.cloud.system.form.RoleForm;
import dev.macula.cloud.system.pojo.entity.SysRole;
import dev.macula.cloud.system.query.RolePageQuery;
import dev.macula.cloud.system.vo.role.RolePageVO;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 角色业务接口层
 *
 * @author haoxr
 * @since 2022/6/3
 */
public interface SysRoleService extends IService<SysRole> {

    /**
     * 角色分页列表
     *
     * @param queryParams
     * @return
     */
    Page<RolePageVO> listRolePages(RolePageQuery queryParams);


    /**
     * 角色下拉列表
     *
     * @return
     */
    List<Option> listRoleOptions();

    /**
     * @param roleForm
     * @return
     */
    boolean saveRole(RoleForm roleForm);

    /**
     * 修改角色状态
     *
     * @param roleId
     * @param status
     * @return
     */
    boolean updateRoleStatus(Long roleId, Integer status);

    /**
     * 批量删除角色
     *
     * @param ids
     * @return
     */
    boolean deleteRoles(String ids);


    /**
     * 获取角色的资源ID集合,资源包括菜单和权限
     *
     * @param roleId
     * @return
     */
    List<Long> getRoleMenuIds(Long roleId);


    /**
     * 修改角色的资源权限
     *
     * @param roleId
     * @param menuIds
     * @return
     */
    boolean updateRoleMenus(Long roleId, Map<String,List<Long>> menuIds);

    /**
     * 获取最大范围的数据权限
     *
     * @param roles
     * @return
     */
    Integer getMaximumDataScope(Set<String> roles);

    /**
     * 验证角色是否可配置该角色code
     *
     * @param id
     * @param code
     * @return
     */
    boolean validtorForCode(Long id, String code);

    /**
     * 验证角色是否可配置该角色名称
     * @param id
     * @param name
     * @return
     */
    boolean validtorForName(Long id, String name);

    /**
     * 获取数据权限的下拉列表
     *
     * @return
     */
    List<Option> optionsByDataScope();

}
