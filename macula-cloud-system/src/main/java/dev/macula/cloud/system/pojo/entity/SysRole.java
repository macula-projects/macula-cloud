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

package dev.macula.cloud.system.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import dev.macula.boot.starter.mp.entity.BaseEntity;
import dev.macula.cloud.system.enums.RoleDataScopeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class SysRole extends BaseEntity {

    private String name;

    @Schema(description = "角色编码")
    private String code;

    private Integer sort;

    private Integer status;

    private RoleDataScopeEnum dataScope;

    @Schema(description = "逻辑删除标识 0-未删除 1-已删除")
    //@TableLogic(value = "0", delval = "1")
    private Integer deleted;

    @TableField(exist = false)
    private List<Long> menuIds;

    @TableField(exist = false)
    private List<Long> permissionIds;

}
