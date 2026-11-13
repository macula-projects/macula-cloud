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
package dev.macula.cloud.system.protocol;

import dev.macula.cloud.system.protocol.oauth2.OAuth2ProtocolSettings;

import java.util.List;
import java.util.Set;

/**
 * 当前实际支持的协议注册表；通用层不定义grant或Scope。
 * @author Rain
 * @since 6.1.0
 */
public final class ApplicationProtocolRegistry {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_CONFIG_BYTES = 32 * 1024;
    public static final String OIDC = "OIDC";
    public static final String OAUTH2_1 = "OAUTH2_1";
    public static final List<String> PROTOCOLS = List.of(OIDC, OAUTH2_1);

    /** 注册表是固定白名单，不可从用户载荷注册Java类型。 */
    private ApplicationProtocolRegistry() { }

    /** 校验信封与类型，关闭的草稿也不能携带未知版本或类型。 */
    public static void require(ApplicationProtocolConfiguration configuration) {
        if (configuration == null || !PROTOCOLS.contains(configuration.protocol())
            || configuration.schemaVersion() != SCHEMA_VERSION
            || !(configuration.settings() instanceof OAuth2ProtocolSettings)) {
            throw new IllegalArgumentException("协议或配置版本不受支持");
        }
    }

    /** 统一识别OAuth秘密依赖，包含禁用但保留的配置。 */
    public static boolean requiresSecret(ApplicationProtocolConfiguration configuration) {
        return OAuth2ProtocolConfiguration.csv(OAuth2ProtocolConfiguration.settings(configuration)
            .getClientAuthenticationMethods()).stream().anyMatch(value -> value.startsWith("client_secret_"));
    }

    /** 选择管理端点调用方的固定认证配置，不读取默认协议或目标令牌。 */
    public static ApplicationProtocolConfiguration management(List<ApplicationProtocolConfiguration> configurations) {
        for (String protocol : List.of(OAUTH2_1, OIDC)) {
            for (ApplicationProtocolConfiguration configuration : configurations) {
                require(configuration);
                if (configuration.enabled() && protocol.equals(configuration.protocol())) {
                    Set<String> methods = OAuth2ProtocolConfiguration.csv(
                        OAuth2ProtocolConfiguration.settings(configuration).getClientAuthenticationMethods());
                    if (!methods.isEmpty() && !methods.contains("none")) {
                        return configuration;
                    }
                }
            }
        }
        throw new IllegalArgumentException("没有可用于令牌管理的机密认证配置");
    }
}
