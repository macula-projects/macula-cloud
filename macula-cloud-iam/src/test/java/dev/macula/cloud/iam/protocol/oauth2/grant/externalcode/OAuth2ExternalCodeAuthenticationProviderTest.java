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

import java.util.Map;
import java.util.Set;

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

    private final dev.macula.cloud.iam.service.identity.IdentityProviderConfigService sources =
            mock(dev.macula.cloud.iam.service.identity.IdentityProviderConfigService.class);
    private final ExternalCodeRateLimiter limiter = mock(ExternalCodeRateLimiter.class);
    private OAuth2ExternalCodeAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new OAuth2ExternalCodeAuthenticationProvider(verifiers, replayStore, accountResolver,
                tokenIssuer, sources, limiter);
        var source = new dev.macula.cloud.iam.service.identity.IdentityProviderConfigService.ProviderConfig(
                REGISTRATION_ID, dev.macula.cloud.system.enums.IdentitySourceType.WECOM_MINIAPP, false, null, 1L,
                "WECOM", "app", "fixture-secret", Map.of(), Map.of(), true,
                dev.macula.cloud.system.enums.IdentitySourceFlow.WECOM_MINIAPP_V1,
                dev.macula.cloud.system.enums.IdentityDirectoryPolicy.IDENTITY_ONLY, "corp", null);
        when(sources.requireEnabledById(REGISTRATION_ID, "WECOM")).thenReturn(source);
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
        ExternalCodeVerification verification =
                dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.sdkIdentity(
                "WECOM", "member-id");
        when(verifiers.require(CODE_TYPE)).thenReturn(verifier);
        when(verifier.verify(CODE_TYPE, REGISTRATION_ID, "short-code")).thenReturn(verification);
        when(accountResolver.resolve(client, CODE_TYPE, verification)).thenReturn(userPrincipal);
        when(tokenIssuer.issue(client, (OAuth2ClientAuthenticationToken) grant.getPrincipal(), userPrincipal,
                grant, OAuth2ExternalCodeAuthenticationToken.GRANT_TYPE, Set.of("profile"), false))
                .thenReturn(issuedToken);

        assertThat(provider.authenticate(grant)).isSameAs(issuedToken);

        verify(replayStore).claim(CODE_TYPE, "short-code");
        verify(verifier).verify(CODE_TYPE, REGISTRATION_ID, "short-code");
        verify(tokenIssuer).issue(client, (OAuth2ClientAuthenticationToken) grant.getPrincipal(), userPrincipal,
                grant, OAuth2ExternalCodeAuthenticationToken.GRANT_TYPE, Set.of("profile"), false);
    }

    /**
     * OIDC只在显式openid请求下继续核验，并仍按公共客户端禁止刷新。
     */
    @Test
    void oidcRequiresOpenidBeforeVerificationAndKeepsFixedSource() {
        var base = client(Set.of(CODE_TYPE.name()), Set.of("openid", "profile"));
        var settings = ClientSettings.withSettings(base.getClientSettings().getSettings())
                .setting(dev.macula.cloud.iam.protocol.oauth2.ApplicationProtocolResolver.EFFECTIVE_PROTOCOL, "OIDC")
                .build();
        var oidc = RegisteredClient.from(base).clientSettings(settings).build();
        var error = catchThrowableOfType(() -> provider.authenticate(grant(oidc, Set.of("profile"))),
                OAuth2AuthenticationException.class);
        assertThat(error.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_SCOPE);
        verifyNoInteractions(verifiers, replayStore, accountResolver, tokenIssuer);
        var grant = grant(oidc, Set.of("openid", "profile"));
        var verified = dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.sdkIdentity("WECOM",
                "oidc-member");
        when(verifiers.require(CODE_TYPE)).thenReturn(verifier);
        when(verifier.verify(CODE_TYPE, REGISTRATION_ID, "short-code")).thenReturn(verified);
        when(accountResolver.resolve(oidc, CODE_TYPE, verified)).thenReturn(userPrincipal);
        provider.authenticate(grant);
        verify(tokenIssuer).issue(oidc, (OAuth2ClientAuthenticationToken) grant.getPrincipal(), userPrincipal,
                grant, OAuth2ExternalCodeAuthenticationToken.GRANT_TYPE, Set.of("openid", "profile"), false);
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
                .thenThrow(new ExternalCodeInfrastructureException(ExternalCodeConsumptionState.NOT_SUBMITTED));

        OAuth2AuthenticationException error = catchThrowableOfType(
                () -> provider.authenticate(grant), OAuth2AuthenticationException.class);

        assertThat(error.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.SERVER_ERROR);
        verify(replayStore).release(claim);
        verifyNoInteractions(accountResolver, tokenIssuer);
    }

    /**
     * 兑换结果不确定或已消费后的故障不能重新释放Code。
     */
    @Test
    void retainsReplayClaimForUncertainOrConsumedCode() {
        RegisteredClient client = client(Set.of(CODE_TYPE.name()), Set.of("profile"));
        when(verifiers.require(CODE_TYPE)).thenReturn(verifier);
        for (var state : Set.of(ExternalCodeConsumptionState.UNKNOWN, ExternalCodeConsumptionState.CONSUMED)) {
            org.mockito.Mockito.doThrow(new ExternalCodeInfrastructureException(state)).when(verifier)
                    .verify(CODE_TYPE, REGISTRATION_ID, "short-code");
            var error = catchThrowableOfType(() -> provider.authenticate(grant(client, Set.of("profile"))),
                    OAuth2AuthenticationException.class);
            assertThat(error.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.SERVER_ERROR);
        }
        org.mockito.Mockito.verify(replayStore, org.mockito.Mockito.never())
                .release(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(accountResolver, tokenIssuer);
    }

    /**
     * 核验已经返回身份后，后续组件的基础设施异常不能把Code重新开放。
     */
    @Test
    void accountResolutionFailureCannotReleaseVerifiedCode() {
        var client = client(Set.of(CODE_TYPE.name()), Set.of("profile"));
        when(verifiers.require(CODE_TYPE)).thenReturn(verifier);
        var verified = dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.sdkIdentity("WECOM",
                "member");
        when(verifier.verify(CODE_TYPE, REGISTRATION_ID, "short-code")).thenReturn(verified);
        when(accountResolver.resolve(client, CODE_TYPE, verified)).thenThrow(
                new ExternalCodeInfrastructureException(ExternalCodeConsumptionState.NOT_SUBMITTED));
        var error = catchThrowableOfType(() -> provider.authenticate(grant(client, Set.of("profile"))),
                OAuth2AuthenticationException.class);
        assertThat(error.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.SERVER_ERROR);
        org.mockito.Mockito.verify(replayStore, org.mockito.Mockito.never())
                .release(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(tokenIssuer);
    }

    /**
     * 即使持久白名单包含Code，受信任应用类型和平台不匹配仍须在占用前拒绝。
     */
    @Test
    void rejectsPersistedCrossTerminalPolicyBeforeAnyUpstreamCall() {
        for (String type : java.util.List.of("NATIVE", "SPA", "WEB_SERVER", "SERVICE", "UNKNOWN")) {
            var original = client(Set.of(CODE_TYPE.name()), Set.of("profile"));
            var settings = ClientSettings.withSettings(original.getClientSettings().getSettings())
                    .setting(ExternalCodeClientSettings.APPLICATION_TYPE, type).build();
            var typed = RegisteredClient.from(original).redirectUri("https://app.example/callback")
                    .clientSettings(settings).build();
            var failure = catchThrowableOfType(() -> provider.authenticate(grant(typed, Set.of("profile"))),
                    OAuth2AuthenticationException.class);
            assertThat(failure.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
        }
        var original = client(Set.of(CODE_TYPE.name()), Set.of("profile"));
        var settings = ClientSettings.withSettings(original.getClientSettings().getSettings())
                .setting(ExternalCodeClientSettings.APPLICATION_TYPE, "MINIAPP")
                .setting(ExternalCodeClientSettings.MINIAPP_PLATFORM, "WECHAT").build();
        var wrongPlatform = RegisteredClient.from(original).redirectUri("https://app.example/callback")
                .clientSettings(settings).build();
        var failure = catchThrowableOfType(() -> provider.authenticate(grant(wrongPlatform, Set.of("profile"))),
                OAuth2AuthenticationException.class);
        assertThat(failure.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
        verifyNoInteractions(replayStore, verifiers, accountResolver, tokenIssuer);
    }

    /**
     * 已开放AppLink必须在claim前校验平台PKCE，并把证明交给固定来源适配器。
     */
    @Test
    void appLinkRequiresProviderProofAndForwardsItToFixedSource() {
        var sources = mock(dev.macula.cloud.iam.service.identity.IdentityProviderConfigService.class);
        var limiter = mock(ExternalCodeRateLimiter.class);
        var guarded = new OAuth2ExternalCodeAuthenticationProvider(verifiers, replayStore, accountResolver, tokenIssuer,
                sources, limiter);
        var codeType = ExternalCodeType.FEISHU_APP_CODE;
        var source = new dev.macula.cloud.iam.service.identity.IdentityProviderConfigService.ProviderConfig(
                REGISTRATION_ID, dev.macula.cloud.system.enums.IdentitySourceType.FEISHU_WEB, false, null, 1L,
                "FEISHU", "app", "fixture-secret", Map.of("appId", "app"), Map.of(), true,
                dev.macula.cloud.system.enums.IdentitySourceFlow.FEISHU_OAUTH_V3,
                dev.macula.cloud.system.enums.IdentityDirectoryPolicy.IDENTITY_ONLY, null,
                "https://app.example/callback", null,
                Map.of("FEISHU_WEB", "FEISHU_OAUTH_V3", "FEISHU_APPLINK", "FEISHU_APPLINK_V3"), "FEISHU_WEB");
        when(sources.requireEnabledById(REGISTRATION_ID, "FEISHU")).thenReturn(source);
        var original = client(Set.of(codeType.name()), Set.of("profile"));
        var settings = ClientSettings.withSettings(original.getClientSettings().getSettings())
                .setting(ExternalCodeClientSettings.APPLICATION_TYPE, "NATIVE")
                .setting(ExternalCodeClientSettings.PROVIDER_BINDINGS, Map.of(codeType.name(), REGISTRATION_ID))
                .build();
        var nativeClient = RegisteredClient.from(original).redirectUri("https://app.example/callback")
                .clientSettings(settings).build();
        var principal = new OAuth2ClientAuthenticationToken(nativeClient, ClientAuthenticationMethod.NONE, null);
        var missing = new OAuth2ExternalCodeAuthenticationToken(principal, Set.of("profile"), Map.of(), codeType,
                "code");
        var failure = catchThrowableOfType(() -> guarded.authenticate(missing), OAuth2AuthenticationException.class);
        assertThat(failure.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_REQUEST);
        verifyNoInteractions(replayStore, verifiers, limiter);
        String proof = "p".repeat(43);
        var missingRedirect = new OAuth2ExternalCodeAuthenticationToken(principal, Set.of("profile"),
                Map.of(OAuth2ExternalCodeParameterNames.PROVIDER_CODE_VERIFIER, proof), codeType, "code");
        var missingFailure = catchThrowableOfType(() -> guarded.authenticate(missingRedirect),
                OAuth2AuthenticationException.class);
        assertThat(missingFailure.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_REQUEST);
        for (String redirect : java.util.List.of("https://other.example/callback", "https://app.example/callback/extra",
                "http://app.example/callback", "https://user@app.example/callback",
                "https://app.example/callback#fragment",
                " https://app.example/callback", "https://app.example/" + "x".repeat(2048))) {
            var invalid = new OAuth2ExternalCodeAuthenticationToken(principal, Set.of("profile"),
                    Map.of(OAuth2ExternalCodeParameterNames.PROVIDER_CODE_VERIFIER, proof,
                            OAuth2ExternalCodeParameterNames.PROVIDER_REDIRECT_URI, redirect), codeType, "code");
            var rejected = catchThrowableOfType(() -> guarded.authenticate(invalid),
                    OAuth2AuthenticationException.class);
            assertThat(rejected.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_REQUEST);
        }
        verifyNoInteractions(replayStore, verifiers, limiter);
        var grant = new OAuth2ExternalCodeAuthenticationToken(principal, Set.of("profile"),
                Map.of(OAuth2ExternalCodeParameterNames.PROVIDER_CODE_VERIFIER, proof,
                        OAuth2ExternalCodeParameterNames.PROVIDER_REDIRECT_URI, "https://app.example/callback"),
                codeType, "code");
        var verification = dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures.sdkIdentity(
                "FEISHU", "fixture-open-id");
        when(verifiers.require(codeType)).thenReturn(verifier);
        when(verifier.verify(codeType, REGISTRATION_ID, "code", proof, "https://app.example/callback")).thenReturn(
                verification);
        when(accountResolver.resolve(nativeClient, codeType, verification)).thenReturn(userPrincipal);
        when(tokenIssuer.issue(nativeClient, principal, userPrincipal, grant,
                OAuth2ExternalCodeAuthenticationToken.GRANT_TYPE, Set.of("profile"), false)).thenReturn(issuedToken);
        assertThat(guarded.authenticate(grant)).isSameAs(issuedToken);
        verify(verifier).verify(codeType, REGISTRATION_ID, "code", proof, "https://app.example/callback");
    }

    /**
     * 固定来源全集在平台核验与占用前检查，不能只验证本次所传Code。
     */
    @Test
    void rejectsMultipleSdksAndCrossRegistrationMiniappCodesBeforeSideEffects() {
        var scenarios = java.util.List.of(
                Map.of("WECOM_APP_CODE", 17L, "FEISHU_APP_CODE", 17L),
                Map.of("WECHAT_MINIAPP_LOGIN_CODE", 17L, "WECHAT_MINIAPP_PHONE_CODE", 18L),
                Map.of("FEISHU_WEBVIEW_CODE", 17L, "DINGTALK_WEBVIEW_CODE", 17L));
        var types = java.util.List.of("NATIVE", "MINIAPP", "IN_APP");
        var requests = java.util.List.of(ExternalCodeType.WECOM_APP_CODE, ExternalCodeType.WECHAT_MINIAPP_LOGIN_CODE,
                ExternalCodeType.FEISHU_WEBVIEW_CODE);
        for (int i = 0; i < scenarios.size(); i++) {
            var original = client(scenarios.get(i).keySet(), Set.of("profile"));
            var settings = ClientSettings.withSettings(original.getClientSettings().getSettings())
                    .setting(ExternalCodeClientSettings.APPLICATION_TYPE, types.get(i))
                    .setting(ExternalCodeClientSettings.MINIAPP_PLATFORM, "WECHAT")
                    .setting(ExternalCodeClientSettings.PROVIDER_BINDINGS, scenarios.get(i)).build();
            var typed = RegisteredClient.from(original).clientSettings(settings).build();
            var grant = new OAuth2ExternalCodeAuthenticationToken(
                    new OAuth2ClientAuthenticationToken(typed, ClientAuthenticationMethod.NONE, null),
                    Set.of("profile"), Map.of(), requests.get(i), "fixture-code");
            var failure = catchThrowableOfType(() -> provider.authenticate(grant), OAuth2AuthenticationException.class);
            assertThat(failure.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
        }
        verifyNoInteractions(replayStore, verifiers, accountResolver, tokenIssuer);
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
