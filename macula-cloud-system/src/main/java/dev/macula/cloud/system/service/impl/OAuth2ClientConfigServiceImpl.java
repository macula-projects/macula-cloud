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
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.macula.boot.constants.CacheConstants;
import dev.macula.cloud.system.form.OAuth2ClientForm;
import dev.macula.cloud.system.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.system.mapper.SysOAuth2ClientConfigMapper;
import dev.macula.cloud.system.mapper.SysOAuth2ClientExternalProviderMapper;
import dev.macula.cloud.system.mapper.SysTenantInfoMapper;
import dev.macula.cloud.system.pojo.entity.SysIdentityProvider;
import dev.macula.cloud.system.pojo.entity.SysOAuth2ClientConfig;
import dev.macula.cloud.system.pojo.entity.SysOAuth2ClientExternalProvider;
import dev.macula.cloud.system.service.OAuth2ClientConfigService;
import dev.macula.cloud.system.security.OAuth2LoginGrantTypes;
import dev.macula.cloud.system.security.OAuth2ClientCacheNames;
import dev.macula.cloud.system.enums.BrowserLoginProviderType;
import dev.macula.cloud.system.enums.ExternalCodeType;
import dev.macula.cloud.system.enums.IdentitySourceType;
import dev.macula.cloud.system.enums.IdentityLoginCapability;
import dev.macula.cloud.system.enums.OAuth2ProtocolMode;
import dev.macula.cloud.system.enums.OAuth2ClientConfigurationStatus;
import dev.macula.cloud.system.service.OAuth2ClientConfigurationCatalogService;
import dev.macula.cloud.system.enums.OAuth2ClientApplicationType;
import dev.macula.cloud.system.enums.OAuth2MiniappPlatform;
import dev.macula.cloud.system.enums.OAuth2HostedLoginMethod;
import dev.macula.cloud.system.vo.OAuth2ClientVO;
import dev.macula.cloud.system.vo.OAuth2ExternalCodeCatalogVO;
import lombok.RequiredArgsConstructor;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Date;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 管理与 IAM 共表的 OAuth 客户端，并在事务提交后清理共享缓存。
 *
 * @author Rain
 * @since 2026-10-13
 */
@Service
@RequiredArgsConstructor
public class OAuth2ClientConfigServiceImpl implements OAuth2ClientConfigService {
    private static final String AUTHORIZATION_CODE_GRANT = "authorization_code";
    private static final String CLIENT_CREDENTIALS_GRANT = "client_credentials";
    private static final String SMS_GRANT = "sms";
    private static final Set<String> AUTH = Set.of("none", "client_secret_basic", "client_secret_post",
        "private_key_jwt");
    private static final Set<String> GRANTS = Set.of(AUTHORIZATION_CODE_GRANT, "refresh_token",
        CLIENT_CREDENTIALS_GRANT, SMS_GRANT, OAuth2LoginGrantTypes.EMAIL, ExternalCodeType.GRANT_TYPE);
    private static final Set<String> USERS = Set.of("member", "employee", "odc", "supplier");
    private static final String MOBILE_APP_ID = "mobileAppId";
    private static final String MOBILE_APP_SECRET = "mobileAppSecret";
    private static final String MINI_APP_ID = "miniAppId";
    private static final String MINI_APP_SECRET = "miniAppSecret";
    private static final int ALLOWED_ORIGINS_MAX_LENGTH = 2048;
    private final JsonMapper json = JsonMapper.builder().build();
    private final SysOAuth2ClientConfigMapper mapper;
    private final SysOAuth2ClientExternalProviderMapper externalProviderMapper;
    private final SysIdentityProviderMapper identityProviderMapper;
    private final SysTenantInfoMapper tenantMapper;
    private final PasswordEncoder passwordEncoder;
    private final CacheManager cacheManager;
    private final OAuth2ClientConfigurationCatalogService configurationCatalog;
    private final ApplicationProtocolConfigurationService protocols;

