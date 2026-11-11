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
package dev.macula.cloud.iam.authentication.hosted;

import java.util.Arrays;
import java.util.stream.Stream;

import dev.macula.cloud.iam.authentication.email.EmailCodeAuthenticationFilter;
import dev.macula.cloud.iam.authentication.thirdparty.browser.ThirdPartyLoginFilterConfigurer;
import dev.macula.cloud.iam.controller.EmailCodeController;
import dev.macula.cloud.iam.controller.ThirdPartyBindingController;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 按安全意图声明匿名入口；permitAll不替代CSRF、Client策略或账号证明。
 *
 * @author Macula
 * @since 6.1.0
 */
public final class LoginRequestMatchers {
    /**
     * 只提供无状态的精确规则构造。
     */
    private LoginRequestMatchers() {
    }

    /**
     * 保留既有静态资源及错误页依赖，不开放管理API。
     */
    public static RequestMatcher staticResources() {
        return paths("/component/**", "/pear.config.json", "/pear.config.yml", "/admin/css/**",
                "/admin/fonts/**", "/admin/js/**", "/admin/images/**", "/favicon.ico", "/iam/**");
    }

    /**
     * 第三方路径与Filter同源，其余均为独立、固定的认证或绑定入口。
     */
    public static RequestMatcher loginEndpoints(ThirdPartyLoginFilterConfigurer thirdParty) {
        return paths(Stream.concat(Stream.of("/login", "/login/sms", EmailCodeAuthenticationFilter.PATH,
                        "/api/v1/sms-code", EmailCodeController.PATH, ThirdPartyBindingController.BINDING_PATH,
                        "/login/bind/external", "/login/bind/sms-code",
                        ThirdPartyBindingController.BINDING_CANCEL_PATH),
                Arrays.stream(thirdParty.publicPaths())).distinct().toArray(String[]::new));
    }

    /**
     * 保持原匿名路径的HTTP方法语义，实际副作用由POST映射与CSRF约束。
     */
    private static RequestMatcher paths(String... paths) {
        return new OrRequestMatcher(Arrays.stream(paths)
                .map(path -> (RequestMatcher) PathPatternRequestMatcher.pathPattern(path)).toList());
    }
}
