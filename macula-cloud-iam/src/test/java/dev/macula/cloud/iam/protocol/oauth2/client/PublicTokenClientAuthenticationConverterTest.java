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
package dev.macula.cloud.iam.protocol.oauth2.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 public Token Client 只从受限 grant 和参数形态建立身份。
 *
 * @author Rain
 * @since 6.1.0
 */
class PublicTokenClientAuthenticationConverterTest {
    private final PublicTokenClientAuthenticationConverter converter =
        new PublicTokenClientAuthenticationConverter(PathPatternRequestMatcher.pathPattern(
            HttpMethod.POST, "/oauth2/token"));

    @ParameterizedTest
    @ValueSource(strings = {"password", "sms", "email",
        "urn:macula:params:oauth:grant-type:external_code", "refresh_token"})
    void convertsSupportedPublicGrant(String grantType) {
        MockHttpServletRequest request = request(grantType);

        OAuth2ClientAuthenticationToken authentication =
            (OAuth2ClientAuthenticationToken)converter.convert(request);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo("public-client");
        assertThat(authentication.getClientAuthenticationMethod()).isEqualTo(ClientAuthenticationMethod.NONE);
        assertThat(authentication.getAdditionalParameters()).containsEntry(OAuth2ParameterNames.GRANT_TYPE,
            grantType);
    }

    @ParameterizedTest
    @ValueSource(strings = {"authorization_code", "client_credentials"})
    void leavesOfficialAndForbiddenGrantToFramework(String grantType) {
        assertThat(converter.convert(request(grantType))).isNull();
    }

    @Test
    void ignoresRequestsWithoutGrantType() {
        MockHttpServletRequest request = request("password");
        request.removeParameter(OAuth2ParameterNames.GRANT_TYPE);

        assertThat(converter.convert(request)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/oauth2/introspect", "/oauth2/revoke", "/oauth2/device_authorization",
        "/oauth2/par", "/oauth2/token-extra"})
    void ignoresOtherClientAuthenticationEndpointsBeforeParsingParameters(String endpoint) {
        MockHttpServletRequest request = request("password");
        request.setRequestURI(endpoint);
        request.removeParameter(OAuth2ParameterNames.CLIENT_ID);

        assertThat(converter.convert(request)).isNull();
    }

    @Test
    void ignoresNonPostTokenRequest() {
        MockHttpServletRequest request = request("password");
        request.setMethod(HttpMethod.GET.name());

        assertThat(converter.convert(request)).isNull();
    }

    @Test
    void rejectsMissingOrDuplicateClientId() {
        MockHttpServletRequest missing = request("sms");
        missing.removeParameter(OAuth2ParameterNames.CLIENT_ID);
        assertThatThrownBy(() -> converter.convert(missing)).isInstanceOf(OAuth2AuthenticationException.class);

        MockHttpServletRequest duplicate = request("sms");
        duplicate.addParameter(OAuth2ParameterNames.CLIENT_ID, "other-client");
        assertThatThrownBy(() -> converter.convert(duplicate)).isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void confidentialCredentialsRemainOwnedByOfficialConverters() {
        MockHttpServletRequest basic = request("sms");
        basic.addHeader(HttpHeaders.AUTHORIZATION, "Basic fixture");
        assertThat(converter.convert(basic)).isNull();

        MockHttpServletRequest secret = request("sms");
        secret.addParameter(OAuth2ParameterNames.CLIENT_SECRET, "fixture");
        assertThat(converter.convert(secret)).isNull();

        MockHttpServletRequest assertion = request("sms");
        assertion.addParameter(OAuth2ParameterNames.CLIENT_ASSERTION, "fixture");
        assertThat(converter.convert(assertion)).isNull();
    }

    private MockHttpServletRequest request(String grantType) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/token");
        request.addParameter(OAuth2ParameterNames.GRANT_TYPE, grantType);
        request.addParameter(OAuth2ParameterNames.CLIENT_ID, "public-client");
        return request;
    }
}
