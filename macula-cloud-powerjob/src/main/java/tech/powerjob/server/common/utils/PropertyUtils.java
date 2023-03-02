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

package tech.powerjob.server.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.InputStream;
import java.net.URL;
import java.util.Objects;
import java.util.Properties;

/**
 * 加载配置文件
 *
 * @author tjq
 * @since 2020/5/18
 */
@Slf4j
public class PropertyUtils {

    private static final Properties PROPERTIES = new Properties();

    public static Properties getProperties() {
        return PROPERTIES;
    }

    public static void init() {
        URL propertiesURL = PropertyUtils.class.getClassLoader().getResource("application.properties");
        Objects.requireNonNull(propertiesURL);
        try (InputStream is = propertiesURL.openStream()) {
            PROPERTIES.load(is);
        } catch (Exception e) {
            ExceptionUtils.rethrow(e);
        }
    }
}
