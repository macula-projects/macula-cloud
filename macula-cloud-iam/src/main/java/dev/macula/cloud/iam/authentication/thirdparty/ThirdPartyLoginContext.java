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
package dev.macula.cloud.iam.authentication.thirdparty;

import dev.macula.cloud.iam.enums.UserTypeEnum;

import java.io.Serializable;
import java.time.Instant;

/**
 * 与一次性 state 绑定的 OAuth client 安全快照。
 *
 * @author Rain
 * @since 6.1.0
 */
public record ThirdPartyLoginContext(String providerType, String clientId, Long tenantId,
                                     UserTypeEnum userType, Instant createdAt,
                                     String savedRequestTarget) implements Serializable {
    public ThirdPartyLoginContext(String providerType, String clientId, Long tenantId,
        UserTypeEnum userType, Instant createdAt) {
        this(providerType, clientId, tenantId, userType, createdAt, "/");
    }
}
