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
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import dev.macula.boot.mapstruct.DateTimeConverter;
import dev.macula.cloud.system.enums.ExternalCodeType;
import dev.macula.cloud.system.enums.IamApplicationType;
import dev.macula.cloud.system.enums.IamWebLoginMode;
import dev.macula.cloud.system.enums.IdentityLoginCapability;
import dev.macula.cloud.system.form.IamApplicationForm;
import dev.macula.cloud.system.pojo.entity.SysIamApplication;
import dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration;
import dev.macula.cloud.system.protocol.ApplicationProtocolRegistry;
import dev.macula.cloud.system.protocol.IamApplicationProtocolRegistration;
import dev.macula.cloud.system.protocol.IamApplicationProtocolRegistry;
import dev.macula.cloud.system.protocol.OAuth2ApplicationProtocolSettings;
import dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration;
import dev.macula.cloud.system.protocol.OAuth2ProtocolDefinition;
import dev.macula.cloud.system.protocol.OAuth2ProtocolSettings;
import dev.macula.cloud.system.security.IamApplicationLoginPolicy;
import dev.macula.cloud.system.service.IamApplicationCatalogService;
import dev.macula.cloud.system.vo.IamApplicationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 既有HTTP形状的边界适配：clientId/settings等不再成为重复持久化权威。
 * OAuth专用投影在此转换，公共协议存储及登录策略不依赖这些旧字段。
 * @author Rain
 * @since 6.1.0
 */
@Component
@RequiredArgsConstructor
public class IamApplicationWireAdapter {
    private final IamApplicationProtocolService protocols;
    private final IamApplicationProtocolRegistry registry;
    private final IamApplicationPolicyValidator policy;
    private final IamApplicationCatalogService catalog;
    private final DateTimeConverter dates;
    private final JsonMapper json = JsonMapper.builder().build();

