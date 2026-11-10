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

package dev.macula.cloud.iam.service.support.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import dev.macula.cloud.iam.mapper.SysOAuth2ClientMapper;
import dev.macula.cloud.iam.pojo.entity.SysOAuth2Client;
import dev.macula.cloud.iam.service.support.SysOAuth2ClientService;
import dev.macula.cloud.system.security.OAuth2ClientCacheNames;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@code SysOauth2ClientServiceImpl} 客户端服务
 *
 * @author rain
 * @since 2023/4/11 19:42
 */
@Service
public class SysOAuth2ClientServiceImpl extends ServiceImpl<SysOAuth2ClientMapper, SysOAuth2Client>
    implements SysOAuth2ClientService {

    private final dev.macula.cloud.iam.mapper.SysApplicationProtocolConfigMapper protocols;
    private final dev.macula.cloud.iam.mapper.SysOAuth2ClientExternalProviderMapper externalProviders;
    /** 生产读取在同一RR事务中组装协议和来源映射。 */
    @org.springframework.beans.factory.annotation.Autowired
    public SysOAuth2ClientServiceImpl(dev.macula.cloud.iam.mapper.SysApplicationProtocolConfigMapper protocols,
        dev.macula.cloud.iam.mapper.SysOAuth2ClientExternalProviderMapper externalProviders) {
        this.protocols = protocols;
        this.externalProviders = externalProviders;
    }
    /** 兼容无外部来源的显式测试构造，不为端内配置猜测默认来源。 */
    public SysOAuth2ClientServiceImpl(dev.macula.cloud.iam.mapper.SysApplicationProtocolConfigMapper protocols) { this(protocols, null); }
    private final tools.jackson.databind.json.JsonMapper json = tools.jackson.databind.json.JsonMapper.builder().build();

    /** 只读新协议子配置；旧共享列不能补齐缺失的协议。 */
    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public SysOAuth2Client getClientByClientId(String clientId) {
        SysOAuth2Client client = this.getOne(new QueryWrapper<SysOAuth2Client>().eq("client_id", clientId));
        if (client == null) { return null; }
        var rows = protocols.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<dev.macula.cloud.iam.pojo.entity.SysApplicationProtocolConfig>()
            .eq(dev.macula.cloud.iam.pojo.entity.SysApplicationProtocolConfig::getApplicationId, client.getId())
            .orderByAsc(dev.macula.cloud.iam.pojo.entity.SysApplicationProtocolConfig::getProtocol));
        try {
            client.setProtocolConfigurations(rows.stream().map(row -> {
                if (row.getConfiguration().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > dev.macula.cloud.system.protocol.ApplicationProtocolRegistry.MAX_CONFIG_BYTES) {
                    throw new IllegalArgumentException("Invalid protocol payload");
                }
                return new dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration(row.getProtocol(), row.getSchemaVersion(),
                    Boolean.TRUE.equals(row.getEnabled()), json.readValue(row.getConfiguration(), dev.macula.cloud.system.protocol.OAuth2ProtocolSettings.class));
            }).toList());
            if (externalProviders != null) {
                client.setExternalProviderBindings(externalProviders.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<dev.macula.cloud.iam.pojo.entity.SysOAuth2ClientExternalProvider>()
                    .eq(dev.macula.cloud.iam.pojo.entity.SysOAuth2ClientExternalProvider::getClientId, clientId)).stream()
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(dev.macula.cloud.iam.pojo.entity.SysOAuth2ClientExternalProvider::getCodeType,
                        dev.macula.cloud.iam.pojo.entity.SysOAuth2ClientExternalProvider::getIdentityProviderId)));
            }
            return dev.macula.cloud.iam.protocol.oauth2.OAuth2ClientProtocolPolicy.enabled(client) ? client : null;
        } catch (RuntimeException ex) { return null; }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    @Cacheable(value = OAuth2ClientCacheNames.TOKEN_ORIGIN_INDEX, key = "'" + OAuth2ClientCacheNames.ORIGIN_INDEX_KEY + "'", sync = true)
    public Set<String> allowedOrigins() {
        LinkedHashSet<String> origins = new LinkedHashSet<>();
        this.list(new QueryWrapper<SysOAuth2Client>().select("client_id").eq("configuration_status", "ENABLED"))
            .forEach(row -> {
                SysOAuth2Client client = getClientByClientId(row.getClientId());
                if (client != null) {
                    client.getProtocolConfigurations().stream().filter(dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration::enabled)
                        .map(dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration::settings)
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
            .filter(dev.macula.cloud.system.protocol.ApplicationProtocolConfiguration::enabled)
            .anyMatch(configuration -> dev.macula.cloud.system.protocol.OAuth2ProtocolConfiguration.settings(configuration).getAllowedOrigins().contains(origin));
    }
}
