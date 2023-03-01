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

package dev.macula.cloud.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.macula.boot.result.Option;
import dev.macula.cloud.system.annotation.AuditLog;
import dev.macula.cloud.system.form.PermissionValidtorForm;
import dev.macula.cloud.system.pojo.entity.SysPermission;
import dev.macula.cloud.system.query.PermPageQuery;
import dev.macula.cloud.system.service.SysPermissionService;
import dev.macula.cloud.system.service.SysRolePermissionService;
import dev.macula.cloud.system.vo.perm.PermPageVO;
import dev.macula.cloud.system.vo.perm.ResourcePermPageVO;
import feign.Param;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@Tag(name = "权限接口")
@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
public class SysPermissionController {

    private final SysPermissionService sysPermissionService;

    private final SysRolePermissionService sysRolePermissionService;

    @Operation(summary = "权限分页列表")
    @GetMapping("/page")
    public Page<PermPageVO> listPermPages(PermPageQuery permPageQuery) {
        Page<PermPageVO> result = sysPermissionService.listPermPages(permPageQuery);
        return result;
    }

    @Operation(summary = "权限列表")
    @Parameter(name = "菜单ID")
    @GetMapping
    public List<SysPermission> listPermissions(
            @RequestParam(required = false) Long menuId
    ) {
        List<SysPermission> list = sysPermissionService.list(
                new LambdaQueryWrapper<SysPermission>()
                        .eq(menuId != null, SysPermission::getMenuId, menuId)
        );
        return list;
    }

    @Operation(summary = "权限详情")
    @Parameter(name = "权限ID")
    @GetMapping("/{permissionId}")
    public SysPermission getPermissionDetail(
            @PathVariable Long permissionId
    ) {
        SysPermission permission = sysPermissionService.getById(permissionId);
        return permission;
    }

    @Operation(summary = "新增权限")
    @AuditLog(title = "新增权限")
    @PostMapping
    public boolean addPerm(
            @RequestBody SysPermission permission
    ) {
        boolean result = sysPermissionService.save(permission);
        return result;
    }

    @Operation(summary = "修改权限")
    @AuditLog(title = "修改权限")
    @Parameter(name = "权限ID")
    @PutMapping(value = "/{permissionId}")
    public boolean updatePerm(
            @PathVariable Long permissionId,
            @RequestBody SysPermission permission
    ) {
        boolean result = sysPermissionService.updateById(permission);
        if (result) {
            sysPermissionService.refreshPermRolesRules();
        }
        return result;
    }

    @Operation(summary = "删除权限")
    @AuditLog(title = "删除权限")
    @Parameter(name = "权限ID", description = "权限ID，多个以英文逗号(,)分割")
    @DeleteMapping("/{ids}")
    public boolean deletePermissions(
            @PathVariable String ids
    ) {
        boolean result = sysPermissionService.removeByIds(Arrays.asList(ids.split(",")));
        if (result) {
            sysPermissionService.refreshPermRolesRules();
        }
        return result;
    }

    @Operation(summary = "接口权限路径验证器")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "接口权限路径验证器， 返回下拉列表对象，label: code:url:method; value: true/false",
            content = {@Content(mediaType="application/json",
                    schema = @Schema(implementation = PermissionValidtorForm.class))}
    )
    @PostMapping("/validtor/urlPerm")
    public List<Option> validtorUrlPerm(@RequestBody List<PermissionValidtorForm> validtorForms){
        return sysPermissionService.validtorUrlPerm(validtorForms);
    }
}

