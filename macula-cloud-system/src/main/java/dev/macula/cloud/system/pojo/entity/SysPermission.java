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

package dev.macula.cloud.system.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import dev.macula.boot.starter.mp.entity.BaseEntity;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class SysPermission extends BaseEntity {

    private String name;

    private Long menuId;

    private String urlPerm;

    // 租户CODE
    @TableField(exist = false)
    private String tenantCode;

    // 有权限的角色编号集合
    @TableField(exist = false)
    private List<String> roles;
}
