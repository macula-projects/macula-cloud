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

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.macula.cloud.system.annotation.AuditLog;
import dev.macula.cloud.system.query.LogPageQuery;
import dev.macula.cloud.system.service.SysLogService;
import dev.macula.cloud.system.vo.log.AuditLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;



@Tag(name = "审计日志接口")
@RestController
@RequestMapping("/api/v1/audit/log")
@RequiredArgsConstructor
public class SysLogController {

    private final SysLogService sysLogService;

    @Operation(summary = "审计日志分页列表")
    @AuditLog(title = "审计日志分页列表")
    @GetMapping("/page")
    public Page<AuditLogVO> listAuditLogPages(LogPageQuery pageQuery) {
        Page<AuditLogVO> result = sysLogService.listUserPages(pageQuery);
        return result;
    }

}

