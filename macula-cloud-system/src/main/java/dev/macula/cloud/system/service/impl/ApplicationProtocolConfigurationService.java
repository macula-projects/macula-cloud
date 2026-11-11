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
import dev.macula.cloud.system.mapper.SysApplicationProtocolConfigMapper;
import dev.macula.cloud.system.pojo.entity.SysApplicationProtocolConfig;
import dev.macula.cloud.system.pojo.entity.SysOAuth2ClientConfig;
import dev.macula.cloud.system.form.OAuth2ClientForm;
import dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration;
import dev.macula.cloud.system.protocol.ApplicationProtocolRegistry;
import dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration;
import dev.macula.cloud.system.protocol.OAuth2ProtocolSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * 协议子配置的有界解析与持久化；调用方必须先锁定应用行，且参与同一事务。
 * @author Rain
 * @since 6.1.0
 */
@Service
@RequiredArgsConstructor
public class ApplicationProtocolConfigurationService {
    private final SysApplicationProtocolConfigMapper mapper;
    private final dev.macula.cloud.system.mapper.SysOAuth2ClientExternalProviderMapper externalProviders;
    private final JsonMapper json = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    /** 只读配置不以主表旧共享字段降级。 */
    public List<ApplicationProtocolConfiguration> list(Long applicationId) {
        return rows(applicationId).stream().map(row -> {
            String value = row.getConfiguration();
            if (value == null || value.getBytes(StandardCharsets.UTF_8).length > ApplicationProtocolRegistry.MAX_CONFIG_BYTES) {
                throw new IllegalStateException("协议配置载荷无效");
            }
            ApplicationProtocolConfiguration result = new ApplicationProtocolConfiguration(row.getProtocol(),
                row.getSchemaVersion(), Boolean.TRUE.equals(row.getEnabled()), json.readValue(value, OAuth2ProtocolSettings.class));
            ApplicationProtocolRegistry.require(result);
            return result;
        }).toList();
    }

    /** 信封和类型对关闭的草稿同样生效，不接受未知字段或超大载荷。 */
    public void validate(List<ApplicationProtocolConfiguration> configurations, String applicationType, boolean applicationEnabled) {
        if (configurations == null || configurations.size() > 1) {
            throw new IllegalArgumentException("协议配置集合无效");
        }
        Set<String> seen = new HashSet<>();
        for (ApplicationProtocolConfiguration configuration : configurations) {
            ApplicationProtocolRegistry.require(configuration);
            if (!seen.add(configuration.protocol()) || json.writeValueAsBytes(configuration.settings()).length > ApplicationProtocolRegistry.MAX_CONFIG_BYTES) {
                throw new IllegalArgumentException("重复协议或载荷超出限制");
            }
            OAuth2ProtocolConfiguration.validateWritable(applicationEnabled ? configuration : new ApplicationProtocolConfiguration(
                configuration.protocol(), configuration.schemaVersion(), false, configuration.settings()), applicationType);
        }
    }

    /** 主/子记录构成同一ETag，不输出摘要、字段或秘密材料。 */
    public String version(SysOAuth2ClientConfig application) {
        try {
            String value = json.writeValueAsString(application) + json.writeValueAsString(rows(application.getId()))
                + json.writeValueAsString(externalProviders.selectList(new LambdaQueryWrapper<dev.macula.cloud.system.pojo.entity.SysOAuth2ClientExternalProvider>()
                    .eq(dev.macula.cloud.system.pojo.entity.SysOAuth2ClientExternalProvider::getClientId, application.getClientId())
                    .orderByAsc(dev.macula.cloud.system.pojo.entity.SysOAuth2ClientExternalProvider::getCodeType)));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("配置版本不可用");
        }
    }

    /** 在已持有应用锁时比较完整快照，不能只比较当前Tab。 */
    public void checkVersion(SysOAuth2ClientConfig application, String expected) {
        if (expected == null || !version(application).equals(expected)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "应用或协议已变化，请刷新后重试");
        }
    }

    /** 替换子配置与主应用更新由外部事务统一提交，不自行提交缓存。 */
    public void replace(Long applicationId, List<ApplicationProtocolConfiguration> configurations) {
        mapper.delete(new LambdaQueryWrapper<SysApplicationProtocolConfig>().eq(SysApplicationProtocolConfig::getApplicationId, applicationId));
        for (ApplicationProtocolConfiguration configuration : configurations) {
            SysApplicationProtocolConfig row = new SysApplicationProtocolConfig();
            row.setApplicationId(applicationId);
            row.setProtocol(configuration.protocol());
            row.setSchemaVersion(configuration.schemaVersion());
            row.setEnabled(configuration.enabled());
            row.setConfiguration(json.writeValueAsString(configuration.settings()));
            mapper.insert(row);
        }
    }

    /** 管理端点规则独立于默认协议，公开说明不含凭据。 */
    public String management(List<ApplicationProtocolConfiguration> configurations) {
        try {
            return ApplicationProtocolRegistry.management(configurations).protocol();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** 在明确协议投影上复用System原有校验，投影不写回主表旧字段。 */
    public OAuth2ClientForm project(OAuth2ClientForm application, ApplicationProtocolConfiguration configuration) {
        OAuth2ClientForm result = new OAuth2ClientForm();
        BeanUtils.copyProperties(application, result);
        BeanUtils.copyProperties(OAuth2ProtocolConfiguration.settings(configuration), result);
        result.setProtocolConfigurations(List.of(configuration));
        result.setEnabledProtocols(Set.of(configuration.protocol()));
        result.setDefaultProtocol(configuration.protocol());
        result.setConfigurationStatus(configuration.enabled() && "ENABLED".equals(application.getConfigurationStatus()) ? "ENABLED" : "DRAFT");
        result.setClientSecret(null);
        Set<String> grants = OAuth2ProtocolConfiguration.csv(result.getAuthorizationGrantTypes());
        if (!grants.contains("authorization_code")) {
            result.setBrowserLoginProviderTypes(Set.of());
            result.setBrowserProviderBindings(java.util.Map.of());
            result.setBrowserCapabilityBindings(java.util.Map.of());
            result.setDirectLoginProviderType(null);
        }
        if (!grants.contains("urn:macula:params:oauth:grant-type:external_code")) {
            result.setExternalCodeTypes(Set.of());
            result.setExternalProviderBindings(java.util.Map.of());
        }
        return result;
    }

    /** 排序稳定，便于乐观并发快照比较。 */
    private List<SysApplicationProtocolConfig> rows(Long applicationId) {
        if (applicationId == null) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<SysApplicationProtocolConfig>()
            .eq(SysApplicationProtocolConfig::getApplicationId, applicationId).orderByAsc(SysApplicationProtocolConfig::getProtocol));
    }
}
