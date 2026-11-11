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
package dev.macula.cloud.iam.authentication.thirdparty.browser;

import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyAuthenticationToken;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyLoginContext;
import dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity;
import dev.macula.cloud.iam.authentication.thirdparty.provider.ThirdPartyProviderAdapterRegistry;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;

/**
 * 网页第三方认证的统一编排；每次执行都复核固定来源，再解析本地身份。
 *
 * @author Macula
 * @since 6.1.0
 */
@RequiredArgsConstructor
public final class ThirdPartyAuthenticationProvider implements AuthenticationProvider {
    private final ThirdPartyProviderAdapterRegistry adapters;
    private final IdentityProviderConfigService configs;
    private final ThirdPartyIdentityResolutionService identities;

    /**
     * 不信任Token携带的平台身份；只接受当前来源服务端核验结果。
     */
    @Override
    public Authentication authenticate(Authentication authentication) {
        ThirdPartyAuthenticationToken token = (ThirdPartyAuthenticationToken) authentication;
        ThirdPartyLoginContext context = token.getLoginContext();
        var adapter = adapters.require(token.getProviderType());
        var config = configs.requireEnabledById(context.registrationId(), token.getProviderType())
                .forCapability(context.loginCapability());
        VerifiedExternalIdentity identity = adapter.verify(config, token.getCredentials().toString(),
                token.getRedirectUri(), context);
        var user = identities.resolve(identity, context);
        return ThirdPartyAuthenticationToken.authenticated(token.getProviderType(), user, context);
    }

    /**
     * 仅处理服务端过滤器构造的网页第三方Token。
     */
    @Override
    public boolean supports(Class<?> authentication) {
        return ThirdPartyAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
