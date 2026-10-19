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

import dev.macula.cloud.iam.authentication.github.GithubLoginFilterConfigurer;
import dev.macula.cloud.iam.authentication.sms.SmsLoginFilterConfigurer;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyAuthenticationFailureHandler;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyAuthenticationSuccessHandler;
import dev.macula.cloud.iam.authentication.wecom.WecomLoginFilterConfigurer;
import dev.macula.cloud.iam.authentication.sms.SmsService;
import dev.macula.cloud.iam.authentication.sms.SmsUserDetailsService;
import dev.macula.cloud.iam.message.LoginSmsDispatcher;
import dev.macula.cloud.iam.message.SmsVerificationCodeStore;
import dev.macula.cloud.iam.message.SmsVerificationProperties;
import dev.macula.cloud.iam.authentication.weapp.WeappClientService;
import dev.macula.cloud.iam.authentication.weapp.WeappLoginFilterConfigurer;
import dev.macula.cloud.iam.authentication.weapp.WeappSessionKeyCache;
import dev.macula.cloud.iam.authentication.weapp.WeappUserDetailsService;
import dev.macula.cloud.iam.enums.ThirdPartyProviderType;
import dev.macula.cloud.iam.handler.JsonAuthenticationEntryPoint;
import dev.macula.cloud.iam.handler.RedirectLoginAuthenticationSuccessHandler;
import dev.macula.cloud.iam.service.support.SysOAuth2ClientService;
import dev.macula.cloud.iam.service.support.SysUserService;
import dev.macula.cloud.iam.service.support.UserAuthInfoService;
import dev.macula.cloud.iam.service.userdetails.*;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;

/**
 * {@code AuthenticationDefaultConfiguration} 默认的登录认证配置
 *
 * @author rain
 * @since 2023/3/11 22:25
 */
@EnableWebSecurity
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SmsVerificationProperties.class)
public class DefaultSecurityConfiguration {
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 2)
    SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
        @Qualifier("sysUserDetailsService") UserDetailsService userDetailsService) throws Exception {
        // @formatter:off
        JsonAuthenticationEntryPoint authenticationEntryPoint = new JsonAuthenticationEntryPoint();
        AuthenticationEntryPointFailureHandler authenticationFailureHandler = new AuthenticationEntryPointFailureHandler(authenticationEntryPoint);
        RedirectLoginAuthenticationSuccessHandler loginAuthenticationSuccessHandler = new RedirectLoginAuthenticationSuccessHandler();

        http.authorizeHttpRequests(authorize -> authorize
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
                .requestMatchers("/iam/**").permitAll()
                .requestMatchers("/login", ThirdPartyProviderType.GITHUB.loginPath(),
                    ThirdPartyProviderType.WECOM.loginPath()).permitAll()
                .requestMatchers("/api/v1/sms-code").permitAll()
                .anyRequest().authenticated())
            .userDetailsService(userDetailsService)
            .csrf(Customizer.withDefaults());

        // FORM登录
        http.formLogin(form -> form
            .loginPage("/login")
            .successHandler(loginAuthenticationSuccessHandler)
            .failureHandler(authenticationFailureHandler)
            .permitAll());

        // 短信验证码登录
        http.with(new SmsLoginFilterConfigurer<>(), code -> code
            .smsUserDetailsService((SmsUserDetailsService)userDetailsService)
            .successHandler(loginAuthenticationSuccessHandler)
            .failureHandler(authenticationFailureHandler)
            .permitAll());

        // 微信小程序登录
        http.with(new WeappLoginFilterConfigurer<>(), weapp -> weapp
            .successHandler(loginAuthenticationSuccessHandler)
            .failureHandler(authenticationFailureHandler)
            .permitAll());

        ThirdPartyAuthenticationSuccessHandler externalSuccessHandler =
            new ThirdPartyAuthenticationSuccessHandler();
        ThirdPartyAuthenticationFailureHandler externalFailureHandler =
            new ThirdPartyAuthenticationFailureHandler();
        http.with(new GithubLoginFilterConfigurer<>(), github -> github
            .successHandler(externalSuccessHandler)
            .failureHandler(externalFailureHandler)
            .permitAll());
        http.with(new WecomLoginFilterConfigurer<>(), wecom -> wecom
            .successHandler(externalSuccessHandler)
            .failureHandler(externalFailureHandler)
            .permitAll());
        return http.build();
        // @formatter:on
    }

    @Bean
    @Qualifier("sysUserDetailsService")
    public SysUserDetailsServiceImpl sysUserDetailsService(SysUserService sysUserService, SysOAuth2ClientService clientService,
        @Nullable UserAuthInfoService authInfoService) {
        return new SysUserDetailsServiceImpl(sysUserService, clientService, authInfoService);
    }

    @Bean
    public SmsService smsService(ObjectProvider<SmsVerificationCodeStore> stores,
        ObjectProvider<LoginSmsDispatcher> dispatchers) {
        SmsVerificationCodeStore store = stores.getIfAvailable();
        LoginSmsDispatcher dispatcher = dispatchers.getIfAvailable();
        if (store != null && dispatcher != null) return new SmsServiceImpl(store, dispatcher);
        return new SmsService() {
            @Override public boolean verifyCode(String phone, String rawCode) { return false; }
            @Override public void sendCode(String phone) { throw new IllegalStateException("短信服务未配置"); }
            @Override public void sendCode(String phone, String remoteAddress) { sendCode(phone); }
        };
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
