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
package dev.macula.cloud.iam.protocol.oauth2.grant.externalcode;

import dev.macula.cloud.system.enums.ExternalCodeType;

import java.util.Set;

/**
 * 使用固定身份源配置向对应第三方核验一种或多种 code type。
 *
 * @author Rain
 * @since 6.1.0
 */
public interface ExternalCodeVerifier {
    Set<ExternalCodeType> supportedTypes();

    ExternalCodeVerification verify(ExternalCodeType codeType, Long identityProviderId, String code);

    /** 仅实现了精确上游PKCE合同的适配器可接收证明；默认不扩展既有平台参数。 */
    default ExternalCodeVerification verify(ExternalCodeType codeType, Long identityProviderId, String code,
        String providerCodeVerifier) {
        if (providerCodeVerifier != null) { throw new IllegalArgumentException("当前flow不接收上游PKCE材料"); }
        return verify(codeType, identityProviderId, code);
    }
    /**
     * 仅精确实现AppLink合同的适配器接收已校验回调，其他厂商默认拒绝。
     * @param codeType 已登记Code类型
     * @param identityProviderId Client固定来源
     * @param code 单次厂商Code
     * @param providerCodeVerifier 专用平台PKCE材料
     * @param providerRedirectUri 已认证Client白名单内的精确回调
     * @return 完成厂商核验后的身份
     */
    default ExternalCodeVerification verify(ExternalCodeType codeType, Long identityProviderId, String code,
        String providerCodeVerifier, String providerRedirectUri) {
        if (providerRedirectUri != null) { throw new IllegalArgumentException("当前能力不接收平台回调参数"); }
        return verify(codeType, identityProviderId, code, providerCodeVerifier);
    }

}
