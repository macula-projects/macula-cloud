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
 * IAM可用于账号匹配的固定字段；不允许主键、角色或任意列名。
 * @author Rain
 * @since 6.1.0
 */
public enum IdentityBindingTargetField {
    USERNAME("username", "用户名"), MOBILE("mobile", "手机号"), EMAIL("email", "邮箱");
    private final String value;
    private final String label;
    /** 定义稳定字段值。 */
    IdentityBindingTargetField(String value, String label) {
        this.value = value;
        this.label = label;
    }
    /** 返回固定协议值。 */
    public String value() { return value; }
    /** 返回界面名称。 */
    public String label() { return label; }
    /** 解析白名单字段，不把输入用于SQL列名拼接。 */
    public static IdentityBindingTargetField fromValue(String value) {
        for (var field : values()) { if (field.value.equals(value)) { return field; } }
        throw new IllegalArgumentException("请选择受支持的IAM字段");
    }
}
