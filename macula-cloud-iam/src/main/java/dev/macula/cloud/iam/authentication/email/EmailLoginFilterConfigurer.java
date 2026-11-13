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

import java.util.Objects;

import dev.macula.cloud.iam.authentication.verification.VerificationCodeChannelService;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractAuthenticationFilterConfigurer;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static org.springframework.security.config.annotation.web.builders.FilterOrderRegUtils.Position.AFTER;
import static org.springframework.security.config.annotation.web.builders.FilterOrderRegUtils.registryFilterOrder;

/**
 * Email登录链装配；使用独立Configurer类型，避免两通道在HttpSecurity中互相覆盖。
 *
 * @author Macula
 * @since 6.1.0
 */
public final class EmailLoginFilterConfigurer extends AbstractAuthenticationFilterConfigurer<HttpSecurity,
        EmailLoginFilterConfigurer, EmailCodeAuthenticationFilter> {
    private final VerificationCodeChannelService codes;
    private final EmailUserDetailsService users;

    /** 显式接收依赖，不隐式查询ApplicationContext。 */
    public EmailLoginFilterConfigurer(VerificationCodeChannelService codes, EmailUserDetailsService users) {
        super(new EmailCodeAuthenticationFilter(), EmailCodeAuthenticationFilter.PATH);
        this.codes = Objects.requireNonNull(codes);
        this.users = Objects.requireNonNull(users);
    }

    /** 仅注册到登录链；由框架处理Session保护、上下文保存及成功续转。 */
    @Override
    public void init(HttpSecurity http) {
        http.authenticationProvider(postProcess(new EmailCodeAuthenticationProvider(codes, users)));
        registryFilterOrder(http, getAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class, AFTER);
        super.init(http);
    }

    /** 认证副作用只允许POST。 */
    @Override
    protected RequestMatcher createLoginProcessingUrlMatcher(String path) {
        return PathPatternRequestMatcher.pathPattern(HttpMethod.POST, path);
    }
}
