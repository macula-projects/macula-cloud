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

package dev.macula.cloud.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import dev.macula.boot.result.Option;
import dev.macula.cloud.system.form.RoleForm;
import dev.macula.cloud.system.pojo.entity.SysRole;
import dev.macula.cloud.system.query.RolePageQuery;
import dev.macula.cloud.system.service.SysPermissionService;
import dev.macula.cloud.system.service.SysRoleService;
import dev.macula.cloud.system.vo.role.RolePageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Tag(name = "角色接口")
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class SysRoleController {
    private final SysRoleService roleService;

    private final SysPermissionService sysPermissionService;

    @Operation(summary = "角色分页列表")
    @GetMapping("/pages")
    public IPage<RolePageVO> listRolePages(RolePageQuery queryParams) {
        IPage<RolePageVO> result = roleService.listRolePages(queryParams);
        return result;
    }

    @Operation(summary = "角色下拉列表")
    @GetMapping("/options")
    public List<Option> listRoleOptions() {
        List<Option> list = roleService.listRoleOptions();
        return list;
    }

    @Operation(summary = "角色详情")
    @Parameter(name = "角色ID")
    @GetMapping("/{roleId}")
    public SysRole getRoleDetail(@PathVariable Long roleId) {
        SysRole role = roleService.getById(roleId);
        return role;
    }

    @Operation(summary = "新增角色")
    @PostMapping
    public boolean addRole(@Valid @RequestBody RoleForm roleForm) {
        boolean result = roleService.saveRole(roleForm);
        return result;
    }

    @Operation(summary = "修改角色")
    @PutMapping(value = "/{id}")
    public boolean updateRole(@Valid @RequestBody RoleForm roleForm) {
        boolean result = roleService.saveRole(roleForm);
        if (result) {
            sysPermissionService.refreshPermRolesRules();
        }
        return result;
    }

    @Operation(summary = "删除角色")
    @Parameter(description = "删除角色，多个以英文逗号(,)分割")
    @DeleteMapping("/{ids}")
    public boolean deleteRoles(@PathVariable String ids) {
        boolean result = roleService.deleteRoles(ids);
        return result;
    }

    @Operation(summary = "修改角色状态")
    @Parameter(name = "角色ID")
    @Parameter(name = "角色状态", description = "角色状态:1-启用；0-禁用")
    @PutMapping(value = "/{roleId}/status")
    public boolean updateRoleStatus(@PathVariable Long roleId, @RequestParam Integer status) {
        boolean result = roleService.updateRoleStatus(roleId, status);
        return result;
    }

    @Operation(summary = "获取角色的菜单ID集合")
    @Parameter(name = "角色ID")
    @GetMapping("/{roleId}/menuIds")
    public List<Long> getRoleMenuIds(@PathVariable Long roleId) {
        List<Long> resourceIds = roleService.getRoleMenuIds(roleId);
        return resourceIds;
    }

    @Operation(summary = "分配角色的资源权限")
    @PutMapping("/{roleId}/menus")
    public boolean updateRoleMenus(@PathVariable Long roleId, @RequestBody List<Long> menuIds) {
        boolean result = roleService.updateRoleMenus(roleId, menuIds);
        if (result) {
            sysPermissionService.refreshPermRolesRules();
        }
        return result;
    }
}
