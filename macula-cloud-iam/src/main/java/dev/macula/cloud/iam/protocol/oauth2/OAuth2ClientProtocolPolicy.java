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
package dev.macula.cloud.iam.protocol.oauth2;

import java.util.Set;
import java.util.stream.Collectors;

import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.system.enums.ExternalCodeType;
import dev.macula.cloud.system.enums.OAuth2ClientConfigurationStatus;
import dev.macula.cloud.system.enums.OAuth2ProtocolMode;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.util.StringUtils;

/**
 * 草稿隔离与单协议请求的统一校验，Scope不能改变已配置的协议。
 *
 * @author Rain
 * @since 6.1.0
 */
public final class OAuth2ClientProtocolPolicy {
    /**
     * 工具类不允许实例化。
     */
    private OAuth2ClientProtocolPolicy() {
    }

    /**
     * 只接受已启用、具备授权版本且仅有一份协议配置的业务Client。
     */
    public static Set<OAuth2ProtocolMode> protocols(SysOAuth2Client client) {
        try {
            if (client == null || !"APPLICATION".equals(client.getPurpose()) ||
                    client.getAuthorizationVersion() == null || client.getTenantId() == null ||
                    client.getUserType() == null && !"SERVICE".equals(client.getApplicationType()) ||
                    OAuth2ClientConfigurationStatus.fromValue(client.getConfigurationStatus())
                            != OAuth2ClientConfigurationStatus.ENABLED) {
                throw rejected();
            }
            if (client.getProtocolConfigurations() == null || client.getProtocolConfigurations().size() != 1) {
                throw rejected();
            }
            Set<String> seen = new java.util.HashSet<>();
            for (var configuration : client.getProtocolConfigurations()) {
                if (!seen.add(configuration.protocol())) {
                    throw rejected();
                }
                dev.macula.cloud.system.protocol.ApplicationProtocolRegistry.require(configuration);
                if (configuration.enabled()) {
                    dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration.validateRuntime(configuration,
                            client.getApplicationType());
                }
            }
            Set<OAuth2ProtocolMode> values = client.getProtocolConfigurations().stream()
                    .filter(dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration::enabled)
                    .map(configuration -> OAuth2ProtocolMode.fromValue(configuration.protocol()))
                    .collect(Collectors.toUnmodifiableSet());
            if (values.size() != 1) {
                throw rejected();
            }
            return values;
        } catch (RuntimeException ex) {
            throw rejected();
        }
    }

    /**
     * 配置非法或草稿时不提供认证入口，不按历史字段推断。
     */
    public static boolean enabled(SysOAuth2Client client) {
        if (client != null && "RESOURCE_SERVER".equals(client.getPurpose())) {
            return "ENABLED".equals(client.getConfigurationStatus()) && client.getAuthorizationVersion() != null
                    && client.getProtocolConfigurations().isEmpty();
        }
        try {
            protocols(client);
            return true;
        } catch (OAuth2AuthenticationException ex) {
            return false;
        }
    }

    /**
     * 按原授权Scope校验协议，仅external_code用户扩展可属于OIDC，refresh不得转换原协议。
     */
    public static void require(SysOAuth2Client client, Set<String> scopes, String grant) {
        try {
            if (client == null ||
                    !dev.macula.cloud.system.enums.OAuth2ClientApplicationType.valueOf(client.getApplicationType())
                            .supportsGrant(grant)) {
                throw rejected();
            }
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw rejected();
        }
        Set<OAuth2ProtocolMode> values = protocols(client);
        boolean oidc = scopes.contains(OidcScopes.OPENID);
        if (scopes.contains(OidcScopes.PROFILE) && "client_credentials".equals(grant)) {
            throw rejected();
        }
        boolean direct = Set.of("client_credentials", ExternalCodeType.GRANT_TYPE).contains(grant);
        if ((direct && oidc && !ExternalCodeType.GRANT_TYPE.equals(grant)) ||
                !values.contains(oidc ? OAuth2ProtocolMode.OIDC : OAuth2ProtocolMode.OAUTH2_1)) {
            throw rejected();
        }
        var configuration = configuration(client, oidc ? "OIDC" : "OAUTH2_1");
        var settings = dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration.settings(configuration);
        if (!StringUtils.commaDelimitedListToSet(settings.getScopes()).containsAll(scopes)
                || !StringUtils.commaDelimitedListToSet(settings.getAuthorizationGrantTypes()).contains(grant)) {
            throw rejected();
        }
    }

    /**
     * 精确选择已启用的权威配置，拒绝主表旧列或其他协议补齐。
     */
    public static dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration configuration(
            SysOAuth2Client client, String protocol) {
        protocols(client);
        return client.getProtocolConfigurations().stream()
                .filter(value -> value.enabled() && value.protocol().equals(protocol))
                .findFirst().orElseThrow(OAuth2ClientProtocolPolicy::rejected);
    }

    /**
     * 不在错误中暴露配置或凭据。
     */
    private static OAuth2AuthenticationException rejected() {
        return new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
    }
}
