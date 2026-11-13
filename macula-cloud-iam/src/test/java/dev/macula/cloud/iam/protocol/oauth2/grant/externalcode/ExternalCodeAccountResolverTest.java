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
import java.util.Optional;

import dev.macula.cloud.iam.authentication.thirdparty.ExternalBindingTicketStore;
import dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceTestFixtures;
import dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity;
import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.service.identity.IdentitySourceBindingPolicyService;
import dev.macula.cloud.system.enums.ExternalCodeType;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SDK的身份/号码结果均使用来源统一策略，固定Client与来源不可被结果覆盖。
 *
 * @author Rain
 * @since 6.1.0
 */
class ExternalCodeAccountResolverTest {
    private final IdentitySourceBindingPolicyService policy = mock(IdentitySourceBindingPolicyService.class);
    private final ExternalBindingTicketStore tickets = mock(ExternalBindingTicketStore.class);
    private final ExternalCodeAccountResolver resolver = new ExternalCodeAccountResolver(policy, tickets);

    /**
     * 号码结果也必须执行来源策略，无自动开关旁路。
     */
    @Test
    void mobileRequiresBindingWhenPolicyDoesNotResolve() {
        var verification = IdentitySourceTestFixtures.mobile("13800000000");
        when(tickets.issue("app", 1L, UserTypeEnum.EMPLOYEE, verification)).thenReturn("opaque-ticket");
        var required = assertThrows(ExternalBindingRequiredException.class,
                () -> resolver.resolve(client(ExternalCodeType.ALIYUN_MOBILE_TOKEN),
                        ExternalCodeType.ALIYUN_MOBILE_TOKEN, verification));
        assertEquals("opaque-ticket", required.getBindingTicket());
        verify(policy).resolve("app", 1L, UserTypeEnum.EMPLOYEE, verification.identity());
    }

    /**
     * 已绑定或合法自动匹配结果被作为用户主体，不再另查手机号。
     */
    @Test
    void resolvedIdentityReturnsPrincipalWithoutTicket() {
        var verification = IdentitySourceTestFixtures.sdkIdentity("WECOM", "member");
        var principal = User.withUsername("alice").password("unused").authorities("USER").build();
        when(policy.resolve("app", 1L, UserTypeEnum.EMPLOYEE, verification.identity())).thenReturn(
                Optional.of(principal));
        assertEquals("alice",
                resolver.resolve(client(ExternalCodeType.WECOM_APP_CODE), ExternalCodeType.WECOM_APP_CODE, verification)
                        .getName());
        verifyNoInteractions(tickets);
    }

    /**
     * 来源ID不一致时不能进入策略，更不能签发其他来源的票据。
     */
    @Test
    void mismatchedRegistrationIsRejected() {
        var identity = new VerifiedExternalIdentity(2L, "WECOM", "WECOM_SDK", 0L,
                IdentitySourceTestFixtures.flow("WECOM_SDK"),
                "WECOM_APP_CODE", "MEMBER_ID", "member", Map.of());
        assertThrows(OAuth2AuthenticationException.class,
                () -> resolver.resolve(client(ExternalCodeType.WECOM_APP_CODE),
                        ExternalCodeType.WECOM_APP_CODE, new ExternalCodeVerification(identity)));
        verifyNoInteractions(policy, tickets);
    }

    /**
     * 已认证Client决定来源和用户路由。
     */
    private RegisteredClient client(ExternalCodeType code) {
        return RegisteredClient.withId("app").clientId("app")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(new AuthorizationGrantType(ExternalCodeType.GRANT_TYPE)).scope("api.read")
                .clientSettings(ClientSettings.builder().setting(ExternalCodeClientSettings.TENANT_ID, 1L)
                        .setting(ExternalCodeClientSettings.USER_TYPE, "employee")
                        .setting(ExternalCodeClientSettings.PROVIDER_BINDINGS, Map.of(code.name(), 1L)).build())
                .build();
    }
}
