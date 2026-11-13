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
import dev.macula.cloud.system.enums.IdentityLoginCapability;
import dev.macula.cloud.system.enums.IdentitySourceType;

/**
 * 一次性state固定具体来源和Client用户路由，旧缺字段状态必须重新发起。
 *
 * @author Rain
 * @since 6.1.0
 */
public record ThirdPartyLoginContext(String providerType, String loginMode, String clientId, Instant createdAt,
                                     String savedRequestTarget, Long registrationId, String sourceType,
                                     Long tenantId, UserTypeEnum userType, Long sourceVersion,
                                     String flow, String upstreamRedirectUri, String loginCapability,
                                     Long clientAuthorizationVersion) implements Serializable {
    /**
     * 旧状态缺少Client版本，仅非端内历史流程可继续按原合同处理。
     */
    public ThirdPartyLoginContext(String providerType, String loginMode, String clientId, Instant createdAt,
            String savedRequestTarget, Long registrationId, String sourceType, Long tenantId, UserTypeEnum userType,
            Long sourceVersion, String flow, String upstreamRedirectUri, String loginCapability) {
        this(providerType, loginMode, clientId, createdAt, savedRequestTarget, registrationId, sourceType, tenantId,
                userType,
                sourceVersion, flow, upstreamRedirectUri, loginCapability, null);
    }

    /**
     * 兼容现有调用，使用已固定来源和模式生成能力，旧序列化state不会经过此重载。
     */
    public ThirdPartyLoginContext(String providerType, String loginMode, String clientId, Instant createdAt,
            String savedRequestTarget, Long registrationId, String sourceType, Long tenantId, UserTypeEnum userType,
            Long sourceVersion, String flow, String upstreamRedirectUri) {
        this(providerType, loginMode, clientId, createdAt, savedRequestTarget, registrationId, sourceType,
                tenantId, userType, sourceVersion, flow, upstreamRedirectUri,
                sourceType == null || loginMode == null ? null :
                        IdentityLoginCapability.browser(
                                        IdentitySourceType.fromValue(sourceType),
                                        loginMode)
                                .value());
    }
}
