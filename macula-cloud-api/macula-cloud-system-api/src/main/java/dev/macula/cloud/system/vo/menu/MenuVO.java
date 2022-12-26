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

package dev.macula.cloud.system.vo.menu;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import dev.macula.cloud.system.enums.MenuTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = " 菜单视图对象")
@Data
public class MenuVO {

    private Long id;

    private Long parentId;

    private String name;

    private String icon;

    private String routeName;

    private String routePath;

    private String component;

    private Integer sort;

    private Integer visible;

    private String redirect;

    @Schema(description = "菜单类型")
    private MenuTypeEnum type;

    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    private List<MenuVO> children;

    @Schema(description = "按钮权限标识")
    private String perm;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

}
