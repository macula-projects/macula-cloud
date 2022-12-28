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

package dev.macula.cloud.system.vo.perm;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 权限视图对象
 *
 * @author haoxr
 * @date 2021/10/30 10:54
 */
@Schema(description = "权限视图对象")
@Data
public class PermPageVO {

    @Schema(description = "权限ID")
    private Long id;

    @Schema(description = "权限名称")
    private String name;

    @Schema(description = "URL权限标识-服务名称")
    private String serviceName;

    @Schema(description = "URL权限标识-请求标识")
    private String requestMethod;

    @Schema(description = "URL权限标识-请求方式")
    private String requestPath;
}
