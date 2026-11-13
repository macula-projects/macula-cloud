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
package dev.macula.cloud.iam.service.account;

import dev.macula.cloud.iam.enums.UserTypeEnum;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 使用显式 OAuth client 上下文加载用户，避免从回调请求猜测租户和用户类型。
 *
 * @author Rain
 * @since 6.1.0
 */
public interface ClientAccountDetailsService {
    /** 使用服务器解析的内部应用ID加载账号；租户和用户目录必须匹配可信快照。 */
    UserDetails loadUserByApplication(Long applicationId, Long expectedTenantId, UserTypeEnum expectedUserType, String username);

    /**
     * 读取一次客户端配置，核对第三方登录 state 的路由快照后加载用户。
     *
     * @param clientId OAuth 客户端标识
     * @param expectedTenantId state 绑定的租户
     * @param expectedUserType state 绑定的用户类型
     * @param username 用户名
     * @return 已验证的用户详情
     */
    UserDetails loadUserByClient(String clientId, Long expectedTenantId, UserTypeEnum expectedUserType,
            String username);

    /**
     * 按登录过滤器的可信路由快照解析标识；门户仅保留原用户名兼容。
     */
    UserDetails loadForHostedLogin(String clientId, Long tenantId, UserTypeEnum userType,
            String identifier, IdentifierType kind);

    /**
     * 保留框架登录成功后的密码编码升级行为。
     */
    UserDetails updatePassword(UserDetails user, String encodedPassword);

    /**
     * 托管登录标识，不作为外部协议值。
     *
     * @since 6.1.0
     */
    enum IdentifierType {USERNAME, MOBILE, EMAIL}
}
