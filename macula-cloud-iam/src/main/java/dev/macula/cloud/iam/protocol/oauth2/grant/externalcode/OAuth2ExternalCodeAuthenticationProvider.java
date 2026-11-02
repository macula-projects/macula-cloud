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

import dev.macula.cloud.iam.protocol.oauth2.grant.base.OAuth2UserGrantTokenIssuer;
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
import org.springframework.util.CollectionUtils;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 校验 external_code Client 策略、外部凭证和本地账号后签发 access token。
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

    public OAuth2ExternalCodeAuthenticationProvider(ExternalCodeVerifierRegistry verifiers,
        ExternalCodeReplayStore replayStore, ExternalCodeAccountResolver accountResolver,
        OAuth2UserGrantTokenIssuer tokenIssuer) {
        this.verifiers = verifiers;
        this.replayStore = replayStore;
        this.accountResolver = accountResolver;
        this.tokenIssuer = tokenIssuer;
    }

    @Override
    public Authentication authenticate(@NonNull Authentication authentication) {
        OAuth2ExternalCodeAuthenticationToken grant = (OAuth2ExternalCodeAuthenticationToken)authentication;
        OAuth2ClientAuthenticationToken clientPrincipal = authenticatedClient(grant);
        RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();
        validateClient(clientPrincipal, registeredClient, grant);
        Long registrationId = providerRegistration(registeredClient, grant.getCodeType());
        Set<String> scopes = validateScopes(registeredClient, grant.getScopes());
        ExternalCodeReplayStore.ReplayClaim replayClaim = replayStore.claim(grant.getCodeType(), grant.getCode());
        try {
            ExternalCodeVerifier verifier = verifiers.require(grant.getCodeType());
            ExternalCodeVerification verification = verifier.verify(grant.getCodeType(), registrationId,
                grant.getCode());
            Authentication userPrincipal = accountResolver.resolve(registeredClient, grant.getCodeType(),
                verification);
            return tokenIssuer.issue(registeredClient, clientPrincipal, userPrincipal, grant,
                OAuth2ExternalCodeAuthenticationToken.GRANT_TYPE, scopes, false);
        } catch (ExternalCodeInfrastructureException ex) {
            replayStore.release(replayClaim);
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

    private OAuth2ClientAuthenticationToken authenticatedClient(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof OAuth2ClientAuthenticationToken client && client.isAuthenticated()) {
            return client;
        }
        throw error(OAuth2ErrorCodes.INVALID_CLIENT, "OAuth client authentication is invalid.");
    }

    private void validateClient(OAuth2ClientAuthenticationToken principal, RegisteredClient client,
        OAuth2ExternalCodeAuthenticationToken grant) {
        if (client == null || !ClientAuthenticationMethod.NONE.equals(principal.getClientAuthenticationMethod())) {
            throw error(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT, "OAuth client is not allowed to use this grant.");
        }
        if (!client.getClientAuthenticationMethods().equals(Set.of(ClientAuthenticationMethod.NONE))
            || !client.getClientSettings().isRequireProofKey()
            || !client.getAuthorizationGrantTypes().contains(OAuth2ExternalCodeAuthenticationToken.GRANT_TYPE)) {
            throw error(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT, "OAuth client is not allowed to use this grant.");
        }
        Set<String> allowedTypes = stringSet(client, ExternalCodeClientSettings.CODE_TYPES);
        if (allowedTypes.isEmpty() || !allowedTypes.contains(grant.getCodeType().name())) {
            throw error(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT, "External credential type is not allowed.");
        }
    }

    private Set<String> validateScopes(RegisteredClient client, Set<String> requestedScopes) {
        if (CollectionUtils.isEmpty(requestedScopes) || requestedScopes.contains(OidcScopes.OPENID)) {
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
        dev.macula.cloud.system.enums.ExternalCodeType codeType) {
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
