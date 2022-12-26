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

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.macula.boot.starter.mp.entity.BaseEntity;
import dev.macula.cloud.system.enums.MenuTypeEnum;
import lombok.Data;

/**
 * 菜单实体类
 *
 * @author haoxr
 * @date 2021/11/06
 */
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class SysMenu extends BaseEntity {

    private Long parentId;

    private String name;

    private String icon;

    /**
     * 路由path
     */
    private String path;

    /**
     * 组件路径
     */
    private String component;

    private Integer sort;

    private Integer visible;

    private String redirect;

    private Integer fullPage;

    /**
     * 菜单类型(1:菜单；2：目录；3：外链；4：按钮)
     */
    private MenuTypeEnum type;

    /**
     * 按钮权限标识
     */
    private String perm;

}
