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

package dev.macula.cloud.system.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.macula.boot.constants.SecurityConstants;
import dev.macula.boot.result.Option;
import dev.macula.boot.starter.security.utils.SecurityUtils;
import dev.macula.cloud.system.converter.RoleConverter;
import dev.macula.cloud.system.form.RoleForm;
import dev.macula.cloud.system.mapper.SysRoleMapper;
import dev.macula.cloud.system.pojo.entity.*;
import dev.macula.cloud.system.query.RolePageQuery;
import dev.macula.cloud.system.service.*;
import dev.macula.cloud.system.vo.role.RolePageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 角色业务实现类
 *
 * @author haoxr
 * @since 2022/6/3
 */
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private final SysRoleMenuService sysRoleMenuService;
    private final SysUserRoleService sysUserRoleService;

    private final SysRolePermissionService sysRolePermissionService;
    private final SysPermissionService sysPermissionService;
    private final RoleConverter roleConverter;

    /**
     * 角色分页列表
     *
     * @param queryParams
     * @return
     */
    @Override
    public Page<RolePageVO> listRolePages(RolePageQuery queryParams) {
        // 查询参数
        int pageNum = queryParams.getPageNum();
        int pageSize = queryParams.getPageSize();
        String keywords = queryParams.getKeywords();

        // 查询数据
        Page<SysRole> rolePage = this.page(new Page<>(pageNum, pageSize),
            new LambdaQueryWrapper<SysRole>().and(StrUtil.isNotBlank(keywords),
                    wrapper -> wrapper.like(StrUtil.isNotBlank(keywords), SysRole::getName, keywords).or()
                        .like(StrUtil.isNotBlank(keywords), SysRole::getCode, keywords))
                .ne(!SecurityUtils.isRoot(), SysRole::getCode, SecurityConstants.ROOT_ROLE_CODE) // 非超级管理员不显示超级管理员角色
        );

        // Page<SysRole> rolePage = this.baseMapper.listRolePages( new Page<>(pageNum, pageSize), queryParams,UserUtils.isRoot(),GlobalConstants.ROOT_ROLE_CODE);
        // 实体转换
        Page<RolePageVO> pageResult = roleConverter.entity2Page(rolePage);
        return pageResult;
    }

    /**
     * 角色下拉列表
     *
     * @return
     */
    @Override
    public List<Option> listRoleOptions() {
        // 查询数据
        List<SysRole> roleList = this.list(
            new LambdaQueryWrapper<SysRole>().ne(!SecurityUtils.isRoot(), SysRole::getCode,
                    SecurityConstants.ROOT_ROLE_CODE).select(SysRole::getId, SysRole::getName)
                .orderByAsc(SysRole::getSort));

        // List<SysRole> roleList = this.baseMapper.listDeptOptions(UserUtils.isRoot(),GlobalConstants.ROOT_ROLE_CODE);
        // 实体转换
        List<Option> list = roleConverter.roles2Options(roleList);
        return list;
    }

    /**
     * @param roleForm
     * @return
     */
    @Override
    public boolean saveRole(RoleForm roleForm) {

        Long roleId = roleForm.getId();
        String roleCode = roleForm.getCode();

        long count = this.count(new LambdaQueryWrapper<SysRole>().ne(roleId != null, SysRole::getId, roleId)
            .and(wrapper -> wrapper.eq(SysRole::getCode, roleCode).or().eq(SysRole::getName, roleCode)));
        Assert.isTrue(count == 0, "角色名称或角色编码重复，请检查！");

        // 实体转换
        SysRole role = roleConverter.form2Entity(roleForm);

        boolean result = this.saveOrUpdate(role);

        return result;
    }

    /**
     * 修改角色状态
     *
     * @param roleId
     * @param status
     * @return
     */
    @Override
    public boolean updateRoleStatus(Long roleId, Integer status) {
        boolean result =
            this.update(new LambdaUpdateWrapper<SysRole>().eq(SysRole::getId, roleId).set(SysRole::getStatus, status));
        return result;
    }

    /**
     * 批量删除角色
     *
     * @param ids
     * @return
     */
    @Override
    public boolean deleteRoles(String ids) {
        List<Long> roleIds =
            Arrays.asList(ids.split(",")).stream().map(id -> Long.parseLong(id)).collect(Collectors.toList());
        Optional.ofNullable(roleIds).orElse(new ArrayList<>()).forEach(id -> {
            long count = sysUserRoleService.count(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
            Assert.isTrue(count <= 0, "该角色已分配用户，无法删除");
            sysRoleMenuService.remove(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        });

        boolean result = this.removeByIds(roleIds);
        return result;
    }

    /**
     * 获取角色的资源ID集合,资源包括菜单和权限
     *
     * @param roleId
     * @return
     */
    @Override
    public List<Long> getRoleMenuIds(Long roleId) {
        // 获取角色拥有的菜单ID集合
        List<Long> menuIds = sysRoleMenuService.listMenuIdsByRoleId(roleId);
        return menuIds;
    }

    /**
     * 修改角色的资源权限
     *
     * @param roleId
     * @param menuIds
     * @return
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = "system", key = "'routes'")
    public boolean updateRoleMenus(Long roleId, List<Long> menuIds) {
        // 删除角色菜单
        sysRoleMenuService.remove(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        // 新增角色菜单关系
        if (CollectionUtil.isNotEmpty(menuIds)) {
            List<SysRoleMenu> roleMenus =
                menuIds.stream().map(menuId -> new SysRoleMenu(roleId, menuId)).collect(Collectors.toList());
            sysRoleMenuService.saveBatch(roleMenus);
        }

        // 删除角色权限
        sysRolePermissionService.remove(
            new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, roleId));
        // 新增角色权限关系（用勾选的菜单ID获取权限，用权限ID和角色ID组装sys_role_permission表)
        List<SysPermission> permList = sysPermissionService.list(
            new LambdaQueryWrapper<SysPermission>().in(menuIds != null, SysPermission::getMenuId, menuIds));
        if (CollectionUtil.isNotEmpty(permList)) {
            List<SysRolePermission> rolePerms =
                permList.stream().map(perm -> new SysRolePermission(roleId, perm.getId())).collect(Collectors.toList());
            sysRolePermissionService.saveBatch(rolePerms);
        }

        return true;
    }

    /**
     * 获取最大范围的数据权限
     *
     * @param roles
     * @return
     */
    @Override
    public Integer getMaximumDataScope(Set<String> roles) {
        Integer dataScope = this.baseMapper.getMaximumDataScope(roles);
        return dataScope;
    }

}
