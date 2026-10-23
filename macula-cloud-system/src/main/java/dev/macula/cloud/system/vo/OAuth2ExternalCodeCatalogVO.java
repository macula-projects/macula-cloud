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
import java.util.Set;

/**
 * OAuth Client 外部凭证类型及可绑定身份源的非敏感目录。
 *
 * @author Rain
 * @since 6.1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2ExternalCodeCatalogVO {
    private List<CodeType> codeTypes;
    private List<ProviderRegistration> providerRegistrations;

    /**
     * 外部凭证稳定类型元数据。
     *
     * @since 6.1.0
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodeType {
        private String value;
        private String providerType;
        private String scene;
        private String resultType;
        private String capability;
    }

    /**
     * 可由 OAuth Client 固定绑定的身份源配置摘要。
     *
     * @since 6.1.0
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderRegistration {
        private Long id;
        private String name;
        private String providerType;
        private Set<String> capabilities;
    }
}
