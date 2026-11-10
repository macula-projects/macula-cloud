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

import dev.macula.cloud.iam.authentication.thirdparty.ExternalBindingTicketStore;
import dev.macula.cloud.iam.authentication.thirdparty.IdentitySourceBindingPolicyService;
import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.system.enums.ExternalCodeType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Service;
import java.util.Map;

/**
 * SDK外部凭证同样遵循来源自动绑定开关，不按手机号快捷登录。
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class ExternalCodeAccountResolver {
    private final IdentitySourceBindingPolicyService policy;
    private final ExternalBindingTicketStore tickets;
    /** 在已认证Client不可覆盖的路由和来源内解析身份或签发待绑定票据。 */
    public Authentication resolve(RegisteredClient client, ExternalCodeType codeType, ExternalCodeVerification verification) {
        Object tenantValue = client.getClientSettings().getSetting(ExternalCodeClientSettings.TENANT_ID);
        Object typeValue = client.getClientSettings().getSetting(ExternalCodeClientSettings.USER_TYPE);
        Object bindingsValue = client.getClientSettings().getSetting(ExternalCodeClientSettings.PROVIDER_BINDINGS);
        if (!(tenantValue instanceof Number tenant) || !(typeValue instanceof String type)
            || !(bindingsValue instanceof Map<?, ?> fixed) || !(fixed.get(codeType.name()) instanceof Number registration)
            || registration.longValue() != verification.identity().registrationId()
            || !codeType.name().equals(verification.identity().capability())
            || !codeType.providerType().equals(verification.providerType())
            || (codeType.resultType() == ExternalCodeType.ResultType.MOBILE) != (verification.mobile() != null)) { throw invalid(); }
        UserTypeEnum userType = java.util.Arrays.stream(UserTypeEnum.values()).filter(v -> v.getType().equals(type))
            .findFirst().orElseThrow(this::invalid);
        UserDetails principal = policy.resolve(client.getClientId(), tenant.longValue(), userType, verification.identity())
            .orElseThrow(() -> new ExternalBindingRequiredException(
                tickets.issue(client.getClientId(), tenant.longValue(), userType, verification)));
        var result = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        result.setDetails(new java.util.HashMap<>(Map.of(
            dev.macula.cloud.iam.authentication.mfa.MfaCompletionService.SOURCE_PROOF,
            dev.macula.cloud.iam.authentication.mfa.MfaCompletionService.verifiedProof(verification.identity()))));
        return result;
    }
    /** 返回无敏感资料的标准错误。 */
    private OAuth2AuthenticationException invalid() {
        return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT));
    }
}
