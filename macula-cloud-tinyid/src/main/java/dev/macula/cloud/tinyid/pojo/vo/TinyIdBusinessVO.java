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

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Read-only TinyID business configuration from one physical data source.
 *
 * @author Rain
 * @since 6.1.0
 */
@Data
public class TinyIdBusinessVO {
    /** 当前数据源中的业务配置主键。 */
    private Long id;
    /** 物理数据源标识。 */
    private String dataSourceKey;
    /** 数据源稳定序号。 */
    private Integer sequence;
    /** 数据源当前是否可访问。 */
    private Boolean healthy;
    /** 业务类型。 */
    private String bizType;
    /** 当前号段起始 ID。 */
    private Long beginId;
    /** 当前已分配的最大 ID。 */
    private Long maxId;
    /** 每次申请 ID 区间的步长。 */
    private Integer step;
    /** 多数据库实例的预留总数。 */
    private Integer delta;
    /** 当前数据源在取模分片中的余数，只读且等于稳定序号。 */
    private Integer remainder;
    /** 乐观锁版本号。 */
    private Long version;
    /** 业务配置创建时间。 */
    private LocalDateTime createTime;
    /** 业务配置最后更新时间。 */
    private LocalDateTime updateTime;
}
