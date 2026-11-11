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

import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractAuthenticationFilterConfigurer;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static org.springframework.security.config.annotation.web.builders.FilterOrderRegUtils.Position.AFTER;
import static org.springframework.security.config.annotation.web.builders.FilterOrderRegUtils.registryFilterOrder;

/**
 * 复用框架认证生命周期，将邮箱Provider仅注册到托管登录链。
 *
 * @author Macula
 * @since 6.1.0
 */
public class EmailLoginFilterConfigurer extends AbstractAuthenticationFilterConfigurer<HttpSecurity,
        EmailLoginFilterConfigurer, EmailCodeAuthenticationFilter> {
    /**
     * 使用独立认证入口，不复用旧协议邮箱端点。
     */
    public EmailLoginFilterConfigurer() {
        super(new EmailCodeAuthenticationFilter(), EmailCodeAuthenticationFilter.PATH);
    }

    /**
     * 框架继续处理SecurityContext、Session固定保护与成功续转。
     */
    @Override
    public void init(HttpSecurity http) {
        ApplicationContext context = http.getSharedObject(ApplicationContext.class);
        http.authenticationProvider(postProcess(new EmailCodeAuthenticationProvider(
                context.getBean(EmailCodeService.class), context.getBean(EmailUserDetailsService.class))));
        registryFilterOrder(http, getAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class, AFTER);
        super.init(http);
    }

    /**
     * 认证副作用仅允许POST。
     */
    @Override
    protected RequestMatcher createLoginProcessingUrlMatcher(String path) {
        return PathPatternRequestMatcher.pathPattern(HttpMethod.POST, path);
    }
}
