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
package dev.macula.cloud.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 资源服务负责登记的业务Scope目录，空目录不代表授权任意scope。
 * @author Rain
 * @since 6.1.0
 */
@Data
@ConfigurationProperties("macula.iam.scopes")
public class OAuth2ScopeCatalogProperties {
    private List<Scope> business = new ArrayList<>();
    /**
     * 明确的资源权限声明，不从URL或角色推断。
     * @since 6.1.0
     */
    @Data
    public static class Scope {
        private String id;
        private String resource;
        private String name;
        private String description;
        private Set<String> applicationTypes = Set.of();
        private Set<String> grants = Set.of();
    }
}
