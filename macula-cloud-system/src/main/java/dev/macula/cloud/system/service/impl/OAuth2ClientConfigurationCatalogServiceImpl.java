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
import dev.macula.cloud.system.config.OAuth2ScopeCatalogProperties;
import dev.macula.cloud.system.enums.ExternalCodeType;
import dev.macula.cloud.system.enums.IdentitySourceType;
import dev.macula.cloud.system.enums.OAuth2ProtocolMode;
import dev.macula.cloud.system.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.system.pojo.entity.SysIdentityProvider;
import dev.macula.cloud.system.service.OAuth2ClientConfigurationCatalogService;
import dev.macula.cloud.system.vo.OAuth2ClientConfigurationCatalogVO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 以已实现协议和管理员登记的资源Scope生成配置目录。
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class OAuth2ClientConfigurationCatalogServiceImpl implements OAuth2ClientConfigurationCatalogService {
    private static final Set<String> RESERVED = Set.of("openid", "profile", "email", "phone", "address", "offline_access", "system:access");
    private final OAuth2ScopeCatalogProperties properties;
    private final SysIdentityProviderMapper providers;

    /** 启动时拒绝重复、保留名或非法的业务声明。 */
    @PostConstruct
    public void validate() {
        Set<String> seen = new HashSet<>();
        for (OAuth2ScopeCatalogProperties.Scope scope : properties.getBusiness()) {
            if (scope.getId() == null || !scope.getId().matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}")
                || !StringUtils.hasText(scope.getResource()) || RESERVED.contains(scope.getId()) || !seen.add(scope.getId()) || !StringUtils.hasText(scope.getName())) {
                throw new IllegalStateException("业务Scope声明无效或重复");
            }
        }
    }

    /** 从同一声明生成前后端一致的Scope目录。 */
    private List<OAuth2ClientConfigurationCatalogVO.Scope> scopes() {
        List<OAuth2ClientConfigurationCatalogVO.Scope> scopes = new ArrayList<>();
        scopes.add(new OAuth2ClientConfigurationCatalogVO.Scope("openid", "OpenID登录", "签发ID Token", true, Set.of(), Set.of()));
        scopes.add(new OAuth2ClientConfigurationCatalogVO.Scope("profile", "基础资料", "当前仅用户名、昵称", false, Set.of(), Set.of("authorization_code", "sms", "email", ExternalCodeType.GRANT_TYPE)));
        scopes.add(new OAuth2ClientConfigurationCatalogVO.Scope("system:access", "系统API", "仍需通过角色及租户权限检查", false, Set.of(), Set.of()));
        for (OAuth2ScopeCatalogProperties.Scope scope : properties.getBusiness()) {
            scopes.add(new OAuth2ClientConfigurationCatalogVO.Scope(scope.getId(), scope.getName(),
                scope.getDescription(), false, scope.getApplicationTypes(), scope.getGrants()));
        }
        return List.copyOf(scopes);
    }

    /** 只使用受信任目录中的资源映射，不接受客户端提交aud。 */
    @Override
    public java.util.Map<String, String> resourcesForScopes(Set<String> scopes) {
        java.util.Map<String, String> result = new java.util.TreeMap<>();
        Set<String> builtin = Set.of("openid", "profile", "system:access");
        result.putAll(OAuth2ClientConfigurationCatalogService.super.resourcesForScopes(
            scopes.stream().filter(builtin::contains).collect(Collectors.toSet())));
        for (String scope : scopes) {
            if (!builtin.contains(scope)) {
                var definition = properties.getBusiness().stream().filter(value -> scope.equals(value.getId()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Scope未登记"));
                result.put(scope, definition.getResource());
            }
        }
        return java.util.Map.copyOf(result);
    }

    /** 返回具体已配置来源，不查询全局唯一厂商。 */
    @Override
    public OAuth2ClientConfigurationCatalogVO catalog() {
        List<OAuth2ClientConfigurationCatalogVO.Source> sources = providers.selectList(
            new LambdaQueryWrapper<SysIdentityProvider>().eq(SysIdentityProvider::getEnabled, true))
            .stream().filter(p -> p.getSourceType() != null).map(p -> {
                IdentitySourceType type = IdentitySourceType.fromValue(p.getSourceType());
                var settings = dev.macula.cloud.system.enums.IdentityLoginCapability.decode(p.getCapabilitiesJson(), type, p.getIdentityFlow());
                var capabilities = settings.entrySet().stream().map(entry -> {
                    var capability = dev.macula.cloud.system.enums.IdentityLoginCapability.fromValue(entry.getKey());
                    var code = capability.code();
                    dev.macula.cloud.system.enums.IdentitySourceFlow flow = null;
                    try { flow = dev.macula.cloud.system.enums.IdentitySourceFlow.resolve(capability.source(), entry.getValue()); }
                    catch (IllegalArgumentException ex) { /* 历史不可用合同仍显示原因，不自动升级。 */ }
                    return new OAuth2ClientConfigurationCatalogVO.Capability(code == null ? null : code.name(), capability.label(),
                        capability.source().scene(), code == null ? null : code.resultType().name(),
                        Arrays.stream(dev.macula.cloud.system.enums.OAuth2ClientApplicationType.values())
                            .filter(application -> code == null ? application.supportsBrowserCapability(capability)
                                : application.supportsExternalCode(code, type.providerType()))
                            .map(Enum::name).collect(Collectors.toUnmodifiableSet()), capability.value(), capability.browser(),
                        capability.environments().stream().map(Enum::name).collect(Collectors.toUnmodifiableSet()),
                        entry.getValue(), flow != null && flow.providerPkce(), null, available(p, capability.source(), entry.getValue()));
                }).toList();
                boolean browser = capabilities.stream().anyMatch(cap -> cap.browser() && cap.available());
                boolean available = capabilities.stream().anyMatch(OAuth2ClientConfigurationCatalogVO.Capability::available);
                return new OAuth2ClientConfigurationCatalogVO.Source(p.getId(), p.getName(), p.getProviderType(), type.value(),
                    dev.macula.cloud.system.enums.IdentityCredentialType.forSource(type).label(), type.scene(), browser, capabilities, available,
                    available ? null : "未启用可用能力或凭据/合同不完整", p.getIdentityFlow(), false, null,
                    Arrays.stream(dev.macula.cloud.system.enums.OAuth2ClientApplicationType.values())
                        .filter(application -> settings.keySet().stream().map(dev.macula.cloud.system.enums.IdentityLoginCapability::fromValue)
                            .anyMatch(application::supportsBrowserCapability))
                        .map(Enum::name).collect(Collectors.toUnmodifiableSet()),
                    capabilities.stream().filter(cap -> cap.browser() && cap.available()).flatMap(cap -> cap.environments().stream()).collect(Collectors.toUnmodifiableSet()));
            }).toList();
        return new OAuth2ClientConfigurationCatalogVO(List.of(
            new OAuth2ClientConfigurationCatalogVO.Protocol("OIDC", "OIDC · 用户登录"),
            new OAuth2ClientConfigurationCatalogVO.Protocol("OAUTH2_1", "OAuth 2.1 · API授权")), scopes(), sources);
    }

    /** 目录可用性与具体来源的实际凭据合同一致，不靠有主密钥就宣称所有能力可用。 */
    private boolean available(SysIdentityProvider provider, IdentitySourceType type, String selectedFlow) {
        if (!type.providerType().equals(provider.getProviderType()) || type == IdentitySourceType.ALIPAY_APPLICATION
            || !StringUtils.hasText(provider.getAppId()) || !StringUtils.hasText(provider.getAppSecret())) {
            return false;
        }
        try {
            var flow = dev.macula.cloud.system.enums.IdentitySourceFlow.resolve(type, selectedFlow);
            var directory = dev.macula.cloud.system.enums.IdentityDirectoryPolicy.fromValue(provider.getDirectoryPolicy());
            if (provider.getVersion() == null || provider.getVersion() < 0 || !Boolean.TRUE.equals(provider.getEnabled())) { return false; }
            if (directory != dev.macula.cloud.system.enums.IdentityDirectoryPolicy.IDENTITY_ONLY
                && (!java.util.Set.of("WECOM", "FEISHU", "DINGTALK").contains(type.providerType())
                    || !StringUtils.hasText(provider.getOrganizationId()))) { return false; }
            var mappings = dev.macula.cloud.system.enums.IdentityBindingMappings.decode(provider.getBindingMappingsJson(),
                provider.getBindingSourceField(), provider.getBindingTargetField(), provider.getBindingRule());
            if (Boolean.TRUE.equals(provider.getAutoBindEnabled())) {
                if ("FEISHU".equals(type.providerType()) && !StringUtils.hasText(provider.getOrganizationId())) { return false; }
                if (mappings.isEmpty()) { return false; }
                var enabled = dev.macula.cloud.system.enums.IdentityLoginCapability.decode(provider.getCapabilitiesJson(),
                    IdentitySourceType.fromValue(provider.getSourceType()), provider.getIdentityFlow());
                for (var mapping : mappings) {
                    for (String capability : enabled.keySet()) { mapping.validateFor(dev.macula.cloud.system.enums.IdentityLoginCapability.fromValue(capability).source()); }
                }
            } else if (!mappings.isEmpty()) { return false; }
            var json = tools.jackson.databind.json.JsonMapper.builder().build();
            if (StringUtils.hasText(provider.getSecretConfigJson())) {
                // 与IAM加载合同一致：历史加密空值和空对象不含附加凭据，混合密钥仍不可用。
                Object additional = json.readValue(provider.getSecretConfigJson(), Object.class);
                if (additional != null && (!(additional instanceof java.util.Map<?, ?> fields) || !fields.isEmpty())) {
                    return false;
                }
            }
            java.util.Map<String,String> values = json.readValue(
                provider.getConfigJson(), new tools.jackson.core.type.TypeReference<>() { });
            return values != null && values.keySet().stream().allMatch(key -> type.requiredFields().contains(key)
                    && !key.equals(type.credentialField()))
                && (!StringUtils.hasText(provider.getOrganizationId()) || !values.containsKey("corpId")
                    || provider.getOrganizationId().equals(values.get("corpId")))
                && provider.getAppId().equals(values.get(type.identifierField()))
                && type.requiredFields().stream().filter(key -> !key.equals(type.credentialField()))
                    .allMatch(key -> StringUtils.hasText(values.get(key)));
        } catch (RuntimeException ex) { return false; }
    }

    /** 仅允许真实内置能力及适用于当前应用/授权的资源声明。 */
    @Override
    public Set<String> allowedScopes(String protocolMode, String applicationType, Set<String> grants) {
        OAuth2ProtocolMode mode = OAuth2ProtocolMode.fromValue(protocolMode);
        return scopes().stream().filter(scope -> !scope.oidcOnly() || mode == OAuth2ProtocolMode.OIDC)
            .filter(scope -> scope.applicationTypes().isEmpty() || scope.applicationTypes().contains(applicationType))
            .filter(scope -> scope.grants().isEmpty() || grants.stream().anyMatch(scope.grants()::contains))
            .map(OAuth2ClientConfigurationCatalogVO.Scope::value).collect(Collectors.toUnmodifiableSet());
    }
}
