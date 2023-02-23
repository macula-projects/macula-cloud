package dev.macula.cloud.system.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.macula.cloud.system.annotation.AuditLog;
import dev.macula.cloud.system.form.TenantForm;
import dev.macula.cloud.system.form.UserForm;
import dev.macula.cloud.system.query.TenantPageQuery;
import dev.macula.cloud.system.service.SysTenantApplicationService;
import dev.macula.cloud.system.service.SysTenantDictService;
import dev.macula.cloud.system.service.SysTenantMenuService;
import dev.macula.cloud.system.service.SysTenantService;
import dev.macula.cloud.system.vo.tenant.TenantPageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "租户接口")
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class SysTenantController {

    private final SysTenantService sysTenantService;

    private final SysTenantMenuService sysTenantMenuService;

    private final SysTenantDictService sysTenantDictService;

    private final SysTenantApplicationService sysTenantApplicationService;


    @Operation(summary = "租户分页列表")
    @GetMapping
    public Page<TenantPageVO> listTenantpages(TenantPageQuery tenantPageQuery){
        Page<TenantPageVO> result = sysTenantService.listTenantpages(tenantPageQuery);
        return result;
    }


    @Operation(summary = "新增租户")
    @AuditLog(title = "新增租户")
    @PostMapping
    public boolean saveTenant(@RequestBody TenantForm tenantForm){
        return sysTenantService.saveTenant(tenantForm);
    }

    @Operation(summary = "修改租户")
    @AuditLog(title = "修改租户")
    @Parameter(name = "租户ID")
    @PutMapping(value = "/{id}")
    public boolean updateTenant(
            @PathVariable Long id,
            @RequestBody @Validated TenantForm tenantForm) {
        boolean result = sysTenantService.updateTenant(id, tenantForm);
        return result;
    }


    @Operation(summary = "删除租户")
    @AuditLog(title = "删除租户")
    @Parameter(name = "租户ID", description = "租户ID，多个以英文逗号(,)分割")
    @DeleteMapping("/{ids}")
    public boolean deleteTenants(@PathVariable String ids) {
        boolean result = sysTenantService.deleteTenants(ids);
        return result;
    }

    @Operation(summary = "获取租户菜单id列表")
    @GetMapping("/menu/{tenantId}")
    public List<Long> tenantMenus(@PathVariable("tenantId") Long tenantId){
        return sysTenantMenuService.tenantMenus(tenantId);
    }

    @Operation(summary = "更新租户菜单列表")
    @PutMapping("/menu/{tenantId}")
    public boolean updateTenantMenus(@PathVariable("tenantId") Long tenantId,
                                     @RequestParam(name="appCode") String appCode,
                                     @RequestBody List<Long> menuIds){
        return sysTenantMenuService.updateTenantMenus(tenantId, menuIds);
    }

    @Operation(summary = "获取租户应用id列表")
    @GetMapping("/application/{tenantId}")
    public List<Long> tenantApplications(@PathVariable("tenantId") Long tenantId){
        return sysTenantApplicationService.tenantApplications(tenantId);
    }

    @Operation(summary = "更新租户应用列表")
    @PutMapping("/application/{tenantId}")
    public boolean updateApplications(@PathVariable("tenantId") Long tenantId,
                                      @RequestParam(name="appCode") String appCode,
                                      @RequestBody List<Long> applicationIds){
        return sysTenantApplicationService.updateTenantApplications(tenantId, applicationIds);
    }

    @Operation(summary = "获取租户字典id列表")
    @GetMapping("/dict/{tenantId}")
    public List<Long> tenantDicts(@PathVariable("tenantId") Long tenantId){
        return sysTenantDictService.tenantDicts(tenantId);
    }

    @Operation(summary = "更新租户菜单列表")
    @PutMapping("/dict/{tenantId}")
    public boolean updateTenantDicts(@PathVariable("tenantId") Long tenantId,
                                     @RequestParam(name="appCode") String appCode,
                                     @RequestBody List<Long> dictIds){
        return sysTenantDictService.updateTenantDicts(tenantId, dictIds);
    }
}
