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
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证发号随机路由与管理显式路由使用同一有序物理数据源集合。
 *
 * @author Rain
 * @since 6.1.0
 */
class DataSourceConfigTest {

    /** 验证单数据源路由可同时支持随机发号和管理显式选库。 */
    @Test
    void supportsRandomAndExplicitRoutingForSingleDatasource() {
        DynamicDataSource routing = new DataSourceConfig().getDynamicDataSource(List.of(new DruidDataSource()));

        assertEquals(1, routing.size());
        assertEquals("tinyid-0", routing.determineCurrentLookupKey());
        assertEquals("tinyid-0", routing.execute(0, routing::determineCurrentLookupKey));
    }

    /** 验证物理库保持注入顺序且管理操作可按列表下标显式选库。 */
    @Test
    void keepsPhysicalOrderForExplicitManagementRouting() {
        DataSourceConfig config = new DataSourceConfig();
        DruidDataSource master = new DruidDataSource();
        DruidDataSource replica = new DruidDataSource();
        DynamicDataSource routing = new DynamicDataSource();

        List<DataSource> sources = List.of(replica, routing, master);
        DynamicDataSource configuredRouting = config.getDynamicDataSource(sources);
        assertEquals(2, configuredRouting.size());
        assertEquals("tinyid-0", configuredRouting.execute(0, configuredRouting::determineCurrentLookupKey));
        assertEquals("tinyid-1", configuredRouting.execute(1, configuredRouting::determineCurrentLookupKey));
        for (int index = 0; index < 20; index++) {
            assertTrue(List.of("tinyid-0", "tinyid-1").contains(configuredRouting.determineCurrentLookupKey()));
        }
    }

    /** 验证没有物理 Druid 数据源时拒绝启动。 */
    @Test
    void rejectsEmptyPhysicalDatasourceList() {
        DataSource nonDruid = new AbstractDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                throw new SQLException("not used");
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                throw new SQLException("not used");
            }
        };

        assertThrows(IllegalStateException.class,
            () -> DataSourceConfig.physicalDataSources(List.of(nonDruid)));
    }
}
