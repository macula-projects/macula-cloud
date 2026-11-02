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
package dev.macula.cloud.iam.protocol.oauth2.grant.email;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证邮箱验证码 grant 仅接受单值邮箱与标准 code 参数。
 *
 * @author Rain
 * @since 6.1.0
 */
class OAuth2ResourceOwnerEmailAuthenticationConverterTest {
    private final OAuth2ResourceOwnerEmailAuthenticationConverter converter =
        new OAuth2ResourceOwnerEmailAuthenticationConverter();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void convertsEmailAndStandardCode() {
        authenticateClient();
        MockHttpServletRequest request = request();

        OAuth2ResourceOwnerEmailAuthenticationToken token =
            (OAuth2ResourceOwnerEmailAuthenticationToken)converter.convert(request);

        assertThat(token).isNotNull();
        assertThat(token.getAuthorizationGrantType())
            .isEqualTo(OAuth2ResourceOwnerEmailAuthenticationToken.GRANT_TYPE);
        assertThat(token.getAdditionalParameters())
            .containsEntry(OAuth2ResourceOwnerEmailAuthenticationConverter.EMAIL_PARAMETER, "user@example.test")
            .containsEntry(OAuth2ParameterNames.CODE, "123456");
    }

    @Test
    void rejectsDuplicateCode() {
        authenticateClient();
        MockHttpServletRequest request = request();
        request.addParameter(OAuth2ParameterNames.CODE, "654321");

        assertThatThrownBy(() -> converter.convert(request)).isInstanceOf(OAuth2AuthenticationException.class);
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.addParameter(OAuth2ParameterNames.GRANT_TYPE,
            OAuth2ResourceOwnerEmailAuthenticationToken.GRANT_TYPE_VALUE);
        request.addParameter(OAuth2ParameterNames.CLIENT_ID, "public-client");
        request.addParameter(OAuth2ResourceOwnerEmailAuthenticationConverter.EMAIL_PARAMETER,
            "user@example.test");
        request.addParameter(OAuth2ParameterNames.CODE, "123456");
        request.addParameter(OAuth2ParameterNames.SCOPE, "profile");
        return request;
    }

    private void authenticateClient() {
        RegisteredClient client = RegisteredClient.withId("public-client")
            .clientId("public-client")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(OAuth2ResourceOwnerEmailAuthenticationToken.GRANT_TYPE)
            .scope("profile")
            .build();
        SecurityContextHolder.getContext().setAuthentication(
            new OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.NONE, null));
    }
}
