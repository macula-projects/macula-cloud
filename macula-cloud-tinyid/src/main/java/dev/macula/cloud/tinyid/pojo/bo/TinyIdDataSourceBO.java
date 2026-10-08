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

/**
 * 物理数据源状态业务对象。
 *
 * @author Rain
 * @since 6.1.0
 */
@Data
@AllArgsConstructor
public class TinyIdDataSourceBO {
    /** 数据源标识。 */
    private String key;
    /** 数据源列表下标。 */
    private int sequence;
    /** 是否健康。 */
    private boolean healthy;
}
