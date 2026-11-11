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

package dev.macula.cloud.iam.service.account;

import dev.macula.boot.enums.StatusEnum;
import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.pojo.dto.UserAuthInfo;
import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.service.client.SysOAuth2ClientService;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * 按显式Client快照加载账号；登录与外部身份绑定共用，不读取Servlet请求或签发Token。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
public final class DefaultClientAccountDetailsService implements ClientAccountDetailsService {
    private final SysUserService sysUserService;
    private final ClientAwareAccountResolver identifiers;
    private final SysOAuth2ClientService sysOAuth2ClientService;
    @Nullable
    private final UserAuthInfoService userAuthInfoService;

    /**
     * 可选扩展用户目录保持原语义；员工在扩展无结果时使用本地目录。
     */
    public DefaultClientAccountDetailsService(SysUserService users, SysOAuth2ClientService clients,
            @Nullable UserAuthInfoService externalUsers, ClientAwareAccountResolver identifiers) {
        this.sysUserService = users;
        this.identifiers = identifiers;
        this.sysOAuth2ClientService = clients;
        this.userAuthInfoService = externalUsers;
    }

    /**
     * Client当前租户/用户类型必须与调用方可信快照一致，配置漂移时拒绝。
     */
    @Override
    public UserDetails loadUserByClient(String clientId, Long expectedTenantId, UserTypeEnum expectedUserType,
            String username) {
        Assert.hasText(clientId, "OAuth client ID 不能为空");
        Assert.notNull(expectedTenantId, "OAuth client 租户快照不能为空");
        Assert.notNull(expectedUserType, "OAuth client 用户类型快照不能为空");
        SysOAuth2Client oauth2Client = sysOAuth2ClientService.getClientByClientId(clientId);
        if (oauth2Client == null || oauth2Client.getTenantId() == null || oauth2Client.getUserType() == null) {
            throw new UsernameNotFoundException("OAuth client 不存在或用户路由未配置");
        }
        if (!expectedTenantId.equals(oauth2Client.getTenantId()) || expectedUserType != oauth2Client.getUserType()) {
            throw new BadCredentialsException("OAuth client 配置已变化");
        }
        Long tenantId = oauth2Client.getTenantId();
        UserAuthInfo userAuthInfo = userAuthInfoService == null ? null
                : userAuthInfoService.getUserAuthInfo(oauth2Client.getUserType(), tenantId, username);
        if (userAuthInfo == null && UserTypeEnum.EMPLOYEE == oauth2Client.getUserType()) {
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
        SysOAuth2Client client = sysOAuth2ClientService.getClientByClientId(clientId);
        if (client == null || !java.util.Objects.equals(tenantId, client.getTenantId()) ||
                userType != client.getUserType()) {
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
        UserDetails loaded = loadUserByClient(clientId, tenantId, userType, account.getUsername());
        if (!(loaded instanceof SysUserDetails principal) ||
                !java.util.Objects.equals(account.getId(), principal.getUserId())
                || !java.util.Objects.equals(tenantId, principal.getTenantId())) {
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
    public UserDetails updatePassword(UserDetails user, String encodedPassword) {
        boolean changed = sysUserService.update(
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<dev.macula.cloud.iam.pojo.entity.SysUser>()
                        .eq(dev.macula.cloud.iam.pojo.entity.SysUser::getUsername, user.getUsername())
                        .set(dev.macula.cloud.iam.pojo.entity.SysUser::getPassword, encodedPassword));
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
