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
package dev.macula.cloud.iam.authentication.inapp;

import dev.macula.cloud.iam.service.application.LoginApplicationResolver;
import dev.macula.cloud.iam.service.application.LoginApplicationResolver.Selection;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import dev.macula.cloud.system.enums.ExternalCodeType;
import dev.macula.cloud.system.enums.IamWebLoginMode;
import dev.macula.cloud.system.enums.IdentityLoginCapability;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * 端内授权只接受当前Client固定来源的服务端证明，普通IAM会话不能代替平台认证。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class InAppLoginPolicyService {
    public static final String CLIENT_ID = "clientId";
    public static final String CLIENT_VERSION = "clientAuthorizationVersion";
    public static final String USER_ID = "userId";
    public static final String TENANT_ID = "tenantId";
    public static final String USER_TYPE = "userType";
    private final LoginApplicationResolver applications;
    private final IdentityProviderConfigService sources;

    /**
     * 只从Session中由授权链保存的请求恢复应用，不信任登录页的client_id参数。
     */
    public Optional<Selection> savedClient(HttpServletRequest request, HttpServletResponse response) {
        return applications.resolve(request, response).filter(selection -> inApp(selection));
    }

    /** 返回可信引用当前对应的应用；不保存匿名Session。 */
    public Selection current(String reference) {
        var app = applications.trusted(reference, null, false);
        return app == null ? null : new Selection(reference, app);
    }

    /** 必须恰好一个合法端内能力，且来源仍启用，不取第一条猜测默认。 */
    public IdentityProviderConfigService.ProviderConfig source(Selection selection) {
        try {
            var app = selection.application();
            if (!inApp(selection) || !app.loginEnabled() || app.login().sources().size() != 1) {
                throw denied();
            }
            var entry = app.login().sources().entrySet().iterator().next();
            var capability = entry.getKey();
            // 协议适配器复核该能力的grant许可，公共策略不解析OAuth设置。
            var current = applications.trusted(selection.reference(), capability, false);
            if (current == null || !Objects.equals(current.authorizationVersion(), app.authorizationVersion())
                || !Objects.equals(current.login(), app.login())) {
                throw denied();
            }
            return sources.requireEnabledById(entry.getValue(), capability.source().providerType())
                .forCapability(capability.value());
        } catch (RuntimeException error) {
            throw denied();
        }
    }

    /** 只允许Web/SPA创建时选定的端内模式。 */
    public boolean inApp(Selection selection) {
        if (selection == null) {
            return false;
        }
        var login = selection.application().login();
        return login.type() != null && IamWebLoginMode.isInApp(login.type().name(),
            login.webLoginMode() == null ? null : login.webLoginMode().value());
    }

    /**
     * 每次授权和成果发布重查当前来源/Client版本，校验无副作用。
     */
    public boolean validProof(Selection selection, Authentication authentication, Map<String, Object> proof,
            String entryPoint) {
        try {
            var client = selection.application();
            if (authentication == null || !authentication.isAuthenticated()
                    || !(authentication.getPrincipal() instanceof SysUserDetails user) || !user.isEnabled()
                    || user.getUserId() == null || !Objects.equals(user.getTenantId(), client.tenantId())) {
                return false;
            }
            var source = source(selection);
            if (!(proof.get("registrationId") instanceof Number id) || id.longValue() != source.registrationId()
                    || !(proof.get("sourceVersion") instanceof Number version) ||
                    version.longValue() != source.version()
                    || !source.loginCapability().equals(proof.get("loginCapability")) ||
                    !source.flow().value().equals(proof.get("flow"))) {
                return false;
            }
            if (ExternalCodeType.GRANT_TYPE.equals(entryPoint)) {
                return IdentityLoginCapability.fromValue(source.loginCapability()).code() != null;
            }
            return IdentityLoginCapability.fromValue(source.loginCapability()).browser()
                    && selection.reference().equals(proof.get(CLIENT_ID))
                    && proof.get(CLIENT_VERSION) instanceof Number clientVersion &&
                    client.authorizationVersion() != null
                    && clientVersion.longValue() == client.authorizationVersion()
                    && Objects.equals(user.getUserId(), proof.get(USER_ID)) &&
                    Objects.equals(user.getTenantId(), proof.get(TENANT_ID))
                    && client.accountUserType() != null && client.accountUserType().name().equals(proof.get(USER_TYPE));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * 非端内应用保持原策略；端内所有完成点都不能用普通会话替代当前平台证明。
     */
    public void requireProof(String clientId, Authentication authentication, Map<String, Object> proof,
            String entryPoint) {
        if (clientId == null) {
            return;
        }
        var client = current(clientId);
        if (inApp(client) &&
                !validProof(client, authentication, proof, entryPoint)) {
            throw denied();
        }
    }

    /**
     * 错误不泄露来源、用户或认证材料。
     */
    private AuthenticationServiceException denied() {
        return new AuthenticationServiceException("端内登录配置或来源证明无效，请重新发起");
    }
}
