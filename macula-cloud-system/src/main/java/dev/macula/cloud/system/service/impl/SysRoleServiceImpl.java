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

package dev.macula.cloud.system.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.macula.boot.constants.SecurityConstants;
import dev.macula.boot.enums.DataScopeEnum;
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
        return roleConverter.entity2Page(rolePage);
    }

    @Override
    public List<Option<Long>> listRoleOptions() {
        // 开启忽略策略
        InterceptorIgnoreHelper.handle(IgnoreStrategy.builder().tenantLine(true).build());

        // 查询数据
        List<SysRole> roleList = this.list(
            new LambdaQueryWrapper<SysRole>().ne(!SecurityUtils.isRoot(), SysRole::getCode,
                SecurityConstants.ROOT_ROLE_CODE).orderByAsc(SysRole::getSort));

        // 关闭忽略策略
        InterceptorIgnoreHelper.clearIgnoreStrategy();

        // 实体转换
        return roleConverter.roles2Options(roleList);
    }

    @Override
    @Transactional
    public boolean saveRole(RoleForm roleForm) {

        Long roleId = roleForm.getId();
        String roleCode = roleForm.getCode();

        long count = this.count(new LambdaQueryWrapper<SysRole>().ne(roleId != null, SysRole::getId, roleId)
            .and(wrapper -> wrapper.eq(SysRole::getCode, roleCode).or().eq(SysRole::getName, roleCode)));
        Assert.isTrue(count == 0, "角色名称或角色编码重复，请检查！");

        // 实体转换
        SysRole role = roleConverter.form2Entity(roleForm);

        return this.saveOrUpdate(role);
    }

    @Override
    public boolean updateRoleStatus(Long roleId, Integer status) {
        return this.update(
            new LambdaUpdateWrapper<SysRole>().eq(SysRole::getId, roleId).set(SysRole::getStatus, status));
    }

    @Override
    @Transactional
    public boolean deleteRoles(String ids) {
        List<Long> roleIds = Arrays.stream(ids.split(",")).map(Long::parseLong).collect(Collectors.toList());
        Optional.of(roleIds).orElse(new ArrayList<>()).forEach(id -> {
            long count = sysUserRoleService.count(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
            Assert.isTrue(count <= 0, "该角色已分配用户，无法删除");
            // 删除角色与菜单关系
            sysRoleMenuService.remove(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
            // 删除角色与权限关系
            sysRolePermissionService.remove(
                new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, id));
        });

        return this.removeByIds(roleIds);
    }

    @Override
    public List<Long> getRoleMenuIds(Long roleId) {
        // 获取角色拥有的菜单ID集合
        return sysRoleMenuService.listMenuIdsByRoleId(roleId);
    }

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
        if (CollectionUtil.isNotEmpty(menuIds)) {
            List<SysPermission> permList = sysPermissionService.list(
                new LambdaQueryWrapper<SysPermission>().in(menuIds != null, SysPermission::getMenuId, menuIds));
            if (CollectionUtil.isNotEmpty(permList)) {
                List<SysRolePermission> rolePerms =
                    permList.stream().map(perm -> new SysRolePermission(roleId, perm.getId()))
                        .collect(Collectors.toList());
                sysRolePermissionService.saveBatch(rolePerms);
            }
        }

        return true;
    }

    @Override
    public Integer getMaximumDataScope(Set<String> roles) {
        return this.baseMapper.getMaximumDataScope(roles);
    }

    @Override
    public Set<String> listRolesByGroupIds(Long tenantId, Set<Long> groupIds) {
        if (CollectionUtil.isNotEmpty(groupIds)) {
            return this.baseMapper.listRolesByGroupIds(tenantId, groupIds);
        }
        return new HashSet<>();
    }

    @Override
    public boolean validatorForCode(Long id, String code) {
        long count = this.count(new LambdaQueryWrapper<SysRole>().ne(id != null, SysRole::getId, id)
            .and(wrapper -> wrapper.eq(SysRole::getCode, code)));
        return count == 0;
    }

    @Override
    public boolean validatorForName(Long id, String name) {
        long count = this.count(new LambdaQueryWrapper<SysRole>().ne(id != null, SysRole::getId, id)
            .and(wrapper -> wrapper.eq(SysRole::getName, name)));
        return count == 0;
    }

    @Override
    public List<Option<DataScopeEnum>> optionsByDataScope() {
        return Arrays.stream(DataScopeEnum.values())
            .map(roleDataScopeEnum -> new Option<>(roleDataScopeEnum, roleDataScopeEnum.getLabel()))
            .collect(Collectors.toList());
    }
}
