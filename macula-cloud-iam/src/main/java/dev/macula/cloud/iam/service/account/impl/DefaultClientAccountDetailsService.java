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

package dev.macula.cloud.iam.service.account.impl;

import dev.macula.cloud.iam.service.account.ClientAccountDetailsService;
import dev.macula.cloud.iam.service.account.ClientAwareAccountResolver;
import dev.macula.cloud.iam.service.account.SysUserService;
import dev.macula.cloud.iam.service.account.UserAuthInfoService;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import dev.macula.boot.enums.StatusEnum;
import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.pojo.dto.UserAuthInfo;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import dev.macula.cloud.iam.service.application.IamApplicationService;
import dev.macula.cloud.iam.service.application.LoginApplicationResolver;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * 按显式Client快照加载账号；登录与外部身份绑定共用，不读取Servlet请求或签发Token。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class DefaultClientAccountDetailsService implements ClientAccountDetailsService {
    private final SysUserService sysUserService;
    private final ClientAwareAccountResolver identifiers;
    private final IamApplicationService applications;
    private final LoginApplicationResolver references;
    @Nullable
    private final UserAuthInfoService userAuthInfoService;

    /**
     * 可选扩展用户目录保持原语义；员工在扩展无结果时使用本地目录。
     */
    public DefaultClientAccountDetailsService(SysUserService users, IamApplicationService applications,
            LoginApplicationResolver references, @Nullable UserAuthInfoService externalUsers, ClientAwareAccountResolver identifiers) {
        this.sysUserService = users;
        this.identifiers = identifiers;
        this.applications = applications;
        this.references = references;
        this.userAuthInfoService = externalUsers;
    }

    /**
     * Client当前租户/用户类型必须与调用方可信快照一致，配置漂移时拒绝。
     */
    @Override
    public UserDetails loadUserByClient(String clientId, Long expectedTenantId, UserTypeEnum expectedUserType,
            String username) {
        Assert.hasText(clientId, "可信Client引用不能为空");
        var application = references.trusted(clientId, null, false);
        if (application == null) {
            throw new UsernameNotFoundException("应用不存在或用户路由未配置");
        }
        return loadUserByApplication(application.id(), expectedTenantId, expectedUserType, username);
    }

    /** 中立应用ID是目录路由的唯一入口；不从请求或其他协议标识推断租户。 */
    @Override
    public UserDetails loadUserByApplication(Long applicationId, Long expectedTenantId, UserTypeEnum expectedUserType,
            String username) {
        Assert.notNull(expectedTenantId, "应用租户快照不能为空");
        Assert.notNull(expectedUserType, "应用用户目录快照不能为空");
        var app = applications.findById(applicationId);
        if (app == null || !app.loginEnabled() || app.tenantId() == null || app.accountUserType() == null) {
            throw new UsernameNotFoundException("应用不存在或用户路由未配置");
        }
        if (!expectedTenantId.equals(app.tenantId()) || expectedUserType != app.accountUserType()) {
            throw new BadCredentialsException("应用配置已变化");
        }
        Long tenantId = app.tenantId();
        UserAuthInfo userAuthInfo = userAuthInfoService == null ? null
            : userAuthInfoService.getUserAuthInfo(app.accountUserType(), tenantId, username);
        if (userAuthInfo == null && UserTypeEnum.EMPLOYEE == app.accountUserType()) {
            userAuthInfo = sysUserService.getUserAuthInfo(tenantId, username, null);
        }
        return details(userAuthInfo, tenantId);
    }

    /**
     * 托管用户名保留本地员工优先，已验证手机号/邮箱保留显式目录路径。
     */
    @Override
    public UserDetails loadForHostedLogin(String clientId, Long tenantId, UserTypeEnum userType,
            String identifier, IdentifierType kind) {
        if (clientId == null || clientId.isBlank()) {
            if (kind != IdentifierType.USERNAME) {
                throw new UsernameNotFoundException("缺少可信Client上下文");
            }
            // 无Client门户保留原员工目录，不允许请求自报路由。
            return details(sysUserService.getUserAuthInfo(1L, identifier, null), 1L);
        }
        var client = references.trusted(clientId, null, false);
        if (client == null || !Objects.equals(tenantId, client.tenantId()) ||
                userType != client.accountUserType()) {
            throw new BadCredentialsException("Client路由已变化");
        }
        if (kind == IdentifierType.USERNAME) {
            UserAuthInfo info;
            if (userType == UserTypeEnum.EMPLOYEE) {
                var account = identifiers.resolveIdentifier(userType, identifier);
                info = sysUserService.getUserAuthInfo(tenantId, account.getUsername(), null);
            } else {
                info = userAuthInfoService == null ? null :
                        userAuthInfoService.getUserAuthInfo(userType, tenantId, identifier);
            }
            return details(info, tenantId);
        }
        var account = (kind == IdentifierType.MOBILE ? identifiers.findByMobile(userType, identifier)
                : identifiers.findByEmail(userType, identifier)).orElseThrow(
                () -> new UsernameNotFoundException("用户不存在"));
        UserDetails loaded = loadUserByApplication(client.id(), tenantId, userType, account.getUsername());
        if (!(loaded instanceof SysUserDetails principal) ||
                !Objects.equals(account.getId(), principal.getUserId())
                || !Objects.equals(tenantId, principal.getTenantId())) {
            throw new BadCredentialsException("账号目录不一致");
        }
        if (kind == IdentifierType.MOBILE) {
            principal.setMobile(account.getMobile());
        }
        return principal;
    }

    /**
     * 仅供框架认证后的编码升级，保留原全局唯一用户名更新条件。
     */
    @Override
    @Transactional
    public UserDetails updatePassword(UserDetails user, String encodedPassword) {
        boolean changed = sysUserService.update(
                new LambdaUpdateWrapper<SysUser>()
                        .eq(SysUser::getUsername, user.getUsername())
                        .set(SysUser::getPassword, encodedPassword));
        if (changed && user instanceof SysUserDetails principal) {
            principal.setPassword(encodedPassword);
        }
        return user;
    }

    /**
     * 统一停用检查和主体转换，不改变两个目录用例的优先级。
     */
    private SysUserDetails details(UserAuthInfo info, Long tenantId) {
        if (info == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        if (!StatusEnum.ENABLE.getValue().equals(info.getStatus())) {
            throw new DisabledException("该账户已被禁用!");
        }
        SysUserDetails result = new SysUserDetails(info);
        result.setTenantId(tenantId);
        return result;
    }
}
