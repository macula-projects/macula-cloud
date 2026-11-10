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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 管理端可用的身份源分类、类型及字段元数据，不包含任何配置值或凭据。
 *
 * @author Rain
 * @since 2026-10-14
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdentityProviderCatalogVO {
    private List<Category> categories;
    private List<Provider> providers;
    private List<Source> sources;
    private List<Credential> credentials;
    /** 保留旧目录构造器，能力元数据由服务端统一追加。 */
    public IdentityProviderCatalogVO(List<Category> categories, List<Provider> providers, List<Source> sources) {
        this.categories = categories;
         this.providers = providers;
         this.sources = sources;
    }
    /**
     * 真实注册只填写一套凭据，微信各注册独立。
     * @since 6.1.0
     */
    public record Credential(String code, String label, String providerType, String sourceType,
        List<Field> fields, List<LoginCapability> capabilities) { }
    /**
     * 不暴露内部flow或密钥的能力目录。
     * @since 6.1.0
     */
    public record LoginCapability(String code, String label, String scene, boolean browser,
        String codeType, String mode, java.util.Set<String> environments, boolean available, String reason) { }


    /**
     * 具体来源独立字段及自动匹配能力。
     * @since 6.1.0
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Source {
        private String code;
        private String label;
        private String providerType;
        private String scene;
        private List<Field> fields;
        private java.util.Set<String> identityFields;
        private List<BindingRule> bindingRules;
        private String autoBindUnavailableReason;
        private List<Flow> flows;
        private List<BindingMapping> bindingMappings;
        private boolean defaultAvailable;
        private String unavailableReason;
        private java.util.Set<String> directoryPolicies;

        /** 保留现有目录字段构造，新能力由同一受控目录追加。 */
        public Source(String code, String label, String providerType, String scene, List<Field> fields,
            java.util.Set<String> identityFields, List<BindingRule> bindingRules, String autoBindUnavailableReason) {
            this.code = code;
            this.label = label;
            this.providerType = providerType;
            this.scene = scene;
            this.fields = fields;
            this.identityFields = identityFields;
            this.bindingRules = bindingRules;
            this.autoBindUnavailableReason = autoBindUnavailableReason;
        }
    }

    /**
     * 来源允许的一组源/目标字段，权限仅为使用前提而不是授权证明。
     * @since 6.1.0
     */
    public record BindingMapping(String sourceField, String sourceLabel, String targetField,
        String targetLabel, String prerequisite) { }

    /**
     * 实际运行合同及可用性，未核实项只用于解释已有记录。
     * @since 6.1.0
     */
    public record Flow(String value, String label, boolean available, boolean providerPkce, String reason) { }

    /**
     * 已核验属性到本地标识的绑定依据。
     * @since 6.1.0
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BindingRule {
        private String value;
        private String label;
        private String prerequisite;
    }

    /**
     * 身份提供商分类元数据。
     * @since 6.1.0
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Category {
        private String code;
        private String label;
    }

    /**
     * 单个身份提供商的管理端配置说明。
     * @since 6.1.0
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Provider {
        private String code;
        private String label;
        private String category;
        private String callbackUrl;
        private String createUrl;
        private String createHint;
        private List<Field> fields;
    }

    /**
     * 配置字段及其填写指引。
     * @since 6.1.0
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Field {
        private String key;
        private String label;
        private boolean required;
        private boolean secret;
        private boolean multiline;
        private String help;
    }
}
