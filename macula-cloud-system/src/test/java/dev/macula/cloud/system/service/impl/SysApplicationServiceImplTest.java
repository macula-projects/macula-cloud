/*
 * Copyright (c) 2026 Macula
 * macula.dev, China
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

package dev.macula.cloud.system.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import dev.macula.boot.context.TenantContextHolder;
import dev.macula.cloud.system.converter.ApplicationConverter;
import dev.macula.cloud.system.form.ApplicationForm;
import dev.macula.cloud.system.mapper.SysApplicationMapper;
import dev.macula.cloud.system.mapper.SysApplicationApiKeyMapper;
import dev.macula.cloud.system.service.ApplicationCredentialCacheService;
import dev.macula.cloud.system.pojo.entity.SysApplication;
import dev.macula.cloud.system.pojo.entity.SysApplicationApiKey;
import dev.macula.cloud.system.pojo.entity.SysTenantInfo;
import dev.macula.cloud.system.service.SysTenantService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 应用认证租户写入权限、缺省兼容、缓存隔离及上下文恢复测试。
 *
 * @author Rain
 * @since 6.1.0
 */
class SysApplicationServiceImplTest {
    private final ApplicationConverter converter = mock(ApplicationConverter.class);
    private final SysTenantService tenants = mock(SysTenantService.class);
    private final SysApplicationMapper mapper = mock(SysApplicationMapper.class);
    private final ApplicationCredentialCacheService cache = mock(ApplicationCredentialCacheService.class);
    private final SysApplicationApiKeyMapper keys = mock(SysApplicationApiKeyMapper.class);
    private final dev.macula.cloud.system.service.MenuRouteTenantContext tenantContext =
        mock(dev.macula.cloud.system.service.MenuRouteTenantContext.class);
    private final SysApplicationServiceImpl service = spy(new SysApplicationServiceImpl(converter, tenants, cache, keys, tenantContext));

    @BeforeEach
    void setup() {
        var trusted = mock(dev.macula.cloud.system.service.MenuRouteTenantContext.TrustedTenant.class);
        when(trusted.tenantId()).thenReturn(12L);
        when(tenantContext.resolve()).thenReturn(trusted);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysApplication.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysApplicationApiKey.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        TenantContextHolder.setCurrentTenantId(12L);
        when(converter.form2Entity(any())).thenAnswer(invocation -> {
            ApplicationForm form = invocation.getArgument(0);
            SysApplication entity = new SysApplication();
            entity.setAuthTenantId(form.getAuthTenantId());
            return entity;
        });
        when(mapper.insert(any(SysApplication.class))).thenReturn(1);
        when(mapper.update(any(SysApplication.class), any(Wrapper.class))).thenReturn(1);
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clearCurrentTenantId();
        SecurityContextHolder.clearContext();
    }

    @Test
    void ordinaryMaintainerCannotAssignAnotherTenantButCanUseOwner() {
        ApplicationForm form = new ApplicationForm();
        form.setAuthTenantId(7L);
        assertThatThrownBy(() -> service.saveApplication(form)).isInstanceOf(ResponseStatusException.class);
        verify(mapper, never()).insert(any(SysApplication.class));
        form.setAuthTenantId(12L);
        when(tenants.getById(12L)).thenReturn(tenant(12L));
        assertThat(service.saveApplication(form)).isTrue();
    }

