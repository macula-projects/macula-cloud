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

package dev.macula.cloud.iam.handler;

import cn.hutool.core.util.StrUtil;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * {@code OAuth2AuthenticationExceptionEntryPoint} is
 *
 * @author rain
 * @since 2023/4/14 22:35
 */
public class OAuth2AuthenticationExceptionEntryPoint extends LoginUrlAuthenticationEntryPoint {
    /**
     * @param loginFormUrl URL where the login page can be found. Should either be relative to the web-app context path
     *                     (include a leading {@code /}) or an absolute URL.
     */
    public OAuth2AuthenticationExceptionEntryPoint(String loginFormUrl) {
        super(loginFormUrl);
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
        AuthenticationException authException) throws IOException, ServletException {
        // 如果存在client_id参数，则添加到COOKIE
        String clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
        if (StrUtil.isNotBlank(clientId)) {
            Cookie cookie = new Cookie(OAuth2ParameterNames.CLIENT_ID, clientId);
            cookie.setPath("/");
            response.addCookie(cookie);
        }
        super.commence(request, response, authException);
    }
}
