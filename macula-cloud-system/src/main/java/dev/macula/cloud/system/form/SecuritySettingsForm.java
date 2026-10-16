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

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 尚未接入 IAM 运行时的全局通用安全设置。
 *
 * @author Rain
 * @since 2026-10-13
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SecuritySettingsForm {
    @NotNull @Min(1) @Max(1000)
    private Integer maxConcurrentSessions;
    @NotNull @Min(1) @Max(43200)
    private Integer sessionTimeoutMinutes;
    @NotNull @Min(0) @Max(365)
    private Integer rememberMeDays;
    @NotNull @Min(30) @Max(3600)
    private Integer captchaValiditySeconds;
    @NotNull @Min(1) @Max(1440)
    private Integer failureWindowMinutes;
    @NotNull @Min(1) @Max(100)
    private Integer maxFailures;
    @NotNull @Min(1) @Max(10080)
    private Integer unlockMinutes;
    @Size(max = 4000)
    private String contentSecurityPolicy;
}
