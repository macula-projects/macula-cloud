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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.macula.boot.result.Option;
import dev.macula.boot.starter.auditlog.annotation.AuditLog;
import dev.macula.cloud.system.form.PermissionValidtorForm;
import dev.macula.cloud.system.form.PermissionForm;
import dev.macula.cloud.system.converter.PermissionConverter;
import dev.macula.cloud.system.vo.perm.PermissionVO;
import dev.macula.cloud.system.pojo.entity.SysPermission;
import dev.macula.cloud.system.query.PermPageQuery;
import dev.macula.cloud.system.service.SysPermissionService;
import dev.macula.cloud.system.vo.perm.PermPageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 权限接口协议适配，使用独立表单和VO隔离数据库实体。
 * @author Rain
 * @since 6.1.0
 */
@Tag(name = "权限接口")
@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
public class SysPermissionController {

    private final SysPermissionService sysPermissionService;
    private final PermissionConverter permissionConverter;

    @Operation(summary = "权限分页列表")
    @GetMapping("/page")
    public Page<PermPageVO> listPermPages(PermPageQuery permPageQuery) {
        return sysPermissionService.listPermPages(permPageQuery);
    }

    /** 读取权限列表并转换审计日期时间。 */
    @Operation(summary = "权限列表")
    @Parameter(name = "菜单ID")
    @GetMapping
    public List<PermissionVO> listPermissions(@RequestParam(required = false) Long menuId) {
        return permissionConverter.entities2VOs(sysPermissionService.list(
            new LambdaQueryWrapper<SysPermission>().eq(menuId != null, SysPermission::getMenuId, menuId)));
    }

    /** 读取权限详情，不直接序列化数据库实体。 */
    @Operation(summary = "权限详情")
    @Parameter(name = "权限ID")
    @GetMapping("/{permissionId}")
    public PermissionVO getPermissionDetail(@PathVariable Long permissionId) {
        return permissionConverter.entity2VO(sysPermissionService.getById(permissionId));
    }

    /** 通过权限业务表单创建记录，忽略客户端审计字段。 */
    @Operation(summary = "新增权限")
    @AuditLog(title = "新增权限")
    @PostMapping
    public boolean addPerm(@RequestBody PermissionForm form) {
        return sysPermissionService.save(permissionConverter.form2Entity(form));
    }

    /** 按路径ID更新权限业务字段，审计字段由服务端维护。 */
    @Operation(summary = "修改权限")
    @AuditLog(title = "修改权限")
    @Parameter(name = "权限ID")
    @PutMapping(value = "/{permissionId}")
    public boolean updatePerm(@PathVariable Long permissionId, @RequestBody PermissionForm form) {
        SysPermission permission = permissionConverter.form2Entity(form);
        // 路径决定更新目标，客户端不能通过表单ID改变本次操作对象。
        permission.setId(permissionId);
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
    public boolean deletePermissions(@PathVariable String ids) {
        boolean result = sysPermissionService.deletePerms(ids);
        if (result) {
            sysPermissionService.refreshPermRolesRules();
        }
        return result;
    }

    @Operation(summary = "接口权限路径验证器")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "接口权限路径验证器， 返回下拉列表对象，label: code:url:method; value: true/false", content = {
        @Content(mediaType = "application/json", schema = @Schema(implementation = PermissionValidtorForm.class))})
    @PostMapping("/validtor/urlPerm")
    public List<Option<Boolean>> validatorUrlPerm(@RequestBody List<PermissionValidtorForm> validatorForms) {
        return sysPermissionService.validatorUrlPerm(validatorForms);
    }
}

