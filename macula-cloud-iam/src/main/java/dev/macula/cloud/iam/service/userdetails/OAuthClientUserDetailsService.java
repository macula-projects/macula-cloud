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
package dev.macula.cloud.iam.service.userdetails;

import dev.macula.cloud.iam.enums.UserTypeEnum;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 使用显式 OAuth client 上下文加载用户，避免从回调请求猜测租户和用户类型。
 *
 * @author Rain
 * @since 6.1.0
 */
public interface OAuthClientUserDetailsService {
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
}
