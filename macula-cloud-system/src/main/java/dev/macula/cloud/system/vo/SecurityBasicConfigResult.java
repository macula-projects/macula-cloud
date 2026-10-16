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
package dev.macula.cloud.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 当前租户通用安全配置读取结果。
 *
 * @author Rain
 * @since 2026-10-16
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "通用安全")
public class SecurityBasicConfigResult implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @Schema(description = "会话有效时间（秒）")
    private Integer sessionValidTime;
    @Schema(description = "验证码有效时间（秒）")
    private Integer verifyCodeValidTime;
    @Schema(description = "记住我有效时间（秒）")
    private Integer rememberMeValidTime;
    @Schema(description = "用户并发数")
    private Integer sessionMaximum;
}
