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

import dev.macula.cloud.iam.authentication.dingtalk.DingtalkAuthenticationFilter;
import dev.macula.cloud.iam.authentication.dingtalk.DingtalkLoginMode;
import dev.macula.cloud.iam.authentication.email.EmailCodeService;
import dev.macula.cloud.iam.authentication.email.EmailLoginFilterConfigurer;
import dev.macula.cloud.iam.authentication.feishu.FeishuAuthenticationFilter;
import dev.macula.cloud.iam.authentication.feishu.FeishuLoginMode;
import dev.macula.cloud.iam.authentication.github.GithubAuthenticationFilter;
import dev.macula.cloud.iam.authentication.hosted.HostedLoginClientPolicyService;
import dev.macula.cloud.iam.authentication.hosted.HostedLoginPolicyFilter;
import dev.macula.cloud.iam.authentication.hosted.LoginRequestMatchers;
import dev.macula.cloud.iam.authentication.mfa.MfaCompletionService;
import dev.macula.cloud.iam.authentication.mfa.MfaSessionAuthenticationStrategy;
import dev.macula.cloud.iam.authentication.sms.SmsCodeService;
import dev.macula.cloud.iam.authentication.sms.SmsLoginFilterConfigurer;
import dev.macula.cloud.iam.authentication.thirdparty.PendingThirdPartyBindingRepository;
import dev.macula.cloud.iam.authentication.thirdparty.browser.ThirdPartyLoginFilterConfigurer;
import dev.macula.cloud.iam.authentication.userdetails.HostedUserDetailsService;
import dev.macula.cloud.iam.authentication.verification.EmailVerificationProperties;
import dev.macula.cloud.iam.authentication.verification.SmsVerificationProperties;
import dev.macula.cloud.iam.authentication.verification.UnavailableVerificationCodeService;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeChannelService;
import dev.macula.cloud.iam.authentication.verification.VerificationCodeService;
import dev.macula.cloud.iam.authentication.wechat.WechatAuthenticationFilter;
import dev.macula.cloud.iam.authentication.wechat.WechatLoginMode;
import dev.macula.cloud.iam.authentication.wecom.WecomAuthenticationFilter;
import dev.macula.cloud.iam.authentication.wecom.WecomLoginMode;
import dev.macula.cloud.iam.enums.ThirdPartyProviderType;
import dev.macula.cloud.iam.handler.BrowserAuthenticationFailureHandler;
import dev.macula.cloud.iam.message.dispatch.LoginMailDispatcher;
import dev.macula.cloud.iam.message.dispatch.LoginSmsDispatcher;
import dev.macula.cloud.iam.service.account.ClientAccountDetailsService;
import dev.macula.cloud.system.security.UserIdentifierNormalizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;

/**
 * {@code AuthenticationDefaultConfiguration} 默认的登录认证配置
 *
 * @author rain
 * @since 2023/3/11 22:25
 */
@EnableWebSecurity
@Import(MfaExtensionConfiguration.class)
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({SmsVerificationProperties.class, EmailVerificationProperties.class})
public class DefaultSecurityConfiguration {
    private static final String DEVTOOLS_WORKSPACE_PATH = "/.well-known/appspecific/com.chrome.devtools.json";

    /**
     * 两条链共享缓存；浏览器探测不能覆盖登录续转，其他请求仍执行Spring默认规则。
     */
    @Bean
    RequestCache browserRequestCache() {
        var cache = new HttpSessionRequestCache();
        cache.setRequestMatcher(
                request -> !request.getRequestURI().equals(request.getContextPath() + DEVTOOLS_WORKSPACE_PATH));
        return cache;
    }

    /**
     * 共用账号标识规范化规则，不在认证适配器重复实现。
     */
    @Bean
    @ConditionalOnMissingBean(name = "identifierNormalizer")
    public UserIdentifierNormalizer identifierNormalizer() {
        return new UserIdentifierNormalizer();
    }

