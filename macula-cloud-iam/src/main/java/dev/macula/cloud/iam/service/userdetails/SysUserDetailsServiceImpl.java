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

package dev.macula.cloud.iam.service.userdetails;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import dev.macula.boot.enums.StatusEnum;
import dev.macula.cloud.iam.authentication.sms.SmsUserDetailsService;
import dev.macula.cloud.iam.pojo.dto.UserAuthInfo;
import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import dev.macula.cloud.iam.service.support.SysOAuth2ClientService;
import dev.macula.cloud.iam.service.support.SysUserService;
import dev.macula.cloud.iam.service.support.UserAuthInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.util.Assert;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Optional;

import static dev.macula.boot.starter.web.utils.RequestUtil.getCurrentRequest;

/**
 * 系统用户体系业务类
 *
 * @author <a href="mailto:xianrui0365@163.com">haoxr</a>
 * @since 6.1.0
 */
@Slf4j
@RequiredArgsConstructor
public class SysUserDetailsServiceImpl implements UserDetailsService, UserDetailsPasswordService,
    SmsUserDetailsService {

    private final SysUserService sysUserService;
    private final SysOAuth2ClientService sysOAuth2ClientService;
    @Nullable
    private final UserAuthInfoService userAuthInfoService;

    @Override
    public @NonNull UserDetails loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {
        String clientId = getClientId();
        Long tenantId = 1L;
        UserAuthInfo userAuthInfo = null;
        // 根据客户端配置获取不同的用户类型，访问不同的身份提供源
        if (StrUtil.isNotBlank(clientId)) {
            SysOAuth2Client oauth2Client = sysOAuth2ClientService.getClientByClientId(clientId);
            if (oauth2Client != null) {
                tenantId = oauth2Client.getTenantId();
                if (userAuthInfoService != null) {
                    userAuthInfo = userAuthInfoService.getUserAuthInfo(oauth2Client.getUserType(), tenantId, username);
                }
            }
        }

        // 默认使用系统用户
        if (userAuthInfo == null) {
            // TODO 如果要支持人群包，这里需要获取该用户的groupIds
            userAuthInfo = sysUserService.getUserAuthInfo(tenantId, username, null);
        }

        Assert.isTrue(userAuthInfo != null, "用户不存在");

        if (!StatusEnum.ENABLE.getValue().equals(userAuthInfo.getStatus())) {
            throw new DisabledException("该账户已被禁用!");
        }
        SysUserDetails details = new SysUserDetails(userAuthInfo);
        details.setTenantId(tenantId);
        return details;
    }

    @Override
    public UserDetails loadUserByPhone(String phone) throws UsernameNotFoundException {
        // TODO 应该从统一的用户与第三方身份绑定表获取用户名【username:手机号、微信OpenId、微信UnionId等】
        String username = "";
        UserDetails userDetails = this.loadUserByUsername(username);
        if (userDetails instanceof SysUserDetails) {
            ((SysUserDetails)userDetails).setMobile(phone);
        }
        return userDetails;
    }

    @Override
    public @NonNull UserDetails updatePassword(UserDetails user, String newPassword) {
        boolean result = sysUserService.update(
            new LambdaUpdateWrapper<SysUser>().eq(SysUser::getUsername, user.getUsername())
                .set(SysUser::getPassword, newPassword));

        if (result && user instanceof SysUserDetails) {
            ((SysUserDetails)user).setPassword(newPassword);
        }

        return user;
    }

    private String getClientId() {
        HttpServletRequest request = getCurrentRequest();
        // 优先从HEADER中获取CLIENT_ID
        String clientId = request.getHeader(OAuth2ParameterNames.CLIENT_ID);
        if (StrUtil.isBlank(clientId)) {
            clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
        }
        if (StrUtil.isBlank(clientId)) {
            // 找不到就从COOKIE中获取
            if (request.getCookies() != null) {
                Optional<Cookie> cookie =
                    Arrays.stream(request.getCookies()).filter(c -> c.getName().equals(OAuth2ParameterNames.CLIENT_ID))
                        .findFirst();
                clientId = cookie.map(Cookie::getValue).orElse("");
            }
        }
        return clientId;
    }
}
