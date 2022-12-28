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

package dev.macula.cloud.system.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 部门分页查询对象
 *
 * @author haoxr
 * @since 2022/6/11
 */
@Schema(description = "部门分页查询对象")
@Data
public class DeptQuery {

    @Schema(description = "关键字(部门名称)")
    private String keywords;

    @Schema(description = "状态(1->正常；0->禁用)")
    private Integer status;

}
