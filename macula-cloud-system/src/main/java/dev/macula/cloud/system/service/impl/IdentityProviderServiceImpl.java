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
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.macula.cloud.system.converter.IdentityProviderConverter;
import dev.macula.cloud.system.enums.IdentityProviderCategory;
import dev.macula.cloud.system.enums.IdentityProviderType;
import dev.macula.cloud.system.enums.IdentitySourceType;
import dev.macula.cloud.system.enums.IdentityCredentialType;
import dev.macula.cloud.system.enums.IdentityLoginCapability;
import dev.macula.cloud.system.enums.IdentityBindingMapping;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.Objects;
import java.util.stream.Collectors;
import dev.macula.cloud.system.mapper.SysOAuth2ClientConfigMapper;
import dev.macula.cloud.system.mapper.SysOAuth2ClientExternalProviderMapper;
import dev.macula.cloud.system.pojo.entity.SysOAuth2ClientConfig;
import dev.macula.cloud.system.pojo.entity.SysOAuth2ClientExternalProvider;
import dev.macula.cloud.system.form.IdentityProviderForm;
import dev.macula.cloud.system.mapper.SysIdentityProviderMapper;
import dev.macula.cloud.system.pojo.entity.SysIdentityProvider;
import dev.macula.cloud.system.service.IdentityProviderService;
import dev.macula.cloud.system.vo.IdentityProviderCatalogVO;
import dev.macula.cloud.system.vo.IdentityProviderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.net.URI;

/**
 * 身份提供商配置实现；仅管理配置，凭据与可回显 JSON 分离。
 *
 * @author Rain
 * @since 2026-10-13
 */
@Service
@RequiredArgsConstructor
public class IdentityProviderServiceImpl implements IdentityProviderService {
    private static final String EXTERNAL_REDIRECT_URI = "externalRedirectUri";

    private final SysIdentityProviderMapper mapper;
    private final IdentityProviderConverter converter;
    private final SysOAuth2ClientConfigMapper clients;
    private final SysOAuth2ClientExternalProviderMapper clientSources;
    private final JsonMapper json = JsonMapper.builder().build();

