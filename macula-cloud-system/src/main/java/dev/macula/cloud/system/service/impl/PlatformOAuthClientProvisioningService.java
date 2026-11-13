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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import dev.macula.cloud.system.config.PlatformOAuthClientProperties;
import dev.macula.cloud.system.enums.IamWebLoginMode;
import dev.macula.cloud.system.mapper.SysIamApplicationMapper;
import dev.macula.cloud.system.pojo.entity.SysIamApplication;
import dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration;
import dev.macula.cloud.system.protocol.OAuth2ProtocolSettings;
import dev.macula.cloud.system.security.OAuth2ResourcePolicy;
import dev.macula.cloud.system.service.IamApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * 受信启动配置显式认领平台身份；保留标识及凭据，幂等重跑不改用户配置。
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class PlatformOAuthClientProvisioningService {
    private final SysIamApplicationMapper mapper;
    private final IamApplicationService applications;
    private final IamApplicationProtocolService protocols;
    private final IamApplicationWireAdapter wire;
    private final JsonMapper json = JsonMapper.builder().build();

    /** 固定Gateway/Admin锁序，所有改变仍经聚合事务保存与提交后缓存失效。 */
    @Transactional
    public void initialize(PlatformOAuthClientProperties properties) {
        if (!properties.isEnabled()) {
            return;
        }
        require(StringUtils.hasText(properties.getAdminClientId()) && StringUtils.hasText(properties.getGatewayClientId())
            && !properties.getAdminClientId().equals(properties.getGatewayClientId()), "请显式声明独立Admin/Gateway身份");
        var gateway = locked(properties.getGatewayClientId(), OAuth2ResourcePolicy.GATEWAY_MANAGED_KEY);
        var admin = locked(properties.getAdminClientId(), OAuth2ResourcePolicy.ADMIN_MANAGED_KEY);
        var adminView = applications.get(admin.getId());
        require("OIDC".equals(adminView.getProtocol()) && "SPA".equals(admin.getApplicationType())
            && IamWebLoginMode.resolve(admin.getApplicationType(), admin.getWebLoginMode()) == IamWebLoginMode.BROWSER
            && admin.getTenantId() != null && StringUtils.hasText(admin.getUserType()), "Admin必须先有单OIDC和明确目录");
        var settings = adminView.getSettings();
        require(settings != null && "none".equals(settings.getClientAuthenticationMethods())
            && Set.of("authorization_code").equals(OAuth2ProtocolConfiguration.csv(settings.getAuthorizationGrantTypes())), "Admin认证授权不符合声明");
        require(adminView.getExternalCodeTypes().isEmpty() && adminView.getExternalProviderBindings().isEmpty(), "Admin浏览器模式不能配置平台Code来源");
        Set<String> scopes = Set.of("openid", "profile", "system:access");
        Map<String, String> permissions = Map.of(OAuth2ResourcePolicy.PROFILE_SCOPE, OAuth2ResourcePolicy.PROFILE_RESOURCE,
            OAuth2ResourcePolicy.SYSTEM_SCOPE, OAuth2ResourcePolicy.SYSTEM_RESOURCE);
        boolean adminChanged = !OAuth2ResourcePolicy.ADMIN_MANAGED_KEY.equals(admin.getManagedKey())
            || !scopes.equals(OAuth2ProtocolConfiguration.csv(settings.getScopes()))
            || !permissions.equals(json.readValue(admin.getResourcePermissions() == null ? "{}" : admin.getResourcePermissions(), Map.class));
        if (adminChanged) {
            settings.setScopes(String.join(",", new TreeSet<>(scopes)));
            var form = wire.form(adminView);
            form.setSettings(settings);
            form.setProtocolConfigurations(List.of());
            applications.save(admin.getId(), form);
            var claim = new SysIamApplication();
            claim.setId(admin.getId());
            claim.setManagedKey(OAuth2ResourcePolicy.ADMIN_MANAGED_KEY);
            // 只更新认领字段，不能用旧主表实体回写刚保存的用户策略。
            mapper.update(null, new LambdaUpdateWrapper<SysIamApplication>()
                .eq(SysIamApplication::getId, admin.getId()).set(SysIamApplication::getManagedKey, claim.getManagedKey()));
        }
        var gatewayState = protocols.state(gateway.getId(), true);
        require(gatewayState.sources().isEmpty(), "Gateway不能隐式清除登录来源");
        var gatewayView = applications.get(gateway.getId());
        OAuth2ProtocolSettings authentication = gatewayView.getSettings();
        if (authentication == null && gatewayState.protocol() != null && gatewayState.protocol().getSchemaVersion() == 0) {
            authentication = new OAuth2ProtocolSettings();
            var legacy = json.readTree(gatewayState.protocol().getConfiguration());
            var methods = legacy.get("client_authentication_methods");
            authentication.setClientAuthenticationMethods(methods == null || methods.isNull() ? null : methods.asString());
        }
        require(gatewayView.isSecretConfigured() && authentication != null
            && !OAuth2ProtocolConfiguration.csv(authentication.getClientAuthenticationMethods()).isEmpty()
            && Set.of("client_secret_basic", "client_secret_post").containsAll(OAuth2ProtocolConfiguration.csv(authentication.getClientAuthenticationMethods())), "Gateway必须保留已有机密认证");
        boolean gatewayChanged = !OAuth2ResourcePolicy.GATEWAY_MANAGED_KEY.equals(gateway.getManagedKey())
            || !"RESOURCE_SERVER".equals(gateway.getPurpose());
        if (gatewayChanged) {
            var form = wire.form(gatewayView);
            authentication.setAuthorizationGrantTypes("");
            authentication.setScopes("");
            authentication.setRedirectUris("");
            authentication.setAllowedOrigins(Set.of());
            form.setSettings(authentication);
            form.setProtocol(null);
            form.setProtocolConfigurations(List.of());
            form.setEnabledProtocols(Set.of());
            form.setDefaultProtocol(null);
            form.setPurpose("RESOURCE_SERVER");
            form.setApplicationType("SERVICE");
            form.setWebLoginMode(null);
            form.setConfigurationStatus("ENABLED");
            form.setHostedLoginMethods(Set.of());
            form.setExternalCodeTypes(Set.of());
            gateway.setManagedKey(OAuth2ResourcePolicy.GATEWAY_MANAGED_KEY);
            gateway.setPurpose("RESOURCE_SERVER");
            gateway.setApplicationType("SERVICE");
            gateway.setResourcePermissions("{}");
            require(mapper.updateById(gateway) == 1, "Gateway认领发生并发变化");
            form.setConfigurationVersion(protocols.version(gateway, protocols.state(gateway.getId(), true)));
            applications.save(gateway.getId(), form);
        }
    }

    /** 只认领声明ID；唯一managedKey仍负责最终并发约束。 */
    private SysIamApplication locked(String code, String managedKey) {
        var app = mapper.selectOne(new LambdaQueryWrapper<SysIamApplication>().eq(SysIamApplication::getApplicationCode, code).last("FOR UPDATE"));
        require(app != null && (app.getManagedKey() == null || managedKey.equals(app.getManagedKey())), "声明的平台身份不存在或归属冲突");
        return app;
    }

    /** 受管配置错误只给有界诊断，不携带凭据或协议原文。 */
    private void require(boolean condition, String reason) {
        if (!condition) {
            throw new IllegalStateException(reason);
        }
    }
}
