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

package dev.macula.cloud.system.service.cache;

import dev.macula.boot.context.TenantContextHolder;
import dev.macula.boot.security.ResourceAccessException;
import dev.macula.cloud.system.mapper.SysTenantInfoMapper;
import dev.macula.cloud.system.mapper.SysTenantUserMapper;
import dev.macula.cloud.system.mapper.SysUserMapper;
import dev.macula.cloud.system.pojo.entity.SysTenantInfo;
import dev.macula.cloud.system.pojo.entity.SysTenantUser;
import dev.macula.cloud.system.pojo.entity.SysUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 菜单路由可信租户解析规则测试。
 *
 * @author Rain
 * @since 6.1.0
 */
class MenuRouteTenantContextTest {

    private final SysUserMapper userMapper = mock(SysUserMapper.class);

    private final SysTenantUserMapper tenantUserMapper = mock(SysTenantUserMapper.class);

    private final SysTenantInfoMapper tenantInfoMapper = mock(SysTenantInfoMapper.class);

    private final MenuRouteTenantContext context =
        new MenuRouteTenantContext(userMapper, tenantUserMapper, tenantInfoMapper);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clearCurrentTenantId();
    }

    @Test
    void applicationUsesTokenTenantWhenRequestTenantIsAbsent() {
        authenticate("service-client", 12L, "SCOPE_system.read");

        assertEquals(12L, context.resolve().tenantId());
        verifyNoInteractions(userMapper, tenantUserMapper, tenantInfoMapper);
    }

    @Test
    void applicationCannotOverrideTokenTenant() {
        authenticate("service-client", 12L, "SCOPE_system.read");
        TenantContextHolder.setCurrentTenantId(1L);

        assertThrows(ResourceAccessException.class, context::resolve);
        verifyNoInteractions(userMapper, tenantUserMapper, tenantInfoMapper);
    }

    @Test
    void userCanUseTokenTenantWithoutMembershipLookup() {
        authenticate("alice", 12L, "ROLE_USER");
        TenantContextHolder.setCurrentTenantId(12L);

        assertEquals(12L, context.resolve().tenantId());
        verifyNoInteractions(userMapper, tenantUserMapper, tenantInfoMapper);
    }

    @Test
    void rootCanSwitchOnlyToExistingTenant() {
        authenticate("root", 1L, "ROLE_ROOT");
        TenantContextHolder.setCurrentTenantId(12L);
        when(tenantInfoMapper.selectById(12L)).thenReturn(new SysTenantInfo());

        assertEquals(12L, context.resolve().tenantId());

        TenantContextHolder.setCurrentTenantId(99L);
        assertThrows(ResourceAccessException.class, context::resolve);
        verify(tenantInfoMapper).selectById(99L);
    }

    @Test
    void userCanSwitchOnlyToExplicitMembership() {
        authenticate("alice", 1L, "ROLE_USER");
        TenantContextHolder.setCurrentTenantId(12L);
        SysUser user = new SysUser();
        user.setId(7L);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(tenantUserMapper.selectList(any())).thenReturn(List.of(new SysTenantUser(7L, 12L)));

        assertEquals(12L, context.resolve().tenantId());

        TenantContextHolder.setCurrentTenantId(13L);
        assertThrows(ResourceAccessException.class, context::resolve);
    }

    @Test
    void userWithoutMembershipKeepsOnlyDefaultTenantCompatibility() {
        authenticate("alice", 12L, "ROLE_USER");
        SysUser user = new SysUser();
        user.setId(7L);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(tenantUserMapper.selectList(any())).thenReturn(List.of());

        TenantContextHolder.setCurrentTenantId(1L);
        assertEquals(1L, context.resolve().tenantId());

        TenantContextHolder.setCurrentTenantId(13L);
        assertThrows(ResourceAccessException.class, context::resolve);
    }

    @Test
    void unknownUserAndMissingTenantAreDenied() {
        authenticate("missing", 1L, "ROLE_USER");
        TenantContextHolder.setCurrentTenantId(12L);
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThrows(ResourceAccessException.class, context::resolve);
        verify(tenantUserMapper, never()).selectList(any());

        SecurityContextHolder.clearContext();
        TenantContextHolder.clearCurrentTenantId();
        assertThrows(ResourceAccessException.class, context::resolve);
    }

    private void authenticate(String subject, Long tenantId, String authority) {
        Jwt.Builder jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject(subject)
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
        if (tenantId != null) {
            jwt.claim("tenantId", tenantId);
        }
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwt.build(), List.of(new SimpleGrantedAuthority(authority)), subject));
    }
}
