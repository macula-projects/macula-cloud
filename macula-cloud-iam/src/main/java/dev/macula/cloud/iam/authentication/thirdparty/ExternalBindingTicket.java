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

import java.io.Serializable;
import java.time.Instant;

import dev.macula.cloud.iam.enums.UserTypeEnum;

/**
 * Redis中的服务端绑定材料，绝不包含原始opaque票据。
 *
 * @author Rain
 * @since 6.1.0
 */
public record ExternalBindingTicket(String clientId, Long tenantId, UserTypeEnum userType,
                                    VerifiedExternalIdentity identity, Instant expiresAt,
                                    int failedAttempts) implements Serializable {
    /**
     * 避免日志输出身份材料。
     */
    @Override
    public String toString() {
        return "ExternalBindingTicket[clientId=" + clientId + "]";
    }
}
