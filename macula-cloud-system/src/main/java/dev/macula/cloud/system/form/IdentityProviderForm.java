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
package dev.macula.cloud.system.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 身份提供商新增及修改表单；空密钥表示保留原值。
 *
 * @author Rain
 * @since 2026-10-13
 */
@Data
public class IdentityProviderForm {
    @NotBlank @Size(max = 100)
    private String name;
    @NotBlank
    private String providerType;
    private String sourceType;
    private String credentialType;
    private java.util.Set<String> loginCapabilities;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private boolean loginCapabilitiesPresent;
    /** 旧PUT省略能力时保留；显式空集合关闭全部，null拒绝。 */
    public void setLoginCapabilities(java.util.Set<String> value) {
        loginCapabilities = value;
         loginCapabilitiesPresent = true;
    }

    /** 同一JSON请求不能用重复键覆盖先前能力选择。 */
    @com.fasterxml.jackson.annotation.JsonSetter("loginCapabilities")
    public void readLoginCapabilities(java.util.Set<String> value) {
        if (loginCapabilitiesPresent) { throw new IllegalArgumentException("能力字段重复"); }
        setLoginCapabilities(value);
    }
    private String identityFlow;
    private String directoryPolicy;
    private String organizationId;
    private String oauthRedirectUri;
    private Boolean autoBindEnabled;
    private java.util.List<dev.macula.cloud.system.enums.IdentityBindingMappings.Entry> bindingMappings;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private boolean bindingMappingsPresent;
    /** 省略保留，显式空集合清空，null不能与省略混同。 */
    public void setBindingMappings(java.util.List<dev.macula.cloud.system.enums.IdentityBindingMappings.Entry> value) {
        bindingMappings = value;
        bindingMappingsPresent = true;
    }
    /** 同一请求不能通过重复顶层字段替换整组规则。 */
    @com.fasterxml.jackson.annotation.JsonSetter("bindingMappings")
    public void readBindingMappings(java.util.List<dev.macula.cloud.system.enums.IdentityBindingMappings.Entry> value) {
        if (bindingMappingsPresent) { throw new IllegalArgumentException("绑定规则集合重复"); }
        setBindingMappings(value);
    }
    private String bindingRule;
    private String bindingSourceField;
    private String bindingTargetField;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private boolean bindingSourceFieldPresent;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private boolean bindingTargetFieldPresent;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private boolean bindingRulePresent;
    /** 记录字段省略与显式清空的差别，旧客户端不得意外删除新映射。 */
    public void setBindingSourceField(String value) {
        bindingSourceField = value;
        bindingSourceFieldPresent = true;
    }
    /** 目标字段必须与源字段一同提交，不能半组清空。 */
    public void setBindingTargetField(String value) {
        bindingTargetField = value;
        bindingTargetFieldPresent = true;
    }
    /** 旧字段显式null也属于客户端写入意图，不能与省略混同。 */
    public void setBindingRule(String value) {
        bindingRule = value;
        bindingRulePresent = true;
    }
    private Long version;
    @NotBlank
    private String category;
    @Size(max = 500)
    private String remark;
    private Map<String, Object> config;
    private Boolean enabled;
    private Boolean visible;
    /** JSON写入只允许该字段出现一次，避免重复键改变匹配规则。 */
    @com.fasterxml.jackson.annotation.JsonSetter("bindingSourceField")
    public void readBindingSourceField(String value) {
        if (bindingSourceFieldPresent) { throw new IllegalArgumentException("绑定字段重复"); }
        setBindingSourceField(value);
    }
    /** JSON写入只允许该字段出现一次，避免重复键改变匹配规则。 */
    @com.fasterxml.jackson.annotation.JsonSetter("bindingTargetField")
    public void readBindingTargetField(String value) {
        if (bindingTargetFieldPresent) { throw new IllegalArgumentException("绑定字段重复"); }
        setBindingTargetField(value);
    }
    /** JSON写入只允许该字段出现一次，避免重复键改变匹配规则。 */
    @com.fasterxml.jackson.annotation.JsonSetter("bindingRule")
    public void readBindingRule(String value) {
        if (bindingRulePresent) { throw new IllegalArgumentException("绑定字段重复"); }
        setBindingRule(value);
    }
    /** 管理接口拒绝未知顶层字段，固定映射不能被任意数据库列或表达式扩展。 */
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("未知身份源字段");
    }

}
