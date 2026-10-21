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

import dev.macula.cloud.iam.service.support.SysUserService;
import dev.macula.cloud.iam.enums.ThirdPartyProviderType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 将已核验的外部 subject 解析为全局本地账号，未绑定时只发出待绑定信号。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class ThirdPartyIdentityResolutionService {
    private final IdentityProviderConfigService configs;
    private final SysUserService users;
    private final ThirdPartyUserDetailsService details;

    public UserDetails resolve(String providerType, String subject, ThirdPartyLoginContext context) {
        boolean directMatch = ThirdPartyProviderType.WECOM.providerType().equals(providerType)
            && users.findUniqueByUsername(subject) != null;
        Optional<String> bound = configs.findBoundUsername(providerType, subject);
        if (directMatch && bound.isPresent() && !subject.equals(bound.get())) {
            throw new AuthenticationServiceException("第三方账号绑定冲突");
        }
        String username = directMatch ? subject : bound.orElse(null);
        if (username == null) {
            throw new BindingRequiredException(providerType, subject, context);
        }
        return details.load(username);
    }

    /**
     * 表示外部身份已验证，但尚未授予本地权限。
     *
     * @since 6.1.0
     */
    public static final class BindingRequiredException extends AuthenticationServiceException {
        private final String providerType;
        private final String subject;
        private final ThirdPartyLoginContext context;

        public BindingRequiredException(String providerType, String subject, ThirdPartyLoginContext context) {
            super("第三方账号待绑定");
            this.providerType = providerType;
            this.subject = subject;
            this.context = context;
        }

        public String providerType() {
            return providerType;
        }

        public String subject() {
            return subject;
        }

        public ThirdPartyLoginContext context() {
            return context;
        }
    }
}
