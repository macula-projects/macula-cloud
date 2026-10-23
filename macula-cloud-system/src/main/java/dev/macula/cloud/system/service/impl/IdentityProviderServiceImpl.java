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
        return new IdentityProviderCatalogVO(categories, providers);
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

    @Override
    @Transactional
    public IdentityProviderVO save(Long id, IdentityProviderForm form) {
        IdentityProviderType type = providerType(form.getProviderType());
        if (!type.category().code().equals(form.getCategory())) {
            bad("认证源类型与分类不匹配");
        }
        if (!StringUtils.hasText(form.getName()) || form.getName().length() > 100) {
            bad("认证源名称无效");
        }
        if (form.getConfig() == null) {
            bad("配置必须为 JSON 对象");
        }

        SysIdentityProvider entity = id == null ? new SysIdentityProvider() : mapper.selectById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "身份提供商不存在");
        }
        if (id != null && !type.code().equals(entity.getProviderType())) {
            bad("认证源类型不可修改");
        }
        if (id != null) {
            readPublicConfig(entity, type);
        }

        Map<String, String> publicConfig = new LinkedHashMap<>();
        Map<String, String> additionalSecrets = readAdditionalSecrets(entity, type);
        String credential = null;
        for (Map.Entry<String, Object> entry : form.getConfig().entrySet()) {
            if (!type.allowedFields().contains(entry.getKey()) || !(entry.getValue() instanceof String)) {
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
        for (String required : type.requiredFields()) {
            if (!StringUtils.hasText(publicConfig.get(required))) {
                bad("缺少必需配置：" + required);
            }
        }
        if (!StringUtils.hasText(credential) && !StringUtils.hasText(entity.getAppSecret())) {
            bad("缺少必需配置：" + type.credentialField());
        }
        for (String requiredSecret : type.requiredSecretFields()) {
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
        if (Boolean.TRUE.equals(form.getEnabled())) {
            long enabledDuplicates = mapper.selectCount(new LambdaQueryWrapper<SysIdentityProvider>()
                .eq(SysIdentityProvider::getProviderType, type.code())
                .eq(SysIdentityProvider::getEnabled, true)
                .ne(id != null, SysIdentityProvider::getId, id));
            if (enabledDuplicates > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "同类型仅允许一个启用配置");
            }
        }

        converter.updateEntity(form, entity);
        entity.setName(form.getName().trim());
        entity.setEnabled(Boolean.TRUE.equals(form.getEnabled()));
        entity.setVisible(Boolean.TRUE.equals(form.getVisible()));
        entity.setAppId(publicConfig.get(type.identifierField()));
        entity.setConfigJson(json.writeValueAsString(publicConfig));
        if (StringUtils.hasText(credential)) {
            entity.setAppSecret(credential);
        }
        entity.setSecretConfigJson(additionalSecrets.isEmpty() ? null : json.writeValueAsString(additionalSecrets));
        try {
            if (id == null) {
                mapper.insert(entity);
            } else {
                mapper.updateById(entity);
            }
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "身份提供商配置冲突", ex);
        }
        return toVO(entity, additionalSecrets);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "身份提供商不存在");
        }
    }

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
        for (String secretField : type.secretFields()) {
            configuredSecrets.put(secretField, secretField.equals(type.credentialField())
                ? StringUtils.hasText(entity.getAppSecret())
                : StringUtils.hasText(additionalSecrets.get(secretField)));
        }
        return converter.toVO(entity, type.category().code(), type.callbackUrl(), config,
            configuredSecrets);
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
            config.putIfAbsent(type.identifierField(), entity.getAppId());
        }
        return config;
    }

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
