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
package dev.macula.cloud.iam.protocol.oauth2.grant.externalcode;

import dev.macula.cloud.iam.protocol.oauth2.ApplicationProtocolResolver;
import dev.macula.cloud.iam.protocol.oauth2.grant.base.OAuth2UserGrantTokenIssuer;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService;
import dev.macula.cloud.system.enums.ExternalCodeType;
import dev.macula.cloud.system.enums.IamApplicationType;
import dev.macula.cloud.system.enums.IamWebLoginMode;
import dev.macula.cloud.system.security.IamApplicationLoginPolicy;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.util.CollectionUtils;

/**
 * 校验第三方Code及真实账号绑定后按当前协议签发用户令牌。
 *
 * @author Rain
 * @since 6.1.0
 */
@Slf4j
public class OAuth2ExternalCodeAuthenticationProvider implements AuthenticationProvider {
    private final ExternalCodeVerifierRegistry verifiers;
    private final ExternalCodeReplayStore replayStore;
    private final ExternalCodeAccountResolver accountResolver;
    private final OAuth2UserGrantTokenIssuer tokenIssuer;
    private final IdentityProviderConfigService sources;
    private final ExternalCodeRateLimiter limiter;

    /**
     * 每次请求在claim前核对来源并执行限流，不提供跳过依赖的构造路径。
     */
    public OAuth2ExternalCodeAuthenticationProvider(ExternalCodeVerifierRegistry verifiers,
            ExternalCodeReplayStore replayStore, ExternalCodeAccountResolver accountResolver,
            OAuth2UserGrantTokenIssuer tokenIssuer,
            IdentityProviderConfigService sources,
            ExternalCodeRateLimiter limiter) {
        this.verifiers = verifiers;
        this.replayStore = replayStore;
        this.accountResolver = accountResolver;
        this.tokenIssuer = tokenIssuer;
        this.sources = Objects.requireNonNull(sources);
        this.limiter = Objects.requireNonNull(limiter);
    }

    /**
     * 核验当前Client、来源与凭据后才解析本地账号并发布协议结果。
     */
    @Override
    public Authentication authenticate(@NonNull Authentication authentication) {
        OAuth2ExternalCodeAuthenticationToken grant = (OAuth2ExternalCodeAuthenticationToken) authentication;
        OAuth2ClientAuthenticationToken clientPrincipal = authenticatedClient(grant);
        RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();
        validateClient(clientPrincipal, registeredClient, grant);
        Long registrationId = providerRegistration(registeredClient, grant.getCodeType());
        Set<String> scopes = validateScopes(registeredClient, grant.getScopes());
        String providerVerifier = validateSourceAndFlow(registeredClient, registrationId, grant);
        String remote =
                grant.getDetails() instanceof WebAuthenticationDetails details
                        ? details.getRemoteAddress() : null;
        limiter.check(registeredClient.getClientId(), remote);
        ExternalCodeReplayStore.ReplayClaim replayClaim = replayStore.claim(grant.getCodeType(), grant.getCode());
        boolean codeVerified = false;
        try {
            ExternalCodeVerifier verifier = verifiers.require(grant.getCodeType());
            ExternalCodeVerification verification = providerVerifier == null
                    ? verifier.verify(grant.getCodeType(), registrationId, grant.getCode())
                    : verifier.verify(grant.getCodeType(), registrationId, grant.getCode(), providerVerifier,
                    (String) grant.getAdditionalParameters()
                            .get(OAuth2ExternalCodeParameterNames.PROVIDER_REDIRECT_URI));
            codeVerified = true;
            Authentication userPrincipal = accountResolver.resolve(registeredClient, grant.getCodeType(),
                    verification);
            return tokenIssuer.issue(registeredClient, clientPrincipal, userPrincipal, grant,
                    OAuth2ExternalCodeAuthenticationToken.GRANT_TYPE, scopes, false);
        } catch (ExternalCodeInfrastructureException ex) {
            // 只有明确尚未向平台提交Code时才安全重试；超时/已换码后的资料失败仍保留占用。
            if (!codeVerified && ex.getConsumptionState() == ExternalCodeConsumptionState.NOT_SUBMITTED) {
                try {
                    replayStore.release(replayClaim);
                } catch (RuntimeException releaseFailure) {
                    log.warn("External code claim release unavailable category=INFRASTRUCTURE");
                }
            }
            log.warn("External code grant failed provider={} codeType={} stage=VERIFY "
                            + "category=PROVIDER_UNAVAILABLE exception={}", grant.getCodeType().providerType(),
                    grant.getCodeType().name(), ex.getClass().getSimpleName());
            throw error(OAuth2ErrorCodes.SERVER_ERROR, "External authentication could not be completed.");
        } catch (OAuth2AuthenticationException ex) {
            throw ex;
        } catch (AuthenticationException ex) {
            log.warn("External code grant rejected provider={} codeType={} stage=VERIFY "
                            + "category=VERIFICATION_REJECTED exception={}", grant.getCodeType().providerType(),
                    grant.getCodeType().name(), ex.getClass().getSimpleName());
            throw error(OAuth2ErrorCodes.INVALID_GRANT, "External credential could not be verified.");
        } catch (RuntimeException ex) {
            log.warn("External code grant failed provider={} codeType={} stage=VERIFY category=SERVER_ERROR "
                            + "exception={}", grant.getCodeType().providerType(), grant.getCodeType().name(),
                    ex.getClass().getSimpleName());
            throw error(OAuth2ErrorCodes.SERVER_ERROR, "External authentication could not be completed.");
        }
    }

