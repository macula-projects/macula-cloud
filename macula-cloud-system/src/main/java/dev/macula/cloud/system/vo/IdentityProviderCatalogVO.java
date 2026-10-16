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

    /** 身份提供商分类元数据。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Category {
        private String code;
        private String label;
    }

    /** 单个身份提供商的管理端配置说明。 */
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

    /** 配置字段及其填写指引。 */
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
