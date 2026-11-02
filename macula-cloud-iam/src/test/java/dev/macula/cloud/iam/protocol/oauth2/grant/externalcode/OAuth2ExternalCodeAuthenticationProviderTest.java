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
import dev.macula.cloud.system.enums.ExternalCodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证 external_code Provider 的 Client、scope、registration 和调用顺序边界。
 *
 * @author Rain
 * @since 6.1.0
 */
class OAuth2ExternalCodeAuthenticationProviderTest {
    private static final ExternalCodeType CODE_TYPE = ExternalCodeType.WECOM_MINIAPP_CODE;
    private static final Long REGISTRATION_ID = 17L;

    private final ExternalCodeVerifierRegistry verifiers = mock(ExternalCodeVerifierRegistry.class);
    private final ExternalCodeReplayStore replayStore = mock(ExternalCodeReplayStore.class);
    private final ExternalCodeAccountResolver accountResolver = mock(ExternalCodeAccountResolver.class);
    private final OAuth2UserGrantTokenIssuer tokenIssuer = mock(OAuth2UserGrantTokenIssuer.class);
    private final ExternalCodeVerifier verifier = mock(ExternalCodeVerifier.class);
    private final Authentication userPrincipal = mock(Authentication.class);
    private final OAuth2AccessTokenAuthenticationToken issuedToken =
        mock(OAuth2AccessTokenAuthenticationToken.class);

    private OAuth2ExternalCodeAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new OAuth2ExternalCodeAuthenticationProvider(verifiers, replayStore, accountResolver,
            tokenIssuer);
    }

    @Test
    void rejectsDisallowedCodeTypeBeforeClaimOrVerification() {
        RegisteredClient client = client(Set.of(), Set.of("profile"));

        OAuth2AuthenticationException error = catchThrowableOfType(
            () -> provider.authenticate(grant(client, Set.of("profile"))), OAuth2AuthenticationException.class);

        assertThat(error.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
        verifyNoInteractions(replayStore, verifiers, accountResolver, tokenIssuer);
    }

    @Test
    void rejectsOpenidBeforeClaimOrVerification() {
        RegisteredClient client = client(Set.of(CODE_TYPE.name()), Set.of("profile", OidcScopes.OPENID));

        OAuth2AuthenticationException error = catchThrowableOfType(
            () -> provider.authenticate(grant(client, Set.of(OidcScopes.OPENID))),
            OAuth2AuthenticationException.class);

        assertThat(error.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_SCOPE);
        verifyNoInteractions(replayStore, verifiers, accountResolver, tokenIssuer);
    }

    @Test
    void usesFixedRegistrationAndIssuesAccessTokenOnly() {
        RegisteredClient client = client(Set.of(CODE_TYPE.name()), Set.of("profile"));
        OAuth2ExternalCodeAuthenticationToken grant = grant(client, Set.of("profile"));
        ExternalCodeVerification verification = ExternalCodeVerification.identity("WECOM", "member-id");
        when(verifiers.require(CODE_TYPE)).thenReturn(verifier);
        when(verifier.verify(CODE_TYPE, REGISTRATION_ID, "short-code")).thenReturn(verification);
        when(accountResolver.resolve(client, CODE_TYPE, verification)).thenReturn(userPrincipal);
        when(tokenIssuer.issue(client, (OAuth2ClientAuthenticationToken)grant.getPrincipal(), userPrincipal,
            grant, OAuth2ExternalCodeAuthenticationToken.GRANT_TYPE, Set.of("profile"), false))
            .thenReturn(issuedToken);

        assertThat(provider.authenticate(grant)).isSameAs(issuedToken);

        verify(replayStore).claim(CODE_TYPE, "short-code");
        verify(verifier).verify(CODE_TYPE, REGISTRATION_ID, "short-code");
        verify(tokenIssuer).issue(client, (OAuth2ClientAuthenticationToken)grant.getPrincipal(), userPrincipal,
            grant, OAuth2ExternalCodeAuthenticationToken.GRANT_TYPE, Set.of("profile"), false);
    }

    @Test
    void releasesReplayClaimOnlyForProviderInfrastructureFailure() {
        RegisteredClient client = client(Set.of(CODE_TYPE.name()), Set.of("profile"));
        OAuth2ExternalCodeAuthenticationToken grant = grant(client, Set.of("profile"));
        ExternalCodeReplayStore.ReplayClaim claim =
            new ExternalCodeReplayStore.ReplayClaim("digest-key", "owner-nonce");
        when(replayStore.claim(CODE_TYPE, "short-code")).thenReturn(claim);
        when(verifiers.require(CODE_TYPE)).thenReturn(verifier);
        when(verifier.verify(CODE_TYPE, REGISTRATION_ID, "short-code"))
            .thenThrow(new ExternalCodeInfrastructureException());

        OAuth2AuthenticationException error = catchThrowableOfType(
            () -> provider.authenticate(grant), OAuth2AuthenticationException.class);

        assertThat(error.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.SERVER_ERROR);
        verify(replayStore).release(claim);
        verifyNoInteractions(accountResolver, tokenIssuer);
    }

    private RegisteredClient client(Set<String> codeTypes, Set<String> scopes) {
        RegisteredClient.Builder builder = RegisteredClient.withId("public-client")
            .clientId("public-client")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(OAuth2ExternalCodeAuthenticationToken.GRANT_TYPE)
            .clientSettings(ClientSettings.builder().requireProofKey(true)
                .setting(ExternalCodeClientSettings.CODE_TYPES, codeTypes)
                .setting(ExternalCodeClientSettings.PROVIDER_BINDINGS,
                    Map.of(CODE_TYPE.name(), REGISTRATION_ID)).build());
        scopes.forEach(builder::scope);
        return builder.build();
    }

    private OAuth2ExternalCodeAuthenticationToken grant(RegisteredClient client, Set<String> scopes) {
        OAuth2ClientAuthenticationToken clientPrincipal =
            new OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.NONE, null);
        return new OAuth2ExternalCodeAuthenticationToken(clientPrincipal, scopes, Map.of(), CODE_TYPE,
            "short-code");
    }
}
