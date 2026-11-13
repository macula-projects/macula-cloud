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
package dev.macula.cloud.system.integration;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import dev.macula.cloud.system.converter.IdentityProviderConverter;
import dev.macula.cloud.system.form.IdentityProviderForm;
import dev.macula.cloud.system.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.system.mapper.SysIamApplicationIdentityProviderMapper;
import dev.macula.cloud.system.service.impl.IdentityProviderServiceImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mapstruct.factory.Mappers;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * 使用真实Mapper、服务和两个事务证明能力更新CAS不会丢失并发修改。
 * @author Rain
 * @since 6.1.0
 */
@EnabledIfEnvironmentVariable(named = "IDENTITY_CAPABILITIES_IT", matches = "true")
class IdentitySourceCapabilitiesConcurrencyIT {
    /** 两个请求读同一版本后仅一个提交，失败请求不覆盖胜者能力或凭据。 */
    @Test
    void concurrentCapabilityUpdatesHaveExactlyOneWinner() throws Exception {
        String url=System.getenv("IAM_SOURCE_IT_URL");
        assertTrue(url.startsWith("jdbc:mysql://127.0.0.1:"));
        assertTrue(URI.create(url.substring(5)).getPath().startsWith("/macula_identity_binding_it_"));
        var ds=new DriverManagerDataSource(url,System.getenv("IAM_SOURCE_IT_USER"),System.getenv("IAM_SOURCE_IT_PASSWORD"));
        var jdbc=new JdbcTemplate(ds);
        assertEquals("23",jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1",String.class));
        jdbc.update("INSERT INTO sys_identity_provider(id,name,provider_type,source_type,credential_type,identity_flow,capabilities_json,directory_policy,organization_id,app_id,app_secret,config_json,enabled,visible,version) "
            + "VALUES(96900,'concurrency-cap','WECOM','WECOM_WEB','WECOM_APPLICATION','WECOM_WEB_V1','{\"WECOM_BROWSER\":\"WECOM_WEB_V1\"}','IDENTITY_ONLY','corp','corp','fixture-secret','{\"corpId\":\"corp\",\"agentId\":\"1001\"}',1,1,0)");
        var configuration=new MybatisConfiguration();
         configuration.setMapUnderscoreToCamelCase(true);
        var factory=new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setConfiguration(configuration);
        SqlSessionFactory sqlFactory=factory.getObject();
        sqlFactory.getConfiguration().addMapper(SysIdentityProviderMapper.class);
        var session=new SqlSessionTemplate(sqlFactory);
        var real=session.getMapper(SysIdentityProviderMapper.class);
        var mapper=mock(SysIdentityProviderMapper.class,delegatesTo(real));
        var ready=new CountDownLatch(2);
        var release=new CountDownLatch(1);
        doAnswer(call->{var row=real.selectById((Long)call.getArgument(0));
        ready.countDown();
        assertTrue(release.await(10,TimeUnit.SECONDS));
        return row;
        })
            .when(mapper).selectById(96900L);
        var service=new IdentityProviderServiceImpl(mapper,Mappers.getMapper(IdentityProviderConverter.class),mock(SysIamApplicationIdentityProviderMapper.class));
        var transactions=new TransactionTemplate(new DataSourceTransactionManager(ds));
        var executor=Executors.newFixedThreadPool(2);
        try {
            var first=executor.submit(()->save(transactions,service,Set.of("WECOM_BROWSER","WECOM_MINIAPP")));
            var second=executor.submit(()->save(transactions,service,Set.of("WECOM_BROWSER","WECOM_WEBVIEW")));
            assertTrue(ready.await(10,TimeUnit.SECONDS));
            release.countDown();
            assertEquals(Set.of(200,409),Set.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS)));
            assertEquals(1L,jdbc.queryForObject("SELECT version FROM sys_identity_provider WHERE id=96900",Long.class));
            assertEquals(2,jdbc.queryForObject("SELECT JSON_LENGTH(capabilities_json) FROM sys_identity_provider WHERE id=96900",Integer.class));
            assertEquals("fixture-secret",jdbc.queryForObject("SELECT app_secret FROM sys_identity_provider WHERE id=96900",String.class));
        } finally {release.countDown();
        executor.shutdownNow();
        jdbc.update("DELETE FROM sys_identity_provider WHERE id=96900");
        }
    }
    /** 使用生产服务校验与更新，不复刻CAS SQL。 */
    private int save(TransactionTemplate transactions,IdentityProviderServiceImpl service,Set<String> capabilities) {
        try {
            transactions.execute(status->{var form=new IdentityProviderForm();
            form.setName("concurrency-cap");
            form.setProviderType("WECOM");
                form.setSourceType("WECOM_WEB");
                form.setCategory("enterprise");
                form.setEnabled(true);
                form.setVisible(true);
                form.setVersion(0L);
                form.setConfig(Map.of("corpId","corp","agentId","1001","appSecret",""));
                form.setLoginCapabilities(capabilities);
                service.save(96900L,form);
                return null;
                });
                return 200;
        } catch(ResponseStatusException ex) {return ex.getStatusCode().value();}
    }
}
