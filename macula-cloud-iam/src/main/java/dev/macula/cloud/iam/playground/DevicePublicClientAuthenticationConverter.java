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

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;

/**
 * 仅演示 Device 的两个端点接受 none 客户端认证，不影响公共授权码 PKCE。
 * @author Rain
 * @since 6.1.0
 */
public final class DevicePublicClientAuthenticationConverter implements AuthenticationConverter {
    private final PlaygroundAccessPolicy policy;
    public DevicePublicClientAuthenticationConverter(PlaygroundAccessPolicy policy) { this.policy = policy; }

    @Override public Authentication convert(HttpServletRequest request) {
        if (!policy.isEnabled() || !"POST".equals(request.getMethod())
            || !PlaygroundRegisteredClientRepository.DEVICE.equals(request.getParameter("client_id"))) return null;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean device = "/oauth2/device_authorization".equals(path);
        boolean token = "/oauth2/token".equals(path)
            && AuthorizationGrantType.DEVICE_CODE.getValue().equals(request.getParameter("grant_type"));
        if (!device && !token) return null;
        if (request.getHeader("Authorization") != null || request.getParameter("client_secret") != null
            || request.getParameter("client_assertion") != null || request.getParameterValues("client_id").length != 1
            || (token && request.getParameterValues("grant_type").length != 1)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }
        return new DeviceClientToken(PlaygroundRegisteredClientRepository.DEVICE);
    }

    /** Distinct type prevents this adapter from matching other none-authentication requests. */
    static final class DeviceClientToken extends OAuth2ClientAuthenticationToken {
        private static final long serialVersionUID = 1L;
        DeviceClientToken(String id) { super(id, ClientAuthenticationMethod.NONE, null, Map.of()); }
    }
}
