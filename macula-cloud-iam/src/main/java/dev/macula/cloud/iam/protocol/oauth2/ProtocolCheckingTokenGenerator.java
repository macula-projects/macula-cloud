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
package dev.macula.cloud.iam.protocol.oauth2;

import dev.macula.cloud.iam.authentication.mfa.AuthenticationCheckpoint;
import dev.macula.cloud.iam.authentication.mfa.MfaCompletionService;
import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2ClientRegistrationService;
import dev.macula.cloud.system.security.OAuth2ResourcePolicy;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

/**
 * 每次签发前读取当前配置，阻止授权码、刷新令牌和缓存快照绕过协议禁用。
 *
 * @author Rain
 * @since 6.1.0
 */
public final class ProtocolCheckingTokenGenerator implements OAuth2TokenGenerator<OAuth2Token> {
    private final OAuth2TokenGenerator<OAuth2Token> delegate;
    private final OAuth2ClientRegistrationService clients;
    private MfaCompletionService mfaCompletion =
            MfaCompletionService.noOp();
    /**
     * 保留框架生成器，仅增加当前配置边界。
     */
    public ProtocolCheckingTokenGenerator(OAuth2TokenGenerator<OAuth2Token> delegate, OAuth2ClientRegistrationService clients) {
        this.delegate = delegate;
        this.clients = clients;
    }

    /**
     * Spring生产装配必须注入统一策略；显式构造的存储/生成器单测保留默认行为。
     */
    @Autowired
    public void setMfaCompletion(MfaCompletionService completion) {
        this.mfaCompletion = Objects.requireNonNull(completion);
    }

    /**
     * refresh以原授权Scope校验，不能用缩小Scope把已禁用的OIDC变成OAuth。
     */
    @Override
    public OAuth2Token generate(OAuth2TokenContext context) {
        String grant = context.getAuthorizationGrantType().getValue();
        Set<String> scopes = "refresh_token".equals(grant) && context.getAuthorization() != null
                ? context.getAuthorization().getAuthorizedScopes() : context.getAuthorizedScopes();
        var current = clients.getClientByClientId(context.getRegisteredClient().getClientId());
        OAuth2ClientProtocolPolicy.require(current, scopes, grant);
        Number snapshotVersion = context.getRegisteredClient().getClientSettings().getSetting(
                OAuth2ResourcePolicy.AUTHORIZATION_VERSION);
        if (snapshotVersion == null || current.getAuthorizationVersion() == null
                || snapshotVersion.longValue() != current.getAuthorizationVersion()
                || context.getAuthorization() != null
                && (context.getAuthorization()
                .getAttribute(OAuth2ResourcePolicy.AUTHORIZATION_VERSION) != null
                || Set.of("authorization_code", "refresh_token").contains(grant))
                && !ResourceAuthorizationPolicy.current(context.getAuthorization(), current)) {
            throw new OAuth2AuthenticationException("invalid_grant");
        }
        ResourceAuthorizationPolicy.audiences(context.getRegisteredClient(), context.getAuthorizedScopes());
        var proof = MfaCompletionService.proof(context.getPrincipal());
        if (proof.isEmpty() && context.getAuthorization() != null) {
            Map<String, Object> stored = context.getAuthorization().getAttribute(
                    MfaCompletionService.SOURCE_PROOF);
            if (stored != null) {
                proof = stored;
            }
        }
        mfaCompletion.checkToken(context.getPrincipal(), context.getRegisteredClient(),
                "refresh_token".equals(grant)
                        ? AuthenticationCheckpoint.REFRESH_TOKEN
                        : AuthenticationCheckpoint.USER_TOKEN, grant, proof);
        return delegate.generate(context);
    }
}
