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

import dev.macula.cloud.system.enums.ExternalCodeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 external_code 只接受标准 code、固定 code_type 和最小参数集合。
 *
 * @author Rain
 * @since 6.1.0
 */
class OAuth2ExternalCodeAuthenticationConverterTest {
    private final OAuth2ExternalCodeAuthenticationConverter converter =
        new OAuth2ExternalCodeAuthenticationConverter();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void convertsKnownCodeTypeAndStandardCode() {
        authenticateClient();

        OAuth2ExternalCodeAuthenticationToken token =
            (OAuth2ExternalCodeAuthenticationToken)converter.convert(request());

        assertThat(token.getCodeType()).isEqualTo(ExternalCodeType.WECOM_MINIAPP_CODE);
        assertThat(token.getCode()).isEqualTo("short-lived-code");
        assertThat(token.getScopes()).containsExactly("profile");
    }

    @Test
    void rejectsProviderOverrideAndPkceParameters() {
        authenticateClient();
        MockHttpServletRequest providerOverride = request();
        providerOverride.addParameter("provider", "WECOM");
        assertThatThrownBy(() -> converter.convert(providerOverride))
            .isInstanceOf(OAuth2AuthenticationException.class);

        MockHttpServletRequest pkce = request();
        pkce.addParameter(PkceParameterNames.CODE_VERIFIER, "not-applicable");
        assertThatThrownBy(() -> converter.convert(pkce)).isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void rejectsUnknownOrDuplicateCodeType() {
        authenticateClient();
        MockHttpServletRequest unknown = request();
        unknown.setParameter(OAuth2ExternalCodeParameterNames.CODE_TYPE, "UNKNOWN");
        assertThatThrownBy(() -> converter.convert(unknown)).isInstanceOf(OAuth2AuthenticationException.class);

        MockHttpServletRequest duplicate = request();
        duplicate.addParameter(OAuth2ExternalCodeParameterNames.CODE_TYPE,
            ExternalCodeType.WECOM_MINIAPP_CODE.name());
        assertThatThrownBy(() -> converter.convert(duplicate)).isInstanceOf(OAuth2AuthenticationException.class);
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.addParameter(OAuth2ParameterNames.GRANT_TYPE, ExternalCodeType.GRANT_TYPE);
        request.addParameter(OAuth2ParameterNames.CLIENT_ID, "public-client");
        request.addParameter(OAuth2ParameterNames.CODE, "short-lived-code");
        request.addParameter(OAuth2ExternalCodeParameterNames.CODE_TYPE,
            ExternalCodeType.WECOM_MINIAPP_CODE.name());
        request.addParameter(OAuth2ParameterNames.SCOPE, "profile");
        return request;
    }

    private void authenticateClient() {
        RegisteredClient client = RegisteredClient.withId("public-client")
            .clientId("public-client")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(OAuth2ExternalCodeAuthenticationToken.GRANT_TYPE)
            .scope("profile")
            .build();
        SecurityContextHolder.getContext().setAuthentication(
            new OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.NONE, null));
    }
}