    @Override
    public IdentityProviderCatalogVO catalog() {
        List<IdentityProviderCatalogVO.Category> categories = Arrays.stream(IdentityProviderCategory.values())
            .map(category -> new IdentityProviderCatalogVO.Category(category.code(), category.label()))
            .toList();
        List<IdentityProviderCatalogVO.Provider> providers = Arrays.stream(IdentityProviderType.values())
            .map(type -> new IdentityProviderCatalogVO.Provider(type.code(), type.label(),
                type.category().code(), type.callbackUrl(), type.createUrl(), type.createHint(), type.fields().stream()
                    .map(field -> new IdentityProviderCatalogVO.Field(field.getKey(), field.getLabel(),
                        field.isRequired(), field.isSecret(), field.isMultiline(), field.getHelp())).toList()))
            .toList();
        List<IdentityProviderCatalogVO.Source> sources = Arrays.stream(IdentitySourceType.values()).map(source -> {
            IdentityProviderType vendor = IdentityProviderType.fromCode(source.providerType());
            var descriptor = new IdentityProviderCatalogVO.Source(source.value(), source.label(), source.providerType(),
                source.scene(), vendor.sourceFields(source).stream().map(field -> new IdentityProviderCatalogVO.Field(
                    field.getKey(), field.getLabel(), field.isRequired(), field.isSecret(), field.isMultiline(),
                    field.getHelp())).toList(), source.identityFields(), source.bindingRules().stream().map(rule ->
                    new IdentityProviderCatalogVO.BindingRule(rule.value(), rule.label(), rule.prerequisite())).toList(),
                source.bindingRules().isEmpty() ? "当前适配器没有可核验的本地账号匹配属性，首次登录需手动绑定。" : null);
            descriptor.setBindingMappings(source.bindingSourceFields().stream().flatMap(field ->
                IdentityBindingMapping.targets(field).stream().filter(target -> source.enterprise()
                    || target == dev.macula.cloud.system.enums.IdentityBindingTargetField.MOBILE)
                    .map(target -> new IdentityProviderCatalogVO.BindingMapping(field.value(), field.label(),
                        target.value(), target.label(), "需要该应用的成员及所选字段读取权限"))).toList());
            descriptor.setDefaultAvailable("CONFIGURATION".equals(source.scene()) || Arrays.stream(
                dev.macula.cloud.system.enums.IdentitySourceFlow.values()).filter(item -> item.supports(source) && item.implemented()).count() == 1);
            descriptor.setUnavailableReason(descriptor.isDefaultAvailable() ? null : "该接入方式的客户端合同尚待核实，暂不可启用");
            descriptor.setFlows(Arrays.stream(dev.macula.cloud.system.enums.IdentitySourceFlow.values())
                .filter(flowItem -> flowItem.supports(source)).map(flowItem -> new IdentityProviderCatalogVO.Flow(
                    flowItem.value(), flowItem.value(), flowItem.implemented(), flowItem.providerPkce(),
                    flowItem.implemented() ? null : "该客户端flow官方合同尚待核实")).toList());
            descriptor.setDirectoryPolicies(Set.of("WECOM", "FEISHU", "DINGTALK").contains(source.providerType())
                ? Arrays.stream(dev.macula.cloud.system.enums.IdentityDirectoryPolicy.values()).map(dev.macula.cloud.system.enums.IdentityDirectoryPolicy::value).collect(Collectors.toSet())
                : Set.of(dev.macula.cloud.system.enums.IdentityDirectoryPolicy.IDENTITY_ONLY.value()));
            return descriptor;
        }).toList();
        var catalog = new IdentityProviderCatalogVO(categories, providers, sources);
        catalog.setCredentials(Arrays.stream(IdentityCredentialType.values()).map(credential -> {
            var anchor = credential.anchor();
            var fields = sources.stream().filter(item -> item.getCode().equals(anchor.value())).findFirst().orElseThrow().getFields();
            var capabilities = Arrays.stream(IdentityLoginCapability.values()).filter(item -> item.credentialType() == credential)
                .map(item -> new IdentityProviderCatalogVO.LoginCapability(item.value(), item.label(), item.source().scene(),
                    item.browser(), item.code() == null ? null : item.code().name(), item.mode(),
                    item.environments().stream().map(Enum::name).collect(Collectors.toSet()), item.available(),
                    item.available() ? null : "该登录能力尚无已核实的接口合同")) .toList();
            return new IdentityProviderCatalogVO.Credential(credential.value(), credential.label(), anchor.providerType(), anchor.value(), fields, capabilities);
        }).toList());
        return catalog;
    }

