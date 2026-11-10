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

import java.util.List;
import java.util.Set;

/**
 * 接入应用可选协议、真实Scope及具体身份源，不包含凭据。
 * @author Rain
 * @since 6.1.0
 */
public record OAuth2ClientConfigurationCatalogVO(List<Protocol> protocols, List<Scope> scopes, List<Source> sources) {
    /**
     * 协议选项。
     * @since 6.1.0
     */
    public record Protocol(String value, String label) { }
    /**
     * 实际实现或已登记的Scope。
     * @since 6.1.0
     */
    public record Scope(String value, String label, String description, boolean oidcOnly,
                        Set<String> applicationTypes, Set<String> grants) { }
    /**
     * 已配置来源及端侧语义能力。
     * @since 6.1.0
     */
    public record Source(Long id, String name, String providerType, String sourceType, String label,
                         String scene, boolean browser, List<Capability> capabilities, boolean available,
                         String unavailableReason, String flow, boolean providerPkce, String providerRedirectUri,
                         Set<String> browserApplicationTypes, Set<String> browserEnvironments) {
        /** 兼容旧内部调用；正式目录总是携带适用架构和环境。 */
        public Source(Long id, String name, String providerType, String sourceType, String label,
            String scene, boolean browser, List<Capability> capabilities, boolean available,
            String unavailableReason, String flow, boolean providerPkce, String providerRedirectUri) {
            this(id, name, providerType, sourceType, label, scene, browser, capabilities, available,
                unavailableReason, flow, providerPkce, providerRedirectUri, Set.of(), Set.of());
        }
    }
    /**
     * 对用户展示语义名称，底层code仅用于稳定接口映射。
     * @since 6.1.0
     */
    public record Capability(String codeType, String label, String scene, String resultType, Set<String> applicationTypes,
        String loginCapability, boolean browser, Set<String> environments, String flow, boolean providerPkce,
        String providerRedirectUri, boolean available) {
        /** 旧目录只描述直接Code；正式能力目录包含网页及各自合同。 */
        public Capability(String codeType, String label, String scene, String resultType, Set<String> applicationTypes) {
            this(codeType, label, scene, resultType, applicationTypes, null, false, Set.of(), null, false, null, true);
        }
        /** 兼容旧内部调用；正式目录始终提供共享白名单产生的应用类型。 */
        public Capability(String codeType, String label, String scene, String resultType) {
            this(codeType, label, scene, resultType, Set.of());
        }
    }
}
