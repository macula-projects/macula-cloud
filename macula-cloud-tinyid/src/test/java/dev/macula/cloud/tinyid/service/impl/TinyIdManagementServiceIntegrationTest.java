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
package dev.macula.cloud.tinyid.service.impl;

import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import dev.macula.cloud.tinyid.config.DynamicDataSource;
import dev.macula.cloud.tinyid.converter.TinyIdManagementConverter;
import dev.macula.cloud.tinyid.mapper.TinyIdAuditLogMapper;
import dev.macula.cloud.tinyid.mapper.TinyIdInfoMapper;
import dev.macula.cloud.tinyid.mapper.TinyIdTokenMapper;
import dev.macula.cloud.tinyid.service.TinyIdTokenService;
import dev.macula.cloud.tinyid.pojo.bo.TinyIdApplicationBO;
import org.flywaydb.core.Flyway;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Exercises aggregate business writes and global application synchronization on two MySQL databases.
 *
 * @author Rain
 * @since 6.1.0
 */
@Testcontainers(disabledWithoutDocker = true)
class TinyIdManagementServiceIntegrationTest {

    /** 第一套隔离 MySQL，模拟物理数据源列表的序号 0。 */
    @Container
    private static final MySQLContainer<?> MASTER = mysql("tinyid_it_master");

    /** 第二套隔离 MySQL，模拟物理数据源列表的序号 1。 */
    @Container
    private static final MySQLContainer<?> REPLICA = mysql("tinyid_it_replica");

