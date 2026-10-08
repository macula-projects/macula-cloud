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
 * Cross-data-source consistency summary for one TinyID business.
 *
 * @author Rain
 * @since 6.1.0
 */
@Data
@AllArgsConstructor
public class TinyIdBusinessConsistencyVO {
    /** 业务类型。 */
    private String bizType;
    /** 跨数据源配置一致性状态。 */
    private String status;
    /** 参与一致性检查的各数据源配置。 */
    private List<TinyIdBusinessVO> dataSources;
}
