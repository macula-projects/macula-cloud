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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import dev.macula.cloud.iam.protocol.oauth2.endpoint.OAuth2EndpointUtils;
import dev.macula.cloud.system.enums.ExternalCodeType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

/**
 * 将 external_code Token Endpoint 请求转换为受控认证对象。
 *
 * @author Rain
 * @since 6.1.0
 */
public class OAuth2ExternalCodeAuthenticationConverter implements AuthenticationConverter {

    static final int MAX_CODE_LENGTH = 8192;
    private static final int MAX_NONCE_LENGTH = 256;
    private static final Set<String> ALLOWED_PARAMETERS = Set.of(OAuth2ParameterNames.GRANT_TYPE,
            OAuth2ParameterNames.CLIENT_ID, OAuth2ParameterNames.CODE, OAuth2ParameterNames.SCOPE,
            OAuth2ExternalCodeParameterNames.CODE_TYPE,
            org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames.NONCE,
            OAuth2ExternalCodeParameterNames.PROVIDER_CODE_VERIFIER,
            OAuth2ExternalCodeParameterNames.PROVIDER_REDIRECT_URI);

    /**
     * 解包框架转发头包装，只使用连接地址或容器已校验的可信代理地址，防止任意XFF换IP限流。
     */
    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!ExternalCodeType.GRANT_TYPE.equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))) {
            return null;
        }
        checkParams(request);
        MultiValueMap<String, String> parameters = OAuth2EndpointUtils.getParameters(request);
        String scope = parameters.getFirst(OAuth2ParameterNames.SCOPE);
        if (parameters.containsKey(OAuth2ParameterNames.SCOPE)
                && parameters.get(OAuth2ParameterNames.SCOPE).size() != 1) {
            invalid(OAuth2ParameterNames.SCOPE);
        }
        Authentication client = SecurityContextHolder.getContext().getAuthentication();
        if (client == null) {
            invalid(OAuth2ErrorCodes.INVALID_CLIENT);
        }
        Set<String> scopes = StringUtils.hasText(scope)
                ? new HashSet<>(Arrays.asList(StringUtils.delimitedListToStringArray(scope, " "))) : Set.of();
        Map<String, Object> additional = parameters.entrySet().stream()
                .filter(entry -> !Set.of(OAuth2ParameterNames.GRANT_TYPE, OAuth2ParameterNames.SCOPE)
                        .contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get(0)));
        OAuth2ExternalCodeAuthenticationToken token = buildToken(client, scopes, additional);
        jakarta.servlet.ServletRequest connection = request;
        while (connection instanceof jakarta.servlet.ServletRequestWrapper wrapper) {
            connection = wrapper.getRequest();
        }
        var session = request.getSession(false);
        token.setDetails(new org.springframework.security.web.authentication.WebAuthenticationDetails(
                connection.getRemoteAddr(), session == null ? null : session.getId()));
        return token;
    }

    /**
     * 先拒绝重复/越界参数；nonce只用于本扩展的请求响应关联。
     */
    private void checkParams(HttpServletRequest request) {
        MultiValueMap<String, String> parameters = OAuth2EndpointUtils.getParameters(request);
        parameters.keySet().stream().filter(parameter -> !ALLOWED_PARAMETERS.contains(parameter))
                .findFirst().ifPresent(this::invalid);
        requireSingle(parameters, OAuth2ParameterNames.GRANT_TYPE, ExternalCodeType.GRANT_TYPE.length());
        String code = requireSingle(parameters, OAuth2ParameterNames.CODE, MAX_CODE_LENGTH);
        if (parameters.containsKey(org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames.NONCE)) {
            requireSingle(parameters, org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames.NONCE,
                    MAX_NONCE_LENGTH);
        }
        if (parameters.containsKey(OAuth2ExternalCodeParameterNames.PROVIDER_CODE_VERIFIER)) {
            String verifier = requireSingle(parameters, OAuth2ExternalCodeParameterNames.PROVIDER_CODE_VERIFIER, 128);
            if (!verifier.matches("[A-Za-z0-9._~-]{43,128}")) {
                invalid(OAuth2ExternalCodeParameterNames.PROVIDER_CODE_VERIFIER);
            }
        }
        if (parameters.containsKey(OAuth2ExternalCodeParameterNames.PROVIDER_REDIRECT_URI)) {
            requireSingle(parameters, OAuth2ExternalCodeParameterNames.PROVIDER_REDIRECT_URI, 2048);
        }
        String codeType = requireSingle(parameters, OAuth2ExternalCodeParameterNames.CODE_TYPE, 64);
        if (!ExternalCodeType.GRANT_TYPE.equals(parameters.getFirst(OAuth2ParameterNames.GRANT_TYPE))) {
            invalid(OAuth2ParameterNames.GRANT_TYPE);
        }
        try {
            ExternalCodeType.fromValue(codeType);
        } catch (IllegalArgumentException ex) {
            invalid(OAuth2ExternalCodeParameterNames.CODE_TYPE);
        }
        if (!code.equals(code.trim())) {
            invalid(OAuth2ParameterNames.CODE);
        }
    }

    /**
     * 仅从已完成参数校验的请求构造待认证凭证。
     */
    private OAuth2ExternalCodeAuthenticationToken buildToken(Authentication clientPrincipal,
            Set<String> requestedScopes, Map<String, Object> additionalParameters) {
        String code = (String) additionalParameters.get(OAuth2ParameterNames.CODE);
        String codeType = (String) additionalParameters.get(OAuth2ExternalCodeParameterNames.CODE_TYPE);
        return new OAuth2ExternalCodeAuthenticationToken(clientPrincipal, requestedScopes, additionalParameters,
                ExternalCodeType.fromValue(codeType), code);
    }

    /**
     * 拒绝重复、空白和越界协议参数。
     */
    private String requireSingle(MultiValueMap<String, String> parameters, String name, int maxLength) {
        String value = parameters.getFirst(name);
        if (!StringUtils.hasText(value) || parameters.get(name) == null || parameters.get(name).size() != 1 ||
                value.length() > maxLength) {
            invalid(name);
        }
        return value;
    }

    /**
     * 使用不包含凭据值的标准协议错误。
     */
    private void invalid(String parameter) {
        OAuth2EndpointUtils.throwError(OAuth2ErrorCodes.INVALID_REQUEST, parameter,
                OAuth2EndpointUtils.ACCESS_TOKEN_REQUEST_ERROR_URI);
    }
}
