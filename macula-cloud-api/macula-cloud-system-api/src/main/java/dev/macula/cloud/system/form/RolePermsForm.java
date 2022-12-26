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

import lombok.Data;

import java.util.List;

/**
 * 角色权限传输层对象
 *
 * @author haoxr
 * @date 2021/12/19 11:46
 */
@Data
public class RolePermsForm {

    /**
     * 菜单ID
     */
    private Long menuId;

    /**
     * 角色ID
     */
    private Long roleId;

    /**
     * 权限ID集合
     */
    private List<Long> permIds;

}
