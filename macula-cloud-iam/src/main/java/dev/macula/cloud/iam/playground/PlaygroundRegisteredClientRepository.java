/*
 * Copyright (c) 2023 Macula
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

package dev.macula.cloud.iam.playground;

import java.time.Duration;
import java.util.Map;
import java.util.LinkedHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.settings.*;

/**
 * 用保留命名空间隔离演示客户端；业务查询仍交给原缓存仓库。
 * @author Rain
 * @since 6.1.0
 */
public final class PlaygroundRegisteredClientRepository implements RegisteredClientRepository {
    public static final String PREFIX = "iam-playground-";
    public static final String PUBLIC = PREFIX + "public";
    public static final String CONFIDENTIAL = PREFIX + "confidential";
    public static final String DEVICE = PREFIX + "device";
    private final RegisteredClientRepository delegate;
    private final PlaygroundAccessPolicy policy;
    private final Map<String, RegisteredClient> clients;

    public PlaygroundRegisteredClientRepository(RegisteredClientRepository delegate, PlaygroundAccessPolicy policy,
        PlaygroundProperties properties, String issuer, PasswordEncoder encoder) {
        this.delegate = delegate;
        this.policy = policy;
        Map<String, RegisteredClient> configured = new LinkedHashMap<>();
        if (policy.isEnabled()) {
            String callback = policy.issuer(issuer) + "/playground/callback";
            configured.put(PUBLIC, codeClient(PUBLIC, callback, ClientAuthenticationMethod.NONE).build());
            configured.put(DEVICE, base(DEVICE).clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.DEVICE_CODE).scope("playground.read").build());
            if (properties.getClientSecret() != null && !properties.getClientSecret().isBlank()) {
                configured.put(CONFIDENTIAL, codeClient(CONFIDENTIAL, callback, ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .clientSecret(encoder.encode(properties.getClientSecret()))
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .authorizationGrantType(new AuthorizationGrantType("password"))
                    .authorizationGrantType(new AuthorizationGrantType("sms")).build());
            }
        }
        clients = Map.copyOf(configured);
    }

    private static RegisteredClient.Builder base(String id) {
        return RegisteredClient.withId(id).clientId(id).clientName("IAM 接入演示")
            .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
            .tokenSettings(TokenSettings.builder().accessTokenFormat(OAuth2TokenFormat.REFERENCE)
                .accessTokenTimeToLive(Duration.ofMinutes(5)).refreshTokenTimeToLive(Duration.ofMinutes(10))
                .authorizationCodeTimeToLive(Duration.ofMinutes(2)).deviceCodeTimeToLive(Duration.ofMinutes(5))
                .reuseRefreshTokens(false).build());
    }

    private static RegisteredClient.Builder codeClient(String id, String callback, ClientAuthenticationMethod method) {
        return base(id).clientAuthenticationMethod(method).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(callback).postLogoutRedirectUri(callback.replace("/callback", ""))
            .scope("openid").scope("profile").scope("playground.read")
            .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(true).build());
    }

    public static boolean isPlayground(@Nullable String id) {
        return id != null && id.startsWith(PREFIX);
    }

    @Override
    public void save(RegisteredClient client) {
        if (isPlayground(client.getId()) || isPlayground(client.getClientId())) {
            throw new IllegalArgumentException("Playground client namespace is reserved");
        }
        delegate.save(client);
    }

    @Override
    public @Nullable RegisteredClient findById(String id) {
        return isPlayground(id) ? demo(id) : business(delegate.findById(id));
    }

    @Override
    public @Nullable RegisteredClient findByClientId(String clientId) {
        return isPlayground(clientId) ? demo(clientId) : business(delegate.findByClientId(clientId));
    }

    private @Nullable RegisteredClient demo(String id) {
        if (!policy.isEnabled() || !clients.containsKey(id)) return null;
        // Check both keys: a DB row must not shadow a reserved registration or client ID.
        if (delegate.findByClientId(id) != null || delegate.findById(id) != null) {
            throw new IllegalStateException("Registered client conflicts with reserved playground namespace");
        }
        return clients.get(id);
    }

    private @Nullable RegisteredClient business(@Nullable RegisteredClient client) {
        if (client != null && (isPlayground(client.getId()) || isPlayground(client.getClientId()))) {
            throw new IllegalStateException("Registered client conflicts with reserved playground namespace");
        }
        return client;
    }
}
