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

package tech.powerjob.server.persistence.config;

import com.google.common.collect.Maps;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 多重数据源配置
 *
 * @author Kung Yao
 * @since 2020/4/27
 */
@Component
@ConfigurationProperties("spring.datasource")
public class MultiDatasourceProperties {

    private DataSourceProperties remote = new DataSourceProperties();

    private DataSourceProperties local = new DataSourceProperties();

    public DataSourceProperties getLocal() {
        return local;
    }

    public void setLocal(DataSourceProperties local) {
        this.local = local;
    }

    public DataSourceProperties getRemote() {
        return remote;
    }

    public void setRemote(DataSourceProperties remote) {
        this.remote = remote;
    }

    public static class DataSourceProperties {

        private HibernateProperties hibernate = new HibernateProperties();

        public void setHibernate(HibernateProperties hibernate) {
            this.hibernate = hibernate;
        }

        public HibernateProperties getHibernate() {
            return hibernate;
        }
    }

    public static class HibernateProperties {

        private Map<String, String> properties = Maps.newHashMap();

        public void setProperties(Map<String, String> properties) {
            this.properties = properties;
        }

        public Map<String, String> getProperties() {
            return properties;
        }
    }
}
