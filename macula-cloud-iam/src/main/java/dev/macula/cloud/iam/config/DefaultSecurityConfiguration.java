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
import dev.macula.cloud.iam.handler.JsonAuthenticationEntryPoint;
import dev.macula.cloud.iam.handler.RedirectLoginAuthenticationSuccessHandler;
import dev.macula.cloud.iam.service.support.SysOAuth2ClientService;
import dev.macula.cloud.iam.service.support.SysUserService;
import dev.macula.cloud.iam.service.support.UserAuthInfoService;
import dev.macula.cloud.iam.service.userdetails.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;

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
        JsonAuthenticationEntryPoint authenticationEntryPoint = new JsonAuthenticationEntryPoint();
        AuthenticationEntryPointFailureHandler authenticationFailureHandler = new AuthenticationEntryPointFailureHandler(authenticationEntryPoint);
        RedirectLoginAuthenticationSuccessHandler loginAuthenticationSuccessHandler = new RedirectLoginAuthenticationSuccessHandler();

        http.authorizeHttpRequests()
                .requestMatchers("/component/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/pear.config.json").permitAll()
                .requestMatchers("/pear.config.yml").permitAll()
                .requestMatchers("/admin/css/**").permitAll()
                .requestMatchers("/admin/fonts/**").permitAll()
                .requestMatchers("/admin/js/**").permitAll()
                .requestMatchers("/admin/images/**").permitAll()
                .requestMatchers("/favicon.ico").permitAll()
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
            .successHandler(loginAuthenticationSuccessHandler)
            .failureHandler(authenticationFailureHandler)
            .permitAll();

        // 微信小程序登录
        http.apply(new WeappLoginFilterConfigurer<>())
            .successHandler(loginAuthenticationSuccessHandler)
            .failureHandler(authenticationFailureHandler)
            .permitAll();
        return http.build();
        // @formatter:on
    }

    @Bean
    @Qualifier("sysUserDetailsService")
    public UserDetailsService sysUserDetailsService(SysUserService sysUserService, SysOAuth2ClientService clientService,
        @Nullable UserAuthInfoService authInfoService) {
        return new SysUserDetailsServiceImpl(sysUserService, clientService, authInfoService);
    }

    @Bean
    public CaptchaService captchaService() {
        return new CaptchaServiceImpl();
    }

    @Bean
    public CaptchaUserDetailsService captchaUserDetailsService(
        @Qualifier("sysUserDetailsService") UserDetailsService userDetailsService) {
        return new CaptchaUserDetailsServiceImpl(userDetailsService);
    }

    @Bean
    WeappClientService weappClientService() {
        return new WeappClientServiceImpl();
    }

    @Bean
    WeappSessionKeyCache weappSessionKeyCache(RedisTemplate<String, Object> redisTemplate) {
        return new WeappSessionKeyCacheImpl(redisTemplate);
    }

    @Bean
    WeappUserDetailsService weappUserDetailsService() {
        return new WeappUserDetailsServiceImpl();
    }
}
