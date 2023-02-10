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
import dev.macula.cloud.system.form.ApplicationForm;
import dev.macula.cloud.system.query.ApplicationPageQuery;
import dev.macula.cloud.system.service.SysApplicationService;
import dev.macula.cloud.system.vo.app.ApplicationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 应用控制器
 *
 */
@Tag(name = "应用接口", description = "应用接口")
@RestController
@RequestMapping("/api/v1/app")
@RequiredArgsConstructor
public class SysApplicationController {

    private final SysApplicationService applicationService;

    @Operation(summary = "获取应用列表")
    @GetMapping
    public IPage<ApplicationVO> listApplications(ApplicationPageQuery queryParams) {
        IPage<ApplicationVO> list = applicationService.listApplicationPages(queryParams);
        return list;
    }

    @Operation(summary = "新增应用")
    @PostMapping
    public boolean saveApplication(
            @Valid @RequestBody ApplicationForm formData
    ) {
        boolean result = applicationService.saveApplication(formData);
        return result;
    }

    @Operation(summary = "修改应用")
    @PutMapping(value = "/{appId}")
    public boolean updateApplication(
            @PathVariable Long appId,
            @Valid @RequestBody ApplicationForm formData
    ) {
        boolean result = applicationService.updateApplication(appId, formData);
        return result;
    }

    @Operation(summary = "删除应用")
    @Parameter(name = "应用ID，多个以英文逗号(,)分割")
    @DeleteMapping("/{ids}")
    public boolean deleteApplications(
            @PathVariable("ids") String ids
    ) {
        boolean result = applicationService.deleteApplications(ids);
        return result;
    }

}
