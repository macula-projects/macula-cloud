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
package dev.macula.cloud.iam.protocol.oauth2.client;

import dev.macula.cloud.iam.mapper.SysIamApplicationProtocolConfigMapper;
import dev.macula.cloud.iam.protocol.oauth2.ApplicationProtocolResolver;
import dev.macula.cloud.iam.protocol.oauth2.OAuth2ClientProtocolPolicy;
import dev.macula.cloud.iam.service.application.IamApplicationService;
import dev.macula.cloud.iam.service.application.IamApplicationSnapshot;
import dev.macula.cloud.iam.service.application.LoginApplicationResolver;
import dev.macula.cloud.system.enums.ExternalCodeType;
import dev.macula.cloud.system.enums.IdentityLoginCapability;
import dev.macula.cloud.system.protocol.OAuth2ApplicationProtocolSettings;
import dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 当前OAuth受控SavedRequest及旧可信证明引用的解析；公共登录层不再解析grant或回调。
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class OAuth2LoginApplicationResolver implements LoginApplicationResolver {
    private final OAuth2ClientRegistrationService clients;
    private final IamApplicationService applications;
    private final SysIamApplicationProtocolConfigMapper protocols;
    private final AuthorizationServerSettings settings;
    private final HttpSessionRequestCache requests = new HttpSessionRequestCache();

    /** 先核验标准请求语义，再返回规范Client引用和中立快照。 */
    @Override
    public Optional<Selection> resolve(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest saved = requests.getRequest(request, response);
        if (saved == null) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(saved.getRedirectUrl());
            if (!(request.getContextPath() + settings.getAuthorizationEndpoint()).equals(uri.getRawPath())
                || uri.getRawFragment() != null || !"code".equals(single(saved, "response_type"))) {
                throw denied();
            }
            var client = clients.getClientByClientId(single(saved, "client_id"));
            var profile = OAuth2ClientProtocolPolicy.configuration(client,
                ApplicationProtocolResolver.requested(Set.of(single(saved, "scope").trim().split("\\s+"))));
            var configured = OAuth2ProtocolConfiguration.settings(profile);
            if (!OAuth2ProtocolConfiguration.csv(configured.getAuthorizationGrantTypes()).contains("authorization_code")
                || !OAuth2ProtocolConfiguration.csv(configured.getRedirectUris()).contains(single(saved, "redirect_uri"))) {
                throw denied();
            }
            var app = applications.findById(client.getId());
            if (app == null || !app.loginEnabled()) {
                throw denied();
            }
            return Optional.of(new Selection(client.getClientId(), app));
        } catch (RuntimeException error) {
            throw denied();
        }
    }

    /** 锁序始终主应用在前；随后以当前读复核引用，不能用先前RR快照恢复旧许可。 */
    @Override
    @Transactional(propagation = Propagation.SUPPORTS)
    public IamApplicationSnapshot trusted(String reference, IdentityLoginCapability capability, boolean lock) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        Long id = protocols.selectApplicationIdByOAuth2Identifier(reference);
        if (id == null) {
            return null;
        }
        var app = lock ? applications.lock(id) : applications.findById(id);
        if (app == null || !app.loginEnabled()) {
            return null;
        }
        if (lock) {
            var current = protocols.selectByOAuth2IdentifierForUpdate(reference);
            if (current == null || !id.equals(current.getApplicationId()) || !Boolean.TRUE.equals(current.getEnabled())) {
                return null;
            }
        }
        if (!(app.protocol().settings() instanceof OAuth2ApplicationProtocolSettings profile)) {
            return null;
        }
        if (capability != null) {
            Set<String> grants = OAuth2ProtocolConfiguration.csv(profile.getOauth2().getAuthorizationGrantTypes());
            boolean permitted = capability.browser() ? grants.contains("authorization_code")
                : capability.code() != null && grants.contains(ExternalCodeType.GRANT_TYPE)
                    && profile.getExternalCodeTypes().contains(capability.code());
            if (!permitted || !app.login().sources().containsKey(capability)) {
                return null;
            }
        }
        return app;
    }

    /** 不能通过重复、缺失或空参数选择另一个Client或协议。 */
    private String single(SavedRequest request, String key) {
        String[] values = request.getParameterValues(key);
        if (values == null || values.length != 1 || values[0].isBlank()) {
            throw denied();
        }
        return values[0];
    }

    /** 不暴露内部应用、来源或载荷错误。 */
    private AuthenticationServiceException denied() {
        return new AuthenticationServiceException("当前应用登录请求无效，请重新发起");
    }
}
