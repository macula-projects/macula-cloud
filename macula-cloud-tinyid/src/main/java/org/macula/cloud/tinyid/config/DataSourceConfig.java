package org.macula.cloud.tinyid.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * @author du_imba
 */
@Configuration
public class DataSourceConfig {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceConfig.class);

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.druid.master")
    public DataSource master() {
        return DruidDataSourceBuilder.create().build();
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
                String name = ((DruidDataSource) dataSource).getName();
                targetDataSources.put(name, dataSource);
                dataSourceKeys.add(name);
            }
        }

        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDataSourceKeys(dataSourceKeys);

        return routingDataSource;
    }

}
