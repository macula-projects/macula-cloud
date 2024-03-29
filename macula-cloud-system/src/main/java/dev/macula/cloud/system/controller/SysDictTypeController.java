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
import dev.macula.boot.starter.auditlog.annotation.AuditLog;
import dev.macula.cloud.system.form.DictTypeForm;
import dev.macula.cloud.system.query.DictTypePageQuery;
import dev.macula.cloud.system.service.SysDictTypeService;
import dev.macula.cloud.system.vo.dict.DictTypePageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "字典类型接口")
@RestController
@RequestMapping("/api/v1/dict/types")
@RequiredArgsConstructor
public class SysDictTypeController {

    private final SysDictTypeService dictTypeService;

    @Operation(summary = "字典类型分页列表")
    @GetMapping("/pages")
    public IPage<DictTypePageVO> listDictTypePages(DictTypePageQuery queryParams) {
        return dictTypeService.listDictTypePages(queryParams);
    }

    @Operation(summary = "字典类型表单详情")
    @Parameter(name = "字典ID")
    @GetMapping("/{id}/form")
    public DictTypeForm getDictTypeFormData(@PathVariable Long id) {
        return dictTypeService.getDictTypeFormData(id);
    }

    @Operation(summary = "新增字典类型")
    @AuditLog(title = "新增字典类型")
    @PostMapping
    public boolean saveDictType(@RequestBody DictTypeForm dictTypeForm) {
        return dictTypeService.saveDictType(dictTypeForm);
    }

    @Operation(summary = "修改字典类型")
    @AuditLog(title = "修改字典类型")
    @PutMapping("/{id}")
    public boolean updateDict(@PathVariable Long id, @RequestBody DictTypeForm dictTypeForm) {
        return dictTypeService.updateDictType(id, dictTypeForm);
    }

    @Operation(summary = "删除字典类型")
    @AuditLog(title = "删除字典类型")
    @Parameter(name = "字典ID", description = "字典类型ID，多个以英文逗号(,)分割")
    @DeleteMapping("/{ids}")
    public boolean deleteDictTypes(@PathVariable String ids) {
        return dictTypeService.deleteDictTypes(ids);
    }

    @Operation(summary = "获取字典类型的数据项")
    @Parameter(name = "字典类型编码")
    @GetMapping("/{typeCode}/items")
    public List<Option<String>> listDictItemsByTypeCode(@PathVariable String typeCode) {
        return dictTypeService.listDictItemsByTypeCode(typeCode);
    }
}
