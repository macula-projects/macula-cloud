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
import dev.macula.cloud.system.config.PlatformOAuthClientProperties;
import dev.macula.cloud.system.mapper.SysOAuth2ClientConfigMapper;
import dev.macula.cloud.system.pojo.entity.SysOAuth2ClientConfig;
import dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration;
import dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration;
import dev.macula.cloud.system.security.OAuth2ResourcePolicy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * 根据受信任配置认领已有平台身份，保留ID/Secret且不为其他应用补授权限。
 * @author Rain
 * @since 6.1.0
 */
@Service
public class PlatformOAuthClientProvisioningService {
    private final SysOAuth2ClientConfigMapper clients;
    private final ApplicationProtocolConfigurationService protocols;
    private final org.springframework.cache.CacheManager cacheManager;
    private final dev.macula.cloud.system.mapper.SysOAuth2ClientExternalProviderMapper externalProviders;
    private final dev.macula.cloud.system.service.OAuth2ClientConfigurationCatalogService catalog;
    private final JsonMapper json = JsonMapper.builder().build();

    /** 平台初始化与普通配置写入共享行锁和协议存储。 */
    public PlatformOAuthClientProvisioningService(SysOAuth2ClientConfigMapper clients, ApplicationProtocolConfigurationService protocols, org.springframework.cache.CacheManager cacheManager,
        dev.macula.cloud.system.mapper.SysOAuth2ClientExternalProviderMapper externalProviders,
        dev.macula.cloud.system.service.OAuth2ClientConfigurationCatalogService catalog) {
        this.clients = clients;
        this.protocols = protocols;
        this.cacheManager = cacheManager;
        this.externalProviders = externalProviders;
        this.catalog = catalog;
    }

    /** 完整验证两个显式身份后原子更新；失败不猜测、不新建身份或密钥。 */
    @Transactional
    public void initialize(PlatformOAuthClientProperties properties) {
        if (!properties.isEnabled()) { return; }
        if (!StringUtils.hasText(properties.getAdminClientId()) || !StringUtils.hasText(properties.getGatewayClientId())
            || properties.getAdminClientId().equals(properties.getGatewayClientId())) {
            throw new IllegalStateException("请显式声明独立的Admin与Gateway客户端身份");
        }
        var gateway = locked(properties.getGatewayClientId(), OAuth2ResourcePolicy.GATEWAY_MANAGED_KEY);
        var admin = locked(properties.getAdminClientId(), OAuth2ResourcePolicy.ADMIN_MANAGED_KEY);
        var configurations = protocols.list(admin.getId());
        if (configurations.size() != 1 || !"OIDC".equals(configurations.get(0).protocol())
            || !"SPA".equals(admin.getApplicationType()) || admin.getTenantId() == null || !StringUtils.hasText(admin.getUserType())) {
            throw new IllegalStateException("Admin必须先有明确的单OIDC配置和用户目录");
        }
        var settings = OAuth2ProtocolConfiguration.settings(configurations.get(0));
        if (!"none".equals(settings.getClientAuthenticationMethods())
            || !OAuth2ProtocolConfiguration.csv(settings.getAuthorizationGrantTypes()).contains("authorization_code")
            || !Set.of("authorization_code", dev.macula.cloud.system.enums.ExternalCodeType.GRANT_TYPE)
                .containsAll(OAuth2ProtocolConfiguration.csv(settings.getAuthorizationGrantTypes()))) {
            throw new IllegalStateException("Admin认证或授权配置不符合声明");
        }
        // 管理端保留标准授权码入口，但显式SDK许可不能被初始化过程清除。
        OAuth2ProtocolConfiguration.validateRuntime(configurations.get(0), "SPA");
        if (OAuth2ProtocolConfiguration.csv(settings.getAuthorizationGrantTypes()).contains(
            dev.macula.cloud.system.enums.ExternalCodeType.GRANT_TYPE)) {
            validateSdkSources(admin);
        }
        String permissions = json.writeValueAsString(Map.of(OAuth2ResourcePolicy.PROFILE_SCOPE, OAuth2ResourcePolicy.PROFILE_RESOURCE,
            OAuth2ResourcePolicy.SYSTEM_SCOPE, OAuth2ResourcePolicy.SYSTEM_RESOURCE));
        Set<String> currentScopes = OAuth2ProtocolConfiguration.csv(settings.getScopes());
        Set<String> requiredScopes = Set.of("openid", "profile", "system:access");
        boolean adminChanged = !OAuth2ResourcePolicy.ADMIN_MANAGED_KEY.equals(admin.getManagedKey())
            || !currentScopes.equals(requiredScopes) || !json.readValue(permissions, Map.class).equals(
                json.readValue(admin.getResourcePermissions() == null ? "{}" : admin.getResourcePermissions(), Map.class));
        if (adminChanged) {
            settings.setScopes(String.join(",", new TreeSet<>(requiredScopes)));
            OAuth2ProtocolConfiguration.validateRuntime(new ApplicationProtocolConfiguration("OIDC", 1, true, settings), "SPA");
            protocols.replace(admin.getId(), List.of(new ApplicationProtocolConfiguration("OIDC", 1, true, settings)));
            admin.setManagedKey(OAuth2ResourcePolicy.ADMIN_MANAGED_KEY);
            admin.setPurpose("APPLICATION");
            admin.setConfigurationStatus("ENABLED");
            admin.setResourcePermissions(permissions);
            admin.setDefaultProtocol("OIDC");
            admin.setAuthorizationVersion(next(admin));
            if (clients.updateById(admin) != 1) { throw new IllegalStateException("Admin身份初始化发生并发变化"); }
        }
        String methods = gateway.getClientAuthenticationMethods();
        var gatewayProfiles = protocols.list(gateway.getId());
        if (!gatewayProfiles.isEmpty()) {
            if (gatewayProfiles.size() != 1) { throw new IllegalStateException("Gateway旧协议存在歧义"); }
            methods = OAuth2ProtocolConfiguration.settings(gatewayProfiles.get(0)).getClientAuthenticationMethods();
        }
        if (!StringUtils.hasText(gateway.getClientSecret()) || OAuth2ProtocolConfiguration.csv(methods).isEmpty()
            || !Set.of("client_secret_basic", "client_secret_post").containsAll(OAuth2ProtocolConfiguration.csv(methods))) {
            throw new IllegalStateException("Gateway必须保留已有机密认证身份");
        }
        boolean gatewayChanged = !OAuth2ResourcePolicy.GATEWAY_MANAGED_KEY.equals(gateway.getManagedKey()) || !gatewayProfiles.isEmpty();
        if (gatewayChanged) {
            gateway.setManagedKey(OAuth2ResourcePolicy.GATEWAY_MANAGED_KEY);
            gateway.setPurpose("RESOURCE_SERVER");
            gateway.setConfigurationStatus("ENABLED");
            gateway.setClientAuthenticationMethods(methods);
            gateway.setAuthorizationGrantTypes("");
            gateway.setScopes("");
            gateway.setRedirectUris("");
            gateway.setTenantId(null);
            gateway.setUserType(null);
            gateway.setResourcePermissions("{}");

            gateway.setAuthorizationVersion(next(gateway));
            protocols.replace(gateway.getId(), List.of());
            if (clients.updateById(gateway) != 1) { throw new IllegalStateException("Gateway身份初始化发生并发变化"); }
        }
        if (adminChanged || gatewayChanged) { evictAfterCommit(); }
    }

