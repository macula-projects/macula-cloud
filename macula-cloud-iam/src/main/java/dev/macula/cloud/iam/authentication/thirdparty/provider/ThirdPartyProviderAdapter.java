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
package dev.macula.cloud.iam.authentication.thirdparty.provider;

import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyLoginContext;
import dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService;

/**
 * 厂商协议差异的策略边界；只核验外部身份，不解析本地权限或签发Token。
 *
 * @author Macula
 * @since 6.1.0
 */
public interface ThirdPartyProviderAdapter {
    /**
     * 返回稳定厂商标识，不使用展示名称作路由。
     */
    String providerType();

    /**
     * 为当前固定来源构造厂商授权地址。
     */
    String authorizationUri(IdentityProviderConfigService.ProviderConfig config, String state,
            String redirectUri, ThirdPartyLoginContext context);

    /**
     * 根据已校验来源与能力兑换凭证，返回服务端核验身份。
     */
    VerifiedExternalIdentity verify(IdentityProviderConfigService.ProviderConfig config, String code,
            String redirectUri, ThirdPartyLoginContext context);
}
