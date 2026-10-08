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
package dev.macula.cloud.tinyid.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Aggregate management view of one business and all of its physical datasource instances.
 *
 * @author Rain
 * @since 6.1.0
 */
@Data
@AllArgsConstructor
public class TinyIdBusinessAggregateVO {
    /** 业务类型。 */
    private String bizType;
    /** 每次申请 ID 区间的步长。 */
    private Integer step;
    /** 多数据库实例的预留总数。 */
    private Integer delta;
    /** 跨数据源配置一致性状态。 */
    private String consistencyStatus;
    /** 各物理数据源上的业务配置。 */
    private List<TinyIdBusinessVO> dataSources;
}
