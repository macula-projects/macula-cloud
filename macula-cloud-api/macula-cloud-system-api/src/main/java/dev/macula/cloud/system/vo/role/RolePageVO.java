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

package dev.macula.cloud.system.vo.role;

import com.fasterxml.jackson.annotation.JsonFormat;
import dev.macula.boot.enums.DataScopeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "角色分页视图对象")
@Data
public class RolePageVO {

    @Schema(description = "角色ID")
    private Long id;

    @Schema(description = "角色名称")
    private String name;

    @Schema(description = "角色编码")
    private String code;

    @Schema(description = "反向角色（1-是，0-否）")
    private Boolean negative;

    @Schema(description = "排他性角色（1-是，0-否）")
    private Boolean exclusivity;

    @Schema(description = "角色状态")
    private Integer status;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "角色数据范围")
    private DataScopeEnum dataScope;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastUpdateTime;
}
