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
import dev.macula.boot.constants.CacheConstants;
import dev.macula.boot.context.TenantContextHolder;
import dev.macula.cloud.system.converter.ApplicationConverter;
import dev.macula.cloud.system.form.ApplicationForm;
import dev.macula.cloud.system.mapper.SysApplicationMapper;
import dev.macula.cloud.system.pojo.entity.SysApplication;
import dev.macula.cloud.system.pojo.entity.SysTenantInfo;
import dev.macula.cloud.system.service.SysTenantService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
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
    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    private final SysApplicationMapper mapper = mock(SysApplicationMapper.class);
    private final SysApplicationServiceImpl service = spy(new SysApplicationServiceImpl(converter, tenants, redis));

    @BeforeEach
    void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysApplication.class);
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
        doReturn(existing).when(service).getById(34L);
        ApplicationForm omitted = new ApplicationForm();
        when(mapper.update(any(SysApplication.class), any(Wrapper.class))).thenAnswer(invocation -> {
            LambdaUpdateWrapper<SysApplication> update = invocation.getArgument(1);
            assertThat(update.getSqlSet()).isNull();
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
        doReturn(null).when(service).getById(34L);
        assertThatThrownBy(() -> service.updateApplication(34L, form)).isInstanceOf(ResponseStatusException.class);
        SysApplication existing = new SysApplication();
        existing.setTenantId(12L);
        doReturn(existing).when(service).getById(34L);
        assertThatThrownBy(() -> service.updateApplication(34L, form)).isInstanceOf(ResponseStatusException.class);
        verify(mapper, never()).update(any(SysApplication.class), any(Wrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void cacheSeparatesOwnerAndAuthTenantAndFallsBackForLegacyApplication() {
        when(tenants.list()).thenReturn(List.of(tenant(12L)));
        SysApplication first = application("first", 7L);
        SysApplication second = application("second", null);
        doReturn(List.of(first, second)).when(service).list();
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        service.refreshApplicationCache();
        verify(hashes).putAll(eq(CacheConstants.SECURITY_SYSTEM_APPS + "first"),
            org.mockito.ArgumentMatchers.<Map<String, String>>argThat(values ->
                "12".equals(values.get("tenantId")) && "7".equals(values.get("authTenantId"))));
        verify(hashes).putAll(eq(CacheConstants.SECURITY_SYSTEM_APPS + "second"),
            org.mockito.ArgumentMatchers.<Map<String, String>>argThat(values ->
                "12".equals(values.get("tenantId")) && "12".equals(values.get("authTenantId"))));
        verify(redis, never()).delete(CacheConstants.SECURITY_SYSTEM_TENANTS);
        assertThat(TenantContextHolder.getCurrentTenantId()).isEqualTo(12L);
    }

    @Test
    void cacheFailureRestoresPreviousTenantIncludingAbsentContext() {
        when(tenants.list()).thenReturn(List.of(tenant(7L)));
        doThrow(new IllegalStateException("test failure")).when(service).list();
        for (Long previous : new Long[] {99L, null}) {
            TenantContextHolder.setCurrentTenantId(previous);
            assertThatThrownBy(service::refreshApplicationCache).isInstanceOf(IllegalStateException.class);
            assertThat(TenantContextHolder.getCurrentTenantId()).isEqualTo(previous);
        }
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
