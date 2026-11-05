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

package dev.macula.cloud.system.vo.menu;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.macula.boot.enums.MenuTypeEnum;
import java.util.Date;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 菜单详情接口模型，保留原详情字段名和空值输出规则。
 * @author Rain
 * @since 6.1.0
 */
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MenuDetailVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String createBy;
    private Date createTime;
    private String lastUpdateBy;
    private Date lastUpdateTime;
    private Long parentId;
    private String name;
    private String icon;
    private String path;
    private String component;
    private Integer sort;
    private Integer visible;
    private String redirect;
    private Integer fullPage;
    private MenuTypeEnum type;
    private String perm;
    private Long tenantId;
}
