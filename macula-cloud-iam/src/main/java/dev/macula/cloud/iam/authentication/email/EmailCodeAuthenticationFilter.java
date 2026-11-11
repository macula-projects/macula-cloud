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
package dev.macula.cloud.iam.authentication.email;

import dev.macula.cloud.iam.authentication.hosted.HostedLoginClientPolicyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * 将托管邮箱表单适配为认证凭据，不从表单读取Client身份。
 *
 * @author Macula
 * @since 6.1.0
 */
public class EmailCodeAuthenticationFilter extends AbstractAuthenticationProcessingFilter {
    public static final String PATH = "/login/email";

    /**
     * 只接受POST，CSRF及Client方法策略由上游安全过滤器执行。
     */
    public EmailCodeAuthenticationFilter() {
        super(PathPatternRequestMatcher.pathPattern(HttpMethod.POST, PATH));
    }

    /**
     * 重复、缺失或越界字段不能被静默取第一个值。
     */
    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
        String email = single(request, "email", 128);
        String code = single(request, "code", 6);
        if (!code.matches("[0-9]{6}")) {
            throw new BadCredentialsException("邮箱或验证码无效");
        }
        var token = new EmailCodeAuthenticationToken(email, code,
                HostedLoginClientPolicyService.context(request).clientId());
        token.setDetails(authenticationDetailsSource.buildDetails(request));
        return getAuthenticationManager().authenticate(token);
    }

    /**
     * 凭据只读取单值，错误消息不回显地址或验证码。
     */
    private String single(HttpServletRequest request, String name, int limit) {
        String[] values = request.getParameterValues(name);
        if (values == null || values.length != 1 || values[0] == null || values[0].isBlank() ||
                values[0].length() > limit) {
            throw new BadCredentialsException("邮箱或验证码无效");
        }
        return values[0].trim();
    }
}
