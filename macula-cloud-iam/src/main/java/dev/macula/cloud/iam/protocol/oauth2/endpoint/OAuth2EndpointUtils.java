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

package dev.macula.cloud.iam.protocol.oauth2.endpoint;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Token端点参数与规范错误构造；保留重复参数供Converter校验，不能先取单值。
 *
 * @author Rain
 * @since 6.1.0
 */
public final class OAuth2EndpointUtils {
    public static final String ACCESS_TOKEN_REQUEST_ERROR_URI = "https://datatracker.ietf" +
            ".org/doc/html/rfc6749#section-5.2";

    /**
     * 端点静态帮助方法，不作为状态服务实例化。
     */
    private OAuth2EndpointUtils() {
    }

    /**
     * 保留请求中每个参数的所有值，由各协议Converter决定可接受字段与数量。
     */
    public static MultiValueMap<String, String> getParameters(HttpServletRequest request) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>(parameterMap.size());
        parameterMap.forEach((key, values) -> {
            for (String value : values) {
                parameters.add(key, value);
            }
        });
        return parameters;
    }

    /**
     * 抛出带有规范引用和参数名称的协议错误，不附带参数值或凭据。
     */
    public static void throwError(String errorCode, String parameterName, String errorUri) {
        throw new OAuth2AuthenticationException(
                new OAuth2Error(errorCode, "OAuth 2.0 Parameter: " + parameterName, errorUri));
    }
}