    /** 列表每条配置及其版本必须来自同一数据库快照。 */
    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public IPage<OAuth2ClientVO> page(int pageNum, int pageSize, String keyword) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            bad("分页参数无效");
        }
        return mapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<SysOAuth2ClientConfig>()
            .and(StringUtils.hasText(keyword), q -> q.like(SysOAuth2ClientConfig::getClientId, keyword)
                .or().like(SysOAuth2ClientConfig::getClientName, keyword))
            .orderByDesc(SysOAuth2ClientConfig::getId)).convert(this::toVO);
    }

    @Override
    public OAuth2ExternalCodeCatalogVO externalCodeCatalog() {
        List<OAuth2ExternalCodeCatalogVO.CodeType> codeTypes = Arrays.stream(ExternalCodeType.values())
            .map(type -> new OAuth2ExternalCodeCatalogVO.CodeType(type.name(), type.providerType(),
                type.scene().name(), type.resultType().name(), type.capability().name()))
            .toList();
        List<OAuth2ExternalCodeCatalogVO.ProviderRegistration> registrations = identityProviderMapper.selectList(
            new LambdaQueryWrapper<SysIdentityProvider>().eq(SysIdentityProvider::getEnabled, true)
                .orderByAsc(SysIdentityProvider::getProviderType).orderByAsc(SysIdentityProvider::getName))
            .stream().map(provider -> new OAuth2ExternalCodeCatalogVO.ProviderRegistration(provider.getId(),
                provider.getName(), provider.getProviderType(), providerCapabilities(provider))).toList();
        return new OAuth2ExternalCodeCatalogVO(codeTypes, registrations);
    }

    private Set<String> providerCapabilities(SysIdentityProvider provider) {
        Set<String> capabilities = new TreeSet<>();
        capabilities.add(ExternalCodeType.Capability.DEFAULT.name());
        Map<String, String> config = jsonObject(provider.getConfigJson());
        Map<String, String> secrets = jsonObject(provider.getSecretConfigJson());
        if (StringUtils.hasText(config.get(MOBILE_APP_ID)) && StringUtils.hasText(secrets.get(MOBILE_APP_SECRET))) {
            capabilities.add(ExternalCodeType.Capability.WECHAT_MOBILE_APP.name());
        }
        if (StringUtils.hasText(config.get(MINI_APP_ID)) && StringUtils.hasText(secrets.get(MINI_APP_SECRET))) {
            capabilities.add(ExternalCodeType.Capability.WECHAT_MINIAPP.name());
        }
        return Collections.unmodifiableSet(capabilities);
    }

    /** 返回内容与配置版本共享快照，避免旧内容带上新版本后覆盖并发更新。 */
    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public OAuth2ClientVO get(Long id) {
        return toVO(require(id));
    }

    /** 只存基础信息，尚未配置的租户/目录为空，所有认证入口必须拒绝草稿。 */
    @Override
    @Transactional
    public OAuth2ClientVO createDraft(dev.macula.cloud.system.form.OAuth2ClientDraftForm form) {
        OAuth2ClientApplicationType type;
        try { type = OAuth2ClientApplicationType.valueOf(form.getApplicationType()); }
        catch (IllegalArgumentException ex) { bad("应用类型无效");
            return null; }
        if (type == OAuth2ClientApplicationType.MINIAPP) {
            if (!Set.of("WECHAT", "WECOM").contains(form.getMiniappPlatform() == null ? "" : form.getMiniappPlatform())) { bad("请选择小程序平台"); }
        } else if (StringUtils.hasText(form.getMiniappPlatform())) { bad("非小程序不能配置小程序平台"); }
        SysOAuth2ClientConfig entity = new SysOAuth2ClientConfig();
        entity.setClientId(java.util.UUID.randomUUID().toString());
        entity.setClientIdIssuedAt(Instant.now());
        entity.setClientName(form.getClientName().trim());
        entity.setPurpose(form.getPurpose() == null ? "APPLICATION" : form.getPurpose());
        if (!Set.of("APPLICATION", "RESOURCE_SERVER").contains(entity.getPurpose())) { bad("Client用途无效"); }
        entity.setAuthorizationVersion(1L);
        entity.setResourcePermissions("{}");

        entity.setApplicationType(type.name());
        entity.setMiniappPlatform(form.getMiniappPlatform());
        entity.setConfigurationStatus(OAuth2ClientConfigurationStatus.DRAFT.value());
        entity.setClientAuthenticationMethods("");
        entity.setAuthorizationGrantTypes("");
        entity.setScopes("");
        entity.setRedirectUris("");
        entity.setBrowserProviderBindings("{}");
        mapper.insert(entity);
        if (!"RESOURCE_SERVER".equals(entity.getPurpose()) && form.getProtocol() != null) {
            if (!dev.macula.cloud.system.protocol.ApplicationProtocolRegistry.PROTOCOLS.contains(form.getProtocol())) { bad("协议无效"); }
            protocols.replace(entity.getId(), List.of(new dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration(
                form.getProtocol(), 1, false, new dev.macula.cloud.system.protocol.OAuth2ProtocolSettings())));
        }
        evictAfterCommit();
        return toVO(entity);
    }

    /** 草稿允许缺项，但已提供的值也必须符合目录及身份边界，不能隐藏无效配置。 */
    private boolean validateConfiguration(OAuth2ClientForm form, SysOAuth2ClientConfig existing) {
        boolean enabled;
        try {
            enabled = OAuth2ClientConfigurationStatus.fromValue(form.getConfigurationStatus())
                == OAuth2ClientConfigurationStatus.ENABLED;
            if (!StringUtils.hasText(form.getApplicationType())) { bad("请选择应用类型"); }
            OAuth2ClientApplicationType.valueOf(form.getApplicationType());
            if (form.getEnabledProtocols() == null || form.getEnabledProtocols().isEmpty()) { bad("至少启用一个协议"); }
            form.getEnabledProtocols().forEach(OAuth2ProtocolMode::fromValue);
        } catch (IllegalArgumentException ex) { bad("应用状态、类型或协议无效");
            return false; }
        if (!form.getEnabledProtocols().contains(form.getDefaultProtocol())) { bad("默认协议必须已启用"); }
        if (Set.of("SERVICE").contains(form.getApplicationType()) && form.getEnabledProtocols().contains(OAuth2ProtocolMode.OIDC.value())) {
            bad("此应用类型不支持OIDC用户登录");
        }
        if (!StringUtils.hasText(form.getClientName())) { bad("应用名称不能为空"); }
        if (!conversion(existing, form) && existing.getApplicationType() != null && (!existing.getApplicationType().equals(form.getApplicationType())
            || !java.util.Objects.equals(existing.getMiniappPlatform(), form.getMiniappPlatform()))) { bad("应用类型和小程序平台不可修改"); }
        if (form.getTenantId() != null && tenantMapper.selectById(form.getTenantId()) == null) { bad("租户不存在"); }
        if (StringUtils.hasText(form.getUserType()) && !USERS.contains(form.getUserType())) { bad("用户类型无效"); }
        if (enabled && (form.getTenantId() == null || !"SERVICE".equals(form.getApplicationType()) && !USERS.contains(form.getUserType() == null ? "" : form.getUserType()))) { bad("启用前请选择认证租户和用户类型"); }
        if (StringUtils.hasText(form.getClientAuthenticationMethods()) && hasInvalidCsvStructure(form.getClientAuthenticationMethods())
            || StringUtils.hasText(form.getAuthorizationGrantTypes()) && hasInvalidCsvStructure(form.getAuthorizationGrantTypes())) { bad("认证或授权字段结构无效"); }
        if (csv(form.getClientAuthenticationMethods()).contains("none") && csv(form.getClientAuthenticationMethods()).size() != 1) { bad("none不能与其他认证组合"); }
        Set<String> grants = csv(form.getAuthorizationGrantTypes());
        if (!GRANTS.containsAll(grants) || !AUTH.containsAll(csv(form.getClientAuthenticationMethods()))) { bad("认证或授权方式无效"); }
        boolean oauthOnlyGrant = grants.stream().anyMatch(grant -> Set.of("client_credentials", "sms", "email").contains(grant));
        if (!form.getEnabledProtocols().contains(OAuth2ProtocolMode.OAUTH2_1.value()) && oauthOnlyGrant) { bad("直接Token授权及SDK能力需要启用OAuth2.1"); }
        Set<String> allowed = form.getEnabledProtocols().stream().flatMap(protocol -> configurationCatalog
            .allowedScopes(protocol, form.getApplicationType(), grants).stream()).collect(Collectors.toSet());
        if (!allowed.containsAll(csv(form.getScopes()))) { bad("Scope不在已启用协议的目录中"); }
        validateOptionalParameters(form);
        if (enabled && (form.getRequireProofKey() == null || form.getRequireAuthorizationConsent() == null || form.getReuseRefreshTokens() == null)) { bad("启用前请明确认证与令牌策略"); }
        if (form.getApplicationType().equals("MINIAPP") && !Set.of("WECHAT", "WECOM").contains(form.getMiniappPlatform() == null ? "" : form.getMiniappPlatform())) { bad("小程序平台无效"); }
        if (!form.getApplicationType().equals("MINIAPP") && StringUtils.hasText(form.getMiniappPlatform())) { bad("非小程序不能指定平台"); }
        normalizeOrigins(form.getAllowedOrigins());
        validateChannelCompatibility(OAuth2ClientApplicationType.valueOf(form.getApplicationType()), form,
            externalCodeTypes(form.getExternalCodeTypes()));
        validateExternalProviderBindings(externalCodeTypes(form.getExternalCodeTypes()), nullSafeMap(form.getExternalProviderBindings()));
        validateBrowserCapabilities(form);
        return enabled;
    }

    /**
     * 校验应用类型及认证边界后保存 Client，并在提交后清除缓存。
     *
     * @param id 现有记录主键，新建时为空
     * @param form Client 配置
     * @return 不含密钥的展示结果
     */
    @Override
    @Transactional
    public OAuth2ClientVO save(Long id, OAuth2ClientForm form) {
        SysOAuth2ClientConfig entity = new SysOAuth2ClientConfig();
        SysOAuth2ClientConfig original = new SysOAuth2ClientConfig();
        BeanUtils.copyProperties(entity, original);
        if (id != null) {
            // 应用行是协议/凭据更新的共同锁，不允许不同Tab绕过并发快照。
            entity = mapper.selectOne(new LambdaQueryWrapper<SysOAuth2ClientConfig>()
                .eq(SysOAuth2ClientConfig::getId, id).last("FOR UPDATE"));
            if (entity == null) { bad("应用不存在"); }
            protocols.checkVersion(entity, form.getConfigurationVersion());
            BeanUtils.copyProperties(entity, original);
            if (!entity.getClientId().equals(form.getClientId())) { bad("clientId创建后不可修改"); }
        }
        boolean converting = conversion(entity, form);
        prepareBrowserCapabilities(form, entity);
        String purpose = form.getPurpose() == null ? "APPLICATION" : form.getPurpose();
        if (!Set.of("APPLICATION", "RESOURCE_SERVER").contains(purpose)
            || original.getPurpose() != null && !original.getPurpose().equals(purpose)) { bad("Client用途创建后不可修改"); }
        if (StringUtils.hasText(original.getManagedKey()) && !"ENABLED".equals(form.getConfigurationStatus())) { bad("平台内置身份不可停用"); }
        if ("RESOURCE_SERVER".equals(purpose)) { return saveResourceServer(id, form, entity); }
        if (dev.macula.cloud.system.security.OAuth2ResourcePolicy.ADMIN_MANAGED_KEY.equals(original.getManagedKey())
            && !"OIDC".equals(form.getProtocol())) { bad("平台Admin必须保持OIDC"); }
        if (form.getProtocol() != null) {
            if (form.getSettings() == null) { bad("协议参数不能为空"); }
            form.setProtocolConfigurations(List.of(new dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration(
                form.getProtocol(), 1, "ENABLED".equals(form.getConfigurationStatus()), form.getSettings())));
            form.setDefaultProtocol("ENABLED".equals(form.getConfigurationStatus()) ? form.getProtocol() : null);
        }
        java.util.List<dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration> configurations = form.getProtocolConfigurations();
        try {
            OAuth2ClientApplicationType.valueOf(form.getApplicationType());
            protocols.validate(configurations, form.getApplicationType(), "ENABLED".equals(form.getConfigurationStatus()));
        } catch (IllegalArgumentException | NullPointerException ex) { bad("协议配置或应用类型无效"); }
        Set<String> enabledProtocols = configurations.stream().filter(dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration::enabled)
            .map(dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration::protocol).collect(Collectors.toSet());
        boolean enabled = OAuth2ClientConfigurationStatus.ENABLED.value().equals(form.getConfigurationStatus());
        if (!Set.of("DRAFT", "ENABLED").contains(form.getConfigurationStatus())
            || enabled && (enabledProtocols.isEmpty() || form.getTenantId() == null || !"SERVICE".equals(form.getApplicationType()) && !StringUtils.hasText(form.getUserType()))
            || !enabledProtocols.isEmpty() && !enabledProtocols.contains(form.getDefaultProtocol())
            || enabledProtocols.isEmpty() && form.getDefaultProtocol() != null) { bad("启用状态或默认协议无效"); }
        if (existingApplicationTypeChanged(original, form)) { bad("应用类型和小程序平台不可修改"); }
        // 即使未启用任何协议，应用身份和来源关联也必须接受同样的目录校验。
        if (!StringUtils.hasText(form.getClientName()) || !StringUtils.hasText(form.getClientId())) { bad("应用名称和Client ID不能为空"); }
        if (form.getTenantId() != null && tenantMapper.selectById(form.getTenantId()) == null) { bad("租户不存在"); }
        if (StringUtils.hasText(form.getUserType()) && !USERS.contains(form.getUserType())) { bad("用户类型无效"); }
        if (form.getHostedLoginMethods() != null && form.getHostedLoginMethods().stream().anyMatch(value ->
            Arrays.stream(OAuth2HostedLoginMethod.values()).noneMatch(method -> method.name().equals(value)))) { bad("托管登录方式无效"); }
        if ("MINIAPP".equals(form.getApplicationType()) != StringUtils.hasText(form.getMiniappPlatform())
            || "MINIAPP".equals(form.getApplicationType()) && !Set.of("WECHAT", "WECOM").contains(form.getMiniappPlatform())) { bad("小程序平台无效"); }
        validateChannelCompatibility(OAuth2ClientApplicationType.valueOf(form.getApplicationType()), form,
            externalCodeTypes(form.getExternalCodeTypes()));
        validateExternalProviderBindings(externalCodeTypes(form.getExternalCodeTypes()), nullSafeMap(form.getExternalProviderBindings()));
        validateBrowserCapabilities(form);
        if (enabled) {
            boolean browserCapable = configurations.stream().filter(dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration::enabled)
                .map(dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration::settings)
                .anyMatch(settings -> csv(settings.getAuthorizationGrantTypes()).contains(AUTHORIZATION_CODE_GRANT));
            boolean sdkCapable = configurations.stream().filter(dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration::enabled)
                .map(dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration::settings)
                .anyMatch(settings -> csv(settings.getAuthorizationGrantTypes()).contains(ExternalCodeType.GRANT_TYPE));
            if (!browserCapable && !form.getBrowserLoginProviderTypes().isEmpty() || !sdkCapable && !form.getExternalCodeTypes().isEmpty()) {
                bad("启用的身份源必须有兼容的协议授权方式");
            }
        }
        for (var configuration : configurations) {
            OAuth2ClientForm projected = protocols.project(form, configuration);
            validateConfiguration(projected, entity);
            if ("ENABLED".equals(projected.getConfigurationStatus())) { validate(projected, entity); }
            if (dev.macula.cloud.system.security.OAuth2ResourcePolicy.ADMIN_MANAGED_KEY.equals(original.getManagedKey())
                && !csv(projected.getAuthorizationGrantTypes()).contains(AUTHORIZATION_CODE_GRANT)) {
                bad("平台Admin必须保留授权码登录入口");
            }
        }
        boolean secretRequired = configurations.stream().anyMatch(dev.macula.cloud.system.protocol.ApplicationProtocolRegistry::requiresSecret);
        if (id == null) {
            if (mapper.selectCount(new LambdaQueryWrapper<SysOAuth2ClientConfig>().eq(SysOAuth2ClientConfig::getClientId, form.getClientId())) > 0) {
                bad("Client ID已存在");
            }
            entity.setClientId(form.getClientId());
            entity.setClientIdIssuedAt(Instant.now());
            entity.setClientAuthenticationMethods("");
            entity.setAuthorizationGrantTypes("");
            entity.setRedirectUris("");
            entity.setScopes("");
        }
        if (StringUtils.hasText(form.getClientSecret())) {
            if (StringUtils.hasText(entity.getClientSecret()) || !secretRequired) { bad("请通过密钥重置入口更新凭据"); }
            entity.setClientSecret(encodeSecret(form.getClientSecret()));
        }
        if (!secretRequired && !("IN_APP".equals(form.getApplicationType())
            && (converting || "IN_APP".equals(original.getApplicationType())))) { entity.setClientSecret(null); }
        if (enabled && configurations.stream().filter(dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration::enabled).anyMatch(dev.macula.cloud.system.protocol.ApplicationProtocolRegistry::requiresSecret) && !StringUtils.hasText(entity.getClientSecret())) { bad("机密协议必须设置应用密钥"); }
        entity.setPurpose(purpose);
        entity.setAuthorizationVersion(original.getAuthorizationVersion() == null ? 1L : original.getAuthorizationVersion() + 1);

        Set<String> selectedScopes = configurations.stream().flatMap(value -> csv(
            dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration.settings(value).getScopes()).stream()).collect(Collectors.toSet());
        entity.setResourcePermissions(json.writeValueAsString(configurationCatalog.resourcesForScopes(selectedScopes)));
        entity.setDefaultProtocol(form.getDefaultProtocol());
        entity.setConfigurationStatus(form.getConfigurationStatus());
        entity.setBrowserProviderBindings(json.writeValueAsString(nullSafeMap(form.getBrowserProviderBindings())));
        entity.setBrowserCapabilityBindings(form.getBrowserCapabilityBindings() == null ? null
            : json.writeValueAsString(form.getBrowserCapabilityBindings()));
        entity.setClientName(form.getClientName());
        entity.setApplicationType(form.getApplicationType());
        entity.setMiniappPlatform(form.getMiniappPlatform());
        entity.setHostedLoginMethods(form.getHostedLoginMethods() == null ? null : csvValue(form.getHostedLoginMethods()));
        entity.setExternalCodeTypes(csvValue(form.getExternalCodeTypes()));
        Set<BrowserLoginProviderType> browserProviders = browserLoginProviderTypes(form.getBrowserLoginProviderTypes());
        entity.setBrowserLoginProviderTypes(enumCsvValue(browserProviders));
        entity.setDirectLoginProviderType(normalizeDirectLoginProviderType(form.getDirectLoginProviderType(), browserProviders));
        entity.setUserType(form.getUserType());
        entity.setTenantId(form.getTenantId());
        if (id == null) {
            mapper.insert(entity);
        } else {
            updateApplication(entity, original);
        }
        protocols.replace(entity.getId(), configurations);
        replaceExternalProviders(entity.getClientId(), form.getExternalProviderBindings());
        evictAfterCommit();
        return toVO(entity);
    }

    /** 资源服务器只保存客户端认证，不伪造业务grant或登录参数。 */
    private OAuth2ClientVO saveResourceServer(Long id, OAuth2ClientForm form, SysOAuth2ClientConfig entity) {
        var settings = form.getSettings();
        if (settings == null) { bad("请配置资源服务器认证"); }
        Set<String> methods;
        try {
            methods = dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration.csv(settings.getClientAuthenticationMethods());
        } catch (IllegalArgumentException error) {
            bad("客户端认证方式格式无效");
            return null;
        }
        if (methods.isEmpty() || methods.contains("none") || !AUTH.containsAll(methods)) { bad("内省必须使用机密客户端认证"); }
        if (StringUtils.hasText(settings.getAuthorizationGrantTypes()) || StringUtils.hasText(settings.getScopes())
            || StringUtils.hasText(form.getProtocol())) { bad("资源服务器不配置业务协议或授权方式"); }
        if (methods.contains("private_key_jwt") && (!StringUtils.hasText(settings.getJwkSetUrl())
            || !settings.getJwkSetUrl().startsWith("https://") || !StringUtils.hasText(settings.getSigningAlgorithm())
            || !Set.of("RS256", "RS384", "RS512").contains(settings.getSigningAlgorithm()))) { bad("请配置HTTPS JWKS URL和签名算法"); }
        if (methods.contains("private_key_jwt")) {
            try {
                URI jwks = URI.create(settings.getJwkSetUrl());
                if (!"https".equalsIgnoreCase(jwks.getScheme()) || jwks.getHost() == null
                    || jwks.getUserInfo() != null || jwks.getFragment() != null) { bad("JWKS URL无效"); }
            } catch (IllegalArgumentException error) { bad("JWKS URL无效"); }
        }
        boolean secretRequired = methods.stream().anyMatch(value -> value.startsWith("client_secret_"));
        if (StringUtils.hasText(form.getClientSecret())) {
            if (StringUtils.hasText(entity.getClientSecret())) { bad("请通过密钥重置入口更新凭据"); }
            entity.setClientSecret(encodeSecret(form.getClientSecret()));
        }
        if (secretRequired && !StringUtils.hasText(entity.getClientSecret())) { bad("内省客户端需要密钥"); }
        if (!secretRequired) { entity.setClientSecret(null); }
        if (!Set.of("ENABLED", "DRAFT").contains(form.getConfigurationStatus())) { bad("状态无效"); }
        entity.setPurpose("RESOURCE_SERVER");
        entity.setClientId(form.getClientId());
        entity.setClientName(form.getClientName());
        entity.setConfigurationStatus(form.getConfigurationStatus());
        entity.setClientAuthenticationMethods(settings.getClientAuthenticationMethods());
        entity.setJwkSetUrl(settings.getJwkSetUrl());
        entity.setSigningAlgorithm(settings.getSigningAlgorithm());
        entity.setAuthorizationGrantTypes("");
        entity.setScopes("");
        entity.setRedirectUris("");
        entity.setUserType(null);
        entity.setTenantId(null);
        entity.setResourcePermissions("{}");

        entity.setAuthorizationVersion(entity.getAuthorizationVersion() == null ? 1L : entity.getAuthorizationVersion() + 1);
        if (id == null) {
            entity.setClientIdIssuedAt(Instant.now());
            mapper.insert(entity);
        } else {
            if (mapper.updateById(entity) != 1) { throw new ResponseStatusException(HttpStatus.CONFLICT, "资源服务器配置已变化"); }
        }
        protocols.replace(entity.getId(), List.of());
        evictAfterCommit();
        return toVO(entity);
    }

    /** 类型保留不可变约束，草稿无协议也不能修改已确认的类型。 */
    private boolean existingApplicationTypeChanged(SysOAuth2ClientConfig original, OAuth2ClientForm form) {
        return !conversion(original, form) && original.getApplicationType() != null && (!original.getApplicationType().equals(form.getApplicationType())
            || !java.util.Objects.equals(original.getMiniappPlatform(), form.getMiniappPlatform()));
    }

    /** 只旋转密钥列；认证类型并发变化时拒绝，避免回写过期配置快照。 */
    @Override
    @Transactional
    public void resetSecret(Long id, String secret, String configurationVersion) {
        SysOAuth2ClientConfig entity = mapper.selectOne(new LambdaQueryWrapper<SysOAuth2ClientConfig>().eq(SysOAuth2ClientConfig::getId, id).last("FOR UPDATE"));
        if (entity == null || ("RESOURCE_SERVER".equals(entity.getPurpose())
            ? csv(entity.getClientAuthenticationMethods()).stream().noneMatch(value -> value.startsWith("client_secret_"))
            : protocols.list(id).stream().noneMatch(dev.macula.cloud.system.protocol.ApplicationProtocolRegistry::requiresSecret))) {
            bad("当前协议不使用客户端密钥");
        }
        protocols.checkVersion(entity, configurationVersion);
        // 只更新密钥，不回写读取时的应用类型和托管策略，避免覆盖并发配置确认。
        int updated = mapper.update(null, new LambdaUpdateWrapper<SysOAuth2ClientConfig>()
            .eq(SysOAuth2ClientConfig::getId, id)
            .eq(SysOAuth2ClientConfig::getClientAuthenticationMethods, entity.getClientAuthenticationMethods())
            .set(SysOAuth2ClientConfig::getClientSecret, encodeSecret(secret))
            .set(SysOAuth2ClientConfig::getAuthorizationVersion, entity.getAuthorizationVersion() == null ? 1L : entity.getAuthorizationVersion() + 1));
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "客户端认证配置已变化，请刷新后重试");
        }
        evictAfterCommit();
    }

    @Override
    @Transactional
    public void delete(Long id, String configurationVersion) {
        SysOAuth2ClientConfig entity = mapper.selectOne(new LambdaQueryWrapper<SysOAuth2ClientConfig>().eq(SysOAuth2ClientConfig::getId, id).last("FOR UPDATE"));
        if (entity == null) { bad("应用不存在"); }
        protocols.checkVersion(entity, configurationVersion);
        if (StringUtils.hasText(entity.getManagedKey())) { bad("平台内置身份不可删除"); }
        protocols.replace(id, java.util.List.of());
        externalProviderMapper.delete(new LambdaQueryWrapper<SysOAuth2ClientExternalProvider>()
            .eq(SysOAuth2ClientExternalProvider::getClientId, entity.getClientId()));
        mapper.deleteById(id);
        evictAfterCommit();
    }

    /**
     * 在写入前校验租户、应用类型、授权组合和身份源，拒绝不明确的配置。
     *
     * @param form 待保存配置
     * @param existing 原始记录，用于不可变应用类型校验
     */
    private void validate(OAuth2ClientForm form, SysOAuth2ClientConfig existing) {
        if (tenantMapper.selectById(form.getTenantId()) == null) {
            bad("租户不存在");
        }
        if (!"SERVICE".equals(form.getApplicationType()) && !USERS.contains(form.getUserType() == null ? "" : form.getUserType())) {
            bad("用户类型无效");
        }
        Set<String> auth = csv(form.getClientAuthenticationMethods());
        Set<String> grants = csv(form.getAuthorizationGrantTypes());
        Set<String> scopes = csv(form.getScopes());
        Set<String> protocols = form.getEnabledProtocols();
        Set<String> allowedScopes = protocols.stream().flatMap(protocol -> configurationCatalog
            .allowedScopes(protocol, form.getApplicationType(), grants).stream()).collect(Collectors.toSet());
        if (!allowedScopes.containsAll(scopes)) { bad("Scope不在已启用协议/应用的受控目录中"); }
        if (protocols.contains(OAuth2ProtocolMode.OIDC.value()) && (!scopes.contains("openid") || !grants.contains(AUTHORIZATION_CODE_GRANT) && !grants.contains(ExternalCodeType.GRANT_TYPE))) {
            bad("OIDC登录需要openid及授权码或第三方Code登录");
        }
        if (grants.contains(AUTHORIZATION_CODE_GRANT) && scopes.isEmpty()) { bad("授权码流程需要明确选择Scope"); }
        if (grants.contains(AUTHORIZATION_CODE_GRANT) && !Boolean.TRUE.equals(form.getRequireProofKey())) {
            bad("授权码流程必须启用S256 PKCE");
        }
        if (auth.contains("none") && grants.contains("refresh_token")) {
            bad("当前公共客户端流程不支持安全刷新令牌，请使用SSO重新授权");
        }
        Set<ExternalCodeType> externalCodeTypes = externalCodeTypes(form.getExternalCodeTypes());
        Map<String, Long> externalProviderBindings = nullSafeMap(form.getExternalProviderBindings());
        Set<BrowserLoginProviderType> browserProviders = browserLoginProviderTypes(
            form.getBrowserLoginProviderTypes());
        normalizeDirectLoginProviderType(form.getDirectLoginProviderType(), browserProviders);
        validateApplication(form, existing, browserProviders, externalCodeTypes);
        if (hasInvalidCsvStructure(form.getClientAuthenticationMethods())
            || hasInvalidCsvStructure(form.getAuthorizationGrantTypes()) || auth.isEmpty()
            || !AUTH.containsAll(auth) || grants.isEmpty() || !GRANTS.containsAll(grants)) {
            bad("认证方式、授权方式或 scope 无效");
        }
        if (grants.contains(AUTHORIZATION_CODE_GRANT) && csv(form.getRedirectUris()).isEmpty()) {
            bad("授权码客户端必须配置回调 URI");
        }
        if (!browserProviders.isEmpty() && (!grants.contains(AUTHORIZATION_CODE_GRANT) ||
            csv(form.getRedirectUris()).isEmpty())) {
            bad("网页登录提供商只能配置给具有回调 URI 的授权码客户端");
        }
        if (externalCodeTypes.contains(ExternalCodeType.FEISHU_APP_CODE) && csv(form.getRedirectUris()).stream().noneMatch(this::appLinkRedirect)) {
            bad("飞书AppLink客户端必须登记至少一个HTTPS授权回调地址");
        }
        boolean publicClient = auth.contains("none");
        if (publicClient && (auth.size() != 1 || grants.contains(CLIENT_CREDENTIALS_GRANT) ||
            !Boolean.TRUE.equals(form.getRequireProofKey()) || StringUtils.hasText(form.getClientSecret()))) {
            bad("公共客户端必须启用 S256 PKCE、不得使用客户端凭据模式或设置密钥");
        }
        if ((!externalCodeTypes.isEmpty() || grants.contains(ExternalCodeType.GRANT_TYPE)) &&
            (!publicClient || !grants.contains(ExternalCodeType.GRANT_TYPE) || externalCodeTypes.isEmpty())) {
            bad("外部凭证登录必须由公共客户端同时配置 grant 和非空 code type");
        }
        Set<String> allowedOrigins = normalizeOrigins(form.getAllowedOrigins());
        boolean verificationCodeGrant = hasVerificationCodeGrant(grants);
        if (verificationCodeGrant && !publicClient) {
            bad("短信或邮箱验证码 grant 必须由公共客户端使用");
        }
        if (csvValue(allowedOrigins).length() > ALLOWED_ORIGINS_MAX_LENGTH) {
            bad("浏览器协议允许 Origin 超出长度限制");
        }
        validateExternalProviderBindings(externalCodeTypes, externalProviderBindings);
        validateBrowserCapabilities(form);
        validateOptionalParameters(form);
        if (auth.contains("private_key_jwt") && !StringUtils.hasText(form.getJwkSetUrl())) {
            bad("私钥 JWT 认证需要 JWK URL");
        }
        if (auth.contains("private_key_jwt")) {
            Set<String> algorithms = Set.of("RS256", "RS384", "RS512");
            if (!StringUtils.hasText(form.getSigningAlgorithm()) ||
                !algorithms.contains(form.getSigningAlgorithm())) {
                bad("JWT 客户端认证签名算法无效");
            }
        }
        if (StringUtils.hasText(form.getJwkSetUrl())) {
            URI uri;
            try {
                uri = URI.create(form.getJwkSetUrl());
            } catch (IllegalArgumentException ex) {
                bad("JWK URL 无效");
                return;
            }
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                bad("JWK URL 必须使用 HTTPS");
            }
        }
    }

    /** AppLink候选与普通授权码回调共存，仅前者要求完整HTTPS且无用户信息。 */
    private boolean appLinkRedirect(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                && uri.getRawUserInfo() == null && uri.getRawFragment() == null;
        } catch (IllegalArgumentException ex) { return false; }
    }

    /** 草稿与启用应用共享可选URL、有效期和格式校验，不借草稿隐藏非法参数。 */
    private void validateOptionalParameters(OAuth2ClientForm form) {
        for (String uriValue : csv(form.getRedirectUris())) {
            URI uri;
            try {
                uri = URI.create(uriValue);
            } catch (IllegalArgumentException ex) {
                bad("回调 URI 无效");
                return;
            }
            if (!uri.isAbsolute() || uri.getFragment() != null ||
                !("https".equalsIgnoreCase(uri.getScheme()) ||
                    ("http".equalsIgnoreCase(uri.getScheme()) &&
                        Set.of("localhost", "127.0.0.1").contains(uri.getHost())))) {
                bad("回调 URI 必须使用 HTTPS");
            }
        }
        if (form.getAuthorizationCodeTimeToLive() != null
            && (form.getAuthorizationCodeTimeToLive() < 30 || form.getAuthorizationCodeTimeToLive() > 3600)) {
            bad("授权码有效期无效");
        }
        if (form.getAccessTokenTimeToLive() != null
            && (form.getAccessTokenTimeToLive() < 60 || form.getAccessTokenTimeToLive() > 86400)) {
            bad("访问令牌有效期无效");
        }
        if (form.getRefreshTokenTimeToLive() != null
            && (form.getRefreshTokenTimeToLive() < 300 || form.getRefreshTokenTimeToLive() > 31536000)) {
            bad("刷新令牌有效期无效");
        }
        if (StringUtils.hasText(form.getTokenFormat())
            && !Set.of("self-contained", "reference").contains(form.getTokenFormat())) {
            bad("令牌格式无效");
        }
        if (StringUtils.hasText(form.getIdTokenSignatureAlgorithm())
            && !Set.of("RS256", "RS384", "RS512").contains(form.getIdTokenSignatureAlgorithm())) {
            bad("ID Token 签名算法无效");
        }
        if (StringUtils.hasText(form.getJwkSetUrl())) {
            try {
                URI uri = URI.create(form.getJwkSetUrl());
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) { bad("JWK URL必须使用HTTPS且不能含凭据或片段"); }
            } catch (IllegalArgumentException ex) { bad("JWK URL无效"); }
        }
    }

    /** 类型确认不修改grant或凭据；冲突必须由管理员明确修正。 */
    private void validateApplication(OAuth2ClientForm form, SysOAuth2ClientConfig existing,
        Set<BrowserLoginProviderType> providers, Set<ExternalCodeType> codeTypes) {
        if (!conversion(existing, form) && existing.getId() != null && existing.getApplicationType() != null
            && (!existing.getApplicationType().equals(form.getApplicationType())
            || !java.util.Objects.equals(existing.getMiniappPlatform(), form.getMiniappPlatform()))) {
            bad("应用类型和小程序平台保存后不可修改");
        }
        if (form.getHostedLoginMethods() != null && !form.getHostedLoginMethods().stream()
            .allMatch(value -> Arrays.stream(OAuth2HostedLoginMethod.values()).anyMatch(method -> method.name().equals(value)))) {
            bad("托管登录方式无效");
        }
        OAuth2ClientApplicationType type;
        try {
            type = OAuth2ClientApplicationType.valueOf(form.getApplicationType());
        } catch (IllegalArgumentException | NullPointerException ex) {
            bad("请选择有效的应用类型");
            return;
        }
        validateChannelCompatibility(type, form, codeTypes);
        Set<String> auth = csv(form.getClientAuthenticationMethods());
        Set<String> grants = csv(form.getAuthorizationGrantTypes());
        if (type == OAuth2ClientApplicationType.MINIAPP) {
            try {
                OAuth2MiniappPlatform.valueOf(form.getMiniappPlatform());
            } catch (IllegalArgumentException | NullPointerException ex) {
                bad("请选择小程序平台");
            }
            Set<String> allowed = "WECHAT".equals(form.getMiniappPlatform())
                ? Set.of("WECHAT_MINIAPP_LOGIN_CODE", "WECHAT_MINIAPP_PHONE_CODE") : Set.of("WECOM_MINIAPP_CODE");
            boolean hostedEntry = grants.contains(AUTHORIZATION_CODE_GRANT);
            boolean codeEntry = !codeTypes.isEmpty() && grants.contains(ExternalCodeType.GRANT_TYPE);
            if (!codeTypes.stream().allMatch(code -> allowed.contains(code.name())) || !hostedEntry && !codeEntry) {
                bad("小程序需启用IAM统一登录或对应平台Code登录");
            }
        } else if (form.getMiniappPlatform() != null) {
            bad("仅小程序可配置平台");
        }
        if ((type == OAuth2ClientApplicationType.SPA || type == OAuth2ClientApplicationType.NATIVE
            || type == OAuth2ClientApplicationType.MINIAPP) && !auth.equals(Set.of("none"))) {
            bad("公共应用必须使用none认证，密钥应仅由服务端保管");
        }
        if (type == OAuth2ClientApplicationType.SERVICE && (auth.contains("none")
            || !grants.equals(Set.of(CLIENT_CREDENTIALS_GRANT)) || !providers.isEmpty() || !codeTypes.isEmpty()
            || (form.getHostedLoginMethods() != null && !form.getHostedLoginMethods().isEmpty()))) {
            bad("后端服务仅允许客户端凭据模式，不提供用户登录或刷新令牌");
        }
    }

    /**
     * 新协议配置与兼容保存共用架构白名单，防止只修旧入口而让完整快照绕过。
     * @param type 已确认的应用架构
     * @param form 当前完整配置快照
     * @param codeTypes 明确关联的Code能力
     */
    private void validateChannelCompatibility(OAuth2ClientApplicationType type, OAuth2ClientForm form,
        Set<ExternalCodeType> codeTypes) {
        try {
            Map<String, Long> browser = form.getBrowserCapabilityBindings();
            if (browser == null) {
                browser = new java.util.LinkedHashMap<>();
                for (var entry : nullSafeMap(form.getBrowserProviderBindings()).entrySet()) {
                    var source = identityProviderMapper.selectById(entry.getValue());
                    if (source == null) { throw new IllegalArgumentException("身份源不存在"); }
                    for (var capability : IdentityLoginCapability.values()) {
                        if (capability.browser() && capability.webLogin() && capability.source().value().equals(source.getSourceType())) {
                            browser.put(capability.value(), entry.getValue());
                        }
                    }
                }
            }
            dev.macula.cloud.system.security.OAuth2ApplicationLoginPolicy.validate(type, form.getMiniappPlatform(),
                browser, nullSafeMap(form.getExternalProviderBindings()), "ENABLED".equals(form.getConfigurationStatus()));
            if (!nullSafeMap(form.getExternalProviderBindings()).keySet().equals(codeTypes.stream().map(Enum::name).collect(Collectors.toSet()))) {
                throw new IllegalArgumentException("Code能力与来源关联不一致");
            }
            if ((type == OAuth2ClientApplicationType.NATIVE || type == OAuth2ClientApplicationType.MINIAPP
                || type == OAuth2ClientApplicationType.SERVICE) && (!nullSafeMap(form.getBrowserProviderBindings()).isEmpty()
                    || !form.getBrowserLoginProviderTypes().isEmpty() || StringUtils.hasText(form.getDirectLoginProviderType()))) {
                throw new IllegalArgumentException("当前应用不允许第三方网页登录");
            }
            if (!type.supportsHostedLogin() && form.getHostedLoginMethods() != null && !form.getHostedLoginMethods().isEmpty()) {
                throw new IllegalArgumentException("当前应用不允许IAM内置登录");
            }
            if (form.getSettings() != null) {
                validateTypeGrants(type, csv(form.getSettings().getAuthorizationGrantTypes()), browser, form.getExternalProviderBindings());
            } else if (StringUtils.hasText(form.getAuthorizationGrantTypes())) {
                validateTypeGrants(type, csv(form.getAuthorizationGrantTypes()), browser, form.getExternalProviderBindings());
            }
        } catch (IllegalArgumentException ex) { bad(ex.getMessage()); }
    }

    /** 不支持的grant在保存时拒绝；端内网页和JSAPI不能被配置成混合入口。 */
    private void validateTypeGrants(OAuth2ClientApplicationType type, Set<String> grants, Map<String, Long> browser, Map<String, Long> external) {
        if (grants.stream().anyMatch(grant -> !type.supportsGrant(grant))) { bad("授权方式与应用类型不兼容"); }
        if (type == OAuth2ClientApplicationType.IN_APP && (!browser.isEmpty() && grants.contains(ExternalCodeType.GRANT_TYPE)
            || !nullSafeMap(external).isEmpty() && grants.contains(AUTHORIZATION_CODE_GRANT))) { bad("端内应用请仅启用所选能力对应的授权方式"); }
    }

    /** 有限类型转换必须明确确认并保持已有用户路由，不能绕过类型不可变规则。 */
    private boolean conversion(SysOAuth2ClientConfig original, OAuth2ClientForm form) {
        if (!form.isConvertToInApp()) { return false; }
        if (original.getId() == null || !"APPLICATION".equals(original.getPurpose()) || StringUtils.hasText(original.getManagedKey())
            || !Set.of("WEB_SERVER", "SPA").contains(original.getApplicationType() == null ? "" : original.getApplicationType())
            || !"IN_APP".equals(form.getApplicationType()) || !java.util.Objects.equals(original.getTenantId(), form.getTenantId())
            || !java.util.Objects.equals(original.getUserType(), form.getUserType()) || form.getMiniappPlatform() != null) {
            bad("仅已有Web/SPA应用可显式转换为端内应用，且不能改变认证路由");
        }
        return true;
    }

    private boolean hasVerificationCodeGrant(Set<String> grants) {
        return grants.contains(SMS_GRANT) || grants.contains(OAuth2LoginGrantTypes.EMAIL);
    }

    private boolean hasInvalidCsvStructure(String value) {
        if (!StringUtils.hasText(value)) {
            return true;
        }
        List<String> tokens = Arrays.stream(value.split(",", -1)).map(String::trim).toList();
        return tokens.stream().anyMatch(String::isEmpty) || Set.copyOf(tokens).size() != tokens.size();
    }

    /** 所有配置更新比较完整快照，防止并发修改覆盖彼此的协议与来源关联。 */
    private void updateApplication(SysOAuth2ClientConfig entity, SysOAuth2ClientConfig original) {
        LambdaUpdateWrapper<SysOAuth2ClientConfig> update = new LambdaUpdateWrapper<SysOAuth2ClientConfig>()
            .eq(SysOAuth2ClientConfig::getId, entity.getId());
        // 比较完整配置快照，绑定事务与配置更新共享同一Client行锁。
        matchOriginal(update, SysOAuth2ClientConfig::getProtocolMode, original.getProtocolMode());
        matchOriginal(update, SysOAuth2ClientConfig::getEnabledProtocols, original.getEnabledProtocols());
        matchOriginal(update, SysOAuth2ClientConfig::getDefaultProtocol, original.getDefaultProtocol());
        matchOriginal(update, SysOAuth2ClientConfig::getConfigurationStatus, original.getConfigurationStatus());
        matchOriginal(update, SysOAuth2ClientConfig::getBrowserProviderBindings, original.getBrowserProviderBindings());
        matchOriginal(update, SysOAuth2ClientConfig::getBrowserCapabilityBindings, original.getBrowserCapabilityBindings());
        matchOriginal(update, SysOAuth2ClientConfig::getApplicationType, original.getApplicationType());
        matchOriginal(update, SysOAuth2ClientConfig::getMiniappPlatform, original.getMiniappPlatform());
        matchOriginal(update, SysOAuth2ClientConfig::getHostedLoginMethods, original.getHostedLoginMethods());
        matchOriginal(update, SysOAuth2ClientConfig::getClientName, original.getClientName());
        matchOriginal(update, SysOAuth2ClientConfig::getClientAuthenticationMethods, original.getClientAuthenticationMethods());
        matchOriginal(update, SysOAuth2ClientConfig::getAuthorizationGrantTypes, original.getAuthorizationGrantTypes());
        matchOriginal(update, SysOAuth2ClientConfig::getClientSecret, original.getClientSecret());
        matchOriginal(update, SysOAuth2ClientConfig::getRedirectUris, original.getRedirectUris());
        matchOriginal(update, SysOAuth2ClientConfig::getScopes, original.getScopes());
        matchOriginal(update, SysOAuth2ClientConfig::getExternalCodeTypes, original.getExternalCodeTypes());
        matchOriginal(update, SysOAuth2ClientConfig::getAllowedOrigins, original.getAllowedOrigins());
        matchOriginal(update, SysOAuth2ClientConfig::getBrowserLoginProviderTypes, original.getBrowserLoginProviderTypes());
        matchOriginal(update, SysOAuth2ClientConfig::getDirectLoginProviderType, original.getDirectLoginProviderType());
        matchOriginal(update, SysOAuth2ClientConfig::getRequireProofKey, original.getRequireProofKey());
        matchOriginal(update, SysOAuth2ClientConfig::getRequireAuthorizationConsent, original.getRequireAuthorizationConsent());
        matchOriginal(update, SysOAuth2ClientConfig::getJwkSetUrl, original.getJwkSetUrl());
        matchOriginal(update, SysOAuth2ClientConfig::getSigningAlgorithm, original.getSigningAlgorithm());
        matchOriginal(update, SysOAuth2ClientConfig::getAuthorizationCodeTimeToLive, original.getAuthorizationCodeTimeToLive());
        matchOriginal(update, SysOAuth2ClientConfig::getAccessTokenTimeToLive, original.getAccessTokenTimeToLive());
        matchOriginal(update, SysOAuth2ClientConfig::getTokenFormat, original.getTokenFormat());
        matchOriginal(update, SysOAuth2ClientConfig::getReuseRefreshTokens, original.getReuseRefreshTokens());
        matchOriginal(update, SysOAuth2ClientConfig::getRefreshTokenTimeToLive, original.getRefreshTokenTimeToLive());
        matchOriginal(update, SysOAuth2ClientConfig::getIdTokenSignatureAlgorithm, original.getIdTokenSignatureAlgorithm());
        matchOriginal(update, SysOAuth2ClientConfig::getUserType, original.getUserType());
        matchOriginal(update, SysOAuth2ClientConfig::getTenantId, original.getTenantId());
        if (mapper.update(entity, update) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "客户端配置已被其他请求修改，请刷新后重试");
        }
    }

    /** 可空字段使用IS NULL，避免SQL三值逻辑使首次类型确认永远失败。 */
    private <T> void matchOriginal(LambdaUpdateWrapper<SysOAuth2ClientConfig> update,
        SFunction<SysOAuth2ClientConfig, T> field, T value) {
        if (value == null) {
            update.isNull(field);
        } else {
            update.eq(field, value);
        }
    }

    private Set<String> csv(String value) {
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    private Set<ExternalCodeType> externalCodeTypes(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        try {
            return values.stream().map(ExternalCodeType::fromValue).collect(Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException ex) {
            bad("外部凭证类型无效");
            return Set.of();
        }
    }

    private Set<BrowserLoginProviderType> browserLoginProviderTypes(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        try {
            return values.stream().map(BrowserLoginProviderType::fromValue)
                .collect(Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException ex) {
            bad("网页登录提供商类型无效");
            return Set.of();
        }
    }

    private String normalizeDirectLoginProviderType(String value,
        Set<BrowserLoginProviderType> browserProviders) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        BrowserLoginProviderType direct;
        try {
            direct = BrowserLoginProviderType.fromValue(value.trim());
        } catch (IllegalArgumentException ex) {
            bad("直接登录提供商类型无效");
            return null;
        }
        if (!browserProviders.contains(direct)) {
            bad("直接登录提供商必须包含在网页登录提供商中");
        }
        return direct.name();
    }

    /** 等价保留旧写入，新网页登录按能力固定注册，不能把不同微信AppID挤入同一平台槽。 */
    private void prepareBrowserCapabilities(OAuth2ClientForm form, SysOAuth2ClientConfig original) {
        if (!form.isBrowserCapabilityBindingsPresent() && original.getBrowserCapabilityBindings() != null) {
            var existing = browserBindings(original.getBrowserCapabilityBindings());
            if (!browserBindings(original.getBrowserProviderBindings()).equals(nullSafeMap(form.getBrowserProviderBindings()))
                || !csv(original.getBrowserLoginProviderTypes()).equals(form.getBrowserLoginProviderTypes())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "该应用使用按能力关联，请刷新后使用新版页面");
            }
            form.setBrowserCapabilityBindings(existing);
        }
        if (form.isBrowserCapabilityBindingsPresent() && form.getBrowserCapabilityBindings() == null) { bad("网页能力必须为对象"); }
        if (form.getBrowserCapabilityBindings() == null) { return; }
        Map<String, Long> representative = new java.util.TreeMap<>();
        for (var entry : new java.util.TreeMap<>(form.getBrowserCapabilityBindings()).entrySet()) {
            var capability = IdentityLoginCapability.fromValue(entry.getKey());
            if (!capability.browser() || entry.getValue() == null) { bad("网页能力关联无效"); }
            representative.putIfAbsent(capability.source().providerType(), entry.getValue());
        }
        if (!nullSafeMap(form.getBrowserProviderBindings()).isEmpty()) {
            for (var entry : form.getBrowserProviderBindings().entrySet()) {
                boolean same = form.getBrowserCapabilityBindings().entrySet().stream().anyMatch(cap ->
                    IdentityLoginCapability.fromValue(cap.getKey()).source().providerType().equals(entry.getKey())
                        && java.util.Objects.equals(cap.getValue(), entry.getValue()));
                if (!same) { bad("新旧网页登录关联冲突"); }
            }
        }
        form.setBrowserProviderBindings(representative);
        form.setBrowserLoginProviderTypes(representative.keySet());
    }

    /** 服务端再次校验启用能力和固定来源，不依赖管理端复选框。 */
    private void validateBrowserCapabilities(OAuth2ClientForm form) {
        if (form.getBrowserCapabilityBindings() == null) {
            validateBrowserLoginProviders(browserLoginProviderTypes(form.getBrowserLoginProviderTypes()), nullSafeMap(form.getBrowserProviderBindings()));
            return;
        }
        for (var entry : form.getBrowserCapabilityBindings().entrySet()) {
            var capability = IdentityLoginCapability.fromValue(entry.getKey());
            var source = identityProviderMapper.selectById(entry.getValue());
            if (!capability.browser() || source == null || !Boolean.TRUE.equals(source.getEnabled())
                || !capability.source().providerType().equals(source.getProviderType())
                || !sourceCapabilities(source).containsKey(entry.getKey()) || !StringUtils.hasText(source.getAppId())
                || !StringUtils.hasText(source.getAppSecret())) { bad("网页登录能力或固定来源不可用"); }
            try { dev.macula.cloud.system.enums.IdentitySourceFlow.resolve(capability.source(), sourceCapabilities(source).get(entry.getKey())); }
            catch (IllegalArgumentException ex) { bad("网页登录能力合同不可用"); }
        }
    }

    /** 目录和保存共用来源真实能力，旧null只投影原来的许可。 */
    private Map<String, String> sourceCapabilities(SysIdentityProvider source) {
        try {
            return IdentityLoginCapability.decode(source.getCapabilitiesJson(), IdentitySourceType.fromValue(source.getSourceType()), source.getIdentityFlow());
        } catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "身份源能力配置无效"); }
    }

    /** 每个Client明确固定来源，同厂商多来源不进行全局择一。 */
    private void validateBrowserLoginProviders(Set<BrowserLoginProviderType> providers, Map<String, Long> bindings) {
        if (!providers.stream().map(Enum::name).collect(Collectors.toSet()).equals(bindings.keySet())) {
            bad("请选择每个网页登录方式对应的具体身份源");
        }
        for (BrowserLoginProviderType provider : providers) {
            SysIdentityProvider source = identityProviderMapper.selectById(bindings.get(provider.name()));
            if (source == null || !Boolean.TRUE.equals(source.getEnabled()) || !provider.name().equals(source.getProviderType())
                || source.getSourceType() == null || !IdentitySourceType.fromValue(source.getSourceType()).browser()
                || sourceCapabilities(source).keySet().stream().map(IdentityLoginCapability::fromValue).noneMatch(IdentityLoginCapability::browser)) {
                bad("网页登录关联的具体身份源不可用");
            }
        }
    }

    private void validateExternalProviderBindings(Set<ExternalCodeType> codeTypes, Map<String, Long> bindings) {
        Set<String> expected = codeTypes.stream().map(Enum::name).collect(Collectors.toSet());
        if (!expected.equals(bindings.keySet())) {
            bad("每个外部凭证类型必须且只能绑定一个身份源配置");
        }
        for (ExternalCodeType codeType : codeTypes) {
            SysIdentityProvider provider = identityProviderMapper.selectById(bindings.get(codeType.name()));
            if (provider == null || !Boolean.TRUE.equals(provider.getEnabled()) ||
                !codeType.providerType().equals(provider.getProviderType())) {
                bad("外部凭证类型绑定的身份源配置无效");
            }
            validateProviderCapability(codeType, provider);
        }
    }

    /** 按具体来源验证能力，不借用厂商整包附加凭据。 */
    private void validateProviderCapability(ExternalCodeType codeType, SysIdentityProvider provider) {
        var capability = IdentityLoginCapability.forCode(codeType);
        if (provider.getSourceType() == null || !sourceCapabilities(provider).containsKey(capability.value())
            || !StringUtils.hasText(provider.getAppId()) || !StringUtils.hasText(provider.getAppSecret())) {
            bad("具体身份源不支持所选端侧认证方式");
        }
        try { dev.macula.cloud.system.enums.IdentitySourceFlow.resolve(capability.source(), sourceCapabilities(provider).get(capability.value())); }
        catch (IllegalArgumentException ex) { bad("所选能力的兑换合同不可用"); }
    }

    private Map<String, String> jsonObject(String value) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        try {
            return json.readValue(value, new TypeReference<>() { });
        } catch (RuntimeException ex) {
            bad("身份源配置无效");
            return Map.of();
        }
    }

    private Set<String> normalizeOrigins(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> origins = new TreeSet<>();
        for (String value : values) {
            try {
                URI uri = new URI(value == null ? "" : value.trim()).normalize();
                boolean loopbackHttp = "http".equalsIgnoreCase(uri.getScheme()) &&
                    Set.of("localhost", "127.0.0.1").contains(uri.getHost());
                if ((!"https".equalsIgnoreCase(uri.getScheme()) && !loopbackHttp) || uri.getHost() == null ||
                    uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null ||
                    (StringUtils.hasText(uri.getPath()) && !"/".equals(uri.getPath()))) {
                    bad("允许 Origin 必须是 HTTPS origin");
                }
                int port = uri.getPort();
                if (("https".equalsIgnoreCase(uri.getScheme()) && port == 443) ||
                    ("http".equalsIgnoreCase(uri.getScheme()) && port == 80)) {
                    port = -1;
                }
                origins.add(new URI(uri.getScheme().toLowerCase(), null, uri.getHost().toLowerCase(),
                    port, null, null, null).toASCIIString());
            } catch (URISyntaxException | IllegalArgumentException ex) {
                bad("允许 Origin 无效");
            }
        }
        return Collections.unmodifiableSet(origins);
    }

    private void replaceExternalProviders(String clientId, Map<String, Long> bindings) {
        externalProviderMapper.delete(new LambdaQueryWrapper<SysOAuth2ClientExternalProvider>()
            .eq(SysOAuth2ClientExternalProvider::getClientId, clientId));
        new TreeSet<>(nullSafeMap(bindings).keySet()).forEach(codeType -> {
            SysOAuth2ClientExternalProvider mapping = new SysOAuth2ClientExternalProvider();
            mapping.setClientId(clientId);
            mapping.setCodeType(codeType);
            mapping.setIdentityProviderId(bindings.get(codeType));
            externalProviderMapper.insert(mapping);
        });
    }

    private Map<String, Long> externalProviderBindings(String clientId) {
        return externalProviderMapper.selectList(new LambdaQueryWrapper<SysOAuth2ClientExternalProvider>()
            .eq(SysOAuth2ClientExternalProvider::getClientId, clientId).orderByAsc(
                SysOAuth2ClientExternalProvider::getCodeType)).stream().collect(Collectors.toMap(
                    SysOAuth2ClientExternalProvider::getCodeType,
                    SysOAuth2ClientExternalProvider::getIdentityProviderId,
                    (left, right) -> left, LinkedHashMap::new));
    }

    private Map<String, Long> nullSafeMap(Map<String, Long> values) {
        return values == null ? Map.of() : Map.copyOf(values);
    }

    private String csvValue(Set<String> values) {
        return String.join(",", new TreeSet<>(values == null ? Set.of() : values));
    }

    private String enumCsvValue(Set<BrowserLoginProviderType> values) {
        return values.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    private String encodeSecret(String secret) {
        if (!StringUtils.hasText(secret) || secret.length() < 16) {
            bad("密钥至少 16 个字符");
        }
        return passwordEncoder.encode(secret);
    }

    private SysOAuth2ClientConfig require(Long id) {
        SysOAuth2ClientConfig entity = mapper.selectById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "接入应用不存在");
        }
        return entity;
    }

    /**
     * 返回持久化应用类型和脱敏配置，避免由登录方式反向猜测类型。
     *
     * @param e Client 记录
     * @return 脱敏配置
     */
    private OAuth2ClientVO toVO(SysOAuth2ClientConfig e) {
        OAuth2ClientVO result = new OAuth2ClientVO();
        BeanUtils.copyProperties(e, result, "requireProofKey", "requireAuthorizationConsent", "reuseRefreshTokens");
        result.setRequireProofKey(Boolean.TRUE.equals(e.getRequireProofKey()));
        result.setRequireAuthorizationConsent(Boolean.TRUE.equals(e.getRequireAuthorizationConsent()));
        result.setReuseRefreshTokens(Boolean.TRUE.equals(e.getReuseRefreshTokens()));
        result.setPurpose(e.getPurpose() == null ? "APPLICATION" : e.getPurpose());

        result.setConfigurationVersion(protocols.version(e));
        result.setClientIdIssuedAt(e.getClientIdIssuedAt() == null ? null : Date.from(e.getClientIdIssuedAt()));
        result.setHostedLoginMethods(e.getHostedLoginMethods() == null ? null : csv(e.getHostedLoginMethods()));
        result.setExternalCodeTypes(csv(e.getExternalCodeTypes()));
        result.setAllowedOrigins(csv(e.getAllowedOrigins()));
        result.setBrowserLoginProviderTypes(csv(e.getBrowserLoginProviderTypes()));
        result.setBrowserProviderBindings(browserBindings(e.getBrowserProviderBindings()));
        result.setBrowserCapabilityBindings(e.getBrowserCapabilityBindings() == null ? null : browserBindings(e.getBrowserCapabilityBindings()));
        result.setExternalProviderBindings(externalProviderBindings(e.getClientId()));
        result.setSecretConfigured(StringUtils.hasText(e.getClientSecret()));
        var configurations = protocols.list(e.getId());
        result.setProtocolConfigurations(configurations);
        if (configurations.size() == 1) {
            result.setProtocol(configurations.get(0).protocol());
            result.setSettings(dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration.settings(configurations.get(0)));
        } else if ("RESOURCE_SERVER".equals(e.getPurpose())) {
            var settings = new dev.macula.cloud.system.protocol.OAuth2ProtocolSettings();
            BeanUtils.copyProperties(e, settings);
            settings.setAuthorizationGrantTypes("");
            settings.setScopes("");
            result.setSettings(settings);
        }
        return result;
    }

    /** 读取明确关联，不从旧厂商允许列表推断来源。 */
    private Map<String, Long> browserBindings(String value) {
        return StringUtils.hasText(value) ? json.readValue(value, new TypeReference<>() { }) : Map.of();
    }

    private void evictAfterCommit() {
        Cache clientCache = requireCache(CacheConstants.OAUTH2_CLIENT_CACHE_KEY);
        Cache originCache = requireCache(OAuth2ClientCacheNames.TOKEN_ORIGIN_INDEX);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                clearCaches(clientCache, originCache);
            }
        });
    }

    private Cache requireCache(String name) {
        Cache cache = cacheManager.getCache(name);
        if (cache == null) {
            throw new IllegalStateException("IAM 客户端缓存不可用");
        }
        return cache;
    }

    /** 提交后立即驱逐全部安全缓存；任一失败仍尝试其余缓存，再向调用者报告故障。
     * @param caches 已确认存在的安全缓存
     */
    private void clearCaches(Cache... caches) {
        RuntimeException failure = null;
        for (Cache cache : caches) {
            try {
                // 已处于提交后边界，使用保证立即失效的Cache API，避免再次延后清理。
                cache.invalidate();
            } catch (RuntimeException ex) {
                if (failure == null) {
                    failure = ex;
                } else {
                    failure.addSuppressed(ex);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static void bad(String reason) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
