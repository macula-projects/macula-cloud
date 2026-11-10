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
package dev.macula.cloud.system.service.impl;

import dev.macula.cloud.system.form.OAuth2ClientForm;
import dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration;
import dev.macula.cloud.system.protocol.OAuth2ProtocolSettings;
import java.util.Set;

/**
 * 前序用例输入的显式迁移，生产服务不读取旧协议表单字段。
 * @author Rain
 * @since 6.1.0
 */
public final class ProtocolConfigurationTestFixtures {
    /** 禁止实例化。 */
    private ProtocolConfigurationTestFixtures() { }

    /** 保留非法组合供负向校验；双协议按真实能力拆分。 */
    public static OAuth2ClientForm configure(OAuth2ClientForm form) {
        Set<String> protocols = form.getEnabledProtocols() == null ? Set.of() : form.getEnabledProtocols();
        form.setProtocolConfigurations(protocols.stream().sorted().map(protocol -> {
            var settings = new OAuth2ProtocolSettings();
            org.springframework.beans.BeanUtils.copyProperties(form, settings);
            if (settings.getAuthorizationCodeTimeToLive() == null) { settings.setAuthorizationCodeTimeToLive(300L); }
            if (settings.getAccessTokenTimeToLive() == null) { settings.setAccessTokenTimeToLive(3600L); }
            if (settings.getRefreshTokenTimeToLive() == null) { settings.setRefreshTokenTimeToLive(3600L); }
            if (settings.getTokenFormat() == null) { settings.setTokenFormat("reference"); }
            if (protocols.size() > 1 && "OIDC".equals(protocol)) {
                settings.setAuthorizationGrantTypes(java.util.Arrays.stream(settings.getAuthorizationGrantTypes().split(","))
                    .filter(value -> Set.of("authorization_code", "refresh_token").contains(value)).collect(java.util.stream.Collectors.joining(",")));
            }
            if (protocols.size() > 1 && "OAUTH2_1".equals(protocol)) {
                settings.setScopes(java.util.Arrays.stream(settings.getScopes().split(","))
                    .filter(value -> !Set.of("openid", "profile").contains(value)).collect(java.util.stream.Collectors.joining(",")));
            }
            return new ApplicationProtocolConfiguration(protocol, 1, true, settings);
        }).toList());
        return form;
    }
}
