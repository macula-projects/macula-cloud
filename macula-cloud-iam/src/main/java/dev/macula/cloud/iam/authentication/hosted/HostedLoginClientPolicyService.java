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

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.service.client.SysOAuth2ClientService;
import dev.macula.cloud.system.enums.OAuth2ClientApplicationType;
import dev.macula.cloud.system.enums.OAuth2HostedLoginMethod;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
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
    private final SysOAuth2ClientService clients;
    private final AuthorizationServerSettings settings;
    private final boolean passwordEnabled;
    private final boolean smsEnabled;
    private final boolean emailEnabled;
    private final HttpSessionRequestCache cache = new HttpSessionRequestCache();

    /**
     * 全局能力始终优先于Client配置。
     */
    public HostedLoginClientPolicyService(SysOAuth2ClientService clients, AuthorizationServerSettings settings,
            @Value("${spring.security.oauth2.server.login.password.enabled:true}") boolean passwordEnabled,
            @Value("${spring.security.oauth2.server.login.sms.enabled:true}") boolean smsEnabled,
            @Value("${spring.security.oauth2.server.login.email.enabled:true}") boolean emailEnabled) {
        this.clients = clients;
        this.settings = settings;
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
        SavedRequest saved = cache.getRequest(request, response);
        if (saved == null) {
            if (request.getParameter(CLIENT_ID) != null || request.getHeader(CLIENT_ID) != null) {
                throw unavailable();
            }
            if (request.getCookies() != null && Arrays.stream(request.getCookies())
                    .anyMatch(cookie -> CLIENT_ID.equals(cookie.getName()))) {
                throw unavailable();
            }
            return new Context(null, enabled(Set.of(OAuth2HostedLoginMethod.values())));
        }
        try {
            URI uri = URI.create(saved.getRedirectUrl());
            if (!(request.getContextPath() + settings.getAuthorizationEndpoint()).equals(uri.getRawPath())
                    || uri.getRawFragment() != null || !"code".equals(single(saved, "response_type"))) {
                throw unavailable();
            }
            String id = single(saved, CLIENT_ID);
            SysOAuth2Client client = clients.getClientByClientId(id);
            var configuration = dev.macula.cloud.iam.protocol.oauth2.OAuth2ClientProtocolPolicy.configuration(client,
                    dev.macula.cloud.iam.protocol.oauth2.ApplicationProtocolResolver.requested(
                            Set.of(single(saved, "scope").trim().split("\\s+"))));
            var settings = dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration.settings(configuration);
            String callback = single(saved, "redirect_uri");
            if (client == null || !csv(settings.getAuthorizationGrantTypes()).contains("authorization_code")
                    || !csv(settings.getRedirectUris()).contains(callback)
                    || !OAuth2ClientApplicationType.valueOf(client.getApplicationType()).supportsHostedLogin()) {
                throw unavailable();
            }
            Set<OAuth2HostedLoginMethod> methods = client.getHostedLoginMethods() == null
                    ? Set.of(OAuth2HostedLoginMethod.values())
                    : csv(client.getHostedLoginMethods()).stream().map(OAuth2HostedLoginMethod::valueOf)
                    .collect(Collectors.toUnmodifiableSet());
            return new Context(client, enabled(methods));
        } catch (RuntimeException ex) {
            throw unavailable();
        }
    }

    /**
     * 交集实现全局关闭优先。
     */
    private Set<OAuth2HostedLoginMethod> enabled(Set<OAuth2HostedLoginMethod> methods) {
        return methods.stream().filter(method -> switch (method) {
            case PASSWORD -> passwordEnabled;
            case SMS -> smsEnabled;
            case EMAIL -> emailEnabled;
        }).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 重复和缺失参数均拒绝，避免参数污染。
     */
    private String single(SavedRequest saved, String key) {
        String[] values = saved.getParameterValues(key);
        if (values == null || values.length != 1 || values[0].isBlank()) {
            throw unavailable();
        }
        return values[0];
    }

    /**
     * 解析已有逗号协议字段。
     */
    private Set<String> csv(String value) {
        return value == null || value.isBlank() ? Set.of()
                : Arrays.stream(value.split(",")).map(String::trim).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 单次请求可信策略；不保存于浏览器可改写字段。
     *
     * @since 6.1.0
     */
    public record Context(SysOAuth2Client client, Set<OAuth2HostedLoginMethod> methods) {
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
            return client == null ? null : client.getClientId();
        }
    }
}
