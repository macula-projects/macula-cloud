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
package dev.macula.cloud.tinyid.pojo.bo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单个物理数据源中的发号业务对象。
 *
 * @author Rain
 * @since 6.1.0
 */
@Data
public class TinyIdBusinessBO {
    /** 主键。 */
    private Long id;
    /** 数据源标识。 */
    private String dataSourceKey;
    /** 数据源列表下标。 */
    private Integer sequence;
    /** 数据源是否健康。 */
    private Boolean healthy;
    /** 业务类型。 */
    private String bizType;
    /** 号段起始 ID。 */
    private Long beginId;
    /** 已分配最大 ID。 */
    private Long maxId;
    /** 号段步长。 */
    private Integer step;
    /** 预留实例数。 */
    private Integer delta;
    /** 当前数据源余数。 */
    private Integer remainder;
    /** 版本号。 */
    private Long version;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}
