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
package dev.macula.cloud.iam.config.oauth2;

import dev.macula.cloud.iam.mapper.SysOAuth2ClientExternalProviderMapper;
import dev.macula.cloud.iam.protocol.oauth2.ProtocolAwareRegisteredClientRepository;
import dev.macula.cloud.iam.protocol.oauth2.persistence.MaculaOAuth2AuthorizationConsentService;
import dev.macula.cloud.iam.protocol.oauth2.persistence.MaculaOAuth2AuthorizationService;
import dev.macula.cloud.iam.protocol.oauth2.persistence.MaculaRegisteredClientRepository;
import dev.macula.cloud.iam.service.client.SysOAuth2ClientService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * 授权与Client仓库装配；基础恢复仓库与无缓存请求视图的依赖方向保持显式。
 *
 * @author Macula
 * @since 6.1.0
 */
@Configuration(proxyBeanMethods = false)
public class OAuth2PersistenceConfiguration {
    /**
     * 同意记录继续使用既有Redis实现。
     */
    @Bean
    OAuth2AuthorizationConsentService oauth2AuthorizationConsentService(RedisTemplate<String, Object> redis) {
        return new MaculaOAuth2AuthorizationConsentService(redis);
    }

    /**
     * 授权恢复只依赖基础仓库，不能反向请求协议视图。
     */
    @Bean
    OAuth2AuthorizationService oauth2AuthorizationService(RedisTemplate<String, Object> redis,
            @Qualifier("baseRegisteredClientRepository") RegisteredClientRepository base,
            SysOAuth2ClientService clients) {
        return new MaculaOAuth2AuthorizationService(redis, base, clients);
    }

    /**
     * 基础Client身份用于持久化恢复，避免依赖当前HTTP请求。
     */
    @Bean("baseRegisteredClientRepository")
    RegisteredClientRepository baseRegisteredClientRepository(SysOAuth2ClientService clients,
            SysOAuth2ClientExternalProviderMapper mappings) {
        return new MaculaRegisteredClientRepository(clients, mappings);
    }

    /**
     * 协议视图按当前请求收窄；延迟授权服务引用保持无循环装配。
     */
    @Bean
    @Primary
    RegisteredClientRepository registeredClientRepository(
            @Qualifier("baseRegisteredClientRepository") RegisteredClientRepository base,
            SysOAuth2ClientService clients, ObjectProvider<OAuth2AuthorizationService> authorizations) {
        return new ProtocolAwareRegisteredClientRepository(base, clients, authorizations);
    }
}
