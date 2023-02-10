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

package dev.macula.cloud.system.form;

import dev.macula.cloud.system.enums.RoleDataScopeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Schema(description = "角色表单对象")
@Data
public class RoleForm {

    @Schema(description = "角色ID")
    private Long id;

    @Schema(description = "角色名称")
    @NotBlank(message = "角色名称不能为空")
    private String name;

    @Schema(description = "角色编码")
    @NotBlank(message = "角色编码不能为空")
    private String code;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "角色状态(1-正常；0-停用)")
    private Integer status;

    @Schema(description = "数据范围（1：全部数据权限  2：本部门数据权限 3：本部门及以下数据权限 4:本人数据）")
    private RoleDataScopeEnum dataScope;

}
