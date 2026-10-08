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
package dev.macula.cloud.tinyid.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import dev.macula.cloud.tinyid.pojo.form.*;
import dev.macula.cloud.tinyid.pojo.query.ApplicationPageQuery;
import dev.macula.cloud.tinyid.pojo.query.AuditLogPageQuery;
import dev.macula.cloud.tinyid.pojo.query.BusinessPageQuery;
import dev.macula.cloud.tinyid.pojo.vo.*;

import java.util.List;

/**
 * ROOT-only TinyID application and business management service.
 *
 * @author Rain
 * @since 6.1.0
 */
public interface TinyIdManagementService {
    /**
     * 分页查询接入应用。
     *
     * @param query 分页及关键字查询条件
     * @return 接入应用分页结果
     */
    IPage<TinyIdApplicationVO> listApplications(ApplicationPageQuery query);

    /**
     * 查询指定接入应用。
     *
     * @param appId 应用主键
     * @return 接入应用详情
     */
    TinyIdApplicationVO getApplication(long appId);

    /**
     * 创建接入应用并为其授权业务类型。
     *
     * @param form 应用创建参数
     * @return 创建后的接入应用
     */
    TinyIdApplicationVO createApplication(CreateApplicationForm form);

    /**
     * 修改接入应用备注。
     *
     * @param appId 应用主键
     * @param form  备注修改参数
     * @return 修改后的接入应用
     */
    TinyIdApplicationVO updateRemark(long appId, UpdateApplicationRemarkForm form);

    /**
     * 为接入应用追加业务授权。
     *
     * @param appId 应用主键
     * @param form  待追加的业务类型
     * @return 追加授权后的接入应用
     */
    TinyIdApplicationVO addBusinesses(long appId, AddApplicationBusinessesForm form);

    /**
     * 删除接入应用及其全部业务授权。
     *
     * @param appId 应用主键
     */
    void deleteApplication(long appId);

    /**
     * 分页查询跨数据源聚合后的发号业务。
     *
     * @param query 分页及关键字查询条件
     * @return 发号业务分页结果
     */
    IPage<TinyIdBusinessAggregateVO> listBusinesses(BusinessPageQuery query);

    /**
     * 查询指定发号业务在全部数据源上的聚合信息。
     *
     * @param bizType 业务类型
     * @return 发号业务聚合信息
     */
    TinyIdBusinessAggregateVO getBusiness(String bizType);

    /**
     * 在全部当前数据源上创建发号业务。
     *
     * @param form 发号业务创建参数
     * @return 创建后的发号业务聚合信息
     */
    TinyIdBusinessAggregateVO createBusiness(CreateBusinessForm form);

    /**
     * 删除尚未发号的业务；全部数据源的最大 ID 均为零时才允许删除。
     *
     * @param bizType 业务类型
     */
    void deleteBusiness(String bizType);

    /**
     * 查询发号业务的跨数据源一致性状态。
     *
     * @param bizType 业务类型
     * @return 一致性检查结果
     */
    TinyIdBusinessConsistencyVO getConsistency(String bizType);

    /**
     * 查询参与 TinyID 发号的数据源及其稳定序号。
     *
     * @return 数据源状态列表
     */
    List<TinyIdDataSourceVO> listDataSources();

    /**
     * 分页查询 TinyID 管理操作审计日志。
     *
     * @param query 分页及操作人查询条件
     * @return 审计日志分页结果
     */
    IPage<TinyIdAuditLogVO> listAuditLogs(AuditLogPageQuery query);
}
