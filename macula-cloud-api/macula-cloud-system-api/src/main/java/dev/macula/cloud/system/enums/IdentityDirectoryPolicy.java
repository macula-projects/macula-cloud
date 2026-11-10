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

/**
 * 身份核验与企业资料查询的独立策略，不改变本地账号权限。
 * @author Rain
 * @since 6.1.0
 */
public enum IdentityDirectoryPolicy {
    AUTO("AUTO"), IDENTITY_ONLY("IDENTITY_ONLY"), OPTIONAL("OPTIONAL"), REQUIRED("REQUIRED"), BACKGROUND("BACKGROUND");
    private final String value;
    /** 固定配置值，不依赖Java枚举名称序列化。 */
    IdentityDirectoryPolicy(String value) { this.value = value; }
    /** 返回稳定配置标识。 */
    public String value() { return value; }

    /** 缺省保持既有身份链路，不隐式增加通讯录权限要求。 */
    public static IdentityDirectoryPolicy fromValue(String value) {
        return value == null ? IDENTITY_ONLY : java.util.Arrays.stream(values())
            .filter(item -> item.value().equals(value)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("目录策略无效"));
    }
}
