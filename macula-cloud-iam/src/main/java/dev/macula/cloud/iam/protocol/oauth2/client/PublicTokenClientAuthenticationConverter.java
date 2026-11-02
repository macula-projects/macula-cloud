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
package dev.macula.cloud.iam.protocol.oauth2.client;

import dev.macula.boot.constants.SecurityConstants;
import dev.macula.cloud.iam.protocol.oauth2.grant.email.OAuth2ResourceOwnerEmailAuthenticationToken;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.OAuth2ExternalCodeAuthenticationToken;
import dev.macula.cloud.iam.utils.OAuth2EndpointUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;

/**
 * 为无密钥 Token grant 提取受限的 public Client 身份。
 *
 * @author Rain
 * @since 6.1.0
 */
public final class PublicTokenClientAuthenticationConverter implements AuthenticationConverter {
    private static final String PASSWORD_GRANT = "password";
    private static final Set<String> SUPPORTED_GRANTS = Set.of(PASSWORD_GRANT,
        SecurityConstants.GRANT_TYPE_SMS, OAuth2ResourceOwnerEmailAuthenticationToken.GRANT_TYPE_VALUE,
        OAuth2ExternalCodeAuthenticationToken.GRANT_TYPE.getValue(), AuthorizationGrantType.REFRESH_TOKEN.getValue());
    private final RequestMatcher tokenEndpointMatcher;

    public PublicTokenClientAuthenticationConverter(RequestMatcher tokenEndpointMatcher) {
        Assert.notNull(tokenEndpointMatcher, "tokenEndpointMatcher cannot be null");
        this.tokenEndpointMatcher = tokenEndpointMatcher;
    }

    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!tokenEndpointMatcher.matches(request)) {
            return null;
        }
        MultiValueMap<String, String> parameters = OAuth2EndpointUtils.getParameters(request);
        String grantType = parameters.getFirst(OAuth2ParameterNames.GRANT_TYPE);
        if (!StringUtils.hasText(grantType) || !SUPPORTED_GRANTS.contains(grantType)) {
            return null;
        }
        if (parameters.get(OAuth2ParameterNames.GRANT_TYPE).size() != 1) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }
        if (hasConfidentialCredentials(request, parameters)) {
            return null;
        }
        String clientId = parameters.getFirst(OAuth2ParameterNames.CLIENT_ID);
        if (!StringUtils.hasText(clientId) || parameters.get(OAuth2ParameterNames.CLIENT_ID) == null
            || parameters.get(OAuth2ParameterNames.CLIENT_ID).size() != 1) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }
        return new OAuth2ClientAuthenticationToken(clientId, ClientAuthenticationMethod.NONE, null,
            Map.of(OAuth2ParameterNames.GRANT_TYPE, grantType));
    }

    static boolean supportsGrant(String grantType) {
        return SUPPORTED_GRANTS.contains(grantType);
    }

    private boolean hasConfidentialCredentials(HttpServletRequest request,
        MultiValueMap<String, String> parameters) {
        return StringUtils.hasText(request.getHeader(HttpHeaders.AUTHORIZATION))
            || parameters.containsKey(OAuth2ParameterNames.CLIENT_SECRET)
            || parameters.containsKey(OAuth2ParameterNames.CLIENT_ASSERTION)
            || parameters.containsKey(OAuth2ParameterNames.CLIENT_ASSERTION_TYPE);
    }
}
