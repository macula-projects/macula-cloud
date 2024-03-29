/*
 * Copyright (c) 2023 Macula
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

package dev.macula.cloud.iam.enums;

import lombok.Getter;

/**
 * {@code PasswordEncoderTypeEnum} 密码编码类型枚举
 *
 * @author haoxr
 * @since 2021/6/5 17:57
 */

public enum PasswordEncoderTypeEnum {

    BCRYPT("{bcrypt}", "BCRYPT加密"), NOOP("{noop}", "无加密明文");

    @Getter
    private final String prefix;
    @Getter
    private final String desc;

    PasswordEncoderTypeEnum(String prefix, String desc) {
        this.prefix = prefix;
        this.desc = desc;
    }
}
