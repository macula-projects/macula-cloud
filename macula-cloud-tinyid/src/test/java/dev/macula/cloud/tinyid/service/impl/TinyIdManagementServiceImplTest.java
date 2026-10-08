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
import dev.macula.cloud.tinyid.mapper.TinyIdTokenMapper;
import dev.macula.cloud.tinyid.pojo.bo.TinyIdApplicationBO;
import dev.macula.cloud.tinyid.pojo.bo.TinyIdBusinessAggregateBO;
import dev.macula.cloud.tinyid.pojo.bo.TinyIdBusinessBO;
import dev.macula.cloud.tinyid.pojo.form.AddApplicationBusinessesForm;
import dev.macula.cloud.tinyid.pojo.form.CreateApplicationForm;
import dev.macula.cloud.tinyid.pojo.form.CreateBusinessForm;
import dev.macula.cloud.tinyid.pojo.query.ApplicationPageQuery;
import dev.macula.cloud.tinyid.pojo.vo.TinyIdApplicationVO;
import dev.macula.cloud.tinyid.service.TinyIdTokenService;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
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
        TinyIdManagementServiceImpl service = service(mock(TinyIdTokenService.class));
        TinyIdApplicationBO application = new TinyIdApplicationBO();
        application.setAppId(8L);
        application.setToken("test-application-token");
        Page<TinyIdApplicationBO> source = new Page<>(2, 20, 21);
        source.setRecords(List.of(application));
        doReturn(source).when(service).listApplications(2, 20, "test");
        ApplicationPageQuery query = new ApplicationPageQuery();
        query.setPage(2);
        query.setPageSize(20);
        query.setKeywords("test");

        IPage<TinyIdApplicationVO> result = service.listApplications(query);

        assertEquals(2, result.getCurrent());
        assertEquals(20, result.getSize());
        assertEquals(21, result.getTotal());
        assertEquals("test-application-token", result.getRecords().get(0).getToken());
    }

    @Test
    void createsBusinessWithDefaultDeltaAcrossAllDataSources() {
        TinyIdManagementServiceImpl service = service(mock(TinyIdTokenService.class));
        TinyIdBusinessAggregateBO created = aggregate("COMPLETE", business("datasource-0", 10, 0));
        doReturn(null, created).when(service).getBusinessBO("order");
        doNothing().when(service).createBusinessOnAllDataSources("order", 100, 10);

        assertEquals("order", service.createBusiness(businessForm(null)).getBizType());

        verify(service).createBusinessOnAllDataSources("order", 100, 10);
    }

    @Test
    void honorsCustomDeltaAtCreation() {
        TinyIdManagementServiceImpl service = service(mock(TinyIdTokenService.class));
        doReturn(null, aggregate("COMPLETE", business("datasource-0", 32, 0)))
            .when(service).getBusinessBO("order");
        doNothing().when(service).createBusinessOnAllDataSources("order", 100, 32);

        service.createBusiness(businessForm(32));

        verify(service).createBusinessOnAllDataSources("order", 100, 32);
    }

    @Test
    void compensatesCompletedBusinessWriteWhenReloadFails() {
        TinyIdManagementServiceImpl service = service(mock(TinyIdTokenService.class));
        doReturn(null).doThrow(new IllegalStateException("Injected reload failure"))
            .when(service).getBusinessBO("order");
        doNothing().when(service).createBusinessOnAllDataSources("order", 100, 10);
        doNothing().when(service).deleteBusinessOnAllDataSources("order");

        assertThrows(IllegalStateException.class, () -> service.createBusiness(businessForm(null)));

        verify(service).deleteBusinessOnAllDataSources("order");
    }

    @Test
    void deletesBusinessOnlyWhenEveryDatasourceHasZeroMaxId() {
        TinyIdTokenService tokenService = mock(TinyIdTokenService.class);
        TinyIdManagementServiceImpl service = service(tokenService);
        doReturn(aggregate("COMPLETE", business("datasource-0", 10, 0, 0),
            business("datasource-1", 10, 1, 0))).when(service).getBusinessBO("order");
        doNothing().when(service).deleteBusinessOnAllDataSources("order");

        service.deleteBusiness("order");

        verify(service).deleteBusinessOnAllDataSources("order");
        verify(tokenService).removeBusiness("order");
    }

    @Test
    void rejectsBusinessDeletionWhenAnyDatasourceHasIssuedIds() {
        TinyIdTokenService tokenService = mock(TinyIdTokenService.class);
        TinyIdManagementServiceImpl service = service(tokenService);
        doReturn(aggregate("COMPLETE", business("datasource-0", 10, 0, 0),
            business("datasource-1", 10, 1, 100))).when(service).getBusinessBO("order");

        assertThrows(IllegalStateException.class, () -> service.deleteBusiness("order"));

        verify(service, never()).deleteBusinessOnAllDataSources("order");
        verify(tokenService, never()).removeBusiness("order");
    }

    @Test
    void createsA256BitServerTokenAndRefreshesAuthorizationImmediately() {
        TinyIdTokenService tokenService = mock(TinyIdTokenService.class);
        TinyIdManagementServiceImpl service = service(tokenService);
        doReturn(false).when(service).tokenExists(anyString());
        doReturn(aggregate("COMPLETE", business("datasource-0", 10, 0)))
            .when(service).getBusinessBO("order");
        TinyIdApplicationBO saved = new TinyIdApplicationBO();
        saved.setAppId(8L);
        saved.setBizTypes(List.of("order"));
        doAnswer(invocation -> {
            saved.setToken(invocation.getArgument(0));
            return saved;
        }).when(service).getApplicationByToken(anyString());
        doNothing().when(service).createApplicationOnAllDataSources(anyString(), eq("order application"),
            eq(List.of("order")));
        CreateApplicationForm form = new CreateApplicationForm();
        form.setRemark("order application");
        form.setBizTypes(List.of("order"));

        TinyIdApplicationVO application = service.createApplication(form);

        assertEquals(32, Base64.getUrlDecoder().decode(application.getToken()).length);
        verify(tokenService).replaceAuthorizations(application.getToken(), List.of("order"));
    }

    @Test
    void compensatesOnlyAuthorizationRowsInsertedByCurrentRequest() {
        TinyIdTokenService tokenService = mock(TinyIdTokenService.class);
        TinyIdManagementServiceImpl service = service(tokenService);
        TinyIdApplicationBO existing = new TinyIdApplicationBO();
        existing.setAppId(8L);
        existing.setToken("test-application-token");
        existing.setRemark("test application");
        existing.setBizTypes(List.of("existing"));
        List<TinyIdManagementServiceImpl.AuthorizationInsert> inserted = List.of(
            new TinyIdManagementServiceImpl.AuthorizationInsert("datasource-1", "order"));
        doReturn(existing).doThrow(new IllegalStateException("Injected reload failure"))
            .when(service).getApplicationBO(8L);
        doReturn(aggregate("COMPLETE", business("datasource-0", 10, 0)))
            .when(service).getBusinessBO("order");
        doReturn(inserted).when(service).addAuthorizationsOnAllDataSources(
            existing.getToken(), existing.getRemark(), List.of("order"));
        doNothing().when(service).deleteInsertedAuthorizations(existing.getToken(), inserted);
        AddApplicationBusinessesForm form = new AddApplicationBusinessesForm();
        form.setBizTypes(List.of("order"));

        assertThrows(IllegalStateException.class, () -> service.addBusinesses(8L, form));

        verify(service).deleteInsertedAuthorizations(existing.getToken(), inserted);
        verify(tokenService, never()).replaceAuthorizations(anyString(), eq(List.of("existing", "order")));
    }

    /** 创建带真实转换器和被监控持久化方法的管理服务。 */
    private TinyIdManagementServiceImpl service(TinyIdTokenService tokenService) {
        DynamicDataSource routingDataSource = new DynamicDataSource();
        routingDataSource.setDataSourceKeys(List.of("datasource-0"));
        TinyIdManagementConverter converter = Mappers.getMapper(TinyIdManagementConverter.class);
        return spy(new TinyIdManagementServiceImpl(mock(TinyIdInfoMapper.class), mock(TinyIdTokenMapper.class),
            mock(TinyIdAuditLogMapper.class), routingDataSource, tokenService, converter));
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
