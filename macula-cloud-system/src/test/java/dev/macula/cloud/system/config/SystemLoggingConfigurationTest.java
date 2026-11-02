/*
 * Copyright (c) 2023 Macula
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

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证包含认证或密钥参数的 Mapper 不启用参数级 DEBUG 日志。
 *
 * @author Rain
 * @since 6.1.0
 */
class SystemLoggingConfigurationTest {

    private static final String LOGGER_PREFIX = "logging.level.dev.macula.cloud.system.mapper.";

    @Test
    void sensitiveMapperLoggersStayAtInfo() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
            .load("system", new ClassPathResource("application.yml"));

        assertEquals("info", property(sources, "SysOAuth2ClientConfigMapper"));
        assertEquals("info", property(sources, "SysUserMapper"));
        assertEquals("info", property(sources, "SysTenantUserMapper"));
    }

    private String property(List<PropertySource<?>> sources, String loggerName) {
        return sources.stream().map(source -> source.getProperty(LOGGER_PREFIX + loggerName))
            .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }
}
