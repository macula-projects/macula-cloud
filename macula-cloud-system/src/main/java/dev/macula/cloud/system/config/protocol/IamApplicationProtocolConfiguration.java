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
package dev.macula.cloud.system.config.protocol;

import dev.macula.cloud.system.protocol.IamApplicationProtocolRegistry;
import dev.macula.cloud.system.protocol.oauth2.OAuth2ProtocolDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 发布侧协议策略的装配点，与用户登录认证配置分离。
 * @author Rain
 * @since 6.1.0
 */
@Configuration(proxyBeanMethods = false)
public class IamApplicationProtocolConfiguration {
    /** 只登记已实现的协议，公共应用服务只依赖注册表端口。 */
    @Bean
    IamApplicationProtocolRegistry iamApplicationProtocolRegistry() {
        return OAuth2ProtocolDefinition.registry();
    }
}
