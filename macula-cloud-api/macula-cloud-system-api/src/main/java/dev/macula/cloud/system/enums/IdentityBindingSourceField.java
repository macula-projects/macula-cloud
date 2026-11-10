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
 * 服务端核实的外部属性标识，区别于可由客户端提交的显示资料。
 * @author Rain
 * @since 6.1.0
 */
public enum IdentityBindingSourceField {
    MEMBER_ID("member_id", "成员 ID"), EMPLOYEE_NO("employee_no", "业务工号"),
    MOBILE("mobile", "手机号"), EMAIL("email", "邮箱");
    private final String value;
    private final String label;
    /** 定义稳定属性值和界面名称。 */
    IdentityBindingSourceField(String value, String label) {
        this.value = value;
        this.label = label;
    }
    /** 返回协议属性值，不使用枚举名持久化。 */
    public String value() { return value; }
    /** 返回管理员可读名称。 */
    public String label() { return label; }
    /** 拒绝未知属性及任意数据库表达式。 */
    public static IdentityBindingSourceField fromValue(String value) {
        for (var field : values()) { if (field.value.equals(value)) { return field; } }
        throw new IllegalArgumentException("请选择受支持的身份源字段");
    }
}