    /** 验证 MyBatis-Plus Mapper 与 XML 无需连接数据库即可完成装配。 */
    @Test
    void loadsMybatisMapperDefinitions() {
        DataSource unused = new AbstractDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                throw new SQLException("Connection is not required while loading mapper definitions");
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                throw new SQLException("Connection is not required while loading mapper definitions");
            }
        };

        assertNotNull(managementService(unused));
    }

    @Test
    void createsBusinessAcrossAllSourcesAndSynchronizesApplicationCredentials() {
        DataSource master = dataSource(MASTER);
        DataSource replica = dataSource(REPLICA);
        migrate(master, true);
        migrate(replica, false);
        TinyIdManagementServiceImpl dao = managementService(master, replica);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String bizType = "it_" + suffix.substring(0, 20);
        String token = "test-token-" + suffix;

        try {
            assertThrows(IllegalArgumentException.class,
                () -> dao.createBusinessOnAllDataSources("too_small_" + bizType, 100, 1));
            dao.createBusinessOnAllDataSources(bizType, 100, 10);
            dao.createApplicationOnAllDataSources(token, "integration test application", List.of(bizType));

            TinyIdApplicationBO application = dao.getApplicationByToken(token);
            assertEquals(List.of(bizType), application.getBizTypes());
            assertEquals(List.of(0, 1), dao.getBusiness(bizType).getDataSources().stream()
                .map(item -> item.getRemainder()).toList());
            assertEquals("COMPLETE", dao.getBusiness(bizType).getConsistencyStatus());

            dao.deleteApplicationOnAllDataSources(token);
            assertEquals(0L, authorizationCount(master, token, bizType));
            assertEquals(0L, authorizationCount(replica, token, bizType));

            dao.createApplicationOnAllDataSources(token, "integration test application", List.of(bizType));
            new JdbcTemplate(replica).update("update tiny_id_info set max_id=100 where biz_type=?", bizType);
            assertThrows(IllegalStateException.class, () -> dao.deleteBusinessOnAllDataSources(bizType));
            assertEquals(1L, count(master, bizType));
            assertEquals(1L, count(replica, bizType));
            assertEquals(1L, authorizationCount(master, token, bizType));
            assertEquals(1L, authorizationCount(replica, token, bizType));

            new JdbcTemplate(replica).update("update tiny_id_info set max_id=0 where biz_type=?", bizType);
            dao.deleteBusinessOnAllDataSources(bizType);
            assertEquals(0L, count(master, bizType));
            assertEquals(0L, count(replica, bizType));
            assertEquals(0L, authorizationCount(master, token, bizType));
            assertEquals(0L, authorizationCount(replica, token, bizType));
        } finally {
            for (DataSource dataSource : List.of(master, replica)) {
                JdbcTemplate jdbc = new JdbcTemplate(dataSource);
                jdbc.update("delete from tiny_id_token where token=?", token);
                jdbc.update("delete from tiny_id_info where biz_type=?", bizType);
            }
        }
    }

    @Test
    void listsBusinessPresentOnlyOnLaterDatasourceAsPending() {
        DataSource master = dataSource(MASTER);
        DataSource replica = dataSource(REPLICA);
        migrate(master, true);
        migrate(replica, false);
        TinyIdManagementServiceImpl dao = managementService(master, replica);
        String bizType = "later_only_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        try {
            dao.createBusinessOnAllDataSources(bizType, 100, 10);
            new JdbcTemplate(master).update("delete from tiny_id_info where biz_type=?", bizType);

            var businesses = dao.listBusinesses(1, 20, bizType);

            assertEquals(1L, businesses.getTotal());
            assertEquals(1, businesses.getRecords().size());
            assertEquals(bizType, businesses.getRecords().get(0).getBizType());
            assertEquals("PENDING", businesses.getRecords().get(0).getConsistencyStatus());
        } finally {
            for (DataSource dataSource : List.of(master, replica)) {
                new JdbcTemplate(dataSource).update("delete from tiny_id_info where biz_type=?", bizType);
            }
        }
    }

    @Test
    void compensatesPartialBusinessCreationAndReportsCompensationFailure() {
        DataSource master = dataSource(MASTER);
        DataSource replica = dataSource(REPLICA);
        migrate(master, true);
        migrate(replica, false);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String compensatedBiz = "compensated_" + suffix;
        String inconsistentBiz = "inconsistent_" + suffix;

        try {
            DataSource failingReplica = failingDataSource(replica,
                sql -> normalized(sql).startsWith("insert into tiny_id_info"));
            TinyIdManagementServiceImpl compensatedDao = managementService(master, failingReplica);

            assertThrows(RuntimeException.class,
                () -> compensatedDao.createBusinessOnAllDataSources(compensatedBiz, 100, 10));
            assertEquals(0L, count(master, compensatedBiz));
            assertEquals(0L, count(replica, compensatedBiz));

            DataSource failingCompensationMaster = failingDataSource(master,
                sql -> normalized(sql).startsWith("delete from tiny_id_info"));
            TinyIdManagementServiceImpl inconsistentDao = managementService(failingCompensationMaster, failingReplica);

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> inconsistentDao.createBusinessOnAllDataSources(inconsistentBiz, 100, 10));
            assertEquals("TinyID cross-datasource business compensation failed", failure.getMessage());
            assertEquals(1L, count(master, inconsistentBiz));
            assertEquals(0L, count(replica, inconsistentBiz));
        } finally {
            for (DataSource dataSource : List.of(master, replica)) {
                JdbcTemplate jdbc = new JdbcTemplate(dataSource);
                jdbc.update("delete from tiny_id_info where biz_type in (?,?)", compensatedBiz, inconsistentBiz);
            }
        }
    }

    @Test
    void restoresDeletedApplicationWhenLaterDatasourceDeleteFails() {
        DataSource master = dataSource(MASTER);
        DataSource replica = dataSource(REPLICA);
        migrate(master, true);
        migrate(replica, false);
        TinyIdManagementServiceImpl setupDao = managementService(master, replica);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String bizType = "delete_restore_" + suffix.substring(0, 16);
        String token = "test-token-" + suffix;

        try {
            setupDao.createBusinessOnAllDataSources(bizType, 100, 10);
            setupDao.createApplicationOnAllDataSources(token, "delete restore test", List.of(bizType));
            DataSource failingReplica = failingDataSource(replica,
                sql -> normalized(sql).startsWith("delete from tiny_id_token"));
            TinyIdManagementServiceImpl failingDao = managementService(master, failingReplica);

            assertThrows(RuntimeException.class, () -> failingDao.deleteApplicationOnAllDataSources(token));
            assertEquals(1L, authorizationCount(master, token, bizType));
            assertEquals(1L, authorizationCount(replica, token, bizType));
        } finally {
            for (DataSource dataSource : List.of(master, replica)) {
                JdbcTemplate jdbc = new JdbcTemplate(dataSource);
                jdbc.update("delete from tiny_id_token where token=?", token);
                jdbc.update("delete from tiny_id_info where biz_type=?", bizType);
            }
        }
    }

    @Test
    void restoresUnusedBusinessAndAuthorizationsWhenLaterDatasourceDeleteFails() {
        DataSource master = dataSource(MASTER);
        DataSource replica = dataSource(REPLICA);
        migrate(master, true);
        migrate(replica, false);
        TinyIdManagementServiceImpl setupDao = managementService(master, replica);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String bizType = "biz_restore_" + suffix.substring(0, 16);
        String token = "test-token-" + suffix;

        try {
            setupDao.createBusinessOnAllDataSources(bizType, 100, 10);
            setupDao.createApplicationOnAllDataSources(token, "business restore test", List.of(bizType));
            DataSource failingReplica = failingDataSource(replica,
                sql -> normalized(sql).startsWith("delete from tiny_id_info"));
            TinyIdManagementServiceImpl failingDao = managementService(master, failingReplica);

            assertThrows(RuntimeException.class, () -> failingDao.deleteBusinessOnAllDataSources(bizType));
            assertEquals(1L, count(master, bizType));
            assertEquals(1L, count(replica, bizType));
            assertEquals(1L, authorizationCount(master, token, bizType));
            assertEquals(1L, authorizationCount(replica, token, bizType));
        } finally {
            for (DataSource dataSource : List.of(master, replica)) {
                JdbcTemplate jdbc = new JdbcTemplate(dataSource);
                jdbc.update("delete from tiny_id_token where token=?", token);
                jdbc.update("delete from tiny_id_info where biz_type=?", bizType);
            }
        }
    }

    @Test
    void compensatesOnlyNewAuthorizationWithoutDeletingPreexistingReplicaRow() {
        DataSource master = dataSource(MASTER);
        DataSource replica = dataSource(REPLICA);
        migrate(master, true);
        migrate(replica, false);
        TinyIdManagementServiceImpl dao = managementService(master, replica);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String baseBiz = "base_" + suffix;
        String addedBiz = "added_" + suffix;
        String token = "compensation-token-" + suffix;

        try {
            dao.createBusinessOnAllDataSources(baseBiz, 100, 10);
            dao.createBusinessOnAllDataSources(addedBiz, 100, 10);
            dao.createApplicationOnAllDataSources(token, "compensation test", List.of(baseBiz));
            new JdbcTemplate(replica).update(
                "insert into tiny_id_token(token,biz_type,remark,create_time,update_time) values(?,?,?,now(),now())",
                token, addedBiz, "compensation test");

            List<TinyIdManagementServiceImpl.AuthorizationInsert> inserted =
                dao.addAuthorizationsOnAllDataSources(token, "compensation test", List.of(addedBiz));
            assertEquals(List.of(new TinyIdManagementServiceImpl.AuthorizationInsert("datasource-0", addedBiz)), inserted);

            dao.deleteInsertedAuthorizations(token, inserted);

            assertEquals(0L, authorizationCount(master, token, addedBiz));
            assertEquals(1L, authorizationCount(replica, token, addedBiz));
        } finally {
            for (DataSource dataSource : List.of(master, replica)) {
                JdbcTemplate jdbc = new JdbcTemplate(dataSource);
                jdbc.update("delete from tiny_id_token where token=?", token);
                jdbc.update("delete from tiny_id_info where biz_type in (?,?)", baseBiz, addedBiz);
            }
        }
    }

    /**
     * 使用真实 MyBatis-Plus Mapper 创建多数据源管理服务。
     *
     * @param dataSources 有序物理数据源
     * @return 管理服务
     */
    private TinyIdManagementServiceImpl managementService(DataSource... dataSources) {
        try {
            DynamicDataSource routingDataSource = new DynamicDataSource();
            Map<Object, Object> targets = new LinkedHashMap<>();
            List<String> keys = java.util.stream.IntStream.range(0, dataSources.length)
                .mapToObj(index -> "tinyid-" + index).toList();
            for (int index = 0; index < dataSources.length; index++) {
                targets.put(keys.get(index), dataSources[index]);
            }
            routingDataSource.setTargetDataSources(targets);
            routingDataSource.setDefaultTargetDataSource(dataSources[0]);
            routingDataSource.setDataSourceKeys(keys);
            routingDataSource.afterPropertiesSet();

            MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
            factoryBean.setDataSource(routingDataSource);
            factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/*.xml"));
            factoryBean.afterPropertiesSet();
            SqlSessionFactory factory = factoryBean.getObject();
            registerMapper(factory, TinyIdInfoMapper.class);
            registerMapper(factory, TinyIdTokenMapper.class);
            registerMapper(factory, TinyIdAuditLogMapper.class);
            SqlSessionTemplate template = new SqlSessionTemplate(factory);
            return new TinyIdManagementServiceImpl(template.getMapper(TinyIdInfoMapper.class),
                template.getMapper(TinyIdTokenMapper.class), template.getMapper(TinyIdAuditLogMapper.class),
                routingDataSource, mock(TinyIdTokenService.class),
                Mappers.getMapper(TinyIdManagementConverter.class));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create TinyID MyBatis integration test fixture", ex);
        }
    }

    /**
     * 注册尚未通过 XML namespace 注册的 Mapper。
     *
     * @param factory MyBatis 会话工厂
     * @param mapperType Mapper 类型
     */
    private <T> void registerMapper(SqlSessionFactory factory, Class<T> mapperType) {
        if (!factory.getConfiguration().hasMapper(mapperType)) {
            factory.getConfiguration().addMapper(mapperType);
        }
    }

    private void migrate(DataSource dataSource, boolean master) {
        String[] locations = master
            ? new String[] {"classpath:db/migration", "classpath:db/master"}
            : new String[] {"classpath:db/migration"};
        Flyway.configure()
            .dataSource(dataSource)
            .locations(locations)
            .baselineOnMigrate(true)
            .baselineVersion("1")
            .validateOnMigrate(true)
            .cleanDisabled(true)
            .load()
            .migrate();
    }

    private long count(DataSource dataSource, String bizType) {
        Long value = new JdbcTemplate(dataSource).queryForObject(
            "select count(*) from tiny_id_info where biz_type=?", Long.class, bizType);
        return value == null ? 0 : value;
    }

    private long authorizationCount(DataSource dataSource, String token, String bizType) {
        Long value = new JdbcTemplate(dataSource).queryForObject(
            "select count(*) from tiny_id_token where token=? and biz_type=?", Long.class, token, bizType);
        return value == null ? 0 : value;
    }

    private DataSource failingDataSource(DataSource delegate, Predicate<String> failure) {
        return new AbstractDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return wrap(delegate.getConnection(), failure);
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return wrap(delegate.getConnection(username, password), failure);
            }
        };
    }

    private Connection wrap(Connection delegate, Predicate<String> failure) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                if (method.getName().startsWith("prepareStatement") && args != null && args.length > 0
                    && args[0] instanceof String sql && failure.test(sql)) {
                    throw new SQLException("Injected TinyID datasource failure");
                }
                try {
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException ex) {
                    throw ex.getCause();
                }
            });
    }

    private String normalized(String sql) {
        return sql.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private static MySQLContainer<?> mysql(String databaseName) {
        return new MySQLContainer<>(DockerImageName.parse("mysql:8.4.6"))
            .withDatabaseName(databaseName)
            .withUsername("tinyid_it")
            .withPassword("tinyid_it");
    }

    private DataSource dataSource(MySQLContainer<?> container) {
        return new DriverManagerDataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }
}
