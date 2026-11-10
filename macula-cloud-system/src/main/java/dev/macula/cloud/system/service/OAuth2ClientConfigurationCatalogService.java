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
package dev.macula.cloud.system.service;

import dev.macula.cloud.system.vo.OAuth2ClientConfigurationCatalogVO;
import java.util.Set;

/**
 * 配置目录和Scope服务端白名单使用同一份受控声明。
 * @author Rain
 * @since 6.1.0
 */
public interface OAuth2ClientConfigurationCatalogService {
    /** 返回不含凭据的配置能力目录。 */
    OAuth2ClientConfigurationCatalogVO catalog();
    /** 返回该协议/应用/授权方式实际允许的Scope。 */
    Set<String> allowedScopes(String protocolMode, String applicationType, Set<String> grants);
    /** 将获准 Scope 映射到受信任资源，未知项拒绝。 */
    default java.util.Map<String, String> resourcesForScopes(java.util.Set<String> scopes) {
        java.util.Map<String, String> result = new java.util.TreeMap<>();
        for (String scope : scopes) {
            if ("profile".equals(scope)) {
                result.put(scope, dev.macula.cloud.system.security.OAuth2ResourcePolicy.PROFILE_RESOURCE);
            } else if ("system:access".equals(scope)) {
                result.put(scope, dev.macula.cloud.system.security.OAuth2ResourcePolicy.SYSTEM_RESOURCE);
            } else if (!"openid".equals(scope)) {
                throw new IllegalArgumentException("Scope没有已登记的目标资源");
            }
        }
        return java.util.Map.copyOf(result);
    }
}
