/*
 * Copyright (c) 2026 Macula
 * macula.dev, China
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

import dev.macula.boot.enums.MenuTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;


/**
 * 菜单可维护业务字段，不接受数据库审计时间或租户身份。
 * @author Rain
 * @since 6.1.0
 */
@Schema(description = "菜单表单对象")
@Data
public class MenuForm {

    private Long id;

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

    private List<PermissionValidtorForm> apiList;
}
