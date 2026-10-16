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

import dev.macula.cloud.system.enums.PasswordComplexityRule;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 当前租户密码策略读取结果。
 *
 * @author Rain
 * @since 2026-10-16
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "密码策略")
public class PasswordPolicyConfigResult implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @Schema(description = "密码最大长度")
    private Integer passwordBiggestLength;
    @Schema(description = "密码最小长度")
    private Integer passwordLeastLength;
    @Schema(description = "密码复杂度")
    private PasswordComplexityRule passwordComplexity;
    @Schema(description = "自定义弱密码")
    private String customWeakPassword;
    @Schema(description = "弱密码检查")
    private Boolean weakPasswordCheck;
    @Schema(description = "账户信息检查")
    private Boolean includeAccountCheck;
    @Schema(description = "不能多少个以上相同字符")
    private Integer notSameChars;
    @Schema(description = "历史密码检查")
    private Boolean historyPasswordCheck;
    @Schema(description = "历史密码检查次数")
    private Integer historyPasswordCheckCount;
    @Schema(description = "非法序列检查")
    private Boolean illegalSequenceCheck;
    @Schema(description = "密码有效天数")
    private Integer passwordValidDays;
    @Schema(description = "密码过期前提醒天数")
    private Integer passwordValidWarnBeforeDays;
}