    /** 合并旧厂商/新能力输入；冲突拒绝，省略能力字段不能隐式覆盖已存在的明确关联。 */
    public Map<String, Long> routes(IamApplicationForm form, IamApplicationProtocolService.Stored stored) {
        Map<String, Long> existing = browser(stored);
        Map<String, Long> selected = form.getBrowserCapabilityBindings();
        if (form.isBrowserCapabilityBindingsPresent() && selected == null) {
            throw bad("网页能力必须为对象");
        }
        if (selected == null && !existing.isEmpty()) {
            if (!representative(existing).equals(safe(form.getBrowserProviderBindings()))
                || !representative(existing).keySet().equals(form.getBrowserLoginProviderTypes())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "该应用使用按能力关联，请刷新后使用新版页面");
            }
            selected = existing;
        }
        if (selected == null) {
            selected = new TreeMap<>();
            for (var entry : safe(form.getBrowserProviderBindings()).entrySet()) {
                String capability = policy.legacyBrowserCapability(entry.getKey(), entry.getValue(), form);
                selected.put(capability, entry.getValue());
            }
            require(selected.isEmpty() ? form.getBrowserLoginProviderTypes() == null || form.getBrowserLoginProviderTypes().isEmpty()
                : representative(selected).keySet().equals(form.getBrowserLoginProviderTypes()), "网页登录允许列表与来源关联不一致");
        } else {
            selected = new TreeMap<>(selected);
            for (var entry : safe(form.getBrowserProviderBindings()).entrySet()) {
                Map<String, Long> selectedRoutes = selected;
                require(selectedRoutes.entrySet().stream().anyMatch(route -> provider(route.getKey()).equals(entry.getKey())
                    && Objects.equals(route.getValue(), entry.getValue())), "新旧网页登录关联冲突");
            }
            require(form.getBrowserLoginProviderTypes() == null || form.getBrowserLoginProviderTypes().isEmpty()
                || representative(selected).keySet().equals(form.getBrowserLoginProviderTypes()), "网页登录允许列表与能力关联冲突");
        }
        Map<String, Long> all = new TreeMap<>(selected);
        for (var entry : safe(form.getExternalProviderBindings()).entrySet()) {
            String capability = IdentityLoginCapability.forCode(ExternalCodeType.fromValue(entry.getKey())).value();
            require(all.putIfAbsent(capability, entry.getValue()) == null, "登录能力重复");
        }
        form.setBrowserCapabilityBindings(selected);
        form.setBrowserProviderBindings(representative(selected));
        form.setBrowserLoginProviderTypes(form.getBrowserProviderBindings().keySet());
        return Map.copyOf(all);
    }

    /** 一种协议一份设置；当前旧HTTP只支持已实现OAuth家族，不为未来协议猜测载荷类型。 */
    public IamApplicationProtocolRegistration<?> selection(IamApplicationForm form, SysIamApplication app,
        IamApplicationProtocolService.Stored stored) {
        boolean resource = "RESOURCE_SERVER".equals(app.getPurpose());
        String protocol = form.getProtocol();
        OAuth2ProtocolSettings settings = form.getSettings();
        var legacy = form.getProtocolConfigurations();
        require(legacy != null && legacy.size() <= 1, "每个应用只能配置一种协议");
        if (!legacy.isEmpty()) {
            ApplicationProtocolConfiguration old = legacy.get(0);
            ApplicationProtocolRegistry.require(old);
            require(protocol == null || protocol.equals(old.protocol()) && (settings == null || settings.equals(old.settings())), "新旧协议输入冲突");
            protocol = old.protocol();
            settings = OAuth2ProtocolConfiguration.settings(old);
        }
        if (resource && protocol == null && settings == null && stored.protocol() == null && "DRAFT".equals(app.getConfigurationStatus())) {
            return null;
        }
        if (resource) {
            require(protocol == null && legacy.isEmpty() && settings != null, "资源身份不配置业务协议");
            protocol = ApplicationProtocolRegistry.OAUTH2_1;
        } else if (protocol == null) {
            require(settings == null && !"ENABLED".equals(app.getConfigurationStatus()), "启用前请选择协议");
            return null;
        }
        require(settings != null, "协议参数不能为空");
        if (!resource && form.getEnabledProtocols() != null && !form.getEnabledProtocols().isEmpty()) {
            require(form.getEnabledProtocols().equals(Set.of(protocol)), "协议选择冲突");
        }
        require(form.getDefaultProtocol() == null || form.getDefaultProtocol().equals(protocol), "默认协议输入冲突");
        var payload = new OAuth2ApplicationProtocolSettings();
        payload.setOauth2(settings);
        payload.setExternalCodeTypes(form.getExternalCodeTypes() == null ? Set.of()
            : form.getExternalCodeTypes().stream().map(ExternalCodeType::fromValue).collect(Collectors.toUnmodifiableSet()));
        var previous = stored.protocol();
        var registration = registry.read(protocol, OAuth2ProtocolDefinition.SCHEMA_VERSION, "ENABLED".equals(app.getConfigurationStatus()),
            previous == null ? app.getApplicationCode() : previous.getProtocolIdentifier(),
            dates.toInstant(previous == null ? app.getApplicationCodeIssuedAt() : previous.getIdentifierIssuedAt()),
            json.writeValueAsString(payload), app.getApplicationType(), app.getPurpose());
        if (!resource) {
            form.setProtocol(protocol);
            form.setSettings(settings);
            Set<String> grants = OAuth2ProtocolConfiguration.csv(settings.getAuthorizationGrantTypes());
            Set<String> scopes = OAuth2ProtocolConfiguration.csv(settings.getScopes());
            require(catalog.allowedScopes(protocol, app.getApplicationType(), grants).containsAll(scopes), "Scope不在受控目录中");
            IamApplicationLoginPolicy.validateGrants(IamApplicationType.valueOf(app.getApplicationType()), app.getWebLoginMode(),
                safe(form.getBrowserCapabilityBindings()), safe(form.getExternalProviderBindings()), grants);
            require(!registration.enabled() || (safe(form.getBrowserCapabilityBindings()).isEmpty() || grants.contains("authorization_code"))
                && (safe(form.getExternalProviderBindings()).isEmpty() || grants.contains(ExternalCodeType.GRANT_TYPE)), "登录来源缺少兼容协议授予");
        }
        return registration;
    }

    /** 旧直登厂商投影成唯一已选浏览器能力；不能借用Code来源。 */
    public String directCapability(IamApplicationForm form, Map<String, Long> routes) {
        if (!StringUtils.hasText(form.getDirectLoginProviderType())) {
            return null;
        }
        var matches = routes.keySet().stream().map(IdentityLoginCapability::fromValue)
            .filter(IdentityLoginCapability::browser)
            .filter(capability -> capability.source().providerType().equals(form.getDirectLoginProviderType().trim())).toList();
        require(matches.size() == 1, "直接登录提供商必须对应一个已选网页登录能力");
        return matches.get(0).value();
    }

    /** 只输出兼容非秘密视图，资源管理登记不成为公开业务协议选项。 */
    public IamApplicationVO view(SysIamApplication app, IamApplicationProtocolService.Stored stored) {
        var result = new IamApplicationVO();
        BeanUtils.copyProperties(app, result);
        result.setClientId(app.getApplicationCode());
        result.setClientName(app.getApplicationName());
        result.setClientIdIssuedAt(dates.toDate(app.getApplicationCodeIssuedAt()));
        result.setConfigurationVersion(protocols.version(app, stored));
        result.setHostedLoginMethods(app.getHostedLoginMethods() == null ? null : OAuth2ProtocolConfiguration.csv(app.getHostedLoginMethods()));
        result.setSecretConfigured(stored.protocol() != null && StringUtils.hasText(stored.protocol().getCredentialHash()));
        result.setWebLoginMode(app.getApplicationType() == null ? null : IamWebLoginMode.resolve(app.getApplicationType(), app.getWebLoginMode()) == null
            ? null : IamWebLoginMode.resolve(app.getApplicationType(), app.getWebLoginMode()).value());
        Map<String, Long> browser = browser(stored);
        result.setBrowserCapabilityBindings(browser);
        result.setBrowserProviderBindings(representative(browser));
        result.setBrowserLoginProviderTypes(result.getBrowserProviderBindings().keySet());
        result.setDirectLoginProviderType(app.getDirectLoginCapability() == null ? null : provider(app.getDirectLoginCapability()));
        Map<String, Long> external = new TreeMap<>();
        stored.sources().forEach(source -> {
            var capability = IdentityLoginCapability.fromValue(source.getLoginCapability());
            if (capability.code() != null) {
                external.put(capability.code().name(), source.getIdentityProviderId());
            }
        });
        result.setExternalProviderBindings(external);
        result.setExternalCodeTypes(Set.of());
        result.setEnabledProtocols(Set.of());
        var registration = protocols.registration(app, stored);
        if (registration != null && registration.assigned()) {
            require(registration.settings() instanceof OAuth2ApplicationProtocolSettings, "协议尚未提供此HTTP视图");
            var settings = (OAuth2ApplicationProtocolSettings) registration.settings();
            BeanUtils.copyProperties(settings.getOauth2(), result, "requireProofKey", "requireAuthorizationConsent", "reuseRefreshTokens");
            result.setRequireProofKey(Boolean.TRUE.equals(settings.getOauth2().getRequireProofKey()));
            result.setRequireAuthorizationConsent(Boolean.TRUE.equals(settings.getOauth2().getRequireAuthorizationConsent()));
            result.setReuseRefreshTokens(Boolean.TRUE.equals(settings.getOauth2().getReuseRefreshTokens()));
            result.setSettings(settings.getOauth2());
            result.setExternalCodeTypes(settings.getExternalCodeTypes().stream().map(Enum::name).collect(Collectors.toUnmodifiableSet()));
            if (!"RESOURCE_SERVER".equals(app.getPurpose())) {
                result.setProtocol(registration.protocol());
                result.setProtocolConfigurations(List.of(new ApplicationProtocolConfiguration(registration.protocol(), 1, registration.enabled(), settings.getOauth2())));
                result.setEnabledProtocols(registration.enabled() ? Set.of(registration.protocol()) : Set.of());
                result.setDefaultProtocol(registration.enabled() ? registration.protocol() : null);
            }
        } else if (stored.protocol() != null) {
            // 兼容展示已保留的旧Code许可；历史影子永远不投影成可执行Settings。
            var codeTypes = json.readTree(stored.protocol().getConfiguration()).get("external_code_types");
            result.setExternalCodeTypes(codeTypes == null || codeTypes.isNull() ? Set.of() : OAuth2ProtocolConfiguration.csv(codeTypes.asString()));
        }
        return result;
    }

    /** 用于受管初始化构造完整公开表单，不包含Secret或哈希。 */
    public IamApplicationForm form(IamApplicationVO view) {
        var result = new IamApplicationForm();
        BeanUtils.copyProperties(view, result);
        return result;
    }

    /** 根据唯一选择的OAuth Scope生成现有资源许可，不依据来源关系增加许可。 */
    public Map<String, String> resourcePermissions(IamApplicationProtocolRegistration<?> registration) {
        if (registration == null) {
            return Map.of();
        }
        require(registration.settings() instanceof OAuth2ApplicationProtocolSettings, "协议尚未提供资源许可映射");
        var settings = (OAuth2ApplicationProtocolSettings) registration.settings();
        return catalog.resourcesForScopes(OAuth2ProtocolConfiguration.csv(settings.getOauth2().getScopes()));
    }

    /** 关系表只投影明确浏览器能力，不能从厂商CSV补齐。 */
    private Map<String, Long> browser(IamApplicationProtocolService.Stored stored) {
        Map<String, Long> result = new TreeMap<>();
        stored.sources().forEach(source -> {
            if (IdentityLoginCapability.fromValue(source.getLoginCapability()).browser()) {
                require(result.putIfAbsent(source.getLoginCapability(), source.getIdentityProviderId()) == null, "重复网页登录能力");
            }
        });
        return result;
    }

    /** 旧厂商JSON仅为展示代表，不成为持久化第二权威。 */
    private Map<String, Long> representative(Map<String, Long> browser) {
        Map<String, Long> result = new TreeMap<>();
        new TreeMap<>(browser).forEach((capability, id) -> {
            require(IdentityLoginCapability.fromValue(capability).browser(), "网页登录能力无效");
            result.putIfAbsent(provider(capability), id);
        });
        return result;
    }

    /** 从固定能力合同取得厂商，不使用字符串前缀推断。 */
    private String provider(String capability) {
        return IdentityLoginCapability.fromValue(capability).source().providerType();
    }

    /** 复制明确映射，不能以null来源ID作为默认选择。 */
    private Map<String, Long> safe(Map<String, Long> values) {
        return values == null ? Map.of() : Map.copyOf(values);
    }

    /** 兼容输入冲突使用既有400状态，固定文案不包含设置片段。 */
    private void require(boolean condition, String reason) {
        if (!condition) {
            throw bad(reason);
        }
    }

    /** 构造有界的HTTP输入错误。 */
    private ResponseStatusException bad(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
