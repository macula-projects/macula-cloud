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
import dev.macula.cloud.system.enums.PasswordComplexityRule;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 当前租户密码策略写入参数。
 *
 * @author Rain
 * @since 2026-10-16
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class PasswordPolicyConfigForm {
    @NotNull @Min(6) @Max(128)
    private Integer passwordBiggestLength;
    @NotNull @Min(6) @Max(128)
    private Integer passwordLeastLength;
    @NotNull
    private PasswordComplexityRule passwordComplexity;
    @Size(max = 60000)
    private String customWeakPassword;
    @NotNull
    private Boolean weakPasswordCheck;
    @NotNull
    private Boolean includeAccountCheck;
    @NotNull @Min(0) @Max(20)
    private Integer notSameChars;
    @NotNull
    private Boolean historyPasswordCheck;
    @NotNull @Min(0) @Max(50)
    private Integer historyPasswordCheckCount;
    @NotNull
    private Boolean illegalSequenceCheck;
    @NotNull @Min(0) @Max(3650)
    private Integer passwordValidDays;
    @NotNull @Min(0) @Max(365)
    private Integer passwordValidWarnBeforeDays;

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("未知配置字段：" + name);
    }
}
