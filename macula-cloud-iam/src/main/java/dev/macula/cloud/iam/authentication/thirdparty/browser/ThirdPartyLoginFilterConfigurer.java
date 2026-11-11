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
package dev.macula.cloud.iam.authentication.thirdparty.browser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dev.macula.cloud.iam.authentication.thirdparty.provider.ThirdPartyProviderAdapterRegistry;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractAuthenticationFilterConfigurer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static org.springframework.security.config.annotation.web.builders.FilterOrderRegUtils.Position.AFTER;
import static org.springframework.security.config.annotation.web.builders.FilterOrderRegUtils.registryFilterOrder;

/**
 * 统一装配所有第三方Filter；同类Configurer在HttpSecurity中只注册一次，避免后一个覆盖前一个。
 *
 * @author Macula
 * @since 6.1.0
 */
public final class ThirdPartyLoginFilterConfigurer
        extends AbstractHttpConfigurer<ThirdPartyLoginFilterConfigurer, HttpSecurity> {
    private final List<Entry> entries = new ArrayList<>();

    /**
     * 添加精确厂商入口，不把差异交给运行时请求猜测。
     */
    public ThirdPartyLoginFilterConfigurer add(AbstractThirdPartyAuthenticationFilter filter, String providerType,
            String loginPath, String... paths) {
        entries.add(new Entry(filter, providerType, loginPath, paths));
        return this;
    }

    /**
     * 返回过滤器共用的精确路径快照，匿名白名单无需再次枚举厂商。
     */
    public String[] publicPaths() {
        return entries.stream().flatMap(entry -> entry.paths.stream()).distinct().toArray(String[]::new);
    }

    /**
     * 所有厂商复用原生成功续转策略。
     */
    public ThirdPartyLoginFilterConfigurer successHandler(AuthenticationSuccessHandler handler) {
        entries.forEach(entry -> entry.successHandler(handler));
        return this;
    }

    /**
     * 所有厂商复用包含绑定分流的既有失败策略。
     */
    public ThirdPartyLoginFilterConfigurer failureHandler(AuthenticationFailureHandler handler) {
        entries.forEach(entry -> entry.failureHandler(handler));
        return this;
    }

    /**
     * 保留每个原生认证Configurer的公开入口配置。
     */
    public ThirdPartyLoginFilterConfigurer permitAll() {
        entries.forEach(Entry::permitAll);
        return this;
    }

    /**
     * 按框架两阶段生命周期初始化，保留Session和SecurityContext处理。
     */
    @Override
    public void init(HttpSecurity http) {
        ApplicationContext context = http.getSharedObject(ApplicationContext.class);
        // 用户登录Provider只注册到托管登录链，不能被协议链全局发现。
        http.authenticationProvider(new ThirdPartyAuthenticationProvider(
                context.getBean(ThirdPartyProviderAdapterRegistry.class),
                context.getBean(IdentityProviderConfigService.class),
                context.getBean(ThirdPartyIdentityResolutionService.class)));
        for (Entry entry : entries) {
            entry.setBuilder(http);
            entry.addObjectPostProcessor(new org.springframework.security.config.ObjectPostProcessor<Object>() {
                /** 共用外层Configurer已装配的对象后处理器。 */
                @Override
                public <O> O postProcess(O object) {
                    return ThirdPartyLoginFilterConfigurer.this.postProcess(object);
                }
            });
            entry.init(http);
        }
    }

    /**
     * AuthenticationManager建立后才交给各原生Configurer安装Filter。
     */
    @Override
    public void configure(HttpSecurity http) {
        for (Entry entry : entries) {
            entry.configure(http);
        }
    }

    /**
     * 单个入口继续委托框架认证装配，不重写会话固定保护或上下文持久化。
     *
     * @since 6.1.0
     */
    private static final class Entry extends
            AbstractAuthenticationFilterConfigurer<HttpSecurity, Entry, AbstractThirdPartyAuthenticationFilter> {
        private final RequestMatcher matcher;
        private final List<String> paths;
        private final String providerType;

        /**
         * 保留每个厂商已支持的精确GET路径，不开放通配回调。
         */
        Entry(AbstractThirdPartyAuthenticationFilter filter, String providerType,
                String loginPath, String... paths) {
            super(filter, loginPath);
            this.providerType = providerType;
            this.paths = List.copyOf(Arrays.asList(paths));
            if (this.paths.isEmpty() || this.paths.stream().anyMatch(path -> path == null || !path.startsWith("/login/")
                    || path.contains("*") || path.contains("?"))) {
                throw new IllegalArgumentException("第三方登录路径必须明确");
            }
            matcher = new OrRequestMatcher(Arrays.stream(paths)
                    .map(path -> (RequestMatcher) PathPatternRequestMatcher.pathPattern(HttpMethod.GET, path))
                    .toList());
            getAuthenticationFilter().setRequiresAuthenticationRequestMatcher(matcher);
        }

        /**
         * Filter共用来源/state规则；认证Provider只在托管链注册一次。
         */
        @Override
        public void init(HttpSecurity http) {
            ApplicationContext context = http.getSharedObject(ApplicationContext.class);
            getAuthenticationFilter().configure(context.getBean(IdentityProviderConfigService.class),
                    context.getBean(BrowserLoginClientPolicyService.class),
                    context.getBean(ThirdPartyLoginStateRepository.class),
                    context.getBean(AuthorizationServerSettings.class).getIssuer());
            getAuthenticationFilter().providerAdapter(
                    context.getBean(ThirdPartyProviderAdapterRegistry.class).require(providerType));
            registryFilterOrder(http, getAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class, AFTER);
            super.init(http);
        }

        /**
         * 重用显式路由集合，避免初始化隐式改变回调路径。
         */
        @Override
        protected RequestMatcher createLoginProcessingUrlMatcher(String loginProcessingUrl) {
            return matcher == null ? PathPatternRequestMatcher.pathPattern(HttpMethod.GET, loginProcessingUrl) :
                    matcher;
        }
    }
}
