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

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 跨数据源聚合的发号业务对象。
 *
 * @author Rain
 * @since 6.1.0
 */
@Data
@AllArgsConstructor
public class TinyIdBusinessAggregateBO {
    /** 业务类型。 */
    private String bizType;
    /** 号段步长。 */
    private Integer step;
    /** 预留实例数。 */
    private Integer delta;
    /** 一致性状态。 */
    private String consistencyStatus;
    /** 各数据源业务配置。 */
    private List<TinyIdBusinessBO> dataSources;
}
