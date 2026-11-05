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

package dev.macula.cloud.system.vo.perm;

import java.util.Date;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;
import java.util.List;
import lombok.Data;

/**
 * 权限列表和详情接口模型，隔离实体审计日期时间。
 * @author Rain
 * @since 6.1.0
 */
@Data
public class PermissionVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String createBy;
    private Date createTime;
    private String lastUpdateBy;
    private Date lastUpdateTime;
    private String name;
    private Long menuId;
    private String urlPerm;
    private String tenantCode;
    private List<String> roles;
}
