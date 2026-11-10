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
package dev.macula.cloud.iam.authentication.thirdparty;

import dev.macula.boot.constants.SecurityConstants;
import dev.macula.cloud.iam.authentication.thirdparty.ExternalBindingTicketStore.ClaimedTicket;
import dev.macula.cloud.iam.pojo.entity.SysUser;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeClientSettings;
import dev.macula.cloud.iam.service.support.SysUserService;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import dev.macula.cloud.system.security.OAuth2LoginGrantTypes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.Objects;
import java.util.Set;

/**
 * 使用同一 OAuth Client 的本地用户 access token 原子完成外部身份或手机号绑定。
 *
 * @author Rain
 * @since 6.1.0
 */
@Slf4j
@Service

public class ExternalIdentityBindingService {
    private static final AuthorizationGrantType SMS_GRANT =
        new AuthorizationGrantType(SecurityConstants.GRANT_TYPE_SMS);
    private static final AuthorizationGrantType EMAIL_GRANT = new AuthorizationGrantType(OAuth2LoginGrantTypes.EMAIL);
    private static final Set<AuthorizationGrantType> ALLOWED_GRANTS = Set.of(
        AuthorizationGrantType.AUTHORIZATION_CODE, SMS_GRANT, EMAIL_GRANT);

    private final ExternalBindingTicketStore tickets;
    private final OAuth2AuthorizationService authorizations;
    @org.springframework.beans.factory.annotation.Qualifier("baseRegisteredClientRepository")
    private final RegisteredClientRepository clients;
    private final SysUserService users;
    private final RegistrationIdentityBindingService bindings;
    private ProviderDirectoryPolicyService directory;
    private org.springframework.transaction.support.TransactionTemplate transactions;


    /** 先核验受信用途Token和目录，再在显式事务内绑定及注册票据提交回调。 */
    public void bind(String accessToken, String bindingTicket) {
        ClaimedTicket claimed = tickets.claim(bindingTicket);
        var failureRecorded = new java.util.concurrent.atomic.AtomicBoolean();
        try {
            BindingAccount account = validateAccessToken(accessToken, claimed.value());
            var admission = directory == null ? null : directory.check(claimed.value().identity());
            Runnable action = () -> {
                registerCompletion(claimed);
                try {
                if (directory == null) {
                    bindings.bind(claimed.value().clientId(), claimed.value().tenantId(), claimed.value().userType(),
                        claimed.value().identity(), account.id(), false);
                } else {
                    bindings.bind(claimed.value().clientId(), claimed.value().tenantId(), claimed.value().userType(),
                        claimed.value().identity(), account.id(), false, admission);
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        /** 仅已提交的已绑定成员进入后台刷新。 */
                        @Override
                        public void afterCommit() { directory.bound(claimed.value().identity()); }
                    });
                }
                } catch (BadCredentialsException | DataIntegrityViolationException ex) {
                    // 在回滚释放owner之前累计失败；避免事务模板退出后owner已释放而漏计。
                    tickets.failedAttempt(claimed);
                    failureRecorded.set(true);
                    throw ex;
                }
            };
            if (transactions == null) { action.run(); }
            else { transactions.executeWithoutResult(status -> action.run()); }
        } catch (BadCredentialsException | DataIntegrityViolationException ex) {
            if (!failureRecorded.get()) { tickets.failedAttempt(claimed); }
            throw denied();
        } catch (RuntimeException ex) {
            tickets.release(claimed);
            throw ex;
        }
    }

    /** 基础身份仅用于已验证访问令牌绑定，不能回查当前绑定HTTP请求。 */
    public ExternalIdentityBindingService(ExternalBindingTicketStore tickets,
        OAuth2AuthorizationService authorizations,
        @org.springframework.beans.factory.annotation.Qualifier("baseRegisteredClientRepository") RegisteredClientRepository clients,
        SysUserService users,
        RegistrationIdentityBindingService bindings) {
        this.tickets = tickets;
        this.authorizations = authorizations;
        this.clients = clients;
        this.users = users;
        this.bindings = bindings;
    }

    /** 生产明确使用事务模板，目录调用不位于事务代理内部。 */
    @org.springframework.beans.factory.annotation.Autowired
    public ExternalIdentityBindingService(ExternalBindingTicketStore tickets, OAuth2AuthorizationService authorizations,
        @org.springframework.beans.factory.annotation.Qualifier("baseRegisteredClientRepository") RegisteredClientRepository clients,
        SysUserService users, RegistrationIdentityBindingService bindings, ProviderDirectoryPolicyService directory,
        org.springframework.transaction.PlatformTransactionManager manager) {
        this(tickets, authorizations, clients, users, bindings);
        this.directory = java.util.Objects.requireNonNull(directory);
        this.transactions = new org.springframework.transaction.support.TransactionTemplate(manager);
    }

    private BindingAccount validateAccessToken(String token, ExternalBindingTicket ticket) {
        if (!StringUtils.hasText(token)) {
            throw denied();
        }
        OAuth2Authorization authorization = authorizations.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
        if (authorization == null || authorization.getAccessToken() == null
            || !authorization.getAccessToken().isActive()) {
            throw denied();
        }
        RegisteredClient client = clients.findById(authorization.getRegisteredClientId());
        if (client == null || !client.getClientId().equals(ticket.clientId())) {
            throw denied();
        }
        AuthorizationGrantType issuedBy = accessTokenGrant(authorization);
        if (!ALLOWED_GRANTS.contains(issuedBy)
            || !client.getAuthorizationGrantTypes().contains(issuedBy)) {
            throw denied();
        }
        Object tenant = client.getClientSettings().getSetting(ExternalCodeClientSettings.TENANT_ID);
        Object userType = client.getClientSettings().getSetting(ExternalCodeClientSettings.USER_TYPE);
        if (!(tenant instanceof Number tenantNumber) || !ticket.tenantId().equals(tenantNumber.longValue())
            || !(userType instanceof String type) || !ticket.userType().getType().equals(type)) {
            throw denied();
        }
        Authentication principal = authorization.getAttribute(Principal.class.getName());
        if (principal == null || !principal.isAuthenticated()
            || !(principal.getPrincipal() instanceof SysUserDetails details)
            || !ticket.tenantId().equals(details.getTenantId())) {
            throw denied();
        }
        SysUser account = users.getById(details.getUserId());
        if (account == null || !Objects.equals(account.getId(), details.getUserId())) {
            throw denied();
        }
        return new BindingAccount(account.getId(), account.getUsername(), account.getMobile());
    }

    private AuthorizationGrantType accessTokenGrant(OAuth2Authorization authorization) {
        if (authorization.getAccessToken().getClaims() == null) {
            throw denied();
        }
        Object value = authorization.getAccessToken().getClaims().get(OAuth2ParameterNames.GRANT_TYPE);
        if (!(value instanceof String grantType)) {
            throw denied();
        }
        return new AuthorizationGrantType(grantType);
    }

    private void registerCompletion(ClaimedTicket claimed) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    tickets.consume(claimed);
                } catch (RuntimeException ex) {
                    log.warn("External binding ticket finalize failed category=INFRASTRUCTURE exception={}",
                        ex.getClass().getSimpleName());
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    tickets.release(claimed);
                }
            }
        });
    }

    private BadCredentialsException denied() {
        return new BadCredentialsException("账号绑定验证失败");
    }

    /**
     * 已由受信 Token 主体定位的本地账号快照。
     *
     * @since 6.1.0
     */
    private record BindingAccount(Long id, String username, String mobile) {
    }
}
