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
package dev.macula.cloud.iam.controller;

import dev.macula.cloud.iam.authentication.thirdparty.ExternalIdentityBindingService;
import dev.macula.cloud.iam.form.ExternalIdentityBindingForm;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 将 Bearer 本地用户身份与 opaque binding ticket 适配到无 Session 绑定服务。
 *
 * @author Rain
 * @since 6.1.0
 */
@RestController
@RequiredArgsConstructor
public class ExternalIdentityBindingController {
    public static final String BINDING_PATH = "/api/v1/external-identity-bindings";

    private final ExternalIdentityBindingService bindingService;

    @PostMapping(BINDING_PATH)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void bind(Authentication authentication, @Valid @RequestBody ExternalIdentityBindingForm form) {
        if (!(authentication instanceof AbstractOAuth2TokenAuthenticationToken<?> tokenAuthentication)) {
            throw new org.springframework.security.authentication.BadCredentialsException("Bearer token 无效");
        }
        try {
            bindingService.bind(tokenAuthentication.getToken().getTokenValue(), form.getBindingTicket());
        } catch (BadCredentialsException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Binding could not be completed.");
        }
    }
}
