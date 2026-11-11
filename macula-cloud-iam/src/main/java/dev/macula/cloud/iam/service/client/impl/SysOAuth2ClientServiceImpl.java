/*
 * Copyright (c) 2023 Macula
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

package dev.macula.cloud.iam.service.client.impl;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import dev.macula.cloud.iam.mapper.SysApplicationProtocolConfigMapper;
import dev.macula.cloud.iam.mapper.SysOAuth2ClientExternalProviderMapper;
import dev.macula.cloud.iam.mapper.SysOAuth2ClientMapper;
import dev.macula.cloud.iam.pojo.entity.SysApplicationProtocolConfig;
import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.pojo.entity.SysOAuth2ClientExternalProvider;
import dev.macula.cloud.iam.protocol.oauth2.OAuth2ClientProtocolPolicy;
import dev.macula.cloud.iam.service.client.SysOAuth2ClientService;
import dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration;
import dev.macula.cloud.system.protocol.ApplicationProtocolRegistry;
import dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration;
import dev.macula.cloud.system.protocol.OAuth2ProtocolSettings;
import dev.macula.cloud.system.security.OAuth2ClientCacheNames;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code SysOauth2ClientServiceImpl} 客户端服务
 *
 * @author rain
 * @since 2023/4/11 19:42
 */
@Service
public class SysOAuth2ClientServiceImpl extends ServiceImpl<SysOAuth2ClientMapper, SysOAuth2Client>
        implements SysOAuth2ClientService {

    private final SysApplicationProtocolConfigMapper protocols;
    private final SysOAuth2ClientExternalProviderMapper externalProviders;
    private final JsonMapper json = JsonMapper.builder()
            .build();

    /**
     * 生产读取在同一RR事务中组装协议和来源映射。
     */
    @Autowired
    public SysOAuth2ClientServiceImpl(SysApplicationProtocolConfigMapper protocols,
            SysOAuth2ClientExternalProviderMapper externalProviders) {
        this.protocols = protocols;
        this.externalProviders = externalProviders;
    }

    /**
     * 兼容无外部来源的显式测试构造，不为端内配置猜测默认来源。
     */
    public SysOAuth2ClientServiceImpl(SysApplicationProtocolConfigMapper protocols) {
        this(protocols, null);
    }

    /**
     * 只读新协议子配置；旧共享列不能补齐缺失的协议。
     */
    @Override
    @Transactional(readOnly = true, isolation =
            Isolation.REPEATABLE_READ)
    public SysOAuth2Client getClientByClientId(String clientId) {
        SysOAuth2Client client = this.getOne(new QueryWrapper<SysOAuth2Client>().eq("client_id", clientId));
        if (client == null) {
            return null;
        }
        var rows = protocols.selectList(
                new LambdaQueryWrapper<SysApplicationProtocolConfig>()
                        .eq(SysApplicationProtocolConfig::getApplicationId,
                                client.getId())
                        .orderByAsc(SysApplicationProtocolConfig::getProtocol));
        try {
            client.setProtocolConfigurations(rows.stream().map(row -> {
                if (row.getConfiguration().getBytes(StandardCharsets.UTF_8).length >
                        ApplicationProtocolRegistry.MAX_CONFIG_BYTES) {
                    throw new IllegalArgumentException("Invalid protocol payload");
                }
                return new ApplicationProtocolConfiguration(row.getProtocol(),
                        row.getSchemaVersion(),
                        Boolean.TRUE.equals(row.getEnabled()), json.readValue(row.getConfiguration(),
                        OAuth2ProtocolSettings.class));
            }).toList());
            if (externalProviders != null) {
                client.setExternalProviderBindings(externalProviders.selectList(
                                new LambdaQueryWrapper<SysOAuth2ClientExternalProvider>()
                                        .eq(SysOAuth2ClientExternalProvider::getClientId,
                                                clientId)).stream()
                        .collect(Collectors.toUnmodifiableMap(
                                SysOAuth2ClientExternalProvider::getCodeType,
                                SysOAuth2ClientExternalProvider::getIdentityProviderId)));
            }
            return OAuth2ClientProtocolPolicy.enabled(client) ? client : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @Override
    @Transactional(readOnly = true, isolation =
            Isolation.REPEATABLE_READ)
    @Cacheable(value = OAuth2ClientCacheNames.TOKEN_ORIGIN_INDEX, key = "'" + OAuth2ClientCacheNames.ORIGIN_INDEX_KEY +
            "'", sync = true)
    public Set<String> allowedOrigins() {
        LinkedHashSet<String> origins = new LinkedHashSet<>();
        this.list(new QueryWrapper<SysOAuth2Client>().select("client_id").eq("configuration_status", "ENABLED"))
                .forEach(row -> {
                    SysOAuth2Client client = getClientByClientId(row.getClientId());
                    if (client != null) {
                        client.getProtocolConfigurations().stream()
                                .filter(ApplicationProtocolConfiguration::enabled)
                                .map(OAuth2ProtocolConfiguration::settings)
                                .forEach(settings -> origins.addAll(settings.getAllowedOrigins()));
                    }
                });
        return Collections.unmodifiableSet(origins);
    }

    @Override
    public boolean isAllowedOrigin(String clientId, String origin) {
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(origin)) {
            return false;
        }
        SysOAuth2Client client = getClientByClientId(clientId);
        return client != null && client.getProtocolConfigurations().stream()
                .filter(ApplicationProtocolConfiguration::enabled)
                .anyMatch(configuration -> OAuth2ProtocolConfiguration.settings(
                        configuration).getAllowedOrigins().contains(origin));
    }
}
