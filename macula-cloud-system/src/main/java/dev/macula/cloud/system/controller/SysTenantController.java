package dev.macula.cloud.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import dev.macula.boot.result.Option;
import dev.macula.boot.starter.auditlog.annotation.AuditLog;
import dev.macula.cloud.system.form.TenantForm;
import dev.macula.cloud.system.query.TenantPageQuery;
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

    @Operation(summary = "租户分页列表")
    @GetMapping
    public IPage<TenantPageVO> listTenantPages(TenantPageQuery tenantPageQuery) {
        IPage<TenantPageVO> result = sysTenantService.listTenantpages(tenantPageQuery);
        return result;
    }

    @Operation(summary = "新增租户")
    @AuditLog(title = "新增租户")
    @PostMapping
    public boolean saveTenant(@RequestBody TenantForm tenantForm) {
        return sysTenantService.saveTenant(tenantForm);
    }

    @Operation(summary = "修改租户")
    @AuditLog(title = "修改租户")
    @Parameter(name = "租户ID")
    @PutMapping(value = "/{id}")
    public boolean updateTenant(@PathVariable Long id, @RequestBody @Validated TenantForm tenantForm) {
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

    @Operation(summary = "获取租户下拉选项")
    @Parameter(name = "过滤出我的租户下拉选项", description = "1: 获取我的租户下拉选项; 0: 获取所有租户下拉选项")
    @GetMapping("/options")
    public List<Option<Long>> listTenantOptions(@RequestParam(name = "filterMe", defaultValue = "1") Integer filterMe) {
        return sysTenantService.listTenantOptions(filterMe == 1);
    }
}