    @Override
    public boolean supports(@NonNull Class<?> authentication) {
        return OAuth2ExternalCodeAuthenticationToken.class.isAssignableFrom(authentication);
    }

    /**
     * 来源与条件参数先校验，未核实flow不能通过requireProofKey被误认为已执行PKCE。
     */
    private String validateSourceAndFlow(RegisteredClient client, Long registrationId,
            OAuth2ExternalCodeAuthenticationToken grant) {
        Object raw = grant.getAdditionalParameters().get(OAuth2ExternalCodeParameterNames.PROVIDER_CODE_VERIFIER);
        String verifier = raw instanceof String value ? value : null;
        Object rawRedirect = grant.getAdditionalParameters()
                .get(OAuth2ExternalCodeParameterNames.PROVIDER_REDIRECT_URI);
        String redirect = rawRedirect instanceof String value ? value : null;
        try {
            var source = sources.requireEnabledById(registrationId, grant.getCodeType().providerType())
                    .forCode(grant.getCodeType());
            if (!source.sourceType().supports(grant.getCodeType())) {
                throw error(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT, "External source is not allowed.");
            }
            boolean pkce = source.flow().providerPkce();
            if (pkce && (grant.getCodeType() != ExternalCodeType.FEISHU_APP_CODE
                    || verifier == null || !verifier.matches("[A-Za-z0-9._~-]{43,128}")
                    || !validProviderRedirect(redirect) || !client.getRedirectUris().contains(redirect))
                    || !pkce && (raw != null || rawRedirect != null)) {
                throw error(OAuth2ErrorCodes.INVALID_REQUEST, "Provider proof is invalid for this flow.");
            }
            return verifier;
        } catch (OAuth2AuthenticationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw error(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT, "External source is unavailable.");
        }
    }

