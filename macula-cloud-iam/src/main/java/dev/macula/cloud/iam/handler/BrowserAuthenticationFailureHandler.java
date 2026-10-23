/*
 * Copyright (c) 2026 Macula
 * macula.dev, China
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

import java.io.IOException;
import dev.macula.cloud.iam.authentication.thirdparty.PendingThirdPartyBindingRepository;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyIdentityResolutionService.BindingRequiredException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/**
 * 统一处理浏览器认证失败：待绑定身份进入绑定页，其余失败只返回受控原因码。
 *
 * @author Rain
 * @since 6.1.0
 */
public class BrowserAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private static final String LOGIN_PATH = "/login";
    private static final String BINDING_PATH = "/login/bind";
    private static final String QUERY_SEPARATOR = "?";
    private static final String PARAMETER_SEPARATOR = "=";

    private final PendingThirdPartyBindingRepository pending;
    private final String issuer;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    public BrowserAuthenticationFailureHandler(PendingThirdPartyBindingRepository pending, String issuer) {
        this.pending = pending;
        this.issuer = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
        AuthenticationException exception) throws IOException, ServletException {
        if (exception instanceof BindingRequiredException required) {
            pending.save(request, required);
            redirectStrategy.sendRedirect(request, response, issuer + BINDING_PATH);
            return;
        }
        AuthenticationFailureReason reason = AuthenticationFailureReason.from(exception);
        redirectStrategy.sendRedirect(request, response, failureUrl(reason));
    }

    private String failureUrl(AuthenticationFailureReason reason) {
        return issuer + LOGIN_PATH + QUERY_SEPARATOR + OAuth2ParameterNames.ERROR + PARAMETER_SEPARATOR + reason.code();
    }
}
