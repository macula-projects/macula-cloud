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

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.macula.cloud.tinyid.config.DynamicDataSource;
import dev.macula.cloud.tinyid.converter.TinyIdManagementConverter;
import dev.macula.cloud.tinyid.mapper.TinyIdAuditLogMapper;
import dev.macula.cloud.tinyid.mapper.TinyIdInfoMapper;
import dev.macula.cloud.tinyid.pojo.bo.TinyIdBusinessAggregateBO;
import dev.macula.cloud.tinyid.pojo.bo.TinyIdBusinessBO;
import dev.macula.cloud.tinyid.pojo.form.CreateBusinessForm;
import dev.macula.cloud.tinyid.pojo.query.BusinessPageQuery;
import dev.macula.cloud.tinyid.pojo.vo.TinyIdBusinessAggregateVO;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Tests management orchestration after persistence operations were merged into the service.
 *
 * @author Rain
 * @since 6.1.0
 */
class TinyIdManagementServiceImplTest {

    @Test
    void convertsMybatisPageWithoutCustomPageWrapper() {
        TinyIdManagementServiceImpl service = service();
        TinyIdBusinessAggregateBO business = new TinyIdBusinessAggregateBO("order", 100, 10, "COMPLETE", List.of());
        Page<TinyIdBusinessAggregateBO> source = new Page<>(2, 20, 21);
        source.setRecords(List.of(business));
        doReturn(source).when(service).listBusinesses(2, 20, "test");
        BusinessPageQuery query = new BusinessPageQuery();
        query.setPage(2);
        query.setPageSize(20);
        query.setKeywords("test");

        IPage<TinyIdBusinessAggregateVO> result = service.listBusinesses(query);

        assertEquals(2, result.getCurrent());
        assertEquals(20, result.getSize());
        assertEquals(21, result.getTotal());
        assertEquals("order", result.getRecords().get(0).getBizType());
    }

    @Test
    void createsBusinessWithDefaultDeltaAcrossAllDataSources() {
        TinyIdManagementServiceImpl service = service();
        TinyIdBusinessAggregateBO created = aggregate("COMPLETE", business("datasource-0", 10, 0));
        doReturn(null, created).when(service).getBusinessBO("order");
        doNothing().when(service).createBusinessOnAllDataSources("order", 100, 10);

        assertEquals("order", service.createBusiness(businessForm(null)).getBizType());

        verify(service).createBusinessOnAllDataSources("order", 100, 10);
    }

    @Test
    void honorsCustomDeltaAtCreation() {
        TinyIdManagementServiceImpl service = service();
        doReturn(null, aggregate("COMPLETE", business("datasource-0", 32, 0)))
            .when(service).getBusinessBO("order");
        doNothing().when(service).createBusinessOnAllDataSources("order", 100, 32);

        service.createBusiness(businessForm(32));

        verify(service).createBusinessOnAllDataSources("order", 100, 32);
    }

    @Test
    void compensatesCompletedBusinessWriteWhenReloadFails() {
        TinyIdManagementServiceImpl service = service();
        doReturn(null).doThrow(new IllegalStateException("Injected reload failure"))
            .when(service).getBusinessBO("order");
        doNothing().when(service).createBusinessOnAllDataSources("order", 100, 10);
        doNothing().when(service).deleteBusinessOnAllDataSources("order");

        assertThrows(IllegalStateException.class, () -> service.createBusiness(businessForm(null)));

        verify(service).deleteBusinessOnAllDataSources("order");
    }

    @Test
    void deletesBusinessOnlyWhenEveryDatasourceHasZeroMaxId() {
        TinyIdManagementServiceImpl service = service();
        doReturn(aggregate("COMPLETE", business("datasource-0", 10, 0, 0),
            business("datasource-1", 10, 1, 0))).when(service).getBusinessBO("order");
        doNothing().when(service).deleteBusinessOnAllDataSources("order");

        service.deleteBusiness("order");

        verify(service).deleteBusinessOnAllDataSources("order");
    }

    @Test
    void rejectsBusinessDeletionWhenAnyDatasourceHasIssuedIds() {
        TinyIdManagementServiceImpl service = service();
        doReturn(aggregate("COMPLETE", business("datasource-0", 10, 0, 0),
            business("datasource-1", 10, 1, 100))).when(service).getBusinessBO("order");

        assertThrows(IllegalStateException.class, () -> service.deleteBusiness("order"));

        verify(service, never()).deleteBusinessOnAllDataSources("order");
    }

    /** 创建带真实转换器和被监控持久化方法的管理服务。 */
    private TinyIdManagementServiceImpl service() {
        DynamicDataSource routingDataSource = new DynamicDataSource();
        routingDataSource.setDataSourceKeys(List.of("datasource-0"));
        TinyIdManagementConverter converter = Mappers.getMapper(TinyIdManagementConverter.class);
        return spy(new TinyIdManagementServiceImpl(mock(TinyIdInfoMapper.class),
            mock(TinyIdAuditLogMapper.class), routingDataSource, converter));
    }

    /** 创建发号业务表单。 */
    private CreateBusinessForm businessForm(Integer delta) {
        CreateBusinessForm form = new CreateBusinessForm();
        form.setBizType("order");
        form.setStep(100);
        form.setDelta(delta);
        return form;
    }

    /** 创建聚合业务对象。 */
    private TinyIdBusinessAggregateBO aggregate(String status, TinyIdBusinessBO... businesses) {
        TinyIdBusinessBO first = businesses[0];
        return new TinyIdBusinessAggregateBO(first.getBizType(), first.getStep(), first.getDelta(), status,
            List.of(businesses));
    }

    /** 创建业务实例对象。 */
    private TinyIdBusinessBO business(String key, int delta, int remainder) {
        return business(key, delta, remainder, 0);
    }

    /** 创建带最大发号值的业务实例对象。 */
    private TinyIdBusinessBO business(String key, int delta, int remainder, long maxId) {
        TinyIdBusinessBO business = new TinyIdBusinessBO();
        business.setId(1L);
        business.setDataSourceKey(key);
        business.setHealthy(true);
        business.setBizType("order");
        business.setMaxId(maxId);
        business.setStep(100);
        business.setDelta(delta);
        business.setRemainder(remainder);
        return business;
    }
}