    /**
     * 回调只作为固定厂商换码端点的参数，不是可由用户控制的HTTP目标。
     */
    private boolean validProviderRedirect(String value) {
        if (value == null || value.length() > 2048 || value.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        try {
            var uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getRawUserInfo() == null && uri.getRawFragment() == null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private OAuth2ClientAuthenticationToken authenticatedClient(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof OAuth2ClientAuthenticationToken client && client.isAuthenticated()) {
            return client;
        }
        throw error(OAuth2ErrorCodes.INVALID_CLIENT, "OAuth client authentication is invalid.");
    }

    /**
     * 按受信任注册Client的应用架构再次限制凭证场景，不依赖Admin保存或请求自报终端。
     *
     * @param principal 已通过客户端认证的主体
     * @param client 服务端注册策略
     * @param grant 当前外部凭证请求
     */
    private void validateClient(OAuth2ClientAuthenticationToken principal, RegisteredClient client,
            OAuth2ExternalCodeAuthenticationToken grant) {
        if (client == null || !client.getClientAuthenticationMethods().contains(principal.getClientAuthenticationMethod())
                || !client.getClientSettings().isRequireProofKey()
                || !client.getAuthorizationGrantTypes().contains(OAuth2ExternalCodeAuthenticationToken.GRANT_TYPE)) {
            throw error(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT, "OAuth client is not allowed to use this grant.");
        }
        try {
            String applicationType = client.getClientSettings().getSetting(ExternalCodeClientSettings.APPLICATION_TYPE);
            String mode = client.getClientSettings().getSetting(ExternalCodeClientSettings.WEB_LOGIN_MODE);
            var type = IamApplicationType.valueOf(applicationType);
            boolean confidentialWeb = type == IamApplicationType.WEB_SERVER
                && IamWebLoginMode.isInApp(applicationType, mode);
            var method = principal.getClientAuthenticationMethod();
            if (confidentialWeb ? !Set.of(ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                    ClientAuthenticationMethod.CLIENT_SECRET_POST, ClientAuthenticationMethod.PRIVATE_KEY_JWT).contains(method)
                : !ClientAuthenticationMethod.NONE.equals(method)
                    || !client.getClientAuthenticationMethods().equals(Set.of(ClientAuthenticationMethod.NONE))) {
                throw error(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT, "Client authentication method is not allowed.");
            }
            String platform = client.getClientSettings().getSetting(ExternalCodeClientSettings.MINIAPP_PLATFORM);
            Map<String, Long> bindings = client.getClientSettings().getSetting(ExternalCodeClientSettings.PROVIDER_BINDINGS);
            IamApplicationLoginPolicy.validate(type, platform, mode, Map.of(),
                bindings == null ? Map.of() : bindings, true);
            if (!type.supportsExternalCode(grant.getCodeType(), platform, mode)) {
                throw error(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT, "External credential scene is not allowed.");
            }
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw error(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT, "Application type or login mode is invalid.");
        }
        // 明确Code白名单与固定注册仍须同时通过，机密认证不代表拥有任意平台能力。
        Set<String> allowedTypes = stringSet(client, ExternalCodeClientSettings.CODE_TYPES);
        if (allowedTypes.isEmpty() || !allowedTypes.contains(grant.getCodeType().name())) {
            throw error(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT, "External credential type is not allowed.");
        }
    }

    /**
     * 当前协议与明确Scope共同约束响应，不能用openid切换业务协议。
     */
    private Set<String> validateScopes(RegisteredClient client, Set<String> requestedScopes) {
        boolean oidc = "OIDC".equals(client.getClientSettings().getSetting(
                ApplicationProtocolResolver.EFFECTIVE_PROTOCOL));
        if (CollectionUtils.isEmpty(requestedScopes) || oidc != requestedScopes.contains(OidcScopes.OPENID)) {
            throw error(OAuth2ErrorCodes.INVALID_SCOPE, "Requested scope is invalid.");
        }
        for (String scope : requestedScopes) {
            if (!client.getScopes().contains(scope)) {
                throw error(OAuth2ErrorCodes.INVALID_SCOPE, "Requested scope is invalid.");
            }
        }
        return new LinkedHashSet<>(requestedScopes);
    }

    private Long providerRegistration(RegisteredClient client,
            ExternalCodeType codeType) {
        Object value = client.getClientSettings().getSetting(ExternalCodeClientSettings.PROVIDER_BINDINGS);
        if (!(value instanceof Map<?, ?> bindings)) {
            throw error(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT, "External provider binding is not configured.");
        }
        Object registrationId = bindings.get(codeType.name());
        if (!(registrationId instanceof Number number)) {
            throw error(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT, "External provider binding is not configured.");
        }
        return number.longValue();
    }

    private Set<String> stringSet(RegisteredClient client, String settingName) {
        Object value = client.getClientSettings().getSetting(settingName);
        if (!(value instanceof Set<?> values)) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object item : values) {
            if (item instanceof String text) {
                result.add(text);
            }
        }
        return Set.copyOf(result);
    }

    private OAuth2AuthenticationException error(String code, String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(code, description, null));
    }
}
