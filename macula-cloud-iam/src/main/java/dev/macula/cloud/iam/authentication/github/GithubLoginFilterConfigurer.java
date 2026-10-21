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
package dev.macula.cloud.iam.authentication.github;

import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyIdentityResolutionService;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyLoginStateRepository;
import dev.macula.cloud.iam.enums.ThirdPartyProviderType;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractAuthenticationFilterConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static org.springframework.security.config.annotation.web.builders.FilterOrderRegUtils.Position.AFTER;
import static org.springframework.security.config.annotation.web.builders.FilterOrderRegUtils.registryFilterOrder;

/**
 * 装配 GitHub 登录过滤器与认证 Provider。
 *
 * @param <H> HttpSecurity 类型
 * @author Rain
 * @since 6.1.0
 */
public class GithubLoginFilterConfigurer<H extends HttpSecurityBuilder<H>>
    extends AbstractAuthenticationFilterConfigurer<H, GithubLoginFilterConfigurer<H>, GithubAuthenticationFilter> {
    public GithubLoginFilterConfigurer() {
        super(new GithubAuthenticationFilter(), ThirdPartyProviderType.GITHUB.loginPath());
    }

    @Override
    public void init(H http) {
        ApplicationContext context = http.getSharedObject(ApplicationContext.class);
        GithubClient client = context.getBean(GithubClient.class);
        IdentityProviderConfigService configs = context.getBean(IdentityProviderConfigService.class);
        ThirdPartyIdentityResolutionService identities = context.getBean(ThirdPartyIdentityResolutionService.class);
        getAuthenticationFilter().githubClient(client);
        getAuthenticationFilter().configure(configs,
            context.getBean(ThirdPartyLoginStateRepository.class),
            context.getBean(AuthorizationServerSettings.class).getIssuer());
        http.authenticationProvider(postProcess(new GithubAuthenticationProvider(client, configs, identities)));
        registryFilterOrder((HttpSecurity)http, getAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class,
            AFTER);
        super.init(http);
    }

    @Override
    protected RequestMatcher createLoginProcessingUrlMatcher(String loginProcessingUrl) {
        return PathPatternRequestMatcher.pathPattern(HttpMethod.GET, loginProcessingUrl);
    }
}
