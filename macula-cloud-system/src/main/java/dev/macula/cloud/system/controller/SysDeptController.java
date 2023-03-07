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

import dev.macula.boot.result.Option;
import dev.macula.cloud.system.annotation.AuditLog;
import dev.macula.cloud.system.form.DeptForm;
import dev.macula.cloud.system.query.DeptQuery;
import dev.macula.cloud.system.service.SysDeptService;
import dev.macula.cloud.system.vo.dept.DeptVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 部门控制器
 *
 * @author haoxr
 * @since 2020/11/6
 */
@Tag(name = "部门接口", description = "部门接口")
@RestController
@RequestMapping("/api/v1/dept")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptService deptService;

    @Operation(summary = "获取部门列表")
    @GetMapping
    public List<DeptVO> listDepartments(DeptQuery queryParams) {
        List<DeptVO> list = deptService.listDepartments(queryParams);
        return list;
    }

    @Operation(summary = "获取部门下拉选项")
    @GetMapping("/options")
    public List<Option> listDeptOptions() {
        List<Option> list = deptService.listDeptOptions();
        return list;
    }

    @Operation(summary = "获取部门详情")
    @Parameter(name = "部门ID")
    @GetMapping("/{deptId}/form")
    public DeptForm getDeptForm(@PathVariable Long deptId) {
        DeptForm deptForm = deptService.getDeptForm(deptId);
        return deptForm;
    }

    @Operation(summary = "新增部门")
    @AuditLog(title = "新增部门")
    @PostMapping
    public Long saveDept(@Valid @RequestBody DeptForm formData) {
        Long id = deptService.saveDept(formData);
        return id;
    }

    @Operation(summary = "修改部门")
    @AuditLog(title = "修改部门")
    @PutMapping(value = "/{deptId}")
    public Long updateDept(@PathVariable Long deptId, @Valid @RequestBody DeptForm formData) {
        deptId = deptService.updateDept(deptId, formData);
        return deptId;
    }

    @Operation(summary = "删除部门")
    @AuditLog(title = "删除部门")
    @Parameter(name = "部门ID，多个以英文逗号(,)分割")
    @DeleteMapping("/{ids}")
    public boolean deleteDepartments(@PathVariable("ids") String ids) {
        boolean result = deptService.deleteByIds(ids);
        return result;
    }

}
