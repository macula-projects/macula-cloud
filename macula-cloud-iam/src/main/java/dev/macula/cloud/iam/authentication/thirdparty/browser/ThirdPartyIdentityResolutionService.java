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

import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyLoginContext;
import dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity;
import dev.macula.cloud.iam.service.identity.IdentitySourceBindingPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/**
 * 浏览器核验结果交给统一来源策略；不包含任何企业自动绑定特例。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class ThirdPartyIdentityResolutionService {
    private final IdentitySourceBindingPolicyService policy;

    /**
     * 首次无合法绑定或匹配结果时进入账号归属证明页面。
     */
    public UserDetails resolve(VerifiedExternalIdentity identity, ThirdPartyLoginContext context) {
        if (!java.util.Objects.equals(identity.sourceVersion(), context.sourceVersion()) ||
                !identity.flow().equals(context.flow())
                || !identity.registrationId().equals(context.registrationId()) ||
                !identity.sourceType().equals(context.sourceType())
                || !identity.loginCapability().equals(context.loginCapability())) {
            throw new AuthenticationServiceException("身份源上下文不一致");
        }
        var resolved = context.clientAuthorizationVersion() == null
                ? policy.resolve(context.clientId(), context.tenantId(), context.userType(), identity)
                : policy.resolve(context.clientId(), context.tenantId(), context.userType(), identity,
                context.clientAuthorizationVersion());
        return resolved.orElseThrow(() -> new BindingRequiredException(identity, context));
    }

    /**
     * 仅保存已核验外部身份，不代表获得本地权限。
     *
     * @since 6.1.0
     */
    public static final class BindingRequiredException extends AuthenticationServiceException {
        private final VerifiedExternalIdentity identity;
        private final ThirdPartyLoginContext context;

        /**
         * 保存来源固定的核验材料。
         */
        public BindingRequiredException(VerifiedExternalIdentity identity, ThirdPartyLoginContext context) {
            super("第三方账号待绑定");
            this.identity = identity;
            this.context = context;
        }

        /**
         * 返回服务端核验身份。
         */
        public VerifiedExternalIdentity identity() {
            return identity;
        }

        /**
         * 返回浏览器路由快照。
         */
        public ThirdPartyLoginContext context() {
            return context;
        }
    }
}
