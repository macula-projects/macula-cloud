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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 保护全部Mapper及profile，避免改名或新增Mapper后重新输出凭据参数。
 * @author Rain
 * @since 6.1.0
 */
class SystemLoggingConfigurationTest {
    private static final String MAPPERS = "logging.level.dev.macula.cloud.system.mapper";

    /** 包级INFO覆盖应用DEBUG；任何profile都不能重新启用Mapper参数日志。 */
    @Test
    void mapperPackagesStayAtInfoAcrossProfiles() throws Exception {
        List<PropertySource<?>> sources = sources();
        assertEquals("info", sources.get(0).getProperty(MAPPERS));
        for (PropertySource<?> source : sources) {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                for (String key : enumerable.getPropertyNames()) {
                    if (key.equals(MAPPERS) || key.startsWith(MAPPERS + ".")) {
                        String level = String.valueOf(source.getProperty(key));
                        assertFalse("debug".equalsIgnoreCase(level) || "trace".equalsIgnoreCase(level), key);
                    }
                    if (key.endsWith("log-impl")) {
                        assertFalse(String.valueOf(source.getProperty(key)).contains("StdOutImpl"), key);
                    }
                }
            }
        }
    }

    /** 读取Maven过滤后的发布配置，避免测试同名资源遮蔽及原始profile占位符。 */
    private List<PropertySource<?>> sources() throws Exception {
        return new YamlPropertySourceLoader().load("system", new FileSystemResource("target/classes/application.yml"));
    }
}
