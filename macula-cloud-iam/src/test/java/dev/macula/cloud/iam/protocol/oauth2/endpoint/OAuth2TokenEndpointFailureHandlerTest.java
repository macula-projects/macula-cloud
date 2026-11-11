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
package dev.macula.cloud.iam.protocol.oauth2.endpoint;

import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalBindingRequiredException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SDK绑定入口只能来自可信issuer，opaque票据保留正确URL编码且不返回302。
 *
 * @author Rain
 * @since 6.1.0
 */
class OAuth2TokenEndpointFailureHandlerTest {
    /**
     * 伪造Host与Forwarded不影响入口，Base64的加号不能变成空格。
     */
    @Test
    void usesConfiguredIssuerAndEncodesOpaqueTicket() throws Exception {
        var request = new MockHttpServletRequest("POST", "/oauth2/token");
        request.addHeader("Host", "attacker.invalid");
        request.addHeader("Forwarded", "host=attacker.invalid;proto=https");
        var response = new MockHttpServletResponse();
        new OAuth2TokenEndpointFailureHandler("https://iam.example/iam").onAuthenticationFailure(request, response,
                new ExternalBindingRequiredException("opaque+ticket/="));
        var json = JsonMapper.builder().build().readTree(response.getContentAsString());
        assertEquals(400, response.getStatus());
        assertEquals("binding_required", json.path("error").asText());
        assertEquals("opaque+ticket/=", json.path("binding_ticket").asText());
        String uri = json.path("binding_uri").asText();
        assertTrue(uri.startsWith("https://iam.example/iam/login/bind/external?ticket="));
        assertTrue(uri.contains("%2B"));
        assertFalse(uri.contains("attacker.invalid"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals("no-referrer", response.getHeader("Referrer-Policy"));
    }

    /**
     * 无可信外部基址时不得回退到调用方Host。
     */
    @Test
    void invalidIssuerCannotCreateBindingUrl() {
        assertThrows(IllegalStateException.class, () -> new OAuth2TokenEndpointFailureHandler("http://attacker.invalid")
                .onAuthenticationFailure(new MockHttpServletRequest(), new MockHttpServletResponse(),
                        new ExternalBindingRequiredException("opaque")));
    }
}
