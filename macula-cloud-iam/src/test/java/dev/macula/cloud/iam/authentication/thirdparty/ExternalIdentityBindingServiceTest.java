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
package dev.macula.cloud.iam.authentication.thirdparty;

import dev.macula.cloud.iam.authentication.thirdparty.ExternalBindingTicketStore.ClaimedTicket;
import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeClientSettings;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.OAuth2ExternalCodeAuthenticationToken;
import dev.macula.cloud.iam.service.support.SysUserService;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证无 Session 绑定仅接受同 Client 的本地用户 Token，并保持 ticket 事务状态。
 *
 * @author Rain
 * @since 6.1.0
 */
class ExternalIdentityBindingServiceTest {
    private static final String ACCESS_TOKEN = "local-access-token";
    private static final String CLIENT_ID = "public-client";

    private final ExternalBindingTicketStore tickets = mock(ExternalBindingTicketStore.class);
    private final OAuth2AuthorizationService authorizations = mock(OAuth2AuthorizationService.class);
    private final RegisteredClientRepository clients = mock(RegisteredClientRepository.class);
    private final SysUserService users = mock(SysUserService.class);
    private final RegistrationIdentityBindingService bindings = mock(RegistrationIdentityBindingService.class);
    private final ExternalIdentityBindingService service =
        new ExternalIdentityBindingService(tickets, authorizations, clients, users, bindings);

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void bindsExternalIdentityAndConsumesTicketAfterCommit() {
        RegisteredClient client = client();
        ClaimedTicket claimed = claimed("EXTERNAL_IDENTITY", "WECOM", "member-id", null);
        when(tickets.claim("binding-ticket")).thenReturn(claimed);
        when(authorizations.findByToken(ACCESS_TOKEN,
            org.springframework.security.oauth2.server.authorization.OAuth2TokenType.ACCESS_TOKEN))
            .thenReturn(authorization(client, AuthorizationGrantType.AUTHORIZATION_CODE));
        when(clients.findById(client.getId())).thenReturn(client);
        when(users.getById(7L)).thenReturn(account(null));

        service.bind(ACCESS_TOKEN, "binding-ticket");
        completeTransaction(true);

        verify(bindings).bind(CLIENT_ID, 1L, UserTypeEnum.EMPLOYEE, claimed.value().identity(), 7L, false);
        verify(tickets).consume(claimed);
        verify(tickets, never()).failedAttempt(claimed);
    }

    @Test
    void rejectsExternalCodeTokenAsAccountOwnershipProof() {
        RegisteredClient client = client();
        ClaimedTicket claimed = claimed("EXTERNAL_IDENTITY", "WECOM", "member-id", null);
        when(tickets.claim("binding-ticket")).thenReturn(claimed);
        when(authorizations.findByToken(ACCESS_TOKEN,
            org.springframework.security.oauth2.server.authorization.OAuth2TokenType.ACCESS_TOKEN))
            .thenReturn(authorization(client, OAuth2ExternalCodeAuthenticationToken.GRANT_TYPE));
        when(clients.findById(client.getId())).thenReturn(client);

        assertThatThrownBy(() -> service.bind(ACCESS_TOKEN, "binding-ticket"))
            .isInstanceOf(BadCredentialsException.class);

        verify(tickets).failedAttempt(claimed);
        verify(bindings, never()).bind(any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void rejectsMobileThatWouldOverwriteAnotherValue() {
        RegisteredClient client = client();
        ClaimedTicket claimed = claimed("MOBILE", null, null, "13800000000");
        when(tickets.claim("binding-ticket")).thenReturn(claimed);
        when(authorizations.findByToken(ACCESS_TOKEN,
            org.springframework.security.oauth2.server.authorization.OAuth2TokenType.ACCESS_TOKEN))
            .thenReturn(authorization(client, AuthorizationGrantType.AUTHORIZATION_CODE));
        when(clients.findById(client.getId())).thenReturn(client);
        when(users.getById(7L)).thenReturn(account("13900000000"));
        when(bindings.bind(CLIENT_ID, 1L, UserTypeEnum.EMPLOYEE, claimed.value().identity(), 7L, false))
            .thenThrow(new BadCredentialsException("phone mismatch"));

        assertThatThrownBy(() -> service.bind(ACCESS_TOKEN, "binding-ticket"))
            .isInstanceOf(BadCredentialsException.class);

        verify(tickets).failedAttempt(claimed);
        verify(users, never()).update(any());
    }

    private RegisteredClient client() {
        return RegisteredClient.withId("registered-client-id")
            .clientId(CLIENT_ID)
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(OAuth2ExternalCodeAuthenticationToken.GRANT_TYPE)
            .redirectUri("https://client.example.test/callback")
            .scope("profile")
            .clientSettings(ClientSettings.builder().requireProofKey(true)
                .setting(ExternalCodeClientSettings.TENANT_ID, 1L)
                .setting(ExternalCodeClientSettings.USER_TYPE, UserTypeEnum.EMPLOYEE.getType()).build())
            .build();
    }

    private OAuth2Authorization authorization(RegisteredClient client, AuthorizationGrantType grantType) {
        Instant issuedAt = Instant.now();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, ACCESS_TOKEN,
            issuedAt, issuedAt.plusSeconds(300), Set.of("profile"));
        SysUserDetails details = new SysUserDetails();
        details.setUserId(7L);
        details.setTenantId(1L);
        details.setUsername("alice");
        details.setEnabled(true);
        Authentication principal = UsernamePasswordAuthenticationToken.authenticated(details, null, List.of());
        return OAuth2Authorization.withRegisteredClient(client)
            .principalName(details.getUsername())
            .authorizationGrantType(grantType)
            .authorizedScopes(Set.of("profile"))
            .attribute(Principal.class.getName(), principal)
            .token(accessToken, metadata -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                Map.of(OAuth2ParameterNames.GRANT_TYPE, grantType.getValue())))
            .build();
    }

    private ClaimedTicket claimed(String bindingType, String providerType, String subject, String mobile) {
        ExternalBindingTicket ticket = new ExternalBindingTicket(CLIENT_ID, 1L, UserTypeEnum.EMPLOYEE,
            mobile == null ? IdentitySourceTestFixtures.sdkIdentity(providerType, subject).identity()
                : IdentitySourceTestFixtures.mobile(mobile).identity(), Instant.now().plusSeconds(300), 0);
        return new ClaimedTicket("ticket-key", "owner-nonce", ticket);
    }

    private SysUser account(String mobile) {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername("alice");
        user.setMobile(mobile);
        return user;
    }

    private void completeTransaction(boolean committed) {
        List<TransactionSynchronization> synchronizations =
            TransactionSynchronizationManager.getSynchronizations();
        if (committed) {
            synchronizations.forEach(TransactionSynchronization::afterCommit);
        }
        int status = committed ? TransactionSynchronization.STATUS_COMMITTED
            : TransactionSynchronization.STATUS_ROLLED_BACK;
        synchronizations.forEach(synchronization -> synchronization.afterCompletion(status));
    }
}
