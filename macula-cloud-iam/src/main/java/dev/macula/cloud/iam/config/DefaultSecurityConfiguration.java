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

package dev.macula.cloud.iam.config;

import dev.macula.cloud.iam.authentication.captcha.CaptchaLoginFilterConfigurer;
import dev.macula.cloud.iam.authentication.captcha.CaptchaService;
import dev.macula.cloud.iam.authentication.captcha.CaptchaUserDetailsService;
import dev.macula.cloud.iam.authentication.weapp.WeappClientService;
import dev.macula.cloud.iam.authentication.weapp.WeappLoginFilterConfigurer;
import dev.macula.cloud.iam.authentication.weapp.WeappSessionKeyCache;
import dev.macula.cloud.iam.authentication.weapp.WeappUserDetailsService;
import dev.macula.cloud.iam.handler.RedirectLoginAuthenticationSuccessHandler;
import dev.macula.cloud.iam.handler.SimpleAuthenticationEntryPoint;
import dev.macula.cloud.iam.service.support.SysUserService;
import dev.macula.cloud.iam.service.userdetails.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;

/**
 * {@code AuthenticationDefaultConfiguration} 默认的登录认证配置
 *
 * @author rain
 * @since 2023/3/11 22:25
 */
@EnableWebSecurity
public class DefaultSecurityConfiguration {
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 2)
    SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
        @Qualifier("sysUserDetailsService") UserDetailsService userDetailsService) throws Exception {
        // @formatter:off
        SimpleAuthenticationEntryPoint authenticationEntryPoint = new SimpleAuthenticationEntryPoint();
        AuthenticationEntryPointFailureHandler authenticationFailureHandler = new AuthenticationEntryPointFailureHandler(authenticationEntryPoint);
        RedirectLoginAuthenticationSuccessHandler loginAuthenticationSuccessHandler = new RedirectLoginAuthenticationSuccessHandler();

        http.authorizeRequests()
                .antMatchers("/component/**").permitAll()
                .antMatchers("/actuator/health").permitAll()
                .antMatchers("/h2-console/**").permitAll()
                .antMatchers("/pear.config.json").permitAll()
                .antMatchers("/pear.config.yml").permitAll()
                .antMatchers("/admin/css/**").permitAll()
                .antMatchers("/admin/fonts/**").permitAll()
                .antMatchers("/admin/js/**").permitAll()
                .antMatchers("/admin/images/**").permitAll()
                .antMatchers("/favicon.ico").permitAll()
                .anyRequest().authenticated().and()
            .userDetailsService(userDetailsService)
            .csrf(AbstractHttpConfigurer::disable);

        // FORM登录
        http.formLogin()
            .loginPage("/login")
            .successHandler(loginAuthenticationSuccessHandler)
            .failureHandler(authenticationFailureHandler)
            .permitAll();

        // 短信验证码登录
        http.apply(new CaptchaLoginFilterConfigurer<>())
            .captchaService(captchaService())
            .captchaUserDetailsService(captchaUserDetailsService())
            .successHandler(loginAuthenticationSuccessHandler)
            .failureHandler(authenticationFailureHandler)
            .permitAll();

        // 微信小程序登录
        http.apply(new WeappLoginFilterConfigurer<>())
            .weappUserDetailsService(weappUserDetailsService())
            .weappClientService(weappClientService())
            .weappSessionKeyCache(weappSessionKeyCache())
            .successHandler(loginAuthenticationSuccessHandler)
            .failureHandler(authenticationFailureHandler)
            .permitAll();
        return http.build();
        // @formatter:on
    }

    @Bean
    @Qualifier("sysUserDetailsService")
    public UserDetailsService sysUserDetailsService(SysUserService sysUserService) {
        return new SysUserDetailsServiceImpl(sysUserService);
    }

    @Bean
    public CaptchaService captchaService() {
        return new CaptchaServiceImpl();
    }

    @Bean
    public CaptchaUserDetailsService captchaUserDetailsService() {
        return new CaptchaUserDetailsServiceImpl();
    }

    @Bean
    WeappClientService weappClientService() {
        return new WeappClientServiceImpl();
    }

    @Bean
    WeappSessionKeyCache weappSessionKeyCache() {
        return new WeappSessionKeyCacheImpl();
    }

    @Bean
    WeappUserDetailsService weappUserDetailsService() {
        return new WeappUserDetailsServiceImpl();
    }
}
