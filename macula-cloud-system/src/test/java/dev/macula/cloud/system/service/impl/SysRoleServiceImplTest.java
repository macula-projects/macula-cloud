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

package dev.macula.cloud.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import dev.macula.boot.context.TenantContextHolder;
import dev.macula.cloud.system.converter.RoleConverter;
import dev.macula.cloud.system.form.RoleForm;
import dev.macula.cloud.system.mapper.SysRoleMapper;
import dev.macula.cloud.system.mapper.SysTenantInfoMapper;
import dev.macula.cloud.system.mapper.SysTenantUserMapper;
import dev.macula.cloud.system.mapper.SysUserMapper;
import dev.macula.cloud.system.pojo.entity.SysRole;
import dev.macula.cloud.system.service.MenuRouteCacheService;
import dev.macula.cloud.system.service.MenuRouteTenantContext;
import dev.macula.cloud.system.service.MenuRouteTenantContext.TrustedTenant;
import dev.macula.cloud.system.service.SysPermissionService;
import dev.macula.cloud.system.service.SysRoleMenuService;
import dev.macula.cloud.system.service.SysRolePermissionService;
import dev.macula.cloud.system.service.SysUserRoleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 角色写路径的租户路由缓存失效测试。
 *
 * @author Rain
 * @since 6.1.0
 */
class SysRoleServiceImplTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clearCurrentTenantId();
    }

    @Test
    void roleWritePathsEvictTheCapturedTenant() {
        SysRoleMapper mapper = mock(SysRoleMapper.class);
        SysRoleMenuService roleMenuService = mock(SysRoleMenuService.class);
        SysUserRoleService userRoleService = mock(SysUserRoleService.class);
        SysRolePermissionService rolePermissionService = mock(SysRolePermissionService.class);
        SysPermissionService permissionService = mock(SysPermissionService.class);
        RoleConverter converter = mock(RoleConverter.class);
        MenuRouteTenantContext tenantContext = mock(MenuRouteTenantContext.class);
        MenuRouteCacheService cacheService = mock(MenuRouteCacheService.class);
        TrustedTenant tenant = trusted(12L);
        when(tenantContext.resolve()).thenReturn(tenant);
        SysRoleServiceImpl service = spy(new SysRoleServiceImpl(roleMenuService, userRoleService,
            rolePermissionService, permissionService, converter, tenantContext, cacheService));
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        doReturn(0L).when(service).count(any(Wrapper.class));
        doReturn(true).when(service).saveOrUpdate(any(SysRole.class));
        doReturn(true).when(service).removeByIds(anyCollection());

        RoleForm form = new RoleForm();
        form.setName("Auditor");
        form.setCode("AUDITOR");
        when(converter.form2Entity(form)).thenReturn(new SysRole());
        service.saveRole(form);
        verify(cacheService).evictAfterCommit(tenant);

        reset(cacheService);
        form.setId(7L);
        form.setCode("AUDITOR_V2");
        service.saveRole(form);
        verify(cacheService).evictAfterCommit(tenant);

        reset(cacheService);
        when(userRoleService.count(any())).thenReturn(0L);
        service.deleteRoles("7");
        verify(cacheService).evictAfterCommit(tenant);

        reset(cacheService);
        service.updateRoleMenus(7L, List.of());
        verify(cacheService).evictAfterCommit(tenant);
        verify(tenantContext, times(4)).resolve();
    }

    @Test
    void failedRoleMenuWriteDoesNotRegisterEviction() {
        SysRoleMenuService roleMenuService = mock(SysRoleMenuService.class);
        SysRolePermissionService rolePermissionService = mock(SysRolePermissionService.class);
        MenuRouteTenantContext tenantContext = mock(MenuRouteTenantContext.class);
        MenuRouteCacheService cacheService = mock(MenuRouteCacheService.class);
        when(tenantContext.resolve()).thenReturn(trusted(12L));
        doReturn(true).when(roleMenuService).remove(any(Wrapper.class));
        doThrow(new IllegalStateException("fixture write failure")).when(rolePermissionService)
            .remove(any(Wrapper.class));
        SysRoleServiceImpl service = new SysRoleServiceImpl(roleMenuService, mock(SysUserRoleService.class),
            rolePermissionService, mock(SysPermissionService.class), mock(RoleConverter.class), tenantContext,
            cacheService);

        assertThrows(IllegalStateException.class, () -> service.updateRoleMenus(7L, List.of()));

        verify(cacheService, never()).evictAfterCommit(any());
    }

    @Test
    void deniedTenantStopsRoleWritesBeforeMapperAccess() {
        SysRoleMapper mapper = mock(SysRoleMapper.class);
        MenuRouteTenantContext tenantContext = mock(MenuRouteTenantContext.class);
        MenuRouteCacheService cacheService = mock(MenuRouteCacheService.class);
        when(tenantContext.resolve()).thenThrow(new AccessDeniedException("denied"));
        SysRoleServiceImpl service = spy(new SysRoleServiceImpl(mock(SysRoleMenuService.class),
            mock(SysUserRoleService.class), mock(SysRolePermissionService.class), mock(SysPermissionService.class),
            mock(RoleConverter.class), tenantContext, cacheService));
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        assertThrows(AccessDeniedException.class, () -> service.saveRole(new RoleForm()));

        verify(service, never()).count(any(Wrapper.class));
        verifyNoInteractions(mapper, cacheService);
    }

    private static TrustedTenant trusted(Long tenantId) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject("service-client")
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
            .claim("tenantId", tenantId).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
            List.of(new SimpleGrantedAuthority("SCOPE_system.read")), "service-client"));
        return new MenuRouteTenantContext(mock(SysUserMapper.class), mock(SysTenantUserMapper.class),
            mock(SysTenantInfoMapper.class)).resolve();
    }
}
