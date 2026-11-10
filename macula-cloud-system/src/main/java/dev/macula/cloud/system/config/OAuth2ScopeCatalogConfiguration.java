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

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 启用受控Scope目录属性，不引入平行配置存储。
 * @author Rain
 * @since 6.1.0
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({OAuth2ScopeCatalogProperties.class, PlatformOAuthClientProperties.class})
public class OAuth2ScopeCatalogConfiguration {
    /** Flyway之后使用代理服务启动认领，事务覆盖两个平台身份。 */
    @org.springframework.context.annotation.Bean
    org.springframework.boot.ApplicationRunner platformClients(
        dev.macula.cloud.system.service.impl.PlatformOAuthClientProvisioningService service, PlatformOAuthClientProperties properties) {
        return args -> service.initialize(properties);
    }
}
