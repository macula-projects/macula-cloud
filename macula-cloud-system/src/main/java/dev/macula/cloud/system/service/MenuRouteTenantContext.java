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

package dev.macula.cloud.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import dev.macula.boot.constants.GlobalConstants;
import dev.macula.boot.context.TenantContextHolder;
import dev.macula.boot.security.ResourceAccessException;
import dev.macula.boot.starter.security.utils.SecurityUtils;
import dev.macula.cloud.system.mapper.SysTenantInfoMapper;
import dev.macula.cloud.system.mapper.SysTenantUserMapper;
import dev.macula.cloud.system.mapper.SysUserMapper;
import dev.macula.cloud.system.pojo.entity.SysTenantUser;
import dev.macula.cloud.system.pojo.entity.SysUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 解析并校验菜单路由操作使用的可信租户。
 *
 * @author Rain
 * @since 6.1.0
 */
@Component
@RequiredArgsConstructor
public class MenuRouteTenantContext {

    private static final String ROLE_PREFIX = "ROLE_";

    private final SysUserMapper userMapper;

    private final SysTenantUserMapper tenantUserMapper;

    private final SysTenantInfoMapper tenantInfoMapper;

    /**
     * 解析当前认证和请求上下文中的可信租户。
     *
     * @return 已校验的租户捕获值
     * @throws ResourceAccessException 无法确定或无权访问当前租户时抛出
     */
    public TrustedTenant resolve() {
        Long tokenTenantId = SecurityUtils.getTenantId();
        Long requestedTenantId = TenantContextHolder.getCurrentTenantId();
        if (requestedTenantId == null) {
            return trusted(requireTenant(tokenTenantId));
        }
        requireTenant(requestedTenantId);
        if (Objects.equals(requestedTenantId, tokenTenantId)) {
            return trusted(requestedTenantId);
        }
        if (!hasUserRole()) {
            throw denied();
        }
        if (SecurityUtils.isRoot()) {
            if (tenantInfoMapper.selectById(requestedTenantId) == null) {
                throw denied();
            }
            return trusted(requestedTenantId);
        }
        String username = SecurityUtils.getCurrentUser();
        if (!StringUtils.hasText(username)) {
            throw denied();
        }
        SysUser user = userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", username));
        if (user == null || user.getId() == null) {
            throw denied();
        }
        List<SysTenantUser> memberships = tenantUserMapper.selectList(
            new QueryWrapper<SysTenantUser>().eq("user_id", user.getId()));
        boolean allowed = memberships == null || memberships.isEmpty()
            ? Objects.equals(GlobalConstants.DEFAULT_TENANT_ID, requestedTenantId)
            : memberships.stream().anyMatch(item -> Objects.equals(item.getTenantId(), requestedTenantId));
        if (!allowed) {
            throw denied();
        }
        return trusted(requestedTenantId);
    }

    private Long requireTenant(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw denied();
        }
        return tenantId;
    }

    private boolean hasUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities() != null
            && authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.startsWith(ROLE_PREFIX));
    }

    private TrustedTenant trusted(Long tenantId) {
        return new TrustedTenant(tenantId);
    }

    private ResourceAccessException denied() {
        return new ResourceAccessException("无权访问当前租户菜单");
    }

    /**
     * 只能由租户解析器创建的可信租户捕获值。
     *
     * @since 6.1.0
     */
    public static final class TrustedTenant {

        private final Long tenantId;

        private TrustedTenant(Long tenantId) {
            this.tenantId = tenantId;
        }

        /**
         * 获取已校验租户 ID。
         *
         * @return 租户 ID
         */
        public Long tenantId() {
            return tenantId;
        }
    }
}
