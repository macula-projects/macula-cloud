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

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 单组外部属性到IAM账号字段的受控映射，兼容原三种固定规则。
 * @author Rain
 * @since 6.1.0
 */
public record IdentityBindingMapping(IdentityBindingSourceField source, IdentityBindingTargetField target) {
    /** 拒绝半组及类型不适用的组合。 */
    public IdentityBindingMapping {
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(target, "target is required");
        if (!targets(source).contains(target)) { throw new IllegalArgumentException("不支持此账号字段映射"); }
    }
    /** 返回源属性语义允许的目标字段。 */
    public static List<IdentityBindingTargetField> targets(IdentityBindingSourceField source) {
        return switch (source) {
            case MEMBER_ID, EMPLOYEE_NO -> List.of(IdentityBindingTargetField.USERNAME);
            case MOBILE -> List.of(IdentityBindingTargetField.MOBILE, IdentityBindingTargetField.USERNAME);
            case EMAIL -> List.of(IdentityBindingTargetField.EMAIL, IdentityBindingTargetField.USERNAME);
        };
    }
    /** 解析一组明确字段；两个空值表示不配置，半组拒绝。 */
    public static IdentityBindingMapping of(String source, String target) {
        if (source == null && target == null) { return null; }
        return new IdentityBindingMapping(IdentityBindingSourceField.fromValue(source), IdentityBindingTargetField.fromValue(target));
    }
    /** 只转换原合同内三种旧规则，未知值拒绝。 */
    public static IdentityBindingMapping legacy(String value) {
        if (value == null) { return null; }
        return switch (IdentityBindingRule.fromValue(value)) {
            case MEMBER_ID_TO_USERNAME -> of("member_id", "username");
            case EMPLOYEE_NO_TO_USERNAME -> of("employee_no", "username");
            case VERIFIED_MOBILE_TO_MOBILE -> of("mobile", "mobile");
        };
    }
    /** 新旧字段并存时必须语义相同，不能只取其中一组。 */
    public static IdentityBindingMapping resolve(String source, String target, String legacy) {
        var mapping = of(source, target);
        var previous = legacy(legacy);
        if (mapping != null && previous != null && !mapping.equals(previous)) {
            throw new IllegalArgumentException("新旧账号绑定规则不一致");
        }
        return mapping == null ? previous : mapping;
    }
    /** 仅能表达旧规则时返回旧值，不能把新组合伪装成旧规则。 */
    public String legacyValue() {
        for (var rule : IdentityBindingRule.values()) { if (equals(legacy(rule.value()))) { return rule.value(); } }
        return null;
    }
    /** 按来源能力收窄全局组合，手机号证明不能绕过本地手机号核对。 */
    public void validateFor(IdentitySourceType type) {
        if (!type.bindingSourceFields().contains(source)
            || !type.enterprise() && target != IdentityBindingTargetField.MOBILE) {
            throw new IllegalArgumentException("该身份源不支持此账号字段映射");
        }
    }
    /** 读取核验属性；旧手机号证明仅兼容原已核验属性名。 */
    public String attribute(Map<String, String> attributes) {
        String value = attributes.get(source.value());
        return value == null && source == IdentityBindingSourceField.MOBILE ? attributes.get("verified_mobile") : value;
    }
}
