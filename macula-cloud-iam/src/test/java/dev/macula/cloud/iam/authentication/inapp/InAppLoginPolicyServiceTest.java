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

import dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures;
import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2ClientRegistration;
import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2ClientRegistrationService;
import dev.macula.cloud.iam.service.application.IamApplicationTestFixtures;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import dev.macula.cloud.system.enums.IdentitySourceType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 端内证明绑定Client、来源、版本和本地用户，不接受其他SSO证明。
 *
 * @author Rain
 * @since 6.1.0
 */
class InAppLoginPolicyServiceTest {
    /**
     * 当前端内来源之外的Client、注册、能力、版本和用户均不能续转。
     */
    @Test
    void rejectsStaleCrossClientCrossSourceAndCrossUserProofs() {
        var sources = mock(IdentityProviderConfigService.class);
        var source = IdentitySourceTestFixtures.source(IdentitySourceType.WECOM_WEB, "WECOM", "corp", "fixture-secret",
                Map.of("corpId", "corp", "agentId", "agent"));
        when(sources.requireEnabledById(1L, "WECOM")).thenReturn(source);
        var client = new OAuth2ClientRegistration();
        client.setClientId("container");
        client.setApplicationType("SPA");
        client.setWebLoginMode("IN_APP");
        client.setHostedLoginMethods("");
        client.setTenantId(1L);
        client.setUserType(UserTypeEnum.EMPLOYEE);
        client.setAuthorizationVersion(7L);
        client.setBrowserCapabilityBindings("{\"WECOM_WEBVIEW\":1}");
        var clients = mock(OAuth2ClientRegistrationService.class);
        when(clients.getClientByClientId("container")).thenReturn(client);
        var policy = new InAppLoginPolicyService(IamApplicationTestFixtures.resolver(clients,
            AuthorizationServerSettings.builder().build()), sources);
        var selected = policy.current("container");
        var user = new SysUserDetails();
        user.setUserId(9L);
        user.setTenantId(1L);
        user.setEnabled(true);
        var auth = UsernamePasswordAuthenticationToken.authenticated(user, "", List.of());
        var proof = new HashMap<String, Object>(
                Map.of("registrationId", 1L, "sourceVersion", source.version(), "loginCapability", "WECOM_WEBVIEW",
                        "flow", source.forCapability("WECOM_WEBVIEW").flow().value(), "clientId", "container",
                        "clientAuthorizationVersion", 7L, "userId", 9L, "tenantId", 1L, "userType", "EMPLOYEE"));
        assertTrue(policy.validProof(selected, auth, proof, "authorization_code"));
        for (var mismatch : Map.<String, Object>of("clientId", "other", "clientAuthorizationVersion", 6L,
                "registrationId", 2L,
                "loginCapability", "WECOM_BROWSER", "sourceVersion", 99L, "userId", 10L).entrySet()) {
            var invalid = new HashMap<>(proof);
            invalid.put(mismatch.getKey(), mismatch.getValue());
            assertFalse(policy.validProof(selected, auth, invalid, "authorization_code"), mismatch.getKey());
        }
        assertFalse(policy.validProof(selected, auth, Map.of(), "authorization_code"));
        assertFalse(policy.validProof(selected, auth, proof, "urn:macula:params:oauth:grant-type:external_code"));
    }
}
