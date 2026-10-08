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
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayDataSource;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置 TinyID 物理数据源以及发号和管理共用的动态路由数据源。
 *
 * @author Rain
 * @since 6.1.0
 */
@Configuration
public class DataSourceConfig {

    /**
     * 创建 TinyID 主数据源。
     *
     * @return Druid 主数据源
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.druid.master")
    public DataSource master() {
        return DruidDataSourceBuilder.create().build();
    }

    /**
     * 创建供发号链路使用的随机路由数据源。
     *
     * @param dataSources Spring 容器中的全部数据源
     * @return 动态路由数据源
     */
    @Bean
    @Primary
    @FlywayDataSource
    public DynamicDataSource getDynamicDataSource(List<DataSource> dataSources) {
        List<DataSource> physicalDataSources = physicalDataSources(dataSources);
        DynamicDataSource routingDataSource = new DynamicDataSource();
        List<String> dataSourceKeys = new ArrayList<>(physicalDataSources.size());
        Map<Object, Object> targetDataSources = new LinkedHashMap<>(physicalDataSources.size());
        for (int index = 0; index < physicalDataSources.size(); index++) {
            String key = "tinyid-" + index;
            dataSourceKeys.add(key);
            targetDataSources.put(key, physicalDataSources.get(index));
        }
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(physicalDataSources.get(0));
        routingDataSource.setDataSourceKeys(dataSourceKeys);
        return routingDataSource;
    }

    /**
     * 通过动态数据源逐库执行迁移，保证同次迁移的所有连接固定在同一物理库。
     * 任一库迁移失败时终止启动，避免未初始化的库进入随机发号池。
     *
     * @param dataSource 发号与迁移共用的动态数据源
     * @return 按物理数据源顺序执行的迁移策略
     */
    @Bean
    public FlywayMigrationStrategy tinyIdMigrationStrategy(DynamicDataSource dataSource) {
        return flyway -> {
            for (int sequence = 0; sequence < dataSource.size(); sequence++) {
                dataSource.execute(sequence, () -> { flyway.migrate(); });
            }
        };
    }

    /**
     * 按 Spring 注入顺序从数据源集合中提取物理 Druid 数据源。
     *
     * @param dataSources Spring 容器中的全部数据源
     * @return 有序物理数据源列表
     */
    static List<DataSource> physicalDataSources(List<DataSource> dataSources) {
        List<DataSource> physical = dataSources.stream()
            .filter(dataSource -> dataSource instanceof DruidDataSource)
            .toList();
        if (physical.isEmpty()) {
            throw new IllegalStateException("TinyID requires at least one physical datasource");
        }
        return List.copyOf(physical);
    }
}
