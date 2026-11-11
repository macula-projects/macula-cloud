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
package dev.macula.cloud.iam.authentication.sms;

import dev.macula.cloud.iam.authentication.hosted.HostedLoginClientPolicyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * 将短信登录请求转换为 {@link SmsAuthenticationToken}。
 *
 * @author n1
 * @since 6.1.0
 */
public class SmsAuthenticationFilter extends AbstractAuthenticationProcessingFilter {
    public static final String SPRING_SECURITY_FORM_PHONE_KEY = "phone";

    public static final String SPRING_SECURITY_FORM_CODE_KEY = "code";

    private static final PathPatternRequestMatcher DEFAULT_REQUEST_MATCHER =
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/login/sms");

    private final String phoneParameter = SPRING_SECURITY_FORM_PHONE_KEY;

    private final String codeParameter = SPRING_SECURITY_FORM_CODE_KEY;

    private final Converter<HttpServletRequest, SmsAuthenticationToken> smsAuthenticationTokenConverter;

    public SmsAuthenticationFilter() {
        super(DEFAULT_REQUEST_MATCHER);
        this.smsAuthenticationTokenConverter = defaultConverter();
    }

    public SmsAuthenticationFilter(AuthenticationManager authenticationManager) {
        super(DEFAULT_REQUEST_MATCHER, authenticationManager);
        this.smsAuthenticationTokenConverter = defaultConverter();
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, @NonNull HttpServletResponse response)
            throws AuthenticationException {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            throw new AuthenticationServiceException("Authentication method not supported: " + request.getMethod());
        }

        SmsAuthenticationToken authRequest = smsAuthenticationTokenConverter.convert(request);
        // Allow subclasses to set the "details" property
        setDetails(request, authRequest);
        return this.getAuthenticationManager().authenticate(authRequest);
    }

    /**
     * 验证码消费绑定到发码时的可信Client，门户路径为空。
     */
    private Converter<HttpServletRequest, SmsAuthenticationToken> defaultConverter() {
        return request -> {
            String phone = request.getParameter(this.phoneParameter);
            phone = (phone != null) ? phone.trim() : "";
            String code = request.getParameter(this.codeParameter);
            code = (code != null) ? code.trim() : "";
            return new SmsAuthenticationToken(phone, code, HostedLoginClientPolicyService.context(request).clientId());
        };
    }

    protected void setDetails(HttpServletRequest request, SmsAuthenticationToken authRequest) {
        authRequest.setDetails(this.authenticationDetailsSource.buildDetails(request));
    }
}
