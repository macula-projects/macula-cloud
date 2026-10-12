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

package dev.macula.cloud.iam.authentication.sms;

import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractAuthenticationFilterConfigurer;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;

import static org.springframework.security.config.annotation.web.builders.FilterOrderRegUtils.Position.AFTER;
import static org.springframework.security.config.annotation.web.builders.FilterOrderRegUtils.registryFilterOrder;

/**
 * 配置短信验证码登录过滤器及其认证 Provider。
 *
 * @param <H> HttpSecurity 构建器类型
 * @author rain
 * @since 6.1.0
 */
public class SmsLoginFilterConfigurer<H extends HttpSecurityBuilder<H>>
    extends AbstractAuthenticationFilterConfigurer<H, SmsLoginFilterConfigurer<H>, SmsAuthenticationFilter> {

    private SmsUserDetailsService smsUserDetailsService;

    private SmsService smsService;

    public SmsLoginFilterConfigurer() {
        super(new SmsAuthenticationFilter(), "/login/sms");
    }

    public SmsLoginFilterConfigurer<H> smsUserDetailsService(
        SmsUserDetailsService smsUserDetailsService) {
        this.smsUserDetailsService = smsUserDetailsService;
        return this;
    }

    public SmsLoginFilterConfigurer<H> smsService(SmsService smsService) {
        this.smsService = smsService;
        return this;
    }

    @Override
    public void init(H http) {
        http.authenticationProvider(postProcess(authenticationProvider(http)));
        registryFilterOrder((HttpSecurity)http, getAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class,
            AFTER);
        super.init(http);
    }

    @Override
    protected RequestMatcher createLoginProcessingUrlMatcher(String loginProcessingUrl) {
        return PathPatternRequestMatcher.pathPattern(HttpMethod.POST, loginProcessingUrl);
    }

    protected AuthenticationProvider authenticationProvider(H http) {
        ApplicationContext applicationContext = http.getSharedObject(ApplicationContext.class);
        SmsUserDetailsService smsUserDetailsService =
            this.smsUserDetailsService != null ? this.smsUserDetailsService
                : getBeanOrNull(applicationContext, SmsUserDetailsService.class);
        Assert.notNull(smsUserDetailsService, "smsUserDetailsService is required");

        SmsService smsService =
            this.smsService != null ? this.smsService : getBeanOrNull(applicationContext, SmsService.class);
        Assert.notNull(smsService, "smsService is required");

        return new SmsAuthenticationProvider(smsUserDetailsService, smsService);
    }

    private <T> T getBeanOrNull(ApplicationContext applicationContext, Class<T> beanType) {
        return applicationContext == null ? null : applicationContext.getBeanProvider(beanType).getIfUnique();
    }
}
