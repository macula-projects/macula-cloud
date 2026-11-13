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

package dev.macula.cloud.iam.protocol.oauth2.client;

import java.util.Set;

/**
 * OAuth协议发布投影端口；只有有效登记可形成Client身份，不暴露数据库写入。
 *
 * @author rain
 * @since 2023/4/10 19:44
 */
public interface OAuth2ClientRegistrationService {
    /** 按协议标识读取当前规范身份；草稿、未分配槽和无效登记返回null。 */
    OAuth2ClientRegistration getClientByClientId(String clientId);

    /**
     * 返回全部 OAuth Client 登记的精确 Origin 索引。
     *
     * @return 不可变 Origin 集合
     */
    Set<String> allowedOrigins();

    /**
     * 使用权威 Client 数据校验精确 Origin。
     *
     * @param clientId 已认证 Client 标识
     * @param origin 已规范化 Origin
     * @return 是否属于该 Client
     */
    boolean isAllowedOrigin(String clientId, String origin);
}
