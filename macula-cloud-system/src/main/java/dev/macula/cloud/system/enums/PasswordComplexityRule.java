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
package dev.macula.cloud.system.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 密码复杂度的稳定持久化规则，code 不随枚举声明顺序变化。
 *
 * @author Rain
 * @since 2026-10-16
 */
@Getter
@RequiredArgsConstructor
public enum PasswordComplexityRule {
    NONE(0, "任意密码"),
    MUST_NUMBERS_AND_LETTERS(1, "必须包含数字和字母"),
    MUST_NUMBERS_AND_CAPITAL_LETTERS(2, "必须包含数字和大写字母"),
    MUST_CONTAIN_NUMBERS_UPPERCASE_LETTERS_LOWERCASE_LETTERS_AND_SPECIAL_CHARACTERS(
        3, "必须包含数字、大小写字母和特殊字符"),
    CONTAIN_AT_LEAST_TWO_OF_NUMBERS_LETTERS_AND_SPECIAL_CHARACTERS(
        4, "数字、字母和特殊字符至少两类"),
    CONTAIN_AT_LEAST_THREE_OF_NUMBERS_UPPERCASE_LETTERS_LOWERCASE_LETTERS_AND_SPECIAL_CHARACTERS(
        5, "数字、大小写字母和特殊字符至少三类");

    private final int code;
    private final String description;

    @JsonValue
    public int code() {
        return code;
    }

    @JsonCreator
    public static PasswordComplexityRule fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values()).filter(rule -> rule.code == code).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("不支持的密码复杂度"));
    }
}
