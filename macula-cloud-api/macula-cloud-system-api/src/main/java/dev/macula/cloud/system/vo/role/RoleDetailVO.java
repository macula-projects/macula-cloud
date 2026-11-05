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

package dev.macula.cloud.system.vo.role;

import dev.macula.boot.enums.DataScopeEnum;
import java.util.Date;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;
import java.util.List;
import lombok.Data;

/**
 * 角色详情接口模型，审计时间使用Date且不暴露数据库基类。
 * @author Rain
 * @since 6.1.0
 */
@Data
public class RoleDetailVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String createBy;
    private Date createTime;
    private String lastUpdateBy;
    private Date lastUpdateTime;
    private String name;
    private String code;
    private Integer sort;
    private Integer status;
    private Boolean negative;
    private Boolean exclusivity;
    private DataScopeEnum dataScope;
    private Integer deleted;
    private List<Long> menuIds;
    private List<Long> permissionIds;
}