    /** 显式SDK许可必须仍绑定可用的具体来源，启动认领不能重新授予或猜测来源。 */
    private void validateSdkSources(SysOAuth2ClientConfig admin) {
        Set<String> expected = OAuth2ProtocolConfiguration.csv(admin.getExternalCodeTypes());
        var mappings = externalProviders.selectList(new LambdaQueryWrapper<dev.macula.cloud.system.pojo.entity.SysOAuth2ClientExternalProvider>()
            .eq(dev.macula.cloud.system.pojo.entity.SysOAuth2ClientExternalProvider::getClientId, admin.getClientId()));
        if (expected.isEmpty() || mappings.size() != expected.size()
            || !mappings.stream().map(dev.macula.cloud.system.pojo.entity.SysOAuth2ClientExternalProvider::getCodeType)
                .collect(java.util.stream.Collectors.toSet()).equals(expected)) {
            throw new IllegalStateException("Admin第三方Code登录缺少明确来源许可");
        }
        var sources = catalog.catalog().sources();
        for (var mapping : mappings) {
            if (sources.stream().noneMatch(source -> source.available()
                && java.util.Objects.equals(source.id(), mapping.getIdentityProviderId())
                && source.capabilities().stream().anyMatch(capability -> mapping.getCodeType().equals(capability.codeType())))) {
                throw new IllegalStateException("Admin第三方Code登录关联来源不可用");
            }
        }
    }

    /** 初始化使用与普通保存相同的提交后缓存失效边界。 */
    private void evictAfterCommit() {
        var clientCache = cacheManager.getCache(dev.macula.boot.constants.CacheConstants.OAUTH2_CLIENT_CACHE_KEY);
        var originCache = cacheManager.getCache(dev.macula.cloud.system.security.OAuth2ClientCacheNames.TOKEN_ORIGIN_INDEX);
        if (clientCache == null || originCache == null) { throw new IllegalStateException("平台身份缓存不可用"); }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                /** 只在两个身份均已提交后清理，回滚不能发布新身份。 */
                @Override
                public void afterCommit() {
                    clientCache.invalidate();
                    originCache.invalidate();
                }
            });
    }

    /** managedKey不能被指向其他受管身份；唯一键仍是最终并发约束。 */
    private SysOAuth2ClientConfig locked(String clientId, String managedKey) {
        var client = clients.selectOne(new LambdaQueryWrapper<SysOAuth2ClientConfig>()
            .eq(SysOAuth2ClientConfig::getClientId, clientId).last("FOR UPDATE"));
        if (client == null || client.getManagedKey() != null && !managedKey.equals(client.getManagedKey())) {
            throw new IllegalStateException("声明的平台身份不存在或归属冲突");
        }
        return client;
    }

    /** 初始化改变授权时旧会话必须重新认证。 */
    private long next(SysOAuth2ClientConfig client) {
        return client.getAuthorizationVersion() == null ? 1L : client.getAuthorizationVersion() + 1;
    }
}
