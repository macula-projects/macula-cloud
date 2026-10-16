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

import dev.macula.cloud.system.form.PasswordPolicyForm;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 全局密码策略展示数据，明确标记当前未接入运行时。
 *
 * @author Rain
 * @since 2026-10-13
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordPolicyVO {
    private Integer minLength;
    private Integer maxLength;
    private Integer complexityLevel;
    private Integer expiryDays;
    private Integer reminderDays;
    private Integer maxConsecutiveCharacters;
    private Boolean checkUserInformation;
    private Integer historyCount;
    private List<String> illegalSequences;
    private Boolean weakPasswordCheck;
    private List<String> customWeakPasswords;
    private boolean effective;

    public static PasswordPolicyVO from(PasswordPolicyForm form) {
        return new PasswordPolicyVO(form.getMinLength(), form.getMaxLength(), form.getComplexityLevel(),
            form.getExpiryDays(), form.getReminderDays(), form.getMaxConsecutiveCharacters(),
            form.getCheckUserInformation(), form.getHistoryCount(), form.getIllegalSequences(),
            form.getWeakPasswordCheck(), form.getCustomWeakPasswords(), false);
    }
}
