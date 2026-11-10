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
 * 服务端核验属性到本地账号标识的受控匹配规则。
 * @author Rain
 * @since 6.1.0
 */
public enum IdentityBindingRule {
    MEMBER_ID_TO_USERNAME("MEMBER_ID_TO_USERNAME", "成员 UserID → 本地用户名", "member_id", "企业成员身份权限"),
    EMPLOYEE_NO_TO_USERNAME("EMPLOYEE_NO_TO_USERNAME", "员工编号 → 本地用户名", "employee_no", "员工编号读取权限"),
    VERIFIED_MOBILE_TO_MOBILE("VERIFIED_MOBILE_TO_MOBILE", "已核验手机号 → 本地手机号", "verified_mobile", "手机号授权或号码认证权限");
    private final String value;
    private final String label;
    private final String attribute;
    private final String prerequisite;
    /** 定义受支持的属性证明与管理员可读说明。 */
    IdentityBindingRule(String value, String label, String attribute, String prerequisite) {
        this.value = value;
        this.label = label;
        this.attribute = attribute;
        this.prerequisite = prerequisite;
    }
    /** 返回稳定规则值。 */
    public String value() { return value; }
    /** 返回界面说明。 */
    public String label() { return label; }
    /** 返回核验结果中的属性键。 */
    public String attribute() { return attribute; }
    /** 返回平台权限前提。 */
    public String prerequisite() { return prerequisite; }
    /** 解析明确配置的规则，拒绝任意字段表达式。 */
    public static IdentityBindingRule fromValue(String value) {
        for (IdentityBindingRule rule : values()) {
            if (rule.value.equals(value)) { return rule; }
        }
        throw new IllegalArgumentException("请选择受支持的绑定依据");
    }
}
