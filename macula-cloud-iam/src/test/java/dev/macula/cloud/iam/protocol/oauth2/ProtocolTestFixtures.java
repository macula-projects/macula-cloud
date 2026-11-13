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

import dev.macula.cloud.iam.enums.UserTypeEnum;
import dev.macula.cloud.iam.protocol.oauth2.client.OAuth2ClientRegistration;
import dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration;
import dev.macula.cloud.system.protocol.oauth2.OAuth2ProtocolSettings;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将前序测试素材显式转为新子配置；生产代码不能使用该迁移辅助。
 *
 * @author Rain
 * @since 6.1.0
 */
public final class ProtocolTestFixtures {
    /**
     * 禁止实例化。
     */
    private ProtocolTestFixtures() {
    }

    /**
     * 使用明确的测试应用身份及两份独立设置，避免共享可变对象。
     */
    public static OAuth2ClientRegistration configure(OAuth2ClientRegistration row) {
        String selected = row.getProtocolConfigurations() == null || row.getProtocolConfigurations().isEmpty()
            ? "OIDC" : row.getProtocolConfigurations().get(0).protocol();
        return configure(row, selected);
    }

    /** 单协议夹具显式选择发布协议，不再恢复旧主表的默认/启用字段。 */
    public static OAuth2ClientRegistration configure(OAuth2ClientRegistration row, String protocol) {
        row.setPurpose("APPLICATION");
        if (row.getAuthorizationVersion() == null) {
            row.setAuthorizationVersion(1L);
        }
        row.setResourcePermissions(
                "{\"profile\":\"urn:macula:iam:profile\",\"api.read\":\"urn:macula:system\"," +
                        "\"system:access\":\"urn:macula:system\"}");
        if (row.getTenantId() == null) {
            row.setTenantId(1L);
        }
        if (row.getUserType() == null) {
            row.setUserType(UserTypeEnum.EMPLOYEE);
        }
        if (row.getApplicationType() == null) {
            row.setApplicationType("none".equals(row.getClientAuthenticationMethods()) ? "SPA" : "WEB_SERVER");
        }
        if (row.getConfigurationStatus() == null) {
            row.setConfigurationStatus("ENABLED");
        }
        String codes = protocol.contains(",") ? "OIDC" : protocol;
        row.setProtocolConfigurations(Arrays.stream(codes.split(",")).map(code -> {
            var settings = new OAuth2ProtocolSettings();
            settings.setClientAuthenticationMethods(
                    row.getClientAuthenticationMethods() == null ? "client_secret_basic" :
                            row.getClientAuthenticationMethods());
            String grants =
                    row.getAuthorizationGrantTypes() == null ? "authorization_code" : row.getAuthorizationGrantTypes();
            if ("OIDC".equals(code)) {
                grants = Arrays.stream(grants.split(","))
                        .filter(value -> Set.of("authorization_code", "refresh_token").contains(value))
                        .collect(Collectors.joining(","));
            }
            settings.setAuthorizationGrantTypes(grants);
            String scopes = row.getScopes() == null ? "openid,profile,api.read" : row.getScopes();
            if (!"OIDC".equals(code)) {
                scopes = Arrays.stream(scopes.split(",")).filter(value -> !"openid".equals(value))
                        .collect(Collectors.joining(","));
            }
            if (Set.of(grants.split(",")).contains("client_credentials")) {
                scopes = Arrays.stream(scopes.split(",")).filter(value -> !"profile".equals(value))
                        .collect(Collectors.joining(","));
            }
            settings.setScopes(scopes);
            settings.setRedirectUris(
                    row.getRedirectUris() == null ? "https://client.example/callback" : row.getRedirectUris());
            settings.setRequireProofKey(true);
            settings.setRequireAuthorizationConsent(Boolean.TRUE.equals(row.isRequireAuthorizationConsent()));
            settings.setAllowedOrigins(row.getAllowedOrigins() == null ? Set.of() :
                    Set.copyOf(List.of(row.getAllowedOrigins().split(","))));
            settings.setTokenFormat(row.getTokenFormat() == null ? "reference" : row.getTokenFormat());
            settings.setReuseRefreshTokens(Boolean.TRUE.equals(row.isReuseRefreshTokens()));
            settings.setJwkSetUrl(row.getJwkSetUrl());
            settings.setSigningAlgorithm(row.getSigningAlgorithm());
            settings.setIdTokenSignatureAlgorithm("RS256");
            return new ApplicationProtocolConfiguration(code, 1, true, settings);
        }).toList());
        return row;
    }
}
