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

package dev.macula.cloud.system.vo.dict;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "字典数据项分页对象")
@Data
public class DictItemPageVO {

    @Schema(description = "数据项ID")
    private Long id;

    @Schema(description = "数据项名称")
    private String name;

    @Schema(description = "值")
    private String value;

    @Schema(description = "类型状态：1->启用;0->禁用")
    private Integer status;

}
