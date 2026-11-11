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
package dev.macula.cloud.iam.service.identity;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import dev.macula.cloud.iam.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.iam.pojo.entity.SysIdentityProvider;
import dev.macula.cloud.system.enums.ExternalCodeType;
import dev.macula.cloud.system.enums.IdentityBindingMapping;
import dev.macula.cloud.system.enums.IdentityBindingMappings;
import dev.macula.cloud.system.enums.IdentityBindingRule;
import dev.macula.cloud.system.enums.IdentityCredentialType;
import dev.macula.cloud.system.enums.IdentityDirectoryPolicy;
import dev.macula.cloud.system.enums.IdentityLoginCapability;
import dev.macula.cloud.system.enums.IdentitySourceFlow;
import dev.macula.cloud.system.enums.IdentitySourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 按Client固定registration读取当前配置，禁止按厂商全局择源与旧绑定回退。
 *
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class IdentityProviderConfigService {
    private final SysIdentityProviderMapper providerMapper;
    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * 从权威数据库读取确定来源；缺少新模型字段要求重新配置。
     */
    public ProviderConfig requireEnabledById(Long registrationId, String expectedProviderType) {
        if (registrationId == null) {
            throw unavailable();
        }
        return requireLoaded(providerMapper.selectById(registrationId), registrationId, expectedProviderType);
    }

    /**
     * 校验已经读取的权威行；绑定事务传入FOR UPDATE结果，避免RR快照回读过期策略。
     *
     * @param provider 来源持久化行
     * @param registrationId 固定来源ID
     * @param expectedProviderType 已核验厂商
     * @return 完整校验的当前配置
     */
    public ProviderConfig requireLoaded(SysIdentityProvider provider, Long registrationId,
            String expectedProviderType) {
        if (provider == null || !Objects.equals(provider.getId(), registrationId) ||
                !Boolean.TRUE.equals(provider.getEnabled())
                || !expectedProviderType.equals(provider.getProviderType()) || provider.getSourceType() == null) {
            throw unavailable();
        }
        IdentitySourceType type;
        Map<String, String> capabilities;
        String selectedCapability;
        IdentityBindingRule rule = null;
        IdentityBindingMapping mapping = null;
        List<IdentityBindingMapping> mappings;
        try {
            type = IdentitySourceType.fromValue(provider.getSourceType());
            if (provider.getCredentialType() != null &&
                    !IdentityCredentialType.forSource(type).value().equals(provider.getCredentialType())) {
                throw unavailable();
            }
            capabilities = IdentityLoginCapability.decode(provider.getCapabilitiesJson(), type,
                    provider.getIdentityFlow());
            var anchor = type;
            selectedCapability = Arrays.stream(IdentityLoginCapability.values())
                    .filter(item -> item.source() == anchor && capabilities.containsKey(item.value())).findFirst()
                    .or(() -> Arrays.stream(IdentityLoginCapability.values())
                            .filter(item -> capabilities.containsKey(item.value())).findFirst())
                    .orElseThrow(this::unavailable).value();
            type = IdentityLoginCapability.fromValue(selectedCapability).source();
            if (!type.providerType().equals(expectedProviderType)) {
                throw unavailable();
            }
            mappings = IdentityBindingMappings.decode(provider.getBindingMappingsJson(),
                    provider.getBindingSourceField(), provider.getBindingTargetField(), provider.getBindingRule());
            if (Boolean.TRUE.equals(provider.getAutoBindEnabled())) {
                if (mappings.isEmpty()) {
                    throw unavailable();
                }
                for (var item : mappings) {
                    for (String capability : capabilities.keySet()) {
                        item.validateFor(IdentityLoginCapability.fromValue(capability).source());
                    }
                }
                mapping = mappings.size() == 1 ? mappings.get(0) : null;
                rule = mapping == null || mapping.legacyValue() == null ? null :
                        IdentityBindingRule.fromValue(mapping.legacyValue());
            } else if (!mappings.isEmpty()) {
                throw unavailable();
            }
        } catch (IllegalArgumentException ex) {
            throw unavailable();
        }
        Map<String, String> values;
        try {
            values = StringUtils.hasText(provider.getConfigJson())
                    ? json.readValue(provider.getConfigJson(), new TypeReference<>() {
            }) : Map.of();
            if (StringUtils.hasText(provider.getSecretConfigJson())) {
                // 独立来源不读取旧的混合凭据；仅兼容加密空值和新写入的空对象。
                Object additional = json.readValue(provider.getSecretConfigJson(), Object.class);
                if (additional != null && (!(additional instanceof Map<?, ?> fields) || !fields.isEmpty())) {
                    throw unavailable();
                }
            }
        } catch (JacksonException ex) {
            throw unavailable();
        }
        var validationType = type;
        String credentialField = type.credentialField();
        if (values == null || values.keySet().stream().anyMatch(key -> credentialField.equals(key)
                || !validationType.requiredFields().contains(key))) {
            throw unavailable();
        }
        if (!StringUtils.hasText(provider.getAppSecret()) || !StringUtils.hasText(provider.getAppId())
                || !provider.getAppId().equals(values.get(type.identifierField()))
                || type.requiredFields().stream().filter(key -> !credentialField.equals(key))
                .anyMatch(key -> !StringUtils.hasText(values.get(key)))) {
            throw unavailable();
        }
        IdentitySourceFlow flow;
        IdentityDirectoryPolicy directoryPolicy;
        try {
            flow = IdentitySourceFlow.resolve(type, capabilities.get(selectedCapability));
            directoryPolicy = IdentityDirectoryPolicy.fromValue(
                    provider.getDirectoryPolicy());
            if (provider.getVersion() == null || provider.getVersion() < 0) {
                throw unavailable();
            }
            if (directoryPolicy != IdentityDirectoryPolicy.IDENTITY_ONLY
                    && (!Set.of("WECOM", "DINGTALK", "FEISHU").contains(type.providerType())
                    || !StringUtils.hasText(provider.getOrganizationId()))) {
                throw unavailable();
            }
        } catch (IllegalArgumentException ex) {
            throw unavailable();
        }
        if (type.providerType().equals("FEISHU") && Boolean.TRUE.equals(provider.getAutoBindEnabled())
                && !StringUtils.hasText(provider.getOrganizationId())) {
            throw unavailable();
        }
        if (StringUtils.hasText(provider.getOrganizationId()) && values.containsKey("corpId")
                && !provider.getOrganizationId().equals(values.get("corpId"))) {
            throw unavailable();
        }
        return new ProviderConfig(provider.getId(), type, Boolean.TRUE.equals(provider.getAutoBindEnabled()), rule,
                provider.getVersion(), provider.getProviderType(), provider.getAppId(), provider.getAppSecret(),
                Map.copyOf(values), Map.of(), Boolean.TRUE.equals(provider.getVisible()), flow, directoryPolicy,
                provider.getOrganizationId(), provider.getOauthRedirectUri(), mapping, capabilities, selectedCapability,
                mappings);
    }

    /**
     * 统一使用无敏感信息的配置错误。
     */
    private AuthenticationServiceException unavailable() {
        return new AuthenticationServiceException("身份源不可用或需重新配置");
    }

    /**
     * 已校验的具体来源配置，日志不输出密钥值。
     *
     * @since 6.1.0
     */
    public record ProviderConfig(Long registrationId, IdentitySourceType sourceType, boolean autoBindEnabled,
                                 IdentityBindingRule bindingRule, Long version, String providerType, String appId,
                                 String appSecret, Map<String, String> values, Map<String, String> secrets,
                                 boolean visible,
                                 IdentitySourceFlow flow,
                                 IdentityDirectoryPolicy directoryPolicy,
                                 String organizationId, String oauthRedirectUri, IdentityBindingMapping bindingMapping,
                                 Map<String, String> capabilities, String loginCapability,
                                 List<IdentityBindingMapping> bindingMappings) {
        /**
         * 配置视图必须持有完整不可变集合，不能被调用方截断或原地改写。
         */
        public ProviderConfig {
            bindingMappings = List.copyOf(bindingMappings);
            capabilities = Map.copyOf(capabilities);
            if (!Objects.equals(bindingMapping,
                    bindingMappings.size() == 1 ? bindingMappings.get(0) : null)) {
                throw new IllegalArgumentException("单条投影与绑定集合不一致");
            }
        }

        /**
         * 保留原完整构造合同，仅将真实旧单项包装为集合。
         */
        public ProviderConfig(Long registrationId, IdentitySourceType sourceType, boolean autoBindEnabled,
                IdentityBindingRule bindingRule, Long version, String providerType, String appId,
                String appSecret, Map<String, String> values, Map<String, String> secrets, boolean visible,
                IdentitySourceFlow flow,
                IdentityDirectoryPolicy directoryPolicy, String organizationId,
                String oauthRedirectUri, IdentityBindingMapping bindingMapping, Map<String, String> capabilities,
                String loginCapability) {
            this(registrationId, sourceType, autoBindEnabled, bindingRule, version, providerType, appId, appSecret,
                    values, secrets, visible, flow, directoryPolicy, organizationId, oauthRedirectUri, bindingMapping,
                    capabilities, loginCapability,
                    bindingMapping == null ? List.of() : List.of(bindingMapping));
        }

        /**
         * 保留原显式映射构造合同，旧来源仅拥有原来已有能力。
         */
        public ProviderConfig(Long registrationId, IdentitySourceType sourceType, boolean autoBindEnabled,
                IdentityBindingRule bindingRule, Long version, String providerType, String appId,
                String appSecret, Map<String, String> values, Map<String, String> secrets, boolean visible,
                IdentitySourceFlow flow,
                IdentityDirectoryPolicy directoryPolicy, String organizationId,
                String oauthRedirectUri, IdentityBindingMapping bindingMapping) {
            this(registrationId, sourceType, autoBindEnabled, bindingRule, version, providerType, appId, appSecret,
                    values, secrets, visible, flow, directoryPolicy, organizationId, oauthRedirectUri, bindingMapping,
                    IdentityLoginCapability.legacy(sourceType, flow == null ? "" : flow.value()),
                    IdentityLoginCapability.legacy(sourceType, flow == null ? "" : flow.value()).keySet().stream()
                            .findFirst().orElse(null));
        }

        /**
         * 旧构造合同仅转换三种已知规则，不让旧测试或适配器猜测新组合。
         */
        public ProviderConfig(Long registrationId, IdentitySourceType sourceType, boolean autoBindEnabled,
                IdentityBindingRule bindingRule, Long version, String providerType, String appId,
                String appSecret, Map<String, String> values, Map<String, String> secrets, boolean visible,
                IdentitySourceFlow flow,
                IdentityDirectoryPolicy directoryPolicy, String organizationId,
                String oauthRedirectUri) {
            this(registrationId, sourceType, autoBindEnabled, bindingRule, version, providerType, appId,
                    appSecret, values, secrets, visible, flow, directoryPolicy, organizationId, oauthRedirectUri,
                    IdentityBindingMapping.legacy(bindingRule == null ? null : bindingRule.value()));
        }

        /**
         * 能力视图只复用同一真实注册凭据，拒绝停用或跨注册类型能力。
         */
        public ProviderConfig forCapability(String value) {
            try {
                var capability = IdentityLoginCapability.fromValue(value);
                if (!capabilities.containsKey(value) ||
                        capability.credentialType() != IdentityCredentialType.forSource(sourceType)) {
                    throw new IllegalArgumentException("能力不可用");
                }
                var contract = IdentitySourceFlow.resolve(capability.source(),
                        capabilities.get(value));
                for (var mapping : bindingMappings) {
                    mapping.validateFor(capability.source());
                }
                return new ProviderConfig(registrationId, capability.source(), autoBindEnabled, bindingRule, version,
                        providerType, appId, appSecret, values, secrets, visible, contract, directoryPolicy,
                        organizationId, oauthRedirectUri, bindingMapping, capabilities, value, bindingMappings);
            } catch (IllegalArgumentException ex) {
                throw new AuthenticationServiceException("身份源未启用该登录能力");
            }
        }

        /**
         * Code与来源能力一一对应，不能因共享Secret而互换Code。
         */
        public ProviderConfig forCode(ExternalCodeType code) {
            return forCapability(IdentityLoginCapability.forCode(code).value());
        }

        /**
         * 旧入口仍由模式明确选择能力，不能任取同厂商其他注册。
         */
        public ProviderConfig forBrowser(String mode) {
            return forCapability(IdentityLoginCapability.browser(sourceType, mode).value());
        }

        /**
         * 读取必填公开字段。
         */
        public String require(String key) {
            String value = values.get(key);
            if (!StringUtils.hasText(value)) {
                throw new AuthenticationServiceException("身份源配置不完整");
            }
            return value;
        }

        /**
         * 读取独立来源凭据；不借用同厂商其他应用的secret。
         */
        public String requireSecret(String key) {
            String value = secrets.get(key);
            if (!StringUtils.hasText(value)) {
                throw new AuthenticationServiceException("身份源配置不完整");
            }
            return value;
        }

        /**
         * 避免record默认实现输出密钥。
         */
        @Override
        public String toString() {
            return "ProviderConfig[registrationId=" + registrationId + ",sourceType=" + sourceType + "]";
        }
    }
}
