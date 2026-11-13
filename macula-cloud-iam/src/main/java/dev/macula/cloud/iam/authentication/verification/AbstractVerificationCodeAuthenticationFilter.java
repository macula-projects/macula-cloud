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
package dev.macula.cloud.iam.authentication.verification;

import dev.macula.cloud.iam.authentication.hosted.HostedLoginClientPolicyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * 验证码表单模板：共享输入边界与可信Client提取，保留各通道原认证载体。
 *
 * @author Macula
 * @since 6.1.0
 */
public abstract class AbstractVerificationCodeAuthenticationFilter<T extends AbstractAuthenticationToken>
        extends AbstractAuthenticationProcessingFilter {
    private static final int MAX_RAW_FIELD_LENGTH = 1024;
    private final String identifierParameter;
    private final int identifierLimit;

    /** 入口及字段由通道固定，不能从请求切换认证方式。 */
    protected AbstractVerificationCodeAuthenticationFilter(String path, String identifierParameter, int identifierLimit) {
        super(PathPatternRequestMatcher.pathPattern(HttpMethod.POST, path));
        this.identifierParameter = identifierParameter;
        this.identifierLimit = identifierLimit;
    }

    /** CSRF与Client策略在上游执行；直接调用时也拒绝非POST与多值凭据。 */
    @Override
    public final Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            throw new AuthenticationServiceException("Authentication method not supported");
        }
        String identifier = single(request, identifierParameter, identifierLimit);
        String code = single(request, "code", 6);
        if (!code.matches("[0-9]{6}")) {
            throw new BadCredentialsException("账号或验证码无效");
        }
        T token = createToken(identifier, code, HostedLoginClientPolicyService.context(request).clientId());
        token.setDetails(authenticationDetailsSource.buildDetails(request));
        return getAuthenticationManager().authenticate(token);
    }

    /** 原始长度限制防止巨量空白输入；业务长度按trim后的字段校验。 */
    private String single(HttpServletRequest request, String name, int limit) {
        String[] values = request.getParameterValues(name);
        if (values == null || values.length != 1 || values[0] == null
                || values[0].length() > MAX_RAW_FIELD_LENGTH) {
            throw new BadCredentialsException("账号或验证码无效");
        }
        String value = values[0].trim();
        if (value.isEmpty() || value.length() > limit) {
            throw new BadCredentialsException("账号或验证码无效");
        }
        return value;
    }

    /** 保持已持久化认证载体的类名与序列化合同。 */
    protected abstract T createToken(String identifier, String code, String clientId);
}
