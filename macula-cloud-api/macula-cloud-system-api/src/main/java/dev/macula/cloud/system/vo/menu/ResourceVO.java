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

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.macula.boot.result.Option;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "资源(菜单+权限)视图对象")
@Data
public class ResourceVO {

    @Schema(description = "选项的值")
    private Long value;

    @Schema(description = "选项的标签")
    private String label;

    @Schema(description = "子菜单")
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
    private List<ResourceVO> children;

    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    private List<Option> perms;

}
