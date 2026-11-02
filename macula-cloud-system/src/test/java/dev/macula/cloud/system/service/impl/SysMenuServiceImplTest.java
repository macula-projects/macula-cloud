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

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import dev.macula.boot.context.TenantContextHolder;
import dev.macula.boot.enums.MenuTypeEnum;
import dev.macula.boot.starter.security.utils.SecurityUtils;
import dev.macula.cloud.system.converter.MenuConverter;
import dev.macula.cloud.system.form.MenuForm;
import dev.macula.cloud.system.mapper.SysMenuMapper;
import dev.macula.cloud.system.mapper.SysTenantInfoMapper;
import dev.macula.cloud.system.mapper.SysTenantUserMapper;
import dev.macula.cloud.system.mapper.SysUserMapper;
import dev.macula.cloud.system.pojo.bo.RouteBO;
import dev.macula.cloud.system.pojo.entity.SysMenu;
import dev.macula.cloud.system.service.MenuRouteCacheService;
import dev.macula.cloud.system.service.MenuRouteTenantContext;
import dev.macula.cloud.system.service.MenuRouteTenantContext.TrustedTenant;
import dev.macula.cloud.system.service.SysMenuService;
import dev.macula.cloud.system.service.SysPermissionService;
import dev.macula.cloud.system.service.SysRoleMenuService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 菜单路由租户缓存和菜单写路径失效测试。
 *
 * @author Rain
 * @since 6.1.0
 */
class SysMenuServiceImplTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clearCurrentTenantId();
    }

    @Test
    void cacheProxySeparatesTenantsInBothAccessOrders() {
        assertTenantOrder(List.of(1L, 12L, 1L));
        assertTenantOrder(List.of(12L, 1L, 12L));
    }

    @Test
    void menuWritePathsEvictTheCapturedTenant() {
        SysMenuMapper mapper = mock(SysMenuMapper.class);
        MenuConverter converter = mock(MenuConverter.class);
        SysPermissionService permissionService = mock(SysPermissionService.class);
        SysRoleMenuService roleMenuService = mock(SysRoleMenuService.class);
        MenuRouteTenantContext tenantContext = mock(MenuRouteTenantContext.class);
        MenuRouteCacheService cacheService = mock(MenuRouteCacheService.class);
        TrustedTenant tenant = trusted(12L);
        when(tenantContext.resolve()).thenReturn(tenant);
        SysMenuServiceImpl service = spy(new SysMenuServiceImpl(converter, permissionService, roleMenuService,
            tenantContext, cacheService));
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        doReturn(true).when(service).saveOrUpdate(any(SysMenu.class));
        doReturn(true).when(service).update(any(Wrapper.class));
        doReturn(true).when(service).removeByIds(anyCollection());

        SysMenu menu = menu();
        service.saveMenu(menu);
        verify(cacheService).evictAfterCommit(tenant);

        reset(cacheService);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), SysMenu.class);
        service.updateMenuVisible(7L, 0);
        verify(cacheService).evictAfterCommit(tenant);

        reset(cacheService);
        MenuForm form = new MenuForm();
        when(converter.form2Entity(form)).thenReturn(menu);
        when(permissionService.saveOrUpdatePerms(any(), any())).thenReturn(true);
        service.updateMenuPermission(form);
        verify(cacheService).evictAfterCommit(tenant);

        reset(cacheService);
        when(permissionService.count(any())).thenReturn(0L);
        service.deleteMenus("7");
        verify(cacheService).evictAfterCommit(tenant);

        reset(cacheService);
        service.cleanCache();
        verify(cacheService).evictAfterCommit(tenant);
        verify(tenantContext, times(5)).resolve();
    }

    @Test
    void deniedTenantStopsMenuWritesBeforeMapperAccess() {
        SysMenuMapper mapper = mock(SysMenuMapper.class);
        MenuRouteTenantContext tenantContext = mock(MenuRouteTenantContext.class);
        MenuRouteCacheService cacheService = mock(MenuRouteCacheService.class);
        when(tenantContext.resolve()).thenThrow(new AccessDeniedException("denied"));
        SysMenuServiceImpl service = spy(new SysMenuServiceImpl(mock(MenuConverter.class),
            mock(SysPermissionService.class), mock(SysRoleMenuService.class), tenantContext, cacheService));
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        assertThrows(AccessDeniedException.class, () -> service.saveMenu(menu()));

        verify(service, never()).saveOrUpdate(any(SysMenu.class));
        verifyNoInteractions(mapper, cacheService);
    }

    private void assertTenantOrder(List<Long> order) {
        try (AnnotationConfigApplicationContext applicationContext =
                 new AnnotationConfigApplicationContext(CacheTestConfiguration.class)) {
            SysMenuService service = applicationContext.getBean(SysMenuService.class);
            SysMenuMapper mapper = applicationContext.getBean(SysMenuMapper.class);
            for (Long tenantId : order) {
                authenticate(tenantId);
                assertEquals("/tenant-" + tenantId, service.listRoutes().get(0).getPath());
            }
            verify(mapper, times(2)).listRoutes();
        }
    }

    private static SysMenu menu() {
        SysMenu menu = new SysMenu();
        menu.setId(7L);
        menu.setPath("/reports");
        menu.setType(MenuTypeEnum.MENU);
        return menu;
    }

    private static RouteBO route(Long tenantId) {
        RouteBO route = new RouteBO();
        route.setId(tenantId);
        route.setParentId(0L);
        route.setType(MenuTypeEnum.MENU);
        route.setPath("/tenant-" + tenantId);
        route.setVisible(1);
        return route;
    }

    private static TrustedTenant trusted(Long tenantId) {
        authenticate(tenantId);
        return new MenuRouteTenantContext(mock(SysUserMapper.class), mock(SysTenantUserMapper.class),
            mock(SysTenantInfoMapper.class)).resolve();
    }

    private static void authenticate(Long tenantId) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject("service-client")
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
            .claim("tenantId", tenantId).build();
        Collection<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("SCOPE_system.read"));
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwt, authorities, "service-client"));
    }

    /**
     * 菜单缓存代理测试配置。
     *
     * @author Rain
     * @since 6.1.0
     */
    @Configuration(proxyBeanMethods = false)
    @EnableCaching(proxyTargetClass = true)
    static class CacheTestConfiguration {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("system");
        }

        @Bean
        SysMenuMapper sysMenuMapper() {
            SysMenuMapper mapper = mock(SysMenuMapper.class);
            when(mapper.listRoutes()).thenAnswer(invocation -> List.of(route(SecurityUtils.getTenantId())));
            return mapper;
        }

        @Bean
        MenuRouteTenantContext menuRouteTenantContext() {
            return new MenuRouteTenantContext(mock(SysUserMapper.class), mock(SysTenantUserMapper.class),
                mock(SysTenantInfoMapper.class));
        }

        @Bean
        MenuRouteCacheService menuRouteCacheService(CacheManager cacheManager) {
            return new MenuRouteCacheService(cacheManager);
        }

        @Bean
        SysMenuServiceImpl sysMenuService(SysMenuMapper mapper, MenuRouteTenantContext tenantContext,
            MenuRouteCacheService cacheService) {
            SysMenuServiceImpl service = new SysMenuServiceImpl(mock(MenuConverter.class),
                mock(SysPermissionService.class), mock(SysRoleMenuService.class), tenantContext, cacheService);
            ReflectionTestUtils.setField(service, "baseMapper", mapper);
            return service;
        }
    }
}
