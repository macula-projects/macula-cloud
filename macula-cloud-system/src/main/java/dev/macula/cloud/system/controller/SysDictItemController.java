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

import com.baomidou.mybatisplus.core.metadata.IPage;
import dev.macula.cloud.system.form.DictItemForm;
import dev.macula.cloud.system.query.DictItemPageQuery;
import dev.macula.cloud.system.service.SysDictItemService;
import dev.macula.cloud.system.vo.dict.DictItemPageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "字典数据接口")
@RestController
@RequestMapping("/api/v1/dict/items")
@RequiredArgsConstructor
public class SysDictItemController {

    private final SysDictItemService dictItemService;

    @Operation(summary = "字典数据分页列表")
    @GetMapping("/pages")
    public IPage<DictItemPageVO> listDictItemPages(DictItemPageQuery queryParams) {
        IPage<DictItemPageVO> result = dictItemService.listDictItemPages(queryParams);
        return result;
    }

    @Operation(summary = "字典数据表单数据")
    @Parameter(name = "字典ID")
    @GetMapping("/{id}/form")
    public DictItemForm getDictItemForm(@PathVariable Long id) {
        DictItemForm formData = dictItemService.getDictItemForm(id);
        return formData;
    }

    @Operation(summary = "新增字典数据")
    @PostMapping
    public boolean saveDictItem(@RequestBody DictItemForm DictItemForm) {
        boolean result = dictItemService.saveDictItem(DictItemForm);
        return result;
    }

    @Operation(summary = "修改字典数据")
    @PutMapping("/{id}")
    public boolean updateDictItem(@PathVariable Long id, @RequestBody DictItemForm DictItemForm) {
        boolean status = dictItemService.updateDictItem(id, DictItemForm);
        return status;
    }

    @Operation(summary = "删除字典")
    @Parameter(name = "字典ID", description = "字典ID，多个以英文逗号(,)拼接")
    @DeleteMapping("/{ids}")
    public boolean deleteDictItems(@PathVariable String ids) {
        boolean result = dictItemService.deleteDictItems(ids);
        return result;
    }
}
