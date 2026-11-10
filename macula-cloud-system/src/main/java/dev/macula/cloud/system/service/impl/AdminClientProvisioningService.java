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
package dev.macula.cloud.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.macula.cloud.system.config.AdminClientProvisioningProperties;
import dev.macula.cloud.system.form.OAuth2ClientForm;
import dev.macula.cloud.system.enums.OAuth2ClientApplicationType;
import dev.macula.cloud.system.enums.OAuth2ProtocolMode;
import dev.macula.cloud.system.enums.OAuth2ClientConfigurationStatus;
import dev.macula.cloud.system.mapper.SysOAuth2ClientConfigMapper;
import dev.macula.cloud.system.pojo.entity.SysOAuth2ClientConfig;
import dev.macula.cloud.system.service.OAuth2ClientConfigService;
import dev.macula.cloud.system.vo.OAuth2ClientVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 原子配置独立 Admin 与已知 Gateway 身份，复用现有完整校验和提交后缓存驱逐。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class AdminClientProvisioningService {
    private static final String SERVICE = OAuth2ClientApplicationType.SERVICE.name();
    private static final String SPA = OAuth2ClientApplicationType.SPA.name();
    private static final String ENABLED = OAuth2ClientConfigurationStatus.ENABLED.value();
    private static final String OIDC = OAuth2ProtocolMode.OIDC.value();
    private static final String OAUTH = OAuth2ProtocolMode.OAUTH2_1.value();
    private static final Set<String> SECRET_METHODS = Set.of("client_secret_basic", "client_secret_post");
    private static final Set<String> UNMANAGED_PROPERTIES = Set.of("class", "clientSecret", "protocolMode",
        "applicationTypePresent", "miniappPlatformPresent", "hostedLoginMethodsPresent", "configurationVersion",
        "enabledProtocols", "clientAuthenticationMethods", "authorizationGrantTypes", "scopes", "redirectUris",
        "allowedOrigins", "requireProofKey", "requireAuthorizationConsent", "jwkSetUrl", "clientAuthenticationSigningAlgorithm",
        "authorizationCodeTimeToLive", "accessTokenTimeToLive", "refreshTokenTimeToLive", "tokenFormat",
        "reuseRefreshTokens", "idTokenSignatureAlgorithm");
    private final SysOAuth2ClientConfigMapper mapper;
    private final OAuth2ClientConfigService clients;

    /**
     * 锁定这两个身份并在同一事务内提交。Admin 已存在时只允许完全一致的幂等重跑。
     *
     * @param properties 已明确核实的公开参数
     */
    @Transactional
    public void provision(AdminClientProvisioningProperties properties) {
        validateProperties(properties);
        // 固定顺序锁定 Gateway，再检查 Admin 唯一键；现有保存方法继续执行完整快照 CAS。
        SysOAuth2ClientConfig gateway = findLocked(properties.getGatewayClientId());
        if (gateway == null || !StringUtils.hasText(gateway.getClientSecret())
            || !Objects.equals(gateway.getTenantId(), properties.getTenantId())
            || !Objects.equals(gateway.getUserType(), properties.getUserType())
            || gateway.getApplicationType() != null && !SERVICE.equals(gateway.getApplicationType())) {
            throw new IllegalStateException("Gateway identity does not match the declared scope");
        }
        OAuth2ClientVO gatewayView = clients.get(gateway.getId());
        if (gatewayView == null || gatewayView.getProtocolConfigurations() == null) {
            throw new IllegalStateException("Gateway protocol configuration is required");
        }
        var gatewayProfile = gatewayView.getProtocolConfigurations().stream()
            .filter(value -> OAUTH.equals(value.protocol()) && value.enabled()).findFirst()
            .orElseThrow(() -> new IllegalStateException("Gateway OAuth configuration is required"));
        Set<String> methods = csv(dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration
            .settings(gatewayProfile).getClientAuthenticationMethods());
        if (methods.isEmpty() || !SECRET_METHODS.containsAll(methods)) {
            throw new IllegalStateException("Gateway must retain a secret authentication method");
        }
        SysOAuth2ClientConfig admin = findLocked(properties.getAdminClientId());
        OAuth2ClientForm adminForm = common(properties, properties.getAdminClientId(), "macula-cloud-admin", SPA, OIDC);
        adminForm.setClientAuthenticationMethods("none");
        adminForm.setAuthorizationGrantTypes("authorization_code");
        adminForm.setScopes("openid,profile");
        adminForm.setRedirectUris(properties.getAdminOrigin() + "/auth/callback");
        adminForm.setAllowedOrigins(Set.of(properties.getAdminOrigin()));
        adminForm.setRequireProofKey(true);
        configureProtocol(adminForm);
        if (admin != null && (!matches(clients.get(admin.getId()), adminForm)
            || StringUtils.hasText(admin.getClientSecret()))) {
            throw new IllegalStateException("Admin identity already exists with different configuration");
        }
        OAuth2ClientForm gatewayForm = common(properties, gateway.getClientId(), gateway.getClientName(), SERVICE, OAUTH);
        gatewayForm.setClientAuthenticationMethods(String.join(",", new TreeSet<>(methods)));
        gatewayForm.setAuthorizationGrantTypes("client_credentials");
        gatewayForm.setHostedLoginMethods(Set.of());
        configureProtocol(gatewayForm);
        gatewayForm.setConfigurationVersion(gatewayView.getConfigurationVersion());
        if (!matches(gatewayView, gatewayForm)) {
            clients.save(gateway.getId(), gatewayForm);
        }
        if (admin == null) {
            clients.save(null, adminForm);
        }
    }

    /** 只接受独立 UUID 与明确 origin；身份范围不能由请求或主机头推导。 */
    private void validateProperties(AdminClientProvisioningProperties properties) {
        try {
            UUID.fromString(properties.getAdminClientId());
            UUID.fromString(properties.getGatewayClientId());
            URI origin = URI.create(properties.getAdminOrigin());
            boolean loopback = Set.of("localhost", "127.0.0.1", "[::1]").contains(origin.getHost());
            if (properties.getAdminClientId().equals(properties.getGatewayClientId())
                || properties.getTenantId() == null || !StringUtils.hasText(properties.getUserType())
                || origin.getHost() == null || origin.getRawUserInfo() != null || origin.getQuery() != null
                || origin.getFragment() != null || !origin.getPath().isEmpty()
                || !("https".equals(origin.getScheme()) || "http".equals(origin.getScheme()) && loopback)) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Explicit independent client identities and trusted origin are required");
        }
    }

    /** 行锁不替代唯一键和既有保存服务的并发校验。 */
    private SysOAuth2ClientConfig findLocked(String clientId) {
        return mapper.selectOne(new LambdaQueryWrapper<SysOAuth2ClientConfig>()
            .eq(SysOAuth2ClientConfig::getClientId, clientId).last("FOR UPDATE"));
    }

    /** 受管应用的固定配置，无默认租户、用户目录或密钥。 */
    private OAuth2ClientForm common(AdminClientProvisioningProperties properties, String id, String name,
        String type, String protocol) {
        OAuth2ClientForm form = new OAuth2ClientForm();
        form.setPurpose("APPLICATION");
        form.setClientId(id);
        form.setClientName(name);
        form.setApplicationType(type);
        form.setMiniappPlatform(null);
        form.setEnabledProtocols(Set.of(protocol));
        form.setDefaultProtocol(protocol);
        form.setConfigurationStatus(ENABLED);
        form.setTenantId(properties.getTenantId());
        form.setUserType(properties.getUserType());
        form.setHostedLoginMethods(null);
        form.setScopes("");
        form.setRedirectUris("");
        form.setRequireProofKey(false);
        form.setRequireAuthorizationConsent(false);
        form.setAuthorizationCodeTimeToLive(300L);
        form.setAccessTokenTimeToLive(3600L);
        form.setTokenFormat("reference");
        form.setReuseRefreshTokens(false);
        form.setRefreshTokenTimeToLive(3600L);
        form.setIdTokenSignatureAlgorithm("RS256");
        return form;
    }

    /** 初始化工具也必须写入类型化子配置，不能依赖旧共享列回退。 */
    private void configureProtocol(OAuth2ClientForm form) {
        var settings = new dev.macula.cloud.system.protocol.OAuth2ProtocolSettings();
        org.springframework.beans.BeanUtils.copyProperties(form, settings);
        form.setProtocol(form.getDefaultProtocol());
        form.setSettings(settings);
        form.setProtocolConfigurations(java.util.List.of(new dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration(
            form.getDefaultProtocol(), dev.macula.cloud.system.protocol.ApplicationProtocolRegistry.SCHEMA_VERSION, true, settings)));
    }

    /** 比较所有受管字段，CSV 忽略顺序；既有 Admin 漂移不自动覆盖。 */
    private boolean matches(OAuth2ClientVO actual, OAuth2ClientForm expected) {
        BeanWrapper target = new BeanWrapperImpl(actual);
        BeanWrapper source = new BeanWrapperImpl(expected);
        for (var property : source.getPropertyDescriptors()) {
            String name = property.getName();
            if (UNMANAGED_PROPERTIES.contains(name) || !target.isReadableProperty(name)) {
                continue;
            }
            Object left = target.getPropertyValue(name);
            Object right = source.getPropertyValue(name);
            if (Set.of("scopes", "clientAuthenticationMethods", "authorizationGrantTypes", "redirectUris").contains(name)) {
                if (!csv((String) left).equals(csv((String) right))) {
                    return false;
                }
            } else if (!Objects.equals(left, right)) {
                return false;
            }
        }
        return true;
    }

    /** 协议 CSV 仅用于固定值集合比较，不将未知值隐式转换为枚举。 */
    private Set<String> csv(String value) {
        return value == null || value.isBlank() ? Set.of() : Arrays.stream(value.split(","))
            .map(String::trim).collect(Collectors.toSet());
    }
}
