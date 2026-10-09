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

package dev.macula.cloud.iam.playground;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * 仅认证经过专用 converter 且当前启用的演示 Device 客户端。
 * @author Rain
 * @since 6.1.0
 */
public final class DevicePublicClientAuthenticationProvider implements AuthenticationProvider {
    private final RegisteredClientRepository clients;
    private final PlaygroundAccessPolicy policy;
    public DevicePublicClientAuthenticationProvider(RegisteredClientRepository clients, PlaygroundAccessPolicy policy) {
        this.clients = clients;
        this.policy = policy;
    }

    @Override public Authentication authenticate(Authentication authentication) {
        if (!supports(authentication.getClass())) return null;
        var client = policy.isEnabled() ? clients.findByClientId(PlaygroundRegisteredClientRepository.DEVICE) : null;
        if (client == null || !PlaygroundRegisteredClientRepository.DEVICE.equals(authentication.getName())
            || !client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)
            || !client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.DEVICE_CODE)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }
        return new OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.NONE, null);
    }

    @Override public boolean supports(Class<?> type) {
        return DevicePublicClientAuthenticationConverter.DeviceClientToken.class.equals(type);
    }
}
