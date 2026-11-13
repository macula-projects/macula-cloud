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
package dev.macula.cloud.iam.authentication.mfa;

import dev.macula.cloud.iam.authentication.inapp.InAppLoginPolicyService;
import dev.macula.cloud.iam.authentication.thirdparty.ThirdPartyAuthenticationToken;
import dev.macula.cloud.iam.authentication.thirdparty.VerifiedExternalIdentity;
import dev.macula.cloud.iam.protocol.oauth2.grant.externalcode.ExternalCodeClientSettings;
import dev.macula.cloud.iam.service.identity.IdentityProviderConfigService;
import dev.macula.cloud.iam.service.userdetails.SysUserDetails;
import dev.macula.cloud.system.enums.ExternalCodeType;
import dev.macula.cloud.system.enums.IamWebLoginMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/**
 * 在凭据验证之后、成果发布之前调用策略，并比较原始来源版本。
 * 策略只接收最小用户投影，不能从请求client_id或平台声明推断已完成MFA。
 *
 * @author Rain
 * @since 6.1.0
 */
public final class MfaCompletionService {
    public static final String SOURCE_PROOF = "macula.authentication.sourceProof";
    private final MfaCompletionPolicy policy;
    private final IdentityProviderConfigService sources;
    private InAppLoginPolicyService inAppPolicy;

    /**
     * 单元测试或独立SPI使用；带来源证明时仍拒绝缺少权威配置读取。
     */
    public MfaCompletionService(MfaCompletionPolicy policy) {
        this(policy, null);
    }

    /**
     * 生产装配同时提供来源版本栅栏。
     */
    public MfaCompletionService(MfaCompletionPolicy policy, IdentityProviderConfigService sources) {
        this.policy = Objects.requireNonNull(policy);
        this.sources = sources;
    }

    /**
     * 无自定义策略时保留现有行为，供显式测试构造使用。
     */
    public static MfaCompletionService noOp() {
        return new MfaCompletionService(context -> {
        });
    }

    /**
     * 从服务端已验证身份构造不含subject和Secret的来源证明。
     */
    public static Map<String, Object> verifiedProof(VerifiedExternalIdentity identity) {
        Map<String, Object> result = new HashMap<>();
        result.put("registrationId", identity.registrationId());
        result.put("loginCapability", identity.loginCapability());
        result.put("providerType", identity.providerType());
        result.put("sourceVersion", identity.sourceVersion());
        result.put("flow", identity.flow());
        return result;
    }

    /**
     * 只读取认证器构造的私有证明；普通请求参数从未进入此处。
     */
    public static Map<String, Object> proof(Authentication authentication) {
        Map<String, Object> result = new HashMap<>();
        if (authentication instanceof ThirdPartyAuthenticationToken external) {
            var context = external.getLoginContext();
            if (context.clientId() != null) {
                result.put(InAppLoginPolicyService.CLIENT_ID,
                        context.clientId());
            }
            if (context.clientAuthorizationVersion() != null) {
                result.put(InAppLoginPolicyService.CLIENT_VERSION,
                        context.clientAuthorizationVersion());
            }
            if (context.userType() != null) {
                result.put(InAppLoginPolicyService.USER_TYPE,
                        context.userType().name());
            }
            if (authentication.getPrincipal() instanceof SysUserDetails user) {
                result.put(InAppLoginPolicyService.USER_ID, user.getUserId());
                result.put(InAppLoginPolicyService.TENANT_ID,
                        user.getTenantId());
            }
            result.put("registrationId", context.registrationId());
            result.put("loginCapability", context.loginCapability());
            result.put("providerType", context.providerType());
            result.put("sourceVersion", context.sourceVersion());
            result.put("flow", context.flow());
        } else if (authentication != null && authentication.getDetails() instanceof Map<?, ?> details
                && details.get(SOURCE_PROOF) instanceof Map<?, ?> stored) {
            stored.forEach((key, value) -> {
                if (key instanceof String text) {
                    result.put(text, value);
                }
            });
        }
        return result;
    }

    /**
     * 生产完成点必须执行端内当前来源约束，不改变原MFA策略。
     */
    @Autowired
    public void setInAppPolicy(InAppLoginPolicyService value) {
        inAppPolicy = value;
    }

    /**
     * 签发路径转换成OAuth安全错误；不将未完成因子当invalid_grant重试凭据。
     */
    public void checkToken(Authentication authentication, RegisteredClient client, AuthenticationCheckpoint checkpoint,
            String entryPoint, Map<String, Object> sourceProof) {
        if ("client_credentials".equals(entryPoint)) {
            return;
        }
        try {
            if (client != null && client.getClientSettings().getSetting(ExternalCodeClientSettings.APPLICATION_TYPE) != null
                && IamWebLoginMode.isInApp(client.getClientSettings().getSetting(
                    ExternalCodeClientSettings.APPLICATION_TYPE), client.getClientSettings().getSetting(
                    ExternalCodeClientSettings.WEB_LOGIN_MODE)) &&
                    inAppPolicy == null) {
                throw new AdditionalAuthenticationRequiredException();
            }
            AuthenticationMethod method = ExternalCodeType.GRANT_TYPE.equals(entryPoint)
                    ? AuthenticationMethod.EXTERNAL : "sms".equals(entryPoint) ? AuthenticationMethod.SMS
                    : Set.of("authorization_code", "refresh_token").contains(entryPoint)
                    ? AuthenticationMethod.EXISTING_SESSION : AuthenticationMethod.UNKNOWN;
            check(authentication, client == null ? null : client.getClientId(), checkpoint,
                    entryPoint, method, sourceProof);
        } catch (AdditionalAuthenticationRequiredException ex) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.SERVER_ERROR);
        }
    }

    /**
     * 来源版本需在每次发布前仍有效；默认策略也不能绕过该栅栏。
     */
    public void check(Authentication authentication, String clientId, AuthenticationCheckpoint checkpoint,
            String entryPoint, AuthenticationMethod method, Map<String, Object> sourceProof) {
        try {
            if (inAppPolicy != null) {
                inAppPolicy.requireProof(clientId, authentication, sourceProof, entryPoint);
            }
            Long registrationId = null;
            if (sourceProof != null && !sourceProof.isEmpty()) {
                if (sources == null || !(sourceProof.get("registrationId") instanceof Number id)
                        || !(sourceProof.get("sourceVersion") instanceof Number version)
                        || !(sourceProof.get("providerType") instanceof String provider)
                        || !(sourceProof.get("flow") instanceof String flow)
                        || !(sourceProof.get("loginCapability") instanceof String capability)) {
                    throw new AdditionalAuthenticationRequiredException();
                }
                var current = sources.requireEnabledById(id.longValue(), provider).forCapability(capability);
                if (current.version().longValue() != version.longValue() || !current.flow().value().equals(flow)) {
                    throw new AdditionalAuthenticationRequiredException();
                }
                registrationId = id.longValue();
            }
            SysUserDetails user =
                    authentication != null && authentication.getPrincipal() instanceof SysUserDetails details
                            ? details : null;
            policy.check(new AuthenticationCompletionContext(clientId, user == null ? null : user.getTenantId(),
                    user == null ? null : user.getUserId(), entryPoint, method, registrationId, checkpoint));
        } catch (RuntimeException ex) {
            // 未来策略抛异常时必须闭合，不能发布半份登录或泄漏策略消息。
            throw new AdditionalAuthenticationRequiredException();
        }
    }
}
