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
package dev.macula.cloud.tinyid.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import dev.macula.boot.starter.auditlog.annotation.AuditLog;
import dev.macula.cloud.tinyid.pojo.form.*;
import dev.macula.cloud.tinyid.pojo.query.AuditLogPageQuery;
import dev.macula.cloud.tinyid.pojo.query.BusinessPageQuery;
import dev.macula.cloud.tinyid.service.TinyIdManagementService;
import dev.macula.cloud.tinyid.pojo.vo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ROOT-only HTTP adapter for TinyID business and audit management.
 *
 * @author Rain
 * @since 6.1.0
 */
@Validated
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROOT')")
public class TinyIdAdminController {

    /** TinyID 管理业务服务。 */
    private final TinyIdManagementService service;

    /**
     * 分页查询发号业务。
     *
     * @param query 分页及关键字查询条件
     * @return 发号业务分页结果
     */
    @GetMapping("/businesses")
    @AuditLog(title = "查询TinyID发号业务", isSaveRequestData = false, isSaveResponseData = false)
    public IPage<TinyIdBusinessAggregateVO> listBusinesses(@Valid BusinessPageQuery query) {
        return service.listBusinesses(query);
    }

    /**
     * 查询发号业务详情。
     *
     * @param bizType 业务类型
     * @return 跨数据源聚合后的业务详情
     */
    @GetMapping("/businesses/{bizType}")
    @AuditLog(title = "查看TinyID发号业务", isSaveRequestData = false, isSaveResponseData = false)
    public TinyIdBusinessAggregateVO getBusiness(@PathVariable @NotBlank String bizType) {
        return service.getBusiness(bizType);
    }

    /**
     * 检查发号业务的跨数据源一致性。
     *
     * @param bizType 业务类型
     * @return 一致性检查结果
     */
    @GetMapping("/businesses/{bizType}/consistency")
    @AuditLog(title = "检查TinyID跨库配置", isSaveRequestData = false, isSaveResponseData = false)
    public TinyIdBusinessConsistencyVO getConsistency(@PathVariable @NotBlank String bizType) {
        return service.getConsistency(bizType);
    }

    /**
     * 在全部当前数据源上创建发号业务。
     *
     * @param form 发号业务创建参数
     * @return 创建后的业务聚合信息
     */
    @PostMapping("/businesses")
    @AuditLog(title = "新增TinyID发号业务", isSaveRequestData = false, isSaveResponseData = false)
    public TinyIdBusinessAggregateVO createBusiness(@Valid @RequestBody CreateBusinessForm form) {
        return service.createBusiness(form);
    }

    /**
     * 删除全部数据源上尚未发号的业务。
     *
     * @param bizType 业务类型
     */
    @DeleteMapping("/businesses/{bizType}")
    @AuditLog(title = "删除TinyID发号业务", isSaveRequestData = false, isSaveResponseData = false)
    public void deleteBusiness(@PathVariable @NotBlank String bizType) {
        service.deleteBusiness(bizType);
    }

    /**
     * 查询 TinyID 数据源状态及稳定序号。
     *
     * @return 数据源状态列表
     */
    @GetMapping("/data-sources")
    @AuditLog(title = "查询TinyID数据源", isSaveRequestData = false, isSaveResponseData = false)
    public List<TinyIdDataSourceVO> listDataSources() {
        return service.listDataSources();
    }

    /**
     * 分页查询 TinyID 管理审计日志。
     *
     * @param query 分页及操作人查询条件
     * @return 审计日志分页结果
     */
    @GetMapping("/audit-logs")
    @AuditLog(title = "查询TinyID审计日志", isSaveRequestData = false, isSaveResponseData = false)
    public IPage<TinyIdAuditLogVO> listAuditLogs(@Valid AuditLogPageQuery query) {
        return service.listAuditLogs(query);
    }
}
