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
package dev.macula.cloud.iam.authentication.inapp;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.service.client.SysOAuth2ClientService;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import dev.macula.cloud.system.enums.ExternalCodeType;
import dev.macula.cloud.system.enums.IdentityLoginCapability;
import dev.macula.cloud.system.enums.OAuth2ClientApplicationType;
import dev.macula.cloud.system.security.OAuth2ApplicationLoginPolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Service;

/**
 * 端内授权只接受当前Client固定来源的服务端证明，普通IAM会话不能代替平台认证。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class InAppLoginPolicyService {
    public static final String CLIENT_ID = "clientId";
    public static final String CLIENT_VERSION = "clientAuthorizationVersion";
    public static final String USER_ID = "userId";
    public static final String TENANT_ID = "tenantId";
    public static final String USER_TYPE = "userType";
    private final SysOAuth2ClientService clients;
    private final IdentityProviderConfigService sources;
    private final AuthorizationServerSettings settings;
    private final tools.jackson.databind.json.JsonMapper json = tools.jackson.databind.json.JsonMapper.builder()
            .enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private final HttpSessionRequestCache requests = new HttpSessionRequestCache();

    /**
     * 只从Session中由授权链保存的请求恢复应用，不信任登录页的client_id参数。
     */
    public Optional<SysOAuth2Client> savedClient(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest saved = requests.getRequest(request, response);
        if (saved == null) {
            return Optional.empty();
        }
        SysOAuth2Client client = clients.getClientByClientId(single(saved, "client_id"));
        if (client == null || !"IN_APP".equals(client.getApplicationType())) {
            return Optional.empty();
        }
        var uri = URI.create(saved.getRedirectUrl());
        if (!(request.getContextPath() + settings.getAuthorizationEndpoint()).equals(uri.getRawPath())
                || uri.getRawFragment() != null || !"code".equals(single(saved, "response_type"))) {
            throw denied();
        }
        var configuration = dev.macula.cloud.iam.protocol.oauth2.OAuth2ClientProtocolPolicy.configuration(client,
                dev.macula.cloud.iam.protocol.oauth2.ApplicationProtocolResolver.requested(
                        java.util.Set.of(single(saved, "scope").trim().split("\\s+"))));
        var configured = dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration.settings(configuration);
        if (!dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration.csv(configured.getRedirectUris())
                .contains(single(saved, "redirect_uri"))) {
            throw denied();
        }
        return Optional.of(client);
    }

    /**
     * 返回当前权威配置，过滤器只临时隔离其请求，不保存匿名Session。
     */
    public SysOAuth2Client current(String clientId) {
        return clients.getClientByClientId(clientId);
    }

    /**
     * 必须恰好一个合法端内能力，且注册仍启用；不取第一条猜测默认。
     */
    public IdentityProviderConfigService.ProviderConfig source(SysOAuth2Client client) {
        try {
            Map<String, Long> browser = client.getBrowserCapabilityBindings() == null ? Map.of()
                    : json.readValue(client.getBrowserCapabilityBindings(),
                    new tools.jackson.core.type.TypeReference<Map<String, Long>>() {
                    });
            Map<String, Long> external = client.getExternalProviderBindings();
            OAuth2ApplicationLoginPolicy.validate(OAuth2ClientApplicationType.IN_APP, null, browser, external, true);
            IdentityLoginCapability capability;
            Long registration;
            if (!browser.isEmpty()) {
                var entry = browser.entrySet().iterator().next();
                capability = IdentityLoginCapability.fromValue(entry.getKey());
                registration = entry.getValue();
            } else {
                var entry = external.entrySet().iterator().next();
                capability = IdentityLoginCapability.forCode(ExternalCodeType.fromValue(entry.getKey()));
                registration = entry.getValue();
            }
            return sources.requireEnabledById(registration, capability.source().providerType())
                    .forCapability(capability.value());
        } catch (RuntimeException ex) {
            throw denied();
        }
    }

    /**
     * 每次授权和成果发布重查当前来源/Client版本，校验无副作用。
     */
    public boolean validProof(SysOAuth2Client client, Authentication authentication, Map<String, Object> proof,
            String entryPoint) {
        try {
            if (authentication == null || !authentication.isAuthenticated()
                    || !(authentication.getPrincipal() instanceof SysUserDetails user) || !user.isEnabled()
                    || user.getUserId() == null || !Objects.equals(user.getTenantId(), client.getTenantId())) {
                return false;
            }
            var source = source(client);
            if (!(proof.get("registrationId") instanceof Number id) || id.longValue() != source.registrationId()
                    || !(proof.get("sourceVersion") instanceof Number version) ||
                    version.longValue() != source.version()
                    || !source.loginCapability().equals(proof.get("loginCapability")) ||
                    !source.flow().value().equals(proof.get("flow"))) {
                return false;
            }
            if (ExternalCodeType.GRANT_TYPE.equals(entryPoint)) {
                return IdentityLoginCapability.fromValue(source.loginCapability()).code() != null;
            }
            return IdentityLoginCapability.fromValue(source.loginCapability()).browser()
                    && client.getClientId().equals(proof.get(CLIENT_ID))
                    && proof.get(CLIENT_VERSION) instanceof Number clientVersion &&
                    client.getAuthorizationVersion() != null
                    && clientVersion.longValue() == client.getAuthorizationVersion()
                    && Objects.equals(user.getUserId(), proof.get(USER_ID)) &&
                    Objects.equals(user.getTenantId(), proof.get(TENANT_ID))
                    && client.getUserType() != null && client.getUserType().name().equals(proof.get(USER_TYPE));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * 非端内应用保持原策略；端内所有完成点都不能用普通会话替代当前平台证明。
     */
    public void requireProof(String clientId, Authentication authentication, Map<String, Object> proof,
            String entryPoint) {
        if (clientId == null) {
            return;
        }
        var client = current(clientId);
        if (client != null && "IN_APP".equals(client.getApplicationType()) &&
                !validProof(client, authentication, proof, entryPoint)) {
            throw denied();
        }
    }

    /**
     * 缺失和重复参数不降级为普通门户。
     */
    private String single(SavedRequest saved, String key) {
        String[] values = saved.getParameterValues(key);
        if (values == null || values.length != 1 || values[0].isBlank()) {
            throw denied();
        }
        return values[0];
    }

    /**
     * 错误不泄露来源、用户或认证材料。
     */
    private AuthenticationServiceException denied() {
        return new AuthenticationServiceException("端内登录配置或来源证明无效，请重新发起");
    }
}