    /**
     * 装配托管登录过滤链，CSRF先于Client策略，策略先于密码和SMS认证。
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 2)
    SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
            @Qualifier("sysUserDetailsService") HostedUserDetailsService userDetailsService,
            @Qualifier("smsCodeService") VerificationCodeChannelService smsCodes,
            @Qualifier("emailCodeService") VerificationCodeChannelService emailCodes,
            BrowserAuthenticationFailureHandler authenticationFailureHandler,
            HostedLoginClientPolicyService hostedPolicy, MfaCompletionService mfaCompletion,
            RequestCache browserRequestCache) throws Exception {
        http.sessionManagement(session -> session.addSessionAuthenticationStrategy(
                new MfaSessionAuthenticationStrategy(mfaCompletion)));
        // @formatter:off
        SavedRequestAwareAuthenticationSuccessHandler authenticationSuccessHandler =
            new SavedRequestAwareAuthenticationSuccessHandler();

        var thirdPartyLogin = new ThirdPartyLoginFilterConfigurer()
            .add(new GithubAuthenticationFilter(),
            ThirdPartyProviderType.GITHUB.providerType(), ThirdPartyProviderType.GITHUB.loginPath(), ThirdPartyProviderType.GITHUB.loginPath())
            .add(new WecomAuthenticationFilter(),
            ThirdPartyProviderType.WECOM.providerType(), ThirdPartyProviderType.WECOM.loginPath(), ThirdPartyProviderType.WECOM.loginPath(), WecomLoginMode.BROWSER.loginPath(), WecomLoginMode.WEBVIEW.loginPath())
            .add(new FeishuAuthenticationFilter(),
            ThirdPartyProviderType.FEISHU.providerType(), ThirdPartyProviderType.FEISHU.loginPath(), ThirdPartyProviderType.FEISHU.loginPath(), FeishuLoginMode.BROWSER.loginPath())
            .add(new DingtalkAuthenticationFilter(),
            ThirdPartyProviderType.DINGTALK.providerType(), ThirdPartyProviderType.DINGTALK.loginPath(), ThirdPartyProviderType.DINGTALK.loginPath(), DingtalkLoginMode.BROWSER.loginPath())
            .add(new WechatAuthenticationFilter(),
            ThirdPartyProviderType.WECHAT.providerType(), ThirdPartyProviderType.WECHAT.loginPath(), ThirdPartyProviderType.WECHAT.loginPath(), WechatLoginMode.BROWSER.loginPath(), WechatLoginMode.WEBVIEW.loginPath());

        http.requestCache(cache -> cache.requestCache(browserRequestCache))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(LoginRequestMatchers.staticResources()).permitAll()
                .requestMatchers(LoginRequestMatchers.loginEndpoints(thirdPartyLogin)).permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated())
            .userDetailsService(userDetailsService)
            .csrf(Customizer.withDefaults());

        // LogoutFilter在CSRF之后、两类表单认证之前，确保拒绝不产生认证或发码副作用。
        http.addFilterBefore(new HostedLoginPolicyFilter(hostedPolicy, authenticationFailureHandler), LogoutFilter.class);

        // FORM登录
        http.formLogin(form -> form
            .loginPage("/login")
            .successHandler(authenticationSuccessHandler)
            .failureHandler(authenticationFailureHandler)
            .permitAll());

        // 短信验证码登录
        http.with(new SmsLoginFilterConfigurer(smsCodes, userDetailsService), code -> code
            .successHandler(authenticationSuccessHandler)
            .failureHandler(authenticationFailureHandler)
            .permitAll());

        http.with(new EmailLoginFilterConfigurer(emailCodes, userDetailsService), email -> email
            .successHandler(authenticationSuccessHandler).failureHandler(authenticationFailureHandler).permitAll());

        http.with(thirdPartyLogin, login -> login.successHandler(authenticationSuccessHandler)
            .failureHandler(authenticationFailureHandler).permitAll());
        return http.build();
        // @formatter:on
    }

    /**
     * 浏览器失败处理保留账号绑定分流与可信issuer。
     */
    @Bean
    public BrowserAuthenticationFailureHandler browserAuthenticationFailureHandler(
            PendingThirdPartyBindingRepository pendingBindings,
            AuthorizationServerSettings authorizationServerSettings) {
        return new BrowserAuthenticationFailureHandler(pendingBindings, authorizationServerSettings.getIssuer());
    }

    /**
     * 登录请求仅适配公共账号服务，不在配置类重新组合目录算法。
     */
    @Bean
    @Qualifier("sysUserDetailsService")
    public HostedUserDetailsService sysUserDetailsService(ClientAccountDetailsService accounts) {
        return new HostedUserDetailsService(accounts);
    }

    /**
     * 邮箱与短信采用一致的设施判定，缺少内核或投递器时均失败关闭。
     */
    @Bean("emailCodeService")
    public VerificationCodeChannelService emailCodeService(ObjectProvider<VerificationCodeService> services,
            ObjectProvider<LoginMailDispatcher> dispatchers, UserIdentifierNormalizer normalizer,
            EmailVerificationProperties settings) {
        VerificationCodeService codes = services.getIfAvailable();
        LoginMailDispatcher dispatcher = dispatchers.getIfAvailable();
        return codes != null && dispatcher != null
                ? new EmailCodeService(codes, dispatcher, normalizer, settings)
                : new UnavailableVerificationCodeService();
    }

    /**
     * 独立名称固定通道，调用方必须以Qualifier选择，不能按公共接口猜测。
     */
    @Bean("smsCodeService")
    public VerificationCodeChannelService smsCodeService(ObjectProvider<VerificationCodeService> services,
            ObjectProvider<LoginSmsDispatcher> dispatchers, UserIdentifierNormalizer normalizer,
            SmsVerificationProperties settings) {
        VerificationCodeService codes = services.getIfAvailable();
        LoginSmsDispatcher dispatcher = dispatchers.getIfAvailable();
        return codes != null && dispatcher != null
                ? new SmsCodeService(codes, dispatcher, normalizer, settings)
                : new UnavailableVerificationCodeService();
    }
}