    @Override
    public IPage<IdentityProviderVO> page(int pageNum, int pageSize, String name, String providerType,
        String category) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            bad("分页参数无效");
        }
        IdentityProviderCategory categoryType = StringUtils.hasText(category) ? category(category) : null;
        IdentityProviderType type = StringUtils.hasText(providerType) ? providerType(providerType) : null;
        if (type != null && categoryType != null && categoryType != type.category()) {
            bad("认证源类型与分类不匹配");
        }
        LambdaQueryWrapper<SysIdentityProvider> query = new LambdaQueryWrapper<SysIdentityProvider>()
            .like(StringUtils.hasText(name), SysIdentityProvider::getName, name)
            .eq(type != null, SysIdentityProvider::getProviderType, providerType)
            .orderByDesc(SysIdentityProvider::getId);
        if (categoryType != null) {
            Set<String> types = IdentityProviderType.typesIn(categoryType);
            query.in(SysIdentityProvider::getProviderType, types);
        }
        return mapper.selectPage(new Page<>(pageNum, pageSize), query).convert(this::toVO);
    }

    @Override
    public IdentityProviderVO get(Long id) {
        SysIdentityProvider entity = mapper.selectById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "身份提供商不存在");
        }
        return toVO(entity);
    }

    /**
     * 保存具体来源配置；空附加密钥使用JSON空对象，避免加密拦截器将null字符串化。
     * @param id 新建时为空，更新时为现有来源ID
     * @param form 已校验的来源配置
     * @return 不含密钥原文的配置
     */
    @Override
    @Transactional
    public IdentityProviderVO save(Long id, IdentityProviderForm form) {
        IdentityProviderType type = providerType(form.getProviderType());
        SysIdentityProvider entity = id == null ? new SysIdentityProvider() : mapper.selectById(id);
        IdentitySourceType source;
        try {
            source = sourceType(form.getSourceType() != null ? form.getSourceType()
                : entity != null && entity.getSourceType() != null ? entity.getSourceType()
                : IdentityCredentialType.fromValue(form.getCredentialType()).anchor().value());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择真实应用类型");
        }
        IdentityCredentialType credentialType = IdentityCredentialType.forSource(source);
        if (!source.providerType().equals(type.code()) || form.getCredentialType() != null
            && !credentialType.value().equals(form.getCredentialType())) {
            bad("厂商、应用注册与凭据不匹配");
        }
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "身份提供商不存在");
        }
        if (id != null && !type.code().equals(entity.getProviderType())) {
            bad("认证源类型不可修改");
        }
        if (id != null && entity.getSourceType() != null && !source.value().equals(entity.getSourceType())) {
            bad("具体身份源类型不可修改，请新建来源");
        }
        if (id != null && !Objects.equals(entity.getVersion(), form.getVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "身份源已修改，请刷新后重试");
        }
        if (id != null && form.getAutoBindEnabled() == null) { form.setAutoBindEnabled(entity.getAutoBindEnabled()); }
        var mappings = bindingMappings(form, entity, source, id != null);
        IdentityBindingMapping mapping = mappings.size() == 1 ? mappings.get(0) : null;
        // 隐藏界面字段不等于重置历史合同；来源和组织信息仍来自服务端固定配置。
        if (id != null) {
            if (form.getIdentityFlow() == null) { form.setIdentityFlow(entity.getIdentityFlow()); }
            if (form.getDirectoryPolicy() == null) { form.setDirectoryPolicy(entity.getDirectoryPolicy()); }
            if (form.getOrganizationId() == null) { form.setOrganizationId(entity.getOrganizationId()); }
            if (form.getOauthRedirectUri() == null) { form.setOauthRedirectUri(entity.getOauthRedirectUri()); }
        } else if (source.enterprise() && form.getDirectoryPolicy() == null) {
            form.setDirectoryPolicy(dev.macula.cloud.system.enums.IdentityDirectoryPolicy.AUTO.value());
        }
        if (!StringUtils.hasText(form.getOrganizationId()) && source.enterprise() && form.getConfig() != null
            && form.getConfig().get("corpId") instanceof String corpId) { form.setOrganizationId(corpId.trim()); }
        dev.macula.cloud.system.enums.IdentitySourceFlow flow = null;
        dev.macula.cloud.system.enums.IdentityDirectoryPolicy directoryPolicy;
        try {
            if (!"CONFIGURATION".equals(source.scene())) {
                if (Boolean.TRUE.equals(form.getEnabled()) || StringUtils.hasText(form.getIdentityFlow())
                    || !source.providerType().equals("FEISHU")) {
                    flow = form.getIdentityFlow() == null && Boolean.TRUE.equals(form.getEnabled())
                        ? dev.macula.cloud.system.enums.IdentitySourceFlow.defaultFor(source)
                        : Boolean.TRUE.equals(form.getEnabled())
                            ? dev.macula.cloud.system.enums.IdentitySourceFlow.resolve(source, form.getIdentityFlow())
                            : dev.macula.cloud.system.enums.IdentitySourceFlow.declared(source, form.getIdentityFlow());
                }
            }
            directoryPolicy = dev.macula.cloud.system.enums.IdentityDirectoryPolicy.fromValue(form.getDirectoryPolicy());
            if (Boolean.TRUE.equals(form.getEnabled()) && "FEISHU".equals(source.providerType())
                && Boolean.TRUE.equals(form.getAutoBindEnabled()) && !StringUtils.hasText(form.getOrganizationId())) {
                bad("飞书员工编号自动绑定必须配置固定tenant_key，避免跨企业编号相同而误绑定");
            }
            if (directoryPolicy != dev.macula.cloud.system.enums.IdentityDirectoryPolicy.IDENTITY_ONLY
                && (!Set.of("WECOM", "FEISHU", "DINGTALK").contains(source.providerType())
                    || !StringUtils.hasText(form.getOrganizationId()))) {
                bad("通讯录策略需要适用的企业来源及明确组织标识");
            }
        } catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage()); }
        Map<String, String> capabilities = capabilitySettings(form, entity, source, flow, id != null);
        if (Boolean.TRUE.equals(form.getEnabled())) {
            for (String capability : capabilities.keySet()) {
                var selected = IdentityLoginCapability.fromValue(capability);
                if (!"CONFIGURATION".equals(selected.source().scene())) {
                    var contract = dev.macula.cloud.system.enums.IdentitySourceFlow.resolve(selected.source(), capabilities.get(capability));

                }
                for (var item : mappings) { item.validateFor(selected.source()); }
            }
        }
        List<IdentityProviderType.Field> schema = type.sourceFields(source);
        Set<String> allowed = schema.stream().map(IdentityProviderType.Field::getKey).collect(Collectors.toSet());
        Set<String> secretFields = schema.stream().filter(IdentityProviderType.Field::isSecret)
            .map(IdentityProviderType.Field::getKey).collect(Collectors.toSet());
        if (!type.category().code().equals(form.getCategory())) {
            bad("认证源类型与分类不匹配");
        }
        if (!StringUtils.hasText(form.getName()) || form.getName().length() > 100) {
            bad("认证源名称无效");
        }
        if (form.getConfig() == null) {
            bad("配置必须为 JSON 对象");
        }

        Map<String, String> originalConfig = id == null || entity.getSourceType() == null
            ? Map.of() : readPublicConfig(entity, type);
        Long originalVersion = entity.getVersion();

        Map<String, String> publicConfig = new LinkedHashMap<>();
        Map<String, String> additionalSecrets = new LinkedHashMap<>();
        String credential = null;
        for (Map.Entry<String, Object> entry : form.getConfig().entrySet()) {
            if (!allowed.contains(entry.getKey()) || !(entry.getValue() instanceof String)) {
                bad("配置包含未知字段或字段类型无效");
            }
            String value = (String) entry.getValue();
            int maxLength = type.secretFields().contains(entry.getKey()) ? 32000
                : entry.getKey().equals(type.identifierField()) ? 255 : 16000;
            if (value.length() > maxLength) {
                bad("配置字段过长：" + entry.getKey());
            }
            if (entry.getKey().equals(type.credentialField())) {
                credential = value;
            } else if (type.additionalSecretFields().contains(entry.getKey())) {
                if (StringUtils.hasText(value)) {
                    additionalSecrets.put(entry.getKey(), value);
                }
            } else if (StringUtils.hasText(value)) {
                String normalized = value.trim();
                if (EXTERNAL_REDIRECT_URI.equals(entry.getKey())) {
                    normalized = normalizeExternalRedirectUri(normalized);
                }
                publicConfig.put(entry.getKey(), normalized);
            }
        }
        for (String required : source.requiredFields().stream().filter(key -> !secretFields.contains(key)).toList()) {
            if (!StringUtils.hasText(publicConfig.get(required))) {
                bad("缺少必需配置：" + required);
            }
        }
        if (!StringUtils.hasText(credential) && (!StringUtils.hasText(entity.getAppSecret())
            || id != null && entity.getSourceType() == null)) {
            bad("缺少必需配置：" + type.credentialField());
        }
        for (String requiredSecret : secretFields) {
            if (!requiredSecret.equals(type.credentialField()) &&
                !StringUtils.hasText(additionalSecrets.get(requiredSecret))) {
                bad("缺少必需配置：" + requiredSecret);
            }
        }
        long duplicates = mapper.selectCount(new LambdaQueryWrapper<SysIdentityProvider>()
            .eq(SysIdentityProvider::getName, form.getName().trim())
            .ne(id != null, SysIdentityProvider::getId, id));
        if (duplicates > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "名称已存在");
        }
        // 身份域不可替换：同一registration的既有绑定不能被改成另一企业/应用。
        if (id != null && entity.getSourceType() != null) {
            for (String key : source.identityFields()) {
                if (!secretFields.contains(key) && !Objects.equals(originalConfig.get(key), publicConfig.get(key))) {
                    bad("身份域标识不可修改，请新建来源：" + key);
                }
            }
        }

        if (id != null && StringUtils.hasText(entity.getOrganizationId())
            && !Objects.equals(entity.getOrganizationId(), form.getOrganizationId())) { bad("组织身份域不可替换，请新建来源"); }
        if (StringUtils.hasText(form.getOrganizationId()) && publicConfig.containsKey("corpId")
            && !form.getOrganizationId().equals(publicConfig.get("corpId"))) { bad("组织标识与具体企业凭据不一致"); }
        entity.setIdentityFlow(flow == null ? form.getIdentityFlow() : flow.value());
        entity.setDirectoryPolicy(directoryPolicy.value());
        entity.setOrganizationId(StringUtils.hasText(form.getOrganizationId()) ? form.getOrganizationId() : null);
        entity.setOauthRedirectUri(StringUtils.hasText(form.getOauthRedirectUri()) ? form.getOauthRedirectUri() : null);
        converter.updateEntity(form, entity);
        entity.setSourceType(source.value());
        entity.setCredentialType(credentialType.value());
        entity.setCapabilitiesJson(IdentityLoginCapability.encode(capabilities));
        entity.setAutoBindEnabled(Boolean.TRUE.equals(form.getAutoBindEnabled()));
        entity.setBindingMappingsJson(dev.macula.cloud.system.enums.IdentityBindingMappings.encode(mappings));
        entity.setBindingRule(mapping == null ? null : mapping.legacyValue());
        entity.setBindingSourceField(mapping == null ? null : mapping.source().value());
        entity.setBindingTargetField(mapping == null ? null : mapping.target().value());
        entity.setVersion(originalVersion == null ? 0L : originalVersion + 1);
        entity.setName(form.getName().trim());
        entity.setEnabled(Boolean.TRUE.equals(form.getEnabled()));
        entity.setVisible(Boolean.TRUE.equals(form.getVisible()));
        entity.setAppId(publicConfig.get(type.identifierField()));
        entity.setConfigJson(json.writeValueAsString(publicConfig));
        if (StringUtils.hasText(credential)) {
            entity.setAppSecret(credential);
        }
        // 加密字段必须有明确JSON值；不让空Java引用进入String.valueOf(null)的持久化路径。
        entity.setSecretConfigJson(json.writeValueAsString(additionalSecrets));
        try {
            if (id == null) {
                mapper.insert(entity);
            } else {
                if (mapper.update(entity, new LambdaUpdateWrapper<SysIdentityProvider>()
                    .eq(SysIdentityProvider::getId, id).eq(SysIdentityProvider::getVersion, originalVersion)) != 1) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "身份源已修改，请刷新后重试");
                }
            }
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "身份提供商配置冲突", ex);
        }
        return toVO(entity, additionalSecrets);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        long references = clientSources.selectCount(new LambdaQueryWrapper<SysOAuth2ClientExternalProvider>()
            .eq(SysOAuth2ClientExternalProvider::getIdentityProviderId, id));
        references += clients.selectCount(new LambdaQueryWrapper<SysOAuth2ClientConfig>()
            .isNotNull(SysOAuth2ClientConfig::getBrowserProviderBindings)
            .apply("JSON_CONTAINS(JSON_EXTRACT(browser_provider_bindings, '$.*'), CAST({0} AS JSON))", id.toString()));
        references += clients.selectCount(new LambdaQueryWrapper<SysOAuth2ClientConfig>()
            .isNotNull(SysOAuth2ClientConfig::getBrowserCapabilityBindings)
            .apply("JSON_CONTAINS(JSON_EXTRACT(browser_capability_bindings, '$.*'), CAST({0} AS JSON))", id.toString()));
        if (references > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先解除应用的身份源关联");
        }
        if (mapper.deleteById(id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "身份提供商不存在");
        }
    }

    /** 解析必填具体来源，缺失不推断。 */
    private IdentitySourceType sourceType(String value) {
        try {
            return IdentitySourceType.fromValue(value);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    /**
     * 合并新旧写入合同；省略不清除，半组/冲突或旧客户端无法表达的新映射均拒绝。
     * @param form 本次写入及字段出现标记
     * @param entity 原持久化配置
     * @param source 明确的来源类型
     * @param updating 是否更新
     * @return 本次完整映射集合，关闭自动绑定时为空集合
     */
    private java.util.List<IdentityBindingMapping> bindingMappings(IdentityProviderForm form, SysIdentityProvider entity,
        IdentitySourceType source, boolean updating) {
        try {
            var previous = updating ? dev.macula.cloud.system.enums.IdentityBindingMappings.decode(entity.getBindingMappingsJson(),
                entity.getBindingSourceField(), entity.getBindingTargetField(), entity.getBindingRule()) : java.util.List.<IdentityBindingMapping>of();
            boolean explicit = form.isBindingSourceFieldPresent() || form.isBindingTargetFieldPresent();
            boolean legacy = explicit || form.isBindingRulePresent();
            if (form.isBindingSourceFieldPresent() != form.isBindingTargetFieldPresent()) {
                throw new IllegalArgumentException("源字段和IAM字段必须一同提交");
            }
            if (explicit && form.getBindingSourceField() == null && form.getBindingTargetField() == null
                && form.getBindingRule() != null) { throw new IllegalArgumentException("新旧账号绑定规则不一致"); }
            var single = legacy ? IdentityBindingMapping.resolve(form.getBindingSourceField(), form.getBindingTargetField(), form.getBindingRule()) : null;
            java.util.List<IdentityBindingMapping> mappings;
            if (form.isBindingMappingsPresent()) {
                mappings = dev.macula.cloud.system.enums.IdentityBindingMappings.fromWire(form.getBindingMappings());
                if (legacy && (single == null ? !mappings.isEmpty() : mappings.size() != 1 || !single.equals(mappings.get(0)))) {
                    throw new IllegalArgumentException("新旧账号绑定规则不一致");
                }
            } else if (legacy) {
                if (updating && previous.size() > 1) { throw new IllegalArgumentException("旧客户端无法修改多条映射，请使用新版管理端"); }
                if (form.isBindingRulePresent() && !explicit && !previous.isEmpty() && previous.get(0).legacyValue() == null
                    && Boolean.TRUE.equals(form.getAutoBindEnabled())) {
                    throw new IllegalArgumentException("旧客户端无法修改此映射，请使用新版管理端");
                }
                mappings = single == null ? java.util.List.of() : java.util.List.of(single);
            } else { mappings = previous; }
            if (!Boolean.TRUE.equals(form.getAutoBindEnabled())) {
                if (!mappings.isEmpty() && (legacy || form.isBindingMappingsPresent())) {
                    throw new IllegalArgumentException("关闭自动绑定时请清空字段映射");
                }
                return java.util.List.of();
            }
            if (mappings.isEmpty()) { throw new IllegalArgumentException("请选择身份源和IAM匹配字段"); }
            for (var mapping : mappings) { mapping.validateFor(source); }
            return mappings;
        } catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage()); }
    }

    /** 明确多选能力的固定合同；旧PUT和旧显式flow都不被默认值重置。 */
    private Map<String, String> capabilitySettings(IdentityProviderForm form, SysIdentityProvider entity,
        IdentitySourceType source, dev.macula.cloud.system.enums.IdentitySourceFlow flow, boolean existing) {
        try {
            var old = existing ? IdentityLoginCapability.decode(entity.getCapabilitiesJson(), source, entity.getIdentityFlow())
                : Map.<String, String>of();
            if (!form.isLoginCapabilitiesPresent() && existing) { return old; }
            if (form.isLoginCapabilitiesPresent() && form.getLoginCapabilities() == null) { bad("能力必须为集合，关闭全部请提交空集合"); }
            var selected = form.isLoginCapabilitiesPresent() ? form.getLoginCapabilities()
                : IdentityLoginCapability.legacy(source, flow == null ? "" : flow.value()).keySet();
            Map<String, String> result = new LinkedHashMap<>();
            for (String key : selected) {
                var capability = IdentityLoginCapability.fromValue(key);
                if (capability.credentialType() != IdentityCredentialType.forSource(source)) { bad("能力与真实应用不匹配"); }
                if (!old.containsKey(key) && !capability.available()) { bad("该能力的应用注册合同尚未核实"); }
                String contract = old.get(key);
                if (contract == null) {
                    contract = "CONFIGURATION".equals(capability.source().scene()) ? ""
                        : capability.source() == source && flow != null ? flow.value()
                        : dev.macula.cloud.system.enums.IdentitySourceFlow.defaultFor(capability.source()).value();
                }
                result.put(key, contract);
            }
            return result;
        } catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage()); }
    }

    /** 生成只读配置，密钥只投影配置状态。 */
    private IdentityProviderVO toVO(SysIdentityProvider entity) {
        IdentityProviderType type = providerType(entity.getProviderType());
        Map<String, String> config = readPublicConfig(entity, type);
        return toVO(entity, type, config, readAdditionalSecrets(entity, type));
    }

    private IdentityProviderVO toVO(SysIdentityProvider entity, Map<String, String> additionalSecrets) {
        IdentityProviderType type = providerType(entity.getProviderType());
        return toVO(entity, type, readPublicConfig(entity, type), additionalSecrets);
    }

    private IdentityProviderVO toVO(SysIdentityProvider entity, IdentityProviderType type,
        Map<String, String> config, Map<String, String> additionalSecrets) {
        Map<String, Boolean> configuredSecrets = new LinkedHashMap<>();
        Set<String> sourceSecrets = entity.getSourceType() == null ? Set.of()
            : type.sourceFields(IdentitySourceType.fromValue(entity.getSourceType())).stream()
                .filter(IdentityProviderType.Field::isSecret).map(IdentityProviderType.Field::getKey).collect(Collectors.toSet());
        for (String secretField : sourceSecrets) {
            configuredSecrets.put(secretField, secretField.equals(type.credentialField())
                ? StringUtils.hasText(entity.getAppSecret())
                : StringUtils.hasText(additionalSecrets.get(secretField)));
        }
        var vo = converter.toVO(entity, type.category().code(), entity.getSourceType() != null
            && IdentitySourceType.fromValue(entity.getSourceType()).browser() ? type.callbackUrl() : null, config,
            configuredSecrets);
        if (entity.getSourceType() != null) {
            var source = IdentitySourceType.fromValue(entity.getSourceType());
            vo.setCredentialType(IdentityCredentialType.forSource(source).value());
            var capabilities = IdentityLoginCapability.decode(entity.getCapabilitiesJson(), source, entity.getIdentityFlow());
            vo.setLoginCapabilities(capabilities.keySet());
            if (capabilities.keySet().stream().map(IdentityLoginCapability::fromValue).anyMatch(IdentityLoginCapability::browser)) {
                vo.setCallbackUrl(type.callbackUrl());
            }
        }
        vo.setBindingMappings(dev.macula.cloud.system.enums.IdentityBindingMappings.toWire(
            dev.macula.cloud.system.enums.IdentityBindingMappings.decode(entity.getBindingMappingsJson(),
                entity.getBindingSourceField(), entity.getBindingTargetField(), entity.getBindingRule())));
        return vo;
    }

    private Map<String, String> readPublicConfig(SysIdentityProvider entity, IdentityProviderType type) {
        Map<String, String> config = new LinkedHashMap<>();
        if (StringUtils.hasText(entity.getConfigJson())) {
            Object parsed;
            try {
                parsed = json.readValue(entity.getConfigJson(), Object.class);
            } catch (RuntimeException ex) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "身份源历史配置不是有效 JSON 对象");
            }
            if (!(parsed instanceof Map<?, ?> values)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "身份源历史配置不是有效 JSON 对象");
            }
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (!(entry.getKey() instanceof String key) || !type.requiredFields().contains(key) &&
                    !type.optionalFields().contains(key) || !(entry.getValue() instanceof String)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "身份源历史配置含不兼容字段");
                }
                config.put((String) entry.getKey(), (String) entry.getValue());
            }
        }
        String identifier = config.get(type.identifierField());
        if (StringUtils.hasText(entity.getAppId())) {
            if (StringUtils.hasText(identifier) && !identifier.equals(entity.getAppId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "身份源历史配置标识不一致");
            }
        }
        return config;
    }

    /**
     * 读取附加密钥并兼容旧加密空值；损坏JSON及未知字段仍拒绝，不放宽密钥白名单。
     * @param entity 已由持久化拦截器解密的来源
     * @param type 提供商字段目录
     * @return 已校验的附加密钥，历史JSON null视为空配置
     */
    private Map<String, String> readAdditionalSecrets(SysIdentityProvider entity, IdentityProviderType type) {
        Map<String, String> secrets = new LinkedHashMap<>();
        if (!StringUtils.hasText(entity.getSecretConfigJson())) {
            return secrets;
        }
        Object parsed;
        try {
            parsed = json.readValue(entity.getSecretConfigJson(), Object.class);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "身份源附加密钥不是有效 JSON 对象");
        }
        // 历史空引用可能被加密为字符串 "null"；它不代表存在任何附加凭据。
        if (parsed == null) {
            return secrets;
        }
        if (!(parsed instanceof Map<?, ?> values)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "身份源附加密钥不是有效 JSON 对象");
        }
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String key) || !type.additionalSecretFields().contains(key) ||
                !(entry.getValue() instanceof String value) || value.length() > 32000) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "身份源附加密钥含不兼容字段");
            }
            if (StringUtils.hasText(value)) {
                secrets.put(key, value);
            }
        }
        return secrets;
    }

    private String normalizeExternalRedirectUri(String value) {
        try {
            URI uri = URI.create(value).normalize();
            if (!uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme())
                || !StringUtils.hasText(uri.getHost()) || uri.getUserInfo() != null || uri.getFragment() != null) {
                bad("端侧浏览器回调地址无效");
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException ex) {
            bad("端侧浏览器回调地址无效");
            return value;
        }
    }

    private IdentityProviderType providerType(String value) {
        try {
            return IdentityProviderType.fromCode(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的身份提供商类型");
        }
    }

    private IdentityProviderCategory category(String value) {
        try {
            return IdentityProviderCategory.fromCode(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "认证源分类无效");
        }
    }

    private void bad(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
