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

import dev.macula.cloud.iam.service.application.IamApplicationSnapshot;
import dev.macula.cloud.iam.service.application.LoginApplicationResolver;
import dev.macula.cloud.system.enums.HostedLoginMethod;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.stereotype.Service;

/**
 * 托管登录仅信任Session中的授权请求，不从header、表单或cookie取得Client身份。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
public class HostedLoginClientPolicyService {
    public static final String CONTEXT_ATTRIBUTE = HostedLoginClientPolicyService.class.getName() + ".context";
    private static final String CLIENT_ID = "client_id";
    private final LoginApplicationResolver applications;
    private final boolean passwordEnabled;
    private final boolean smsEnabled;
    private final boolean emailEnabled;

    /**
     * 全局能力始终优先于Client配置。
     */
    public HostedLoginClientPolicyService(LoginApplicationResolver applications,
            @Value("${spring.security.oauth2.server.login.password.enabled:true}") boolean passwordEnabled,
            @Value("${spring.security.oauth2.server.login.sms.enabled:true}") boolean smsEnabled,
            @Value("${spring.security.oauth2.server.login.email.enabled:true}") boolean emailEnabled) {
        this.applications = applications;
        this.passwordEnabled = passwordEnabled;
        this.smsEnabled = smsEnabled;
        this.emailEnabled = emailEnabled;
    }

    /**
     * 从已校验请求属性读取上下文，禁止调用方自行传入clientId。
     */
    public static Context context(HttpServletRequest request) {
        Object value = request.getAttribute(CONTEXT_ATTRIBUTE);
        if (!(value instanceof Context context)) {
            throw unavailable();
        }
        return context;
    }

    /**
     * 不向浏览器泄露Client存在性和内部配置。
     */
    private static AuthenticationServiceException unavailable() {
        return new AuthenticationServiceException("当前应用不允许该登录方式");
    }

    /**
     * 无Client声明的旧门户兼容；有声明却没有受控请求时拒绝降级。
     */
    public Context resolve(HttpServletRequest request, HttpServletResponse response) {
        var selected = applications.resolve(request, response);
        if (selected.isEmpty()) {
            if (request.getParameter(CLIENT_ID) != null || request.getHeader(CLIENT_ID) != null) {
                throw unavailable();
            }
            if (request.getCookies() != null && Arrays.stream(request.getCookies())
                    .anyMatch(cookie -> CLIENT_ID.equals(cookie.getName()))) {
                throw unavailable();
            }
            return new Context(null, null, enabled(Set.of(HostedLoginMethod.values())));
        }
        var selection = selected.orElseThrow();
        var app = selection.application();
        if (!app.loginEnabled() || app.login().type() == null
            || !app.login().type().supportsHostedLogin(app.login().webLoginMode() == null
                ? null : app.login().webLoginMode().value())) {
            throw unavailable();
        }
        Set<HostedLoginMethod> methods = app.login().hostedMethods() == null
            ? Set.of(HostedLoginMethod.values()) : app.login().hostedMethods();
        return new Context(selection.reference(), app, enabled(methods));
    }

    /**
     * 交集实现全局关闭优先。
     */
    private Set<HostedLoginMethod> enabled(Set<HostedLoginMethod> methods) {
        return methods.stream().filter(method -> switch (method) {
            case PASSWORD -> passwordEnabled;
            case SMS -> smsEnabled;
            case EMAIL -> emailEnabled;
        }).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 单次请求可信策略；不保存于浏览器可改写字段。
     *
     * @since 6.1.0
     */
    public record Context(String reference, IamApplicationSnapshot client, Set<HostedLoginMethod> methods) {
        /** 无协议引用的门户上下文；只用于平台自有登录。 */
        public Context(IamApplicationSnapshot client, Set<HostedLoginMethod> methods) {
            this(null, client, methods);
        }

        /**
         * 固化允许集合。
         */
        public Context {
            methods = Set.copyOf(methods);
        }

        /**
         * 门户保留空Client上下文。
         */
        public String clientId() {
            return reference;
        }
    }
}
