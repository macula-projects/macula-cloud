/*
 * Copyright (c) 2023 Macula
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
import dev.macula.cloud.system.form.PermissionValidtorForm;
import dev.macula.cloud.system.pojo.entity.SysPermission;
import dev.macula.cloud.system.query.PermPageQuery;
import dev.macula.cloud.system.vo.perm.PermPageVO;

import java.util.List;

/**
 * 权限业务接口
 *
 * @author haoxr
 * @since 2022/1/22
 */
public interface SysPermissionService extends IService<SysPermission> {

    /**
     * 权限<->有权限的角色集合
     *
     * @return 权限集合
     */
    List<SysPermission> listPermRoles();

    /**
     * 获取权限分页列表
     *
     * @param permPageQuery 查询参数
     * @return 权限列表
     */
    Page<PermPageVO> listPermPages(PermPageQuery permPageQuery);

    /**
     * 刷新Redis缓存中角色菜单的权限规则，角色和菜单信息变更调用
     */
    void refreshPermRolesRules();

    /**
     * 接口权限路径验证器
     *
     * @param validatorForms 校验数据
     * @return Boolean
     */
    List<Option<Boolean>> validatorUrlPerm(List<PermissionValidtorForm> validatorForms);

    /**
     * 更新或保存相关菜单和路径权限信息
     * @param menuId 菜单ID
     * @param apiList 权限URL列表数据
     * @return 更新保存是否成功
     */
    boolean saveOrUpdatePerms(Long menuId, List<PermissionValidtorForm> apiList);

    /**
     * 根据ID删除URL权限
     *
     * @param ids URL权限ID
     * @return 删除是否成功
     */
    boolean deletePerms(String ids);
}
