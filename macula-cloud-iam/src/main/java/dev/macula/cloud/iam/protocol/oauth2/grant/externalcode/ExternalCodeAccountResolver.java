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
import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import dev.macula.cloud.iam.service.userdetails.ClientAwareAccountResolver;
import dev.macula.cloud.iam.service.userdetails.OAuthClientUserDetailsService;
import dev.macula.cloud.system.enums.ExternalCodeType;
import dev.macula.cloud.system.security.UserIdentifierNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 在 OAuth Client 路由快照内把已核验外部身份解析为本地认证主体。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class ExternalCodeAccountResolver {
    private final ClientAwareAccountResolver accounts;
    private final OAuthClientUserDetailsService userDetails;
    private final ExternalBindingTicketStore bindingTickets;
    private final UserIdentifierNormalizer normalizer;

    /**
     * 解析已核验身份；未绑定时签发 opaque ticket 并返回受控扩展错误。
     *
     * @param registeredClient 已认证 OAuth Client
     * @param codeType 外部凭证类型
     * @param verification 服务端核验结果
     * @return 本地用户认证主体
     */
    public Authentication resolve(RegisteredClient registeredClient, ExternalCodeType codeType,
        ExternalCodeVerification verification) {
        ClientRoute route = route(registeredClient);
        validateVerification(codeType, verification);
        if (codeType.resultType() == ExternalCodeType.ResultType.MOBILE) {
            String mobile = normalizer.mobile(verification.mobile()).orElseThrow(this::invalidGrant);
            verification = ExternalCodeVerification.mobile(mobile);
        }
        Optional<SysUser> account = codeType.resultType() == ExternalCodeType.ResultType.MOBILE
            ? accounts.findByMobile(route.userType(), verification.mobile())
            : accounts.findByExternalIdentity(route.userType(), verification.providerType(), verification.subject());
        if (account.isEmpty()) {
            String ticket = bindingTickets.issue(registeredClient.getClientId(), route.tenantId(), route.userType(),
                verification);
            throw new ExternalBindingRequiredException(ticket);
        }
        UserDetails principal = userDetails.loadUserByClient(registeredClient.getClientId(), route.tenantId(),
            route.userType(), account.get().getUsername());
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    private ClientRoute route(RegisteredClient registeredClient) {
        Object tenantValue = registeredClient.getClientSettings().getSetting(ExternalCodeClientSettings.TENANT_ID);
        Object userTypeValue = registeredClient.getClientSettings().getSetting(ExternalCodeClientSettings.USER_TYPE);
        if (!(tenantValue instanceof Number tenantNumber) || !(userTypeValue instanceof String userTypeText)) {
            throw invalidGrant();
        }
        UserTypeEnum userType = null;
        for (UserTypeEnum candidate : UserTypeEnum.values()) {
            if (candidate.getType().equals(userTypeText)) {
                userType = candidate;
                break;
            }
        }
        if (userType == null) {
            throw invalidGrant();
        }
        return new ClientRoute(tenantNumber.longValue(), userType);
    }

    private void validateVerification(ExternalCodeType codeType, ExternalCodeVerification verification) {
        boolean validIdentity = codeType.resultType() == ExternalCodeType.ResultType.EXTERNAL_IDENTITY
            && codeType.providerType().equals(verification.providerType())
            && verification.subject() != null && verification.mobile() == null;
        boolean validMobile = codeType.resultType() == ExternalCodeType.ResultType.MOBILE
            && verification.providerType() == null && verification.subject() == null
            && verification.mobile() != null;
        if (!validIdentity && !validMobile) {
            throw invalidGrant();
        }
    }

    private OAuth2AuthenticationException invalidGrant() {
        return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT,
            "External credential could not be verified.", null));
    }

    /**
     * 已认证 Client 中不可由请求覆盖的本地用户路由快照。
     *
     * @since 6.1.0
     */
    private record ClientRoute(Long tenantId, UserTypeEnum userType) {
    }
}
