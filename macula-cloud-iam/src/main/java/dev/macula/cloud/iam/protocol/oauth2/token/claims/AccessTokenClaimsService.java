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
package dev.macula.cloud.iam.protocol.oauth2.token.claims;

import java.util.Map;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/**
 * OAuth2访问令牌声明端口；JWT与opaque共用协议规则，不属于通用账号服务。
 *
 * @author Macula
 * @since 6.1.0
 */
public interface AccessTokenClaimsService {
    /**
     * 根据受信任主体和当前Client许可生成最小资源声明，不接受请求自报租户或aud。
     *
     * @param principal 已通过认证与准入校验的主体
     * @param client 当前服务端Client配置快照
     * @param scopes 已获许可的Scope集合
     * @param grant 真实授权方式，不从未校验请求推断
     * @return 新建的Access Token声明，不适用于ID Token
     * @throws org.springframework.security.oauth2.core.OAuth2AuthenticationException 缺少资源许可或可信租户
     */
    Map<String, Object> create(Authentication principal, RegisteredClient client, Set<String> scopes, String grant);
}