    @Test
    void rootCanAssignExistingTenantButCannotAssignUnknownTenant() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("root", "unused", "ROLE_ROOT"));
        ApplicationForm form = new ApplicationForm();
        form.setAuthTenantId(7L);
        assertThatThrownBy(() -> service.saveApplication(form)).isInstanceOf(ResponseStatusException.class);
        when(tenants.getById(7L)).thenReturn(tenant(7L));
        assertThat(service.saveApplication(form)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void omittedFieldPreservesValueAndExplicitNullClearsIt() {
        SysApplication existing = new SysApplication();
        existing.setTenantId(12L);
        existing.setAuthTenantId(7L);
        when(keys.lockApplication(34L, 12L)).thenReturn(existing);
        ApplicationForm omitted = new ApplicationForm();
        when(mapper.update(any(SysApplication.class), any(Wrapper.class))).thenAnswer(invocation -> {
            LambdaUpdateWrapper<SysApplication> update = invocation.getArgument(1);
            assertThat(update.getSqlSet()).doesNotContain("auth_tenant_id");
            return 1;
        });
        assertThat(service.updateApplication(34L, omitted)).isTrue();
        ApplicationForm clear = new ApplicationForm();
        clear.setAuthTenantId(null);
        when(mapper.update(any(SysApplication.class), any(Wrapper.class))).thenAnswer(invocation -> {
            LambdaUpdateWrapper<SysApplication> update = invocation.getArgument(1);
            assertThat(update.getSqlSet()).contains("auth_tenant_id");
            assertThat(update.getParamNameValuePairs()).containsValue(null);
            return 1;
        });
        assertThat(service.updateApplication(34L, clear)).isTrue();
    }

    @Test
    void updateChecksOwnerFromPersistedRecordAndRejectsMissingApplication() {
        ApplicationForm form = new ApplicationForm();
        form.setAuthTenantId(7L);
        when(keys.lockApplication(34L, 12L)).thenReturn(null);
        assertThatThrownBy(() -> service.updateApplication(34L, form)).isInstanceOf(ResponseStatusException.class);
        SysApplication existing = new SysApplication();
        existing.setTenantId(12L);
        when(keys.lockApplication(34L, 12L)).thenReturn(existing);
        assertThatThrownBy(() -> service.updateApplication(34L, form)).isInstanceOf(ResponseStatusException.class);
        verify(mapper, never()).update(any(SysApplication.class), any(Wrapper.class));
    }

    @Test
    void applicationCacheRefreshDelegatesToSharedRebuild() {
        service.refreshApplicationCache();
        verify(cache).rebuild();
    }

    @Test
    void deletingApplicationLocksBeforeRevokingKeysAndSchedulesCacheRemoval() {
        SysApplication app = new SysApplication();
        app.setTenantId(12L);
        app.setId(34L);
        app.setCode("sample");
        when(keys.lockApplication(34L, 12L)).thenReturn(app);
        org.mockito.Mockito.doReturn(true).when(service).removeByIds(org.mockito.ArgumentMatchers.anyCollection());
        assertThat(service.deleteApplications("34")).isTrue();
        var order = org.mockito.Mockito.inOrder(keys, cache, service);
        order.verify(keys).lockApplication(34L, 12L);
        order.verify(keys).update(org.mockito.ArgumentMatchers.isNull(), any(Wrapper.class));
        order.verify(cache).afterCommit(12L, 34L, "sample");
        order.verify(service).removeByIds(List.of(34L));
    }

    @Test
    void deletingUnknownOrForeignApplicationCannotRevokeKeys() {
        assertThatThrownBy(() -> service.deleteApplications("34")).isInstanceOf(ResponseStatusException.class);
        verify(keys, never()).update(any(), any(Wrapper.class));
        verify(cache, never()).afterCommit(any(), any(), any());
    }

    @Test
    void duplicateCodeCheckFailureRestoresPreviousTenant() {
        when(tenants.list()).thenReturn(List.of(tenant(7L)));
        when(mapper.selectCount(any())).thenThrow(new IllegalStateException("test failure"));
        assertThatThrownBy(() -> service.validatorAppCode(null, "app")).isInstanceOf(IllegalStateException.class);
        assertThat(TenantContextHolder.getCurrentTenantId()).isEqualTo(12L);
    }

    private static SysTenantInfo tenant(Long id) {
        SysTenantInfo tenant = new SysTenantInfo();
        tenant.setId(id);
        tenant.setCode(id.equals(12L) ? "macula-samples" : "tenant-" + id);
        return tenant;
    }

    private static SysApplication application(String code, Long authTenantId) {
        SysApplication application = new SysApplication();
        application.setCode(code);
        application.setSk("test-secret");
        application.setAuthTenantId(authTenantId);
        return application;
    }
}
