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
package dev.macula.cloud.iam.protocol.oauth2.grant.email;

import dev.macula.cloud.iam.protocol.oauth2.grant.base.OAuth2ResourceOwnerBaseAuthenticationConverter;
import dev.macula.cloud.iam.utils.OAuth2EndpointUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;

/**
 * 把邮箱和标准 {@code code} 参数转换为邮箱验证码 grant 请求。
 *
 * @author Rain
 * @since 6.1.0
 */
public class OAuth2ResourceOwnerEmailAuthenticationConverter
    extends OAuth2ResourceOwnerBaseAuthenticationConverter<OAuth2ResourceOwnerEmailAuthenticationToken> {
    /** 邮箱参数名。 */
    public static final String EMAIL_PARAMETER = "email";
    private static final Set<String> ALLOWED_PARAMETERS = Set.of(OAuth2ParameterNames.GRANT_TYPE,
        OAuth2ParameterNames.CLIENT_ID, OAuth2ParameterNames.SCOPE, OAuth2ParameterNames.CODE, EMAIL_PARAMETER);

    @Override
    public boolean support(String grantType) {
        return OAuth2ResourceOwnerEmailAuthenticationToken.GRANT_TYPE_VALUE.equals(grantType);
    }

    @Override
    public OAuth2ResourceOwnerEmailAuthenticationToken buildToken(Authentication clientPrincipal,
        Set<String> requestedScopes, Map<String, Object> additionalParameters) {
        return new OAuth2ResourceOwnerEmailAuthenticationToken(clientPrincipal, requestedScopes,
            additionalParameters);
    }

    @Override
    public void checkParams(HttpServletRequest request) {
        MultiValueMap<String, String> parameters = OAuth2EndpointUtils.getParameters(request);
        parameters.keySet().stream().filter(parameter -> !ALLOWED_PARAMETERS.contains(parameter))
            .findFirst().ifPresent(this::invalid);
        requireSingle(parameters, EMAIL_PARAMETER);
        requireSingle(parameters, OAuth2ParameterNames.CODE);
    }

    private void requireSingle(MultiValueMap<String, String> parameters, String name) {
        String value = parameters.getFirst(name);
        if (!StringUtils.hasText(value) || parameters.get(name).size() != 1) {
            invalid(name);
        }
    }

    private void invalid(String parameter) {
        OAuth2EndpointUtils.throwError(OAuth2ErrorCodes.INVALID_REQUEST, parameter,
            OAuth2EndpointUtils.ACCESS_TOKEN_REQUEST_ERROR_URI);
    }
}
