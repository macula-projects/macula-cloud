/*
 * Copyright (c) 2026 Macula
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 当前租户通用安全配置写入参数。
 *
 * @author Rain
 * @since 2026-10-16
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class SecurityBasicConfigForm {
    @NotNull @Min(1) @Max(2592000)
    private Integer sessionValidTime;
    @NotNull @Min(1) @Max(86400)
    private Integer verifyCodeValidTime;
    @NotNull @Min(0) @Max(31536000)
    private Integer rememberMeValidTime;
    @NotNull @Min(1) @Max(1000)
    private Integer sessionMaximum;

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("未知配置字段：" + name);
    }
}
