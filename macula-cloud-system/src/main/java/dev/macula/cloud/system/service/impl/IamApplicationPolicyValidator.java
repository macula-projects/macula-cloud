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

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import dev.macula.cloud.system.enums.HostedLoginMethod;
import dev.macula.cloud.system.enums.IamApplicationPurpose;
import dev.macula.cloud.system.enums.IamApplicationStatus;
import dev.macula.cloud.system.enums.IamApplicationType;
import dev.macula.cloud.system.enums.IamWebLoginMode;
import dev.macula.cloud.system.enums.IdentityCredentialType;
import dev.macula.cloud.system.enums.IdentityLoginCapability;
import dev.macula.cloud.system.enums.IdentitySourceFlow;
import dev.macula.cloud.system.enums.IdentitySourceType;
import dev.macula.cloud.system.form.IamApplicationForm;
import dev.macula.cloud.system.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.system.mapper.SysTenantInfoMapper;
import dev.macula.cloud.system.pojo.entity.SysIamApplication;
import dev.macula.cloud.system.pojo.entity.SysIdentityProvider;
import dev.macula.cloud.system.security.IamApplicationLoginPolicy;
import dev.macula.cloud.system.security.OAuth2ResourcePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * 公共应用元数据及登录来源规则，不读写协议设置或客户端Secret。
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class IamApplicationPolicyValidator {
    private static final Set<String> USERS = Set.of("member", "employee", "odc", "supplier");
    private final SysTenantInfoMapper tenants;
    private final SysIdentityProviderMapper identityProviders;

    /** 固定创建时已确认的架构/网页登录模式；草稿同样不能切换。 */
    public void metadata(IamApplicationForm form, SysIamApplication existing) {
        IamApplicationType type;
        IamApplicationPurpose purpose;
        boolean enabled;
        try {
            type = IamApplicationType.valueOf(form.getApplicationType());
            purpose = IamApplicationPurpose.valueOf(form.getPurpose() == null ? "APPLICATION" : form.getPurpose());
            enabled = IamApplicationStatus.fromValue(form.getConfigurationStatus()) == IamApplicationStatus.ENABLED;
        } catch (IllegalArgumentException | NullPointerException error) {
            throw bad("应用用途、状态或架构类型无效");
        }
        require(StringUtils.hasText(form.getClientId()) && form.getClientId().length() <= 100
            && StringUtils.hasText(form.getClientName()) && form.getClientName().length() <= 200, "应用名称或代码无效");
        if (existing.getId() != null) {
            require(Objects.equals(existing.getApplicationCode(), form.getClientId()), "clientId创建后不可修改");
            require(existing.getPurpose() == null || purpose.name().equals(existing.getPurpose()), "应用用途创建后不可修改");
            require(existing.getApplicationType() == null || type.name().equals(existing.getApplicationType())
                && Objects.equals(existing.getMiniappPlatform(), form.getMiniappPlatform()), "应用架构及小程序平台创建后不可修改");
        }
        if (StringUtils.hasText(existing.getManagedKey())) {
            require(enabled, "平台内置身份不可停用");
        }
        IamWebLoginMode mode;
        try {
            mode = IamWebLoginMode.resolve(type.name(), form.isWebLoginModePresent() ? form.getWebLoginMode() : existing.getWebLoginMode());
            if (existing.getId() != null && existing.getApplicationType() != null) {
                require(mode == IamWebLoginMode.resolve(existing.getApplicationType(), existing.getWebLoginMode()), "登录模式创建后不可修改，请新建应用");
            }
        } catch (IllegalArgumentException error) {
            throw bad("应用类型或网页登录模式无效");
        }
        require(purpose != IamApplicationPurpose.RESOURCE_SERVER || mode == null, "资源服务器不支持网页登录模式");
        require(!OAuth2ResourcePolicy.ADMIN_MANAGED_KEY.equals(existing.getManagedKey()) || mode == IamWebLoginMode.BROWSER, "平台Admin必须使用浏览器模式");
        form.setPurpose(purpose.name());
        form.setWebLoginMode(mode == null ? null : mode.value());
        require(type == IamApplicationType.MINIAPP ? Set.of("WECHAT", "WECOM").contains(form.getMiniappPlatform() == null ? "" : form.getMiniappPlatform())
            : !StringUtils.hasText(form.getMiniappPlatform()), "小程序平台无效");
        if (form.getHostedLoginMethods() != null) {
            require(Arrays.stream(HostedLoginMethod.values()).map(Enum::name).collect(Collectors.toSet()).containsAll(form.getHostedLoginMethods()), "托管登录方式无效");
        }
        if (purpose != IamApplicationPurpose.RESOURCE_SERVER) {
            require(form.getTenantId() == null || tenants.selectById(form.getTenantId()) != null, "租户不存在");
            require(!StringUtils.hasText(form.getUserType()) || USERS.contains(form.getUserType()), "用户类型无效");
            require(!enabled || form.getTenantId() != null && (type == IamApplicationType.SERVICE || USERS.contains(form.getUserType() == null ? "" : form.getUserType())), "启用前请选择认证租户和用户类型");
        }
    }

    /** 兼容旧厂商输入时只在该具体来源/模式的能力中唯一选择，禁止全局择源。 */
    public String legacyBrowserCapability(String provider, Long sourceId, IamApplicationForm form) {
        SysIdentityProvider source = identityProviders.selectById(sourceId);
        require(source != null && provider.equals(source.getProviderType()), "网页登录关联的具体身份源无效");
        var type = IamApplicationType.valueOf(form.getApplicationType());
        Map<String, String> declared = capabilities(source);
        var candidates = Arrays.stream(IdentityLoginCapability.values()).filter(IdentityLoginCapability::browser)
            .filter(capability -> capability.source().providerType().equals(provider))
            .filter(capability -> type.supportsBrowserCapability(capability, form.getWebLoginMode()))
            .filter(capability -> declared.containsKey(capability.value())).toList();
        require(candidates.size() == 1, "网页登录来源能力存在歧义，请使用新版页面");
        return candidates.get(0).value();
    }

    /** 数量、架构、凭据域和固定flow均以服务器来源为准；来源关系不隐含协议授予。 */
    public void login(IamApplicationForm form, Map<String, Long> routes) {
        var type = IamApplicationType.valueOf(form.getApplicationType());
        Map<String, Long> browser = new TreeMap<>();
        Map<String, Long> external = new TreeMap<>();
        Map<Long, SysIdentityProvider> locked = new TreeMap<>();
        for (Long id : new TreeSet<>(routes.values())) {
            require(id != null && id > 0, "必须选择明确身份源");
            locked.put(id, identityProviders.selectByIdForUpdate(id));
        }
        for (var route : new TreeMap<>(routes).entrySet()) {
            IdentityLoginCapability capability;
            try {
                capability = IdentityLoginCapability.fromValue(route.getKey());
            } catch (IllegalArgumentException error) {
                throw bad("登录能力无效");
            }
            require(route.getValue() != null && route.getValue() > 0, "必须选择明确身份源");
            SysIdentityProvider source = locked.get(route.getValue());
            require(source != null && Boolean.TRUE.equals(source.getEnabled())
                && capability.source().providerType().equals(source.getProviderType())
                && StringUtils.hasText(source.getAppId()) && StringUtils.hasText(source.getAppSecret()), "固定身份源不可用");
            var declared = capabilities(source);
            require(declared.containsKey(capability.value()), "身份源未开放所选能力");
            try {
                var credential = source.getCredentialType() == null ? IdentityCredentialType.forSource(IdentitySourceType.fromValue(source.getSourceType()))
                    : IdentityCredentialType.fromValue(source.getCredentialType());
                require(credential == capability.credentialType(), "身份源凭据类型不兼容");
                IdentitySourceFlow.resolve(capability.source(), declared.get(capability.value()));
            } catch (IllegalArgumentException error) {
                throw bad("登录能力或兑换合同不可用");
            }
            if (capability.browser()) {
                browser.put(capability.value(), route.getValue());
            } else {
                require(capability.code() != null, "该能力不提供登录");
                external.put(capability.code().name(), route.getValue());
            }
        }
        if ("RESOURCE_SERVER".equals(form.getPurpose())) {
            require(routes.isEmpty() && !StringUtils.hasText(form.getDirectLoginProviderType())
                && (form.getHostedLoginMethods() == null || form.getHostedLoginMethods().isEmpty()), "资源身份不能配置用户登录");
            return;
        }
        try {
            IamApplicationLoginPolicy.validate(type, form.getMiniappPlatform(), form.getWebLoginMode(), browser, external, "ENABLED".equals(form.getConfigurationStatus()));
        } catch (IllegalArgumentException error) {
            throw bad("登录来源与架构或模式不兼容");
        }
        require(external.keySet().equals(form.getExternalCodeTypes() == null ? Set.of() : form.getExternalCodeTypes()), "Code许可与来源关联不一致");
        if (IamWebLoginMode.isInApp(type.name(), form.getWebLoginMode())) {
            require(form.getHostedLoginMethods() != null && form.getHostedLoginMethods().isEmpty()
                && !StringUtils.hasText(form.getDirectLoginProviderType()), "端内模式必须关闭内置登录及普通直登");
        }
        require(type.supportsHostedLogin(form.getWebLoginMode()) || form.getHostedLoginMethods() == null
            || form.getHostedLoginMethods().isEmpty(), "当前应用不支持内置登录");
    }

    /** 来源能力解析失败不向HTTP透传JSON片段或秘密异常。 */
    private Map<String, String> capabilities(SysIdentityProvider source) {
        try {
            return IdentityLoginCapability.decode(source.getCapabilitiesJson(), IdentitySourceType.fromValue(source.getSourceType()), source.getIdentityFlow());
        } catch (RuntimeException error) {
            throw bad("身份源能力配置无效");
        }
    }

    /** 统一使用既有400输入错误，固定消息不包含秘密。 */
    private void require(boolean condition, String message) {
        if (!condition) {
            throw bad(message);
        }
    }

    /** 构造可公开的有界输入错误。 */
    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
