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

import dev.macula.boot.constants.SecurityConstants;
import dev.macula.cloud.iam.authentication.challenge.LoginCaptchaService;
import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.form.ProtocolCaptchaForm;
import dev.macula.cloud.iam.form.ProtocolVerificationCodeForm;
import dev.macula.cloud.iam.message.ProtocolLoginCodeDispatcher;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeClientSettings;
import dev.macula.cloud.system.security.OAuth2LoginGrantTypes;
import dev.macula.cloud.system.security.UserIdentifierNormalizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 为公共 OAuth Client 提供无 Session CAPTCHA 及短信、邮箱验证码申请端点。
 *
 * @author Rain
 * @since 6.1.0
 */
@RestController
@RequiredArgsConstructor
public class ProtocolVerificationCodeController {
    public static final String CAPTCHA_PATH = "/api/v1/oauth2/login-challenges/captcha";
    public static final String SMS_CODE_PATH = "/api/v1/oauth2/sms-codes";
    public static final String EMAIL_CODE_PATH = "/api/v1/oauth2/email-codes";
    public static final String CLIENT_ID_HEADER = "X-OAuth-Client-Id";

    private static final AuthorizationGrantType SMS_GRANT =
        new AuthorizationGrantType(SecurityConstants.GRANT_TYPE_SMS);
    private static final AuthorizationGrantType EMAIL_GRANT =
        new AuthorizationGrantType(OAuth2LoginGrantTypes.EMAIL);

    private final RegisteredClientRepository clients;
    private final LoginCaptchaService captchaService;
    private final ProtocolLoginCodeDispatcher dispatcher;
    private final UserIdentifierNormalizer normalizer;

    @PostMapping(CAPTCHA_PATH)
    public ResponseEntity<Map<String, String>> captcha(@Valid @RequestBody ProtocolCaptchaForm form,
        HttpServletRequest request) {
        RequestContext context = context(form.getClientId(), request);
        requireVerificationGrant(context.client());
        LoginCaptchaService.CaptchaChallenge challenge;
        try {
            challenge = captchaService.issue(context.client().getClientId(), context.origin(),
                request.getRemoteAddr());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Verification request is temporarily unavailable.");
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
            "challenge_id", challenge.id(), "prompt", challenge.prompt()));
    }

    @PostMapping(SMS_CODE_PATH)
    public ResponseEntity<Void> sms(@Valid @RequestBody ProtocolVerificationCodeForm form,
        HttpServletRequest request) {
        RequestContext context = context(form.getClientId(), request);
        requireGrant(context.client(), SMS_GRANT);
        requireCaptcha(form, context);
        normalizer.mobile(form.getTarget()).ifPresent(mobile -> submitSms(mobile, request.getRemoteAddr(), context));
        return ResponseEntity.accepted().build();
    }

    @PostMapping(EMAIL_CODE_PATH)
    public ResponseEntity<Void> email(@Valid @RequestBody ProtocolVerificationCodeForm form,
        HttpServletRequest request) {
        RequestContext context = context(form.getClientId(), request);
        requireGrant(context.client(), EMAIL_GRANT);
        requireCaptcha(form, context);
        normalizer.email(form.getTarget()).ifPresent(email -> submitEmail(email, request.getRemoteAddr(), context));
        return ResponseEntity.accepted().build();
    }

    private RequestContext context(String bodyClientId, HttpServletRequest request) {
        String headerClientId = singleHeader(request, CLIENT_ID_HEADER);
        String origin = singleHeader(request, HttpHeaders.ORIGIN);
        if (!bodyClientId.equals(headerClientId)) {
            throw badRequest();
        }
        RegisteredClient client = clients.findByClientId(headerClientId);
        if (!isPublicPkceClient(client) || !allowedOrigins(client).contains(origin)) {
            throw badRequest();
        }
        Object userTypeValue = client.getClientSettings().getSetting(ExternalCodeClientSettings.USER_TYPE);
        Object tenantValue = client.getClientSettings().getSetting(ExternalCodeClientSettings.TENANT_ID);
        UserTypeEnum userType = userTypeValue instanceof String value ? userType(value) : null;
        if (userType == null || !(tenantValue instanceof Number tenantNumber)) {
            throw badRequest();
        }
        return new RequestContext(client, tenantNumber.longValue(), userType, origin);
    }

    private boolean isPublicPkceClient(RegisteredClient client) {
        return client != null
            && client.getClientAuthenticationMethods().equals(Set.of(ClientAuthenticationMethod.NONE))
            && client.getClientSettings().isRequireProofKey();
    }

    private String singleHeader(HttpServletRequest request, String name) {
        List<String> values = Collections.list(request.getHeaders(name));
        if (values.size() != 1 || !StringUtils.hasText(values.get(0)) || values.get(0).contains(",")) {
            throw badRequest();
        }
        return values.get(0).trim();
    }

    private Collection<String> allowedOrigins(RegisteredClient client) {
        Object value = client.getClientSettings().getSetting(ExternalCodeClientSettings.ALLOWED_ORIGINS);
        if (value instanceof Collection<?> values) {
            return values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
    }

    private UserTypeEnum userType(String value) {
        for (UserTypeEnum candidate : UserTypeEnum.values()) {
            if (candidate.getType().equals(value)) {
                return candidate;
            }
        }
        return null;
    }

    private void requireGrant(RegisteredClient client, AuthorizationGrantType grant) {
        if (!client.getAuthorizationGrantTypes().contains(grant)) {
            throw badRequest();
        }
    }

    private void requireVerificationGrant(RegisteredClient client) {
        if (!client.getAuthorizationGrantTypes().contains(SMS_GRANT)
            && !client.getAuthorizationGrantTypes().contains(EMAIL_GRANT)) {
            throw badRequest();
        }
    }

    private void requireCaptcha(ProtocolVerificationCodeForm form, RequestContext context) {
        if (!captchaService.consume(form.getCaptchaChallenge(), form.getCaptchaAnswer(),
            context.client().getClientId(), context.origin())) {
            throw badRequest();
        }
    }

    private void submitSms(String mobile, String remoteAddress, RequestContext context) {
        try {
            dispatcher.sms(mobile, remoteAddress, context.client().getClientId(), context.tenantId(),
                context.userType());
        } catch (RuntimeException ignored) {
            // Executor saturation must not reveal account or supplier state.
        }
    }

    private void submitEmail(String email, String remoteAddress, RequestContext context) {
        try {
            dispatcher.email(email, remoteAddress, context.client().getClientId(),
                context.client().getClientName(), context.tenantId(), context.userType());
        } catch (RuntimeException ignored) {
            // Executor saturation must not reveal account or supplier state.
        }
    }

    private ResponseStatusException badRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification request is invalid.");
    }

    /**
     * 已核对 Client、用户类型和 Origin 的请求上下文。
     *
     * @param client OAuth Client
     * @param userType 用户类型
     * @param origin 精确 Origin
     * @since 6.1.0
     */
    private record RequestContext(RegisteredClient client, Long tenantId, UserTypeEnum userType, String origin) {
    }
}
