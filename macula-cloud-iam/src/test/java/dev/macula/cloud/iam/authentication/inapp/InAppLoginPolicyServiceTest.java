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

import dev.macula.cloud.iam.authentication.thirdparty.IdentityProviderConfigService;
import dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures;
import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.service.support.SysOAuth2ClientService;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import dev.macula.cloud.system.enums.IdentitySourceType;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 端内证明绑定Client、来源、版本和本地用户，不接受其他SSO证明。
 * @author Rain
 * @since 6.1.0
 */
class InAppLoginPolicyServiceTest {
    /** 当前端内来源之外的Client、注册、能力、版本和用户均不能续转。 */
    @Test
    void rejectsStaleCrossClientCrossSourceAndCrossUserProofs() {
        var sources = mock(IdentityProviderConfigService.class);
        var source = IdentitySourceTestFixtures.source(IdentitySourceType.WECOM_WEB,"WECOM","corp","fixture-secret",Map.of("corpId","corp","agentId","agent"));
        when(sources.requireEnabledById(1L,"WECOM")).thenReturn(source);
        var policy = new InAppLoginPolicyService(mock(SysOAuth2ClientService.class),sources,AuthorizationServerSettings.builder().build());
        var client = new SysOAuth2Client();
        client.setClientId("container");
        client.setApplicationType("IN_APP");
        client.setTenantId(1L);
        client.setUserType(UserTypeEnum.EMPLOYEE);
        client.setAuthorizationVersion(7L);
        client.setBrowserCapabilityBindings("{\"WECOM_WEBVIEW\":1}");
        var user = new SysUserDetails();
        user.setUserId(9L);
        user.setTenantId(1L);
        user.setEnabled(true);
        var auth = UsernamePasswordAuthenticationToken.authenticated(user,"",java.util.List.of());
        var proof = new HashMap<String,Object>(Map.of("registrationId",1L,"sourceVersion",source.version(),"loginCapability","WECOM_WEBVIEW",
            "flow",source.forCapability("WECOM_WEBVIEW").flow().value(),"clientId","container","clientAuthorizationVersion",7L,"userId",9L,"tenantId",1L,"userType","EMPLOYEE"));
        assertTrue(policy.validProof(client,auth,proof,"authorization_code"));
        for (var mismatch : Map.<String,Object>of("clientId","other","clientAuthorizationVersion",6L,"registrationId",2L,
            "loginCapability","WECOM_BROWSER","sourceVersion",99L,"userId",10L).entrySet()) {
            var invalid = new HashMap<>(proof);
            invalid.put(mismatch.getKey(),mismatch.getValue());
            assertFalse(policy.validProof(client,auth,invalid,"authorization_code"),mismatch.getKey());
        }
        assertFalse(policy.validProof(client,auth,Map.of(),"authorization_code"));
        assertFalse(policy.validProof(client,auth,proof,"urn:macula:params:oauth:grant-type:external_code"));
    }
}
