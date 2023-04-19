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

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * {@code UserTypeEnum} is
 *
 * @author rain
 * @since 2023/4/15 20:25
 */
public enum UserTypeEnum {
    MEMBER("member", "会员"), EMPLOYEE("employee", "行政员工"), ODC("odc", "外包人员"), SUPPLIER("supplier", "供应商");

    @Getter
    @EnumValue
    private final String type;
    @Getter
    private final String desc;

    UserTypeEnum(String type, String desc) {
        this.type = type;
        this.desc = desc;
    }
}
