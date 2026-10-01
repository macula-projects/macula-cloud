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

package dev.macula.cloud.tinyid.config;

import com.alibaba.druid.pool.DruidDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configures TinyID's Druid master datasource and dynamic routing datasource.
 *
 * @author du_imba
 * @since 6.1.0
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.druid.master")
    public DataSource master() {
        return new DruidDataSource();
    }

    @Bean
    @Primary
    public DataSource getDynamicDataSource(List<DataSource> dataSources) {
        DynamicDataSource routingDataSource = new DynamicDataSource();

        List<String> dataSourceKeys = new ArrayList<>();
        Map<Object, Object> targetDataSources = new HashMap<>(4);

        // 添加多个数据源
        for (DataSource dataSource : dataSources) {
            if (dataSource instanceof DruidDataSource) {
                String name = ((DruidDataSource)dataSource).getName();
                targetDataSources.put(name, dataSource);
                dataSourceKeys.add(name);
            }
        }

        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDataSourceKeys(dataSourceKeys);

        return routingDataSource;
    }

}
