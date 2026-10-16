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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 尚未接入 IAM 运行时的全局密码策略。
 *
 * @author Rain
 * @since 2026-10-13
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordPolicyForm {
    @NotNull @Min(6) @Max(128)
    private Integer minLength;
    @NotNull @Min(6) @Max(128)
    private Integer maxLength;
    @NotNull @Min(0) @Max(4)
    private Integer complexityLevel;
    @NotNull @Min(0) @Max(3650)
    private Integer expiryDays;
    @NotNull @Min(0) @Max(365)
    private Integer reminderDays;
    @NotNull @Min(0) @Max(20)
    private Integer maxConsecutiveCharacters;
    @NotNull
    private Boolean checkUserInformation;
    @NotNull @Min(0) @Max(50)
    private Integer historyCount;
    private List<String> illegalSequences;
    @NotNull
    private Boolean weakPasswordCheck;
    private List<String> customWeakPasswords;
}
